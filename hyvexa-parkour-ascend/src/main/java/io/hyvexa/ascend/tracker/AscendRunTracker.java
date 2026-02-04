package io.hyvexa.ascend.tracker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.ascension.AscensionManager;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.ghost.GhostRecorder;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.common.visibility.EntityVisibilityManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AscendRunTracker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double FINISH_RADIUS_SQ = 2.25; // 1.5^2
    private static final double MOVEMENT_THRESHOLD_SQ = 0.01; // 0.1^2 - minimum movement to start run

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final GhostRecorder ghostRecorder;
    private final Map<UUID, ActiveRun> activeRuns = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRun> pendingRuns = new ConcurrentHashMap<>();

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
        }
        if (oldActive != null) {
            showRunnersForMap(playerId, oldActive.mapId);
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

        // Show runners from the cancelled run's map
        if (pending != null) {
            showRunnersForMap(playerId, pending.mapId);
        }
        if (active != null) {
            showRunnersForMap(playerId, active.mapId);
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

    public void checkPlayer(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());

        if (playerRef == null || player == null || transform == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        Vector3d pos = transform.getPosition();

        // Check for pending run - start timer when player moves from start position
        PendingRun pendingRun = pendingRuns.get(playerId);
        if (pendingRun != null) {
            double dx = pos.getX() - pendingRun.startPos.getX();
            double dy = pos.getY() - pendingRun.startPos.getY();
            double dz = pos.getZ() - pendingRun.startPos.getZ();

            if (dx * dx + dy * dy + dz * dz > MOVEMENT_THRESHOLD_SQ) {
                // Player has moved - start the actual run
                startRun(playerId, pendingRun.mapId);
                player.sendMessage(Message.raw("[Ascend] Timer started!").color(SystemMessageUtils.SUCCESS));
            }
            return;
        }

        ActiveRun run = activeRuns.get(playerId);
        if (run == null) {
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

        if (dx * dx + dy * dy + dz * dz <= FINISH_RADIUS_SQ) {
            completeRun(playerRef, player, run, map, ref, store);
        }
    }

    private void completeRun(PlayerRef playerRef, Player player, ActiveRun run,
                             AscendMap map, Ref<EntityStore> ref, Store<EntityStore> store) {
        UUID playerId = playerRef.getUuid();
        activeRuns.remove(playerId);

        // Show runners again after completing the run
        showRunnersForMap(playerId, run.mapId);

        AscendPlayerProgress.MapProgress mapProgress = playerStore.getOrCreateMapProgress(playerId, run.mapId);

        boolean firstCompletion = !mapProgress.isCompletedManually();

        mapProgress.setCompletedManually(true);
        mapProgress.setUnlocked(true);
        playerStore.markDirty(playerId);

        // Calculate bonuses from Summit and Ascension
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        BigDecimal summitCoinFlowMultiplier = BigDecimal.ONE;
        BigDecimal ascensionBonus = BigDecimal.ZERO;
        BigDecimal chainBonus = BigDecimal.ZERO;
        BigDecimal sessionBonus = BigDecimal.ONE;

        MathContext ctx = new MathContext(30, RoundingMode.HALF_UP);

        if (plugin != null) {
            SummitManager summitManager = plugin.getSummitManager();
            AscensionManager ascensionManager = plugin.getAscensionManager();

            if (summitManager != null) {
                // COIN_FLOW is now multiplicative: 1.20^level
                summitCoinFlowMultiplier = summitManager.getCoinFlowMultiplier(playerId);
            }

            if (ascensionManager != null) {
                ascensionBonus = BigDecimal.valueOf(ascensionManager.getManualMultiplierBonus(playerId));
                int consecutive = playerStore.getConsecutiveManualRuns(playerId);
                chainBonus = BigDecimal.valueOf(ascensionManager.getChainBonus(playerId, consecutive));
                sessionBonus = BigDecimal.valueOf(ascensionManager.getSessionFirstRunMultiplier(playerId));
            }
        }

        // Calculate multiplier increment with Ascension bonuses only
        // (Manual Mastery removed, Evolution Power only affects runners)
        BigDecimal baseMultiplierIncrement = new BigDecimal("0.1"); // MANUAL_MULTIPLIER_INCREMENT
        BigDecimal totalMultiplierBonus = BigDecimal.ONE.add(ascensionBonus, ctx)
                                                        .add(chainBonus, ctx);
        BigDecimal finalMultiplierIncrement = baseMultiplierIncrement.multiply(totalMultiplierBonus, ctx);

        // Calculate payout BEFORE adding multiplier (use current multiplier, not the new one)
        List<AscendMap> multiplierMaps = mapStore.listMapsSorted();
        BigDecimal basePayout = playerStore.getCompletionPayout(playerId, multiplierMaps, AscendConstants.MULTIPLIER_SLOTS, run.mapId, BigDecimal.ZERO);

        // Apply coin flow multiplier (multiplicative) and session bonus
        BigDecimal coinMultiplier = summitCoinFlowMultiplier.multiply(sessionBonus, ctx);
        BigDecimal payout = basePayout.multiply(coinMultiplier, ctx).setScale(2, RoundingMode.HALF_UP);

        // Use atomic operations to prevent race conditions
        playerStore.atomicAddCoins(playerId, payout);
        playerStore.atomicAddTotalCoinsEarned(playerId, payout);
        playerStore.atomicAddMapMultiplier(playerId, run.mapId, finalMultiplierIncrement);
        playerStore.incrementTotalManualRuns(playerId);
        playerStore.incrementConsecutiveManualRuns(playerId);

        // Mark session first run as claimed if applicable
        if (sessionBonus.compareTo(BigDecimal.ONE) > 0) {
            playerStore.setSessionFirstRunClaimed(playerId, true);
        }

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

        if (firstCompletion) {
            player.sendMessage(Message.raw("[Ascend] Map completed! You can now buy a runner for this map.")
                .color(SystemMessageUtils.SUCCESS));
        }

        if (isPersonalBest && !firstCompletion) {
            long seconds = completionTimeMs / 1000;
            long millis = completionTimeMs % 1000;
            String timeStr = String.format("%d.%03ds", seconds, millis);
            player.sendMessage(Message.raw("[Ascend] New personal best! " + timeStr)
                .color(SystemMessageUtils.SUCCESS));
        }

        // Show payout with bonus info
        StringBuilder bonusInfo = new StringBuilder();
        if (sessionBonus.compareTo(BigDecimal.ONE) > 0) {
            bonusInfo.append(" (Session Bonus x3!)");
        } else if (chainBonus.compareTo(BigDecimal.ZERO) > 0) {
            bonusInfo.append(" (Chain +" + (int)(chainBonus.doubleValue() * 100) + "%)");
        }
        player.sendMessage(Message.raw("[Ascend] +" + FormatUtils.formatCoinsForHudDecimal(payout) + " coins." + bonusInfo)
            .color(SystemMessageUtils.PRIMARY_TEXT));

        // Check achievements
        if (plugin != null && plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
        }

        // Give back menu items
        if (plugin != null) {
            plugin.giveMenuItems(player);
        }

        Vector3d startPos = new Vector3d(map.getStartX(), map.getStartY(), map.getStartZ());
        Vector3f startRot = new Vector3f(map.getStartRotX(), map.getStartRotY(), map.getStartRotZ());
        store.addComponent(ref, Teleport.getComponentType(),
            new Teleport(store.getExternalData().getWorld(), startPos, startRot));
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
