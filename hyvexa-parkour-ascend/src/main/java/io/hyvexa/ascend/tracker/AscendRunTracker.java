package io.hyvexa.ascend.tracker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.tutorial.TutorialTriggerService;

import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.hud.ToastType;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.AscendSettingsStore;
import io.hyvexa.ascend.util.AscendInventoryUtils;
import io.hyvexa.ascend.util.MapUnlockHelper;
import io.hyvexa.ascend.ghost.GhostRecorder;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.common.visibility.EntityVisibilityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AscendRunTracker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double FINISH_RADIUS_SQ = 1.0; // 1.0^2 (horizontal XZ)
    private static final double FINISH_VERTICAL_RANGE = 1.5; // vertical tolerance for finish detection
    private static final double START_DETECTION_RADIUS_SQ = 2.25; // 1.5^2 (horizontal XZ)
    private static final double START_VERTICAL_RANGE = 3.0; // vertical tolerance for walk-on start detection
    private static final double MOVEMENT_THRESHOLD_SQ = 0.01; // 0.1^2 - minimum movement to start run
    private static final long POST_COMPLETION_FREEZE_MS = 500; // 0.5s freeze after completing a run

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final GhostRecorder ghostRecorder;
    private final Map<UUID, ActiveRun> activeRuns = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRun> pendingRuns = new ConcurrentHashMap<>();
    private final Map<UUID, FreezeData> frozenPlayers = new ConcurrentHashMap<>();
    private volatile List<StartMapEntry> startMapEntries = List.of();

    public AscendRunTracker(AscendMapStore mapStore, AscendPlayerStore playerStore, GhostRecorder ghostRecorder) {
        this.mapStore = mapStore;
        this.playerStore = playerStore;
        this.ghostRecorder = ghostRecorder;
        rebuildStartMapCache();
    }

    public void onMapStoreChanged() {
        rebuildStartMapCache();
    }

    public void startRun(UUID playerId, String mapId) {
        AscendMap map = mapStore.getMap(mapId);
        if (map == null) {
            return;
        }

        // Cancel existing active run if present to avoid inconsistent state
        ActiveRun existing = activeRuns.remove(playerId);
        if (existing != null) {
            showRunnersAndReapplyVisibility(playerId, existing.mapId);
            if (ghostRecorder != null) {
                ghostRecorder.cancelRecording(playerId);
            }
        }

        pendingRuns.remove(playerId);
        activeRuns.put(playerId, new ActiveRun(mapId, System.currentTimeMillis()));

        // Start ghost recording
        if (ghostRecorder != null) {
            ghostRecorder.startRecording(playerId, mapId);
        }
    }

    public void setPendingRun(UUID playerId, String mapId, Vector3d startPos) {
        // Cancel any existing active run first (and show runners from old map)
        PendingRun oldPending = pendingRuns.remove(playerId);
        ActiveRun oldActive = activeRuns.remove(playerId);
        if (oldPending != null) {
            showRunnersAndReapplyVisibility(playerId, oldPending.mapId);
        }
        if (oldActive != null) {
            showRunnersAndReapplyVisibility(playerId, oldActive.mapId);
        }

        // Cancel ghost recording if there was an active run
        if (ghostRecorder != null) {
            ghostRecorder.cancelRecording(playerId);
        }

        pendingRuns.put(playerId, new PendingRun(mapId, startPos));

        // Hide all runners on this map from the player
        hideRunnersForMap(playerId, mapId);
    }

    public void cancelRun(UUID playerId) {
        PendingRun pending = pendingRuns.remove(playerId);
        ActiveRun active = activeRuns.remove(playerId);
        frozenPlayers.remove(playerId);

        // Flush deferred tutorials now that the player is out of the run loop
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getTutorialTriggerService() != null) {
            plugin.getTutorialTriggerService().flushPendingTutorials(playerId);
        }

        // Show runners from the cancelled run's map
        if (pending != null) {
            showRunnersAndReapplyVisibility(playerId, pending.mapId);
        }
        if (active != null) {
            showRunnersAndReapplyVisibility(playerId, active.mapId);
        }

        // Cancel ghost recording
        if (ghostRecorder != null) {
            ghostRecorder.cancelRecording(playerId);
        }
    }

    public String getActiveMapId(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        if (run != null) {
            return run.mapId;
        }
        PendingRun pending = pendingRuns.get(playerId);
        return pending != null ? pending.mapId : null;
    }

    public Long getElapsedTimeMs(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        if (run == null) {
            return null;
        }
        return System.currentTimeMillis() - run.startTimeMs;
    }

    public boolean isRunActive(UUID playerId) {
        return activeRuns.containsKey(playerId);
    }

    public boolean isPendingRun(UUID playerId) {
        return pendingRuns.containsKey(playerId);
    }

    public boolean isNearFinish(UUID playerId, Vector3d pos) {
        ActiveRun run = activeRuns.get(playerId);
        if (run == null) {
            return false;
        }
        AscendMap map = mapStore.getMap(run.mapId);
        if (map == null) {
            return false;
        }
        double dx = pos.getX() - map.getFinishX();
        double dy = pos.getY() - map.getFinishY();
        double dz = pos.getZ() - map.getFinishZ();
        return dx * dx + dz * dz <= FINISH_RADIUS_SQ && Math.abs(dy) <= FINISH_VERTICAL_RANGE;
    }

    public void checkPlayer(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());

        if (playerRef == null || player == null || transform == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        Vector3d pos = transform.getPosition();

        // Check void Y threshold - reset to map start if in a run, else teleport to spawn
        if (isPlayerBelowVoidThreshold(pos.getY())) {
            String mapId = getActiveMapId(playerId);
            if (mapId != null) {
                teleportToMapStart(ref, store, playerRef, mapId);
            } else {
                teleportToSpawn(ref, store);
            }
            return;
        }

        // Check if player is frozen (post-completion freeze) - skip processing without re-teleporting
        FreezeData freezeData = frozenPlayers.get(playerId);
        if (freezeData != null) {
            if (System.currentTimeMillis() < freezeData.endTime) {
                // Still frozen - just skip processing (initial teleport already positioned the player)
                return;
            }
            // Freeze expired - remove and continue
            frozenPlayers.remove(playerId);
        }

        // Check for pending run - start timer when player moves from start position
        PendingRun pendingRun = pendingRuns.get(playerId);
        if (pendingRun != null) {
            double dx = pos.getX() - pendingRun.startPos.getX();
            double dy = pos.getY() - pendingRun.startPos.getY();
            double dz = pos.getZ() - pendingRun.startPos.getZ();

            if (dx * dx + dy * dy + dz * dz > MOVEMENT_THRESHOLD_SQ) {
                // Player has moved - start the actual run
                startRun(playerId, pendingRun.mapId);
            }
            return;
        }

        ActiveRun run = activeRuns.get(playerId);

        // If not in any run, check if player walked onto a map start
        if (run == null) {
            checkWalkOnStart(ref, store, playerRef, player, playerId, pos);
            return;
        }

        AscendMap map = mapStore.getMap(run.mapId);
        if (map == null) {
            activeRuns.remove(playerId);
            return;
        }

        double dx = pos.getX() - map.getFinishX();
        double dy = pos.getY() - map.getFinishY();
        double dz = pos.getZ() - map.getFinishZ();

        if (dx * dx + dz * dz <= FINISH_RADIUS_SQ && Math.abs(dy) <= FINISH_VERTICAL_RANGE) {
            completeRun(playerRef, player, run, map, ref, store);
        }
    }

    private void completeRun(PlayerRef playerRef, Player player, ActiveRun run,
                             AscendMap map, Ref<EntityStore> ref, Store<EntityStore> store) {
        UUID playerId = playerRef.getUuid();
        if (activeRuns.remove(playerId) == null) {
            return; // Already completed by a concurrent call
        }

        // Show runners again after completing the run
        showRunnersAndReapplyVisibility(playerId, run.mapId);

        // Play checkpoint sound on map completion
        int soundIndex = SoundEvent.getAssetMap().getIndex("SFX_Parkour_Checkpoint");
        if (soundIndex > SoundEvent.EMPTY_ID) {
            SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, SoundCategory.SFX);
        }

        AscendPlayerProgress.MapProgress mapProgress = playerStore.getOrCreateMapProgress(playerId, run.mapId);

        boolean firstCompletion = !mapProgress.isCompletedManually();

        mapProgress.setCompletedManually(true);
        mapProgress.setUnlocked(true);
        playerStore.markDirty(playerId);

        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();

        // Manual multiplier increment = runner's multiplier increment for this map × 5
        int runnerStars = playerStore.getRobotStars(playerId, run.mapId);
        double multiplierGainBonus = 1.0;
        double evolutionPowerBonus = 3.0;
        double baseMultiplierBonus = 0.0;
        if (plugin != null && plugin.getSummitManager() != null) {
            multiplierGainBonus = plugin.getSummitManager().getMultiplierGainBonus(playerId);
            evolutionPowerBonus = plugin.getSummitManager().getEvolutionPowerBonus(playerId);
            baseMultiplierBonus = plugin.getSummitManager().getBaseMultiplierBonus(playerId);
        }
        BigNumber runnerIncrement = AscendConstants.getRunnerMultiplierIncrement(runnerStars, multiplierGainBonus, evolutionPowerBonus, baseMultiplierBonus);

        BigNumber multiplierIncrement = runnerIncrement.multiply(BigNumber.fromDouble(5.0));

        // Calculate payout BEFORE adding multiplier (use current multiplier, not the new one)
        List<AscendMap> multiplierMaps = mapStore.listMapsSorted();
        BigNumber payout = playerStore.getCompletionPayout(playerId, multiplierMaps, AscendConstants.MULTIPLIER_SLOTS, run.mapId, BigNumber.ZERO);

        // Use atomic operations to prevent race conditions
        if (!playerStore.atomicAddVolt(playerId, payout)) {
            LOGGER.atWarning().log("Failed to add volt for manual run: " + playerId);
        }
        if (!playerStore.atomicAddTotalVoltEarned(playerId, payout)) {
            LOGGER.atWarning().log("Failed to add total volt earned for manual run: " + playerId);
        }
        if (!playerStore.atomicAddMapMultiplier(playerId, run.mapId, multiplierIncrement)) {
            LOGGER.atWarning().log("Failed to add map multiplier for manual run: " + playerId);
        }
        playerStore.incrementTotalManualRuns(playerId);
        playerStore.incrementConsecutiveManualRuns(playerId);

        // Calculate completion time and validate range
        long completionTimeMs = System.currentTimeMillis() - run.startTimeMs;
        long maxReasonableMs = map.getEffectiveBaseRunTimeMs() * 10;
        boolean validTime = completionTimeMs >= 500 && completionTimeMs <= maxReasonableMs;
        if (!validTime) {
            LOGGER.atWarning().log("Rejected suspicious completion time %dms for player %s on map %s (valid range: 500-%dms)",
                completionTimeMs, playerId, map.getId(), maxReasonableMs);
        }

        Long previousBest = playerStore.getBestTimeMs(playerId, run.mapId);
        boolean isPersonalBest = validTime && (previousBest == null || completionTimeMs < previousBest);

        if (isPersonalBest) {
            mapProgress.setBestTimeMs(completionTimeMs);
            playerStore.markDirty(playerId);
            playerStore.invalidateMapLeaderboardCache(run.mapId);
            playerStore.invalidateLeaderboardCache();
        }

        // Stop ghost recording and save if personal best
        if (ghostRecorder != null) {
            ghostRecorder.stopRecording(playerId, completionTimeMs, isPersonalBest);
        } else {
            LOGGER.atWarning().log("GhostRecorder is null - cannot save recording");
        }

        // Format completion time
        long seconds = completionTimeMs / 1000;
        long millis = completionTimeMs % 1000;
        String timeStr = String.format("%d.%03ds", seconds, millis);

        // Toast for run completion
        String toastMsg = "+" + FormatUtils.formatBigNumber(payout) + " volt | " + timeStr;
        if (isPersonalBest) {
            toastMsg += " | PB!";
        }
        showToast(playerId, ToastType.SUCCESS, toastMsg);

        // Check achievements
        if (plugin != null && plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
        }

        // Activate Momentum boost if player has AUTO_RUNNERS skill
        if (plugin != null && plugin.getAscensionManager() != null
                && plugin.getAscensionManager().hasAutoRunners(playerId)) {
            boolean wasActive = mapProgress.isMomentumActive();
            long momentumDuration;
            if (plugin.getAscensionManager().hasMomentumMastery(playerId)) {
                momentumDuration = AscendConstants.MOMENTUM_MASTERY_DURATION_MS;
            } else if (plugin.getAscensionManager().hasMomentumEndurance(playerId)) {
                momentumDuration = AscendConstants.MOMENTUM_ENDURANCE_DURATION_MS;
            } else {
                momentumDuration = AscendConstants.MOMENTUM_DURATION_MS;
            }
            mapProgress.activateMomentum(momentumDuration);
            if (!wasActive) {
                String mapName = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
                boolean hasMastery = plugin.getAscensionManager().hasMomentumMastery(playerId);
                boolean hasSurge = plugin.getAscensionManager().hasMomentumSurge(playerId);
                String momentumText = hasMastery ? "3" : (hasSurge ? "2.5" : "2");
                showToast(playerId, ToastType.ECONOMY, "Momentum: x" + momentumText + " speed on " + mapName);
            }
        }

        // Trigger first completion tutorial
        if (firstCompletion && plugin != null && plugin.getTutorialTriggerService() != null) {
            plugin.getTutorialTriggerService().checkFirstCompletion(playerId, ref);
        }

        try {
            io.hyvexa.core.analytics.AnalyticsStore.getInstance().logEvent(playerId, "ascend_manual_run",
                    "{\"map_id\":\"" + map.getId() + "\",\"time_ms\":" + completionTimeMs + "}");
        } catch (Exception e) { /* silent */ }

        Vector3d startPos = new Vector3d(map.getStartX(), map.getStartY(), map.getStartZ());
        Vector3f startRot = new Vector3f(map.getStartRotX(), map.getStartRotY(), map.getStartRotZ());
        store.addComponent(ref, Teleport.getComponentType(),
            new Teleport(store.getExternalData().getWorld(), startPos, startRot));

        // Freeze player briefly after completion (skip processing during this window)
        frozenPlayers.put(playerId, new FreezeData(
            System.currentTimeMillis() + POST_COMPLETION_FREEZE_MS));

        // Set pending run immediately so player is ready to go after freeze
        setPendingRun(playerId, map.getId(), startPos);

        // Give run items (reset + leave) since player is now in a pending run
        AscendInventoryUtils.giveRunItems(player);
    }

    public void teleportToMapStart(Ref<EntityStore> ref, Store<EntityStore> store,
                                   PlayerRef playerRef, String mapId) {
        AscendMap map = mapStore.getMap(mapId);
        if (map == null) {
            return;
        }

        Vector3d pos = new Vector3d(map.getStartX(), map.getStartY(), map.getStartZ());
        Vector3f rot = new Vector3f(map.getStartRotX(), map.getStartRotY(), map.getStartRotZ());

        store.addComponent(ref, Teleport.getComponentType(),
            new Teleport(store.getExternalData().getWorld(), pos, rot));

        // Set pending run - timer will start when player moves
        setPendingRun(playerRef.getUuid(), mapId, pos);
    }

    private void checkWalkOnStart(Ref<EntityStore> ref, Store<EntityStore> store,
                                   PlayerRef playerRef, Player player, UUID playerId, Vector3d pos) {
        // Find if player is standing on any cached map start position.
        for (StartMapEntry entry : startMapEntries) {
            AscendMap map = entry.map;
            double dx = pos.getX() - entry.startX;
            double dy = pos.getY() - entry.startY;
            double dz = pos.getZ() - entry.startZ;

            if (dx * dx + dz * dz <= START_DETECTION_RADIUS_SQ && Math.abs(dy) <= START_VERTICAL_RANGE) {
                // Check if map is blocked by active challenge
                ParkourAscendPlugin challengePlugin = ParkourAscendPlugin.getInstance();
                if (challengePlugin != null && challengePlugin.getChallengeManager() != null
                        && challengePlugin.getChallengeManager().isMapBlocked(playerId, map.getDisplayOrder())) {
                    // Teleport player back to spawn
                    if (challengePlugin.getSettingsStore() != null && challengePlugin.getSettingsStore().hasSpawnPosition()) {
                        Vector3d spawnPos = challengePlugin.getSettingsStore().getSpawnPosition();
                        Vector3f spawnRot = challengePlugin.getSettingsStore().getSpawnRotation();
                        store.addComponent(ref, Teleport.getComponentType(),
                            new Teleport(store.getExternalData().getWorld(), spawnPos, spawnRot));
                    }
                    player.sendMessage(Message.raw("[Ascend] This map is locked during your active challenge!")
                        .color(SystemMessageUtils.ERROR));
                    return;
                }

                // Player is on this map's start - check if unlocked
                MapUnlockHelper.UnlockResult unlockResult = MapUnlockHelper.checkAndEnsureUnlock(
                    playerId, map, playerStore, mapStore);
                if (!unlockResult.unlocked) {
                    // Map not unlocked - teleport player back to spawn
                    ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
                    if (plugin != null) {
                        AscendSettingsStore settingsStore = plugin.getSettingsStore();
                        if (settingsStore != null && settingsStore.hasSpawnPosition()) {
                            Vector3d spawnPos = settingsStore.getSpawnPosition();
                            Vector3f spawnRot = settingsStore.getSpawnRotation();
                            store.addComponent(ref, Teleport.getComponentType(),
                                new Teleport(store.getExternalData().getWorld(), spawnPos, spawnRot));
                        }
                    }

                    // Build message with previous map requirement info
                    AscendMap previousMap = MapUnlockHelper.getPreviousMap(map, mapStore);
                    String prevName = previousMap != null && previousMap.getName() != null && !previousMap.getName().isBlank()
                        ? previousMap.getName() : "previous map";
                    player.sendMessage(Message.raw("[Ascend] This map is locked! Upgrade the runner on " + prevName
                        + " to speed level " + AscendConstants.MAP_UNLOCK_REQUIRED_RUNNER_LEVEL + " first. Open /ascend to manage runners.")
                        .color(SystemMessageUtils.ERROR));
                    return;
                }

                // Start the run for this map
                Vector3d startPos = new Vector3d(map.getStartX(), map.getStartY(), map.getStartZ());
                setPendingRun(playerId, map.getId(), startPos);

                // Give run items (reset + leave)
                AscendInventoryUtils.giveRunItems(player);

                // Only trigger for the first matching map
                return;
            }
        }
    }

    private void rebuildStartMapCache() {
        List<StartMapEntry> entries = new ArrayList<>();
        for (AscendMap map : mapStore.listMaps()) {
            if (map == null) {
                continue;
            }
            if (map.getStartX() == 0 && map.getStartY() == 0 && map.getStartZ() == 0) {
                continue;
            }
            entries.add(new StartMapEntry(map));
        }
        startMapEntries = entries;
    }

    private static class ActiveRun {
        final String mapId;
        final long startTimeMs;

        ActiveRun(String mapId, long startTimeMs) {
            this.mapId = mapId;
            this.startTimeMs = startTimeMs;
        }
    }

    private static class PendingRun {
        final String mapId;
        final Vector3d startPos;

        PendingRun(String mapId, Vector3d startPos) {
            this.mapId = mapId;
            this.startPos = startPos;
        }
    }

    private static class FreezeData {
        final long endTime;

        FreezeData(long endTime) {
            this.endTime = endTime;
        }
    }

    private static class StartMapEntry {
        final AscendMap map;
        final double startX;
        final double startY;
        final double startZ;

        StartMapEntry(AscendMap map) {
            this.map = map;
            this.startX = map.getStartX();
            this.startY = map.getStartY();
            this.startZ = map.getStartZ();
        }
    }

    private boolean isPlayerBelowVoidThreshold(double currentY) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return false;
        }
        AscendSettingsStore settingsStore = plugin.getSettingsStore();
        if (settingsStore == null) {
            return false;
        }
        Double threshold = settingsStore.getVoidYThreshold();
        return threshold != null && Double.isFinite(threshold) && currentY <= threshold;
    }

    private void teleportToSpawn(Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        AscendSettingsStore settingsStore = plugin.getSettingsStore();
        if (settingsStore == null || !settingsStore.hasSpawnPosition()) {
            return;
        }
        Vector3d spawnPos = settingsStore.getSpawnPosition();
        Vector3f spawnRot = settingsStore.getSpawnRotation();
        store.addComponent(ref, Teleport.getComponentType(),
            new Teleport(store.getExternalData().getWorld(), spawnPos, spawnRot));
    }

    private void showToast(UUID playerId, ToastType type, String message) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            AscendHudManager hm = plugin.getHudManager();
            if (hm != null) {
                hm.showToast(playerId, type, message);
            }
        }
    }

    private void hideRunnersForMap(UUID viewerId, String mapId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        RobotManager robotManager = plugin.getRobotManager();
        if (robotManager == null) {
            return;
        }
        EntityVisibilityManager visibilityManager = EntityVisibilityManager.get();
        // Only hide other players' runners - the viewer should still see their own
        for (UUID runnerUuid : robotManager.getRunnerUuidsForMapExcludingOwner(mapId, viewerId)) {
            visibilityManager.hideEntity(viewerId, runnerUuid);
        }
    }

    private void showRunnersAndReapplyVisibility(UUID playerId, String mapId) {
        showRunnersForMap(playerId, mapId);
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getRobotManager() != null) {
            plugin.getRobotManager().applyRunnerVisibility(playerId);
        }
    }

    private void showRunnersForMap(UUID viewerId, String mapId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        RobotManager robotManager = plugin.getRobotManager();
        if (robotManager == null) {
            return;
        }
        EntityVisibilityManager visibilityManager = EntityVisibilityManager.get();
        for (UUID runnerUuid : robotManager.getRunnerUuidsForMap(mapId)) {
            visibilityManager.showEntity(viewerId, runnerUuid);
        }
    }
}
