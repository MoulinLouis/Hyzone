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

    public AscendRunTracker(AscendMapStore mapStore, AscendPlayerStore playerStore, GhostRecorder ghostRecorder) {
        this.mapStore = mapStore;
        this.playerStore = playerStore;
        this.ghostRecorder = ghostRecorder;
    }

    public void startRun(UUID playerId, String mapId) {
        AscendMap map = mapStore.getMap(mapId);
        if (map == null) {
            return;
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
            showRunnersForMap(playerId, oldPending.mapId);
            // Re-apply "hide other runners" setting
            {
                ParkourAscendPlugin p = ParkourAscendPlugin.getInstance();
                if (p != null && p.getRobotManager() != null) {
                    p.getRobotManager().applyRunnerVisibility(playerId);
                }
            }
        }
        if (oldActive != null) {
            showRunnersForMap(playerId, oldActive.mapId);
            // Re-apply "hide other runners" setting
            {
                ParkourAscendPlugin p = ParkourAscendPlugin.getInstance();
                if (p != null && p.getRobotManager() != null) {
                    p.getRobotManager().applyRunnerVisibility(playerId);
                }
            }
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
            showRunnersForMap(playerId, pending.mapId);
            // Re-apply "hide other runners" setting
            {
                ParkourAscendPlugin p = ParkourAscendPlugin.getInstance();
                if (p != null && p.getRobotManager() != null) {
                    p.getRobotManager().applyRunnerVisibility(playerId);
                }
            }
        }
        if (active != null) {
            showRunnersForMap(playerId, active.mapId);
            // Re-apply "hide other runners" setting
            {
                ParkourAscendPlugin p = ParkourAscendPlugin.getInstance();
                if (p != null && p.getRobotManager() != null) {
                    p.getRobotManager().applyRunnerVisibility(playerId);
                }
            }
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
        showRunnersForMap(playerId, run.mapId);
        // Re-apply "hide other runners" setting
        {
            ParkourAscendPlugin p = ParkourAscendPlugin.getInstance();
            if (p != null && p.getRobotManager() != null) {
                p.getRobotManager().applyRunnerVisibility(playerId);
            }
        }

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

        // Manual multiplier increment = runner's multiplier increment for this map × 2
        int runnerStars = playerStore.getRobotStars(playerId, run.mapId);
        double multiplierGainBonus = 1.0;
        double evolutionPowerBonus = 3.0;
        if (plugin != null && plugin.getSummitManager() != null) {
            multiplierGainBonus = plugin.getSummitManager().getMultiplierGainBonus(playerId);
            evolutionPowerBonus = plugin.getSummitManager().getEvolutionPowerBonus(playerId);
        }
        BigNumber runnerIncrement = AscendConstants.getRunnerMultiplierIncrement(runnerStars, multiplierGainBonus, evolutionPowerBonus);
        BigNumber multiplierIncrement = runnerIncrement.multiply(BigNumber.fromDouble(2.0));

        // Calculate payout BEFORE adding multiplier (use current multiplier, not the new one)
        List<AscendMap> multiplierMaps = mapStore.listMapsSorted();
        BigNumber payout = playerStore.getCompletionPayout(playerId, multiplierMaps, AscendConstants.MULTIPLIER_SLOTS, run.mapId, BigNumber.ZERO);

        // Use atomic operations to prevent race conditions
        if (!playerStore.atomicAddCoins(playerId, payout)) {
            LOGGER.atWarning().log("Failed to add coins for manual run: " + playerId);
        }
        if (!playerStore.atomicAddTotalCoinsEarned(playerId, payout)) {
            LOGGER.atWarning().log("Failed to add total coins earned for manual run: " + playerId);
        }
        if (!playerStore.atomicAddMapMultiplier(playerId, run.mapId, multiplierIncrement)) {
            LOGGER.atWarning().log("Failed to add map multiplier for manual run: " + playerId);
        }
        playerStore.incrementTotalManualRuns(playerId);
        playerStore.incrementConsecutiveManualRuns(playerId);

        // Calculate completion time and check for personal best
        long completionTimeMs = System.currentTimeMillis() - run.startTimeMs;
        Long previousBest = mapProgress.getBestTimeMs();
        boolean isPersonalBest = previousBest == null || completionTimeMs < previousBest;

        if (isPersonalBest) {
            mapProgress.setBestTimeMs(completionTimeMs);
            playerStore.markDirty(playerId);
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

        // Build payout message with time and optional PB/bonus info
        StringBuilder payoutMsg = new StringBuilder();
        payoutMsg.append("[Ascend] +")
                 .append(FormatUtils.formatBigNumber(payout))
                 .append(" coins | ")
                 .append(timeStr);

        if (isPersonalBest) {
            payoutMsg.append(" | PB!");
        }

        player.sendMessage(Message.raw(payoutMsg.toString())
            .color(isPersonalBest ? SystemMessageUtils.SUCCESS : SystemMessageUtils.PRIMARY_TEXT));

        // Check achievements
        if (plugin != null && plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
        }

        // Activate Momentum boost if player has AUTO_RUNNERS skill
        if (plugin != null && plugin.getAscensionManager() != null
                && plugin.getAscensionManager().hasAutoRunners(playerId)) {
            boolean wasActive = mapProgress.isMomentumActive();
            mapProgress.activateMomentum();
            if (!wasActive) {
                String mapName = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
                long durationSec = AscendConstants.MOMENTUM_DURATION_MS / 1000;
                player.sendMessage(Message.raw("[Momentum] x2 runner speed on " + mapName + " for " + durationSec + "s!")
                    .color(SystemMessageUtils.WARN));
            }
        }

        // Trigger first completion tutorial
        if (firstCompletion && plugin != null && plugin.getTutorialTriggerService() != null) {
            plugin.getTutorialTriggerService().checkFirstCompletion(playerId, ref);
        }

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
        // Find if player is standing on any map's start position
        for (AscendMap map : mapStore.listMaps()) {
            if (map == null) {
                continue;
            }
            // Skip maps with no start set
            if (map.getStartX() == 0 && map.getStartY() == 0 && map.getStartZ() == 0) {
                continue;
            }

            double dx = pos.getX() - map.getStartX();
            double dy = pos.getY() - map.getStartY();
            double dz = pos.getZ() - map.getStartZ();

            if (dx * dx + dz * dz <= START_DETECTION_RADIUS_SQ && Math.abs(dy) <= START_VERTICAL_RANGE) {
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
        for (UUID runnerUuid : robotManager.getRunnerUuidsForMap(mapId)) {
            visibilityManager.hideEntity(viewerId, runnerUuid);
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
