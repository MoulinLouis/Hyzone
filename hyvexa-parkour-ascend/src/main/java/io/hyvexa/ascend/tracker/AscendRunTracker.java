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
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.ghost.GhostRecorder;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.common.util.SystemMessageUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AscendRunTracker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double FINISH_RADIUS_SQ = 2.25; // 1.5^2

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final GhostRecorder ghostRecorder;
    private final Map<UUID, ActiveRun> activeRuns = new ConcurrentHashMap<>();

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

        activeRuns.put(playerId, new ActiveRun(mapId, System.currentTimeMillis()));

        // Start ghost recording
        if (ghostRecorder != null) {
            ghostRecorder.startRecording(playerId, mapId);
        }
    }

    public void cancelRun(UUID playerId) {
        activeRuns.remove(playerId);

        // Cancel ghost recording
        if (ghostRecorder != null) {
            ghostRecorder.cancelRecording(playerId);
        }
    }

    public String getActiveMapId(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        return run != null ? run.mapId : null;
    }

    public void checkPlayer(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());

        if (playerRef == null || player == null || transform == null) {
            return;
        }

        ActiveRun run = activeRuns.get(playerRef.getUuid());
        if (run == null) {
            return;
        }

        AscendMap map = mapStore.getMap(run.mapId);
        if (map == null) {
            activeRuns.remove(playerRef.getUuid());
            return;
        }

        Vector3d pos = transform.getPosition();
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

        AscendPlayerProgress.MapProgress mapProgress = playerStore.getOrCreateMapProgress(playerId, run.mapId);

        boolean firstCompletion = !mapProgress.isCompletedManually();

        mapProgress.setCompletedManually(true);
        mapProgress.setUnlocked(true);
        playerStore.markDirty(playerId);

        // Calculate bonuses from Summit and Ascension
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        double summitCoinBonus = 0.0;
        double summitManualBonus = 0.0;
        double ascensionBonus = 0.0;
        double chainBonus = 0.0;
        double sessionBonus = 1.0;

        if (plugin != null) {
            SummitManager summitManager = plugin.getSummitManager();
            AscensionManager ascensionManager = plugin.getAscensionManager();

            if (summitManager != null) {
                summitCoinBonus = summitManager.getCoinFlowBonus(playerId);
                summitManualBonus = summitManager.getManualMasteryBonus(playerId);
            }

            if (ascensionManager != null) {
                ascensionBonus = ascensionManager.getManualMultiplierBonus(playerId);
                int consecutive = playerStore.getConsecutiveManualRuns(playerId);
                chainBonus = ascensionManager.getChainBonus(playerId, consecutive);
                sessionBonus = ascensionManager.getSessionFirstRunMultiplier(playerId);
            }
        }

        // Calculate multiplier increment with bonuses
        double baseMultiplierIncrement = AscendConstants.MANUAL_MULTIPLIER_INCREMENT;
        double totalMultiplierBonus = 1.0 + summitManualBonus + ascensionBonus + chainBonus;
        double finalMultiplierIncrement = baseMultiplierIncrement * totalMultiplierBonus;

        List<AscendMap> multiplierMaps = mapStore.listMapsSorted();
        long basePayout = playerStore.getCompletionPayout(playerId, multiplierMaps, AscendConstants.MULTIPLIER_SLOTS, run.mapId, finalMultiplierIncrement);

        // Apply coin flow bonus and session bonus
        double coinMultiplier = (1.0 + summitCoinBonus) * sessionBonus;
        long payout = (long) Math.floor(basePayout * coinMultiplier);

        playerStore.addMapMultiplier(playerId, run.mapId, finalMultiplierIncrement);
        playerStore.addCoins(playerId, payout);

        // Track for achievements
        playerStore.addTotalCoinsEarned(playerId, payout);
        playerStore.incrementTotalManualRuns(playerId);
        playerStore.incrementConsecutiveManualRuns(playerId);

        // Mark session first run as claimed if applicable
        if (sessionBonus > 1.0) {
            playerStore.setSessionFirstRunClaimed(playerId, true);
        }

        // Calculate completion time and check for personal best
        long completionTimeMs = System.currentTimeMillis() - run.startTimeMs;
        Long previousBest = mapProgress.getBestTimeMs();
        boolean isPersonalBest = previousBest == null || completionTimeMs < previousBest;

        LOGGER.atInfo().log("[GhostDebug] Player " + playerId + " completed " + run.mapId
            + " in " + completionTimeMs + "ms. Previous best: " + previousBest
            + ". Is PB: " + isPersonalBest);

        if (isPersonalBest) {
            mapProgress.setBestTimeMs(completionTimeMs);
            playerStore.markDirty(playerId);
        }

        // Stop ghost recording and save if personal best
        if (ghostRecorder != null) {
            ghostRecorder.stopRecording(playerId, completionTimeMs, isPersonalBest);
        } else {
            LOGGER.atWarning().log("[GhostDebug] GhostRecorder is null! Cannot save recording.");
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
        if (sessionBonus > 1.0) {
            bonusInfo.append(" (Session Bonus x3!)");
        } else if (chainBonus > 0) {
            bonusInfo.append(" (Chain +" + (int)(chainBonus * 100) + "%)");
        }
        player.sendMessage(Message.raw("[Ascend] +" + payout + " coins." + bonusInfo)
            .color(SystemMessageUtils.PRIMARY_TEXT));

        // Check achievements
        if (plugin != null && plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
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

        // Debug: log rotation being used for teleport
        LOGGER.atInfo().log("Teleport to " + mapId + " rot: " + rot.getX() + ", " + rot.getY() + ", " + rot.getZ());

        store.addComponent(ref, Teleport.getComponentType(),
            new Teleport(store.getExternalData().getWorld(), pos, rot));

        startRun(playerRef.getUuid(), mapId);
    }

    private static class ActiveRun {
        final String mapId;
        final long startTimeMs;

        ActiveRun(String mapId, long startTimeMs) {
            this.mapId = mapId;
            this.startTimeMs = startTimeMs;
        }
    }
}
