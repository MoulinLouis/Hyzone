package io.hyvexa.ascend.passive;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.ghost.GhostRecording;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.ui.PassiveEarningsPage;
import io.hyvexa.common.math.BigNumber;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PassiveEarningsManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AscendPlayerStore playerStore;
    private final AscendMapStore mapStore;
    private final GhostStore ghostStore;

    public PassiveEarningsManager(AscendPlayerStore playerStore, AscendMapStore mapStore,
                                 GhostStore ghostStore) {
        this.playerStore = playerStore;
        this.mapStore = mapStore;
        this.ghostStore = ghostStore;
    }

    /**
     * Calculate and apply passive earnings for a player
     * @return PassiveEarningsResult with detailed breakdown, or null if no earnings
     */
    public PassiveEarningsResult calculateAndApplyPassiveEarnings(UUID playerId) {
        Long lastActive = playerStore.getLastActiveTimestamp(playerId);
        if (lastActive == null) {
            return null; // First time visiting Ascend
        }

        long now = System.currentTimeMillis();
        long timeAwayMs = now - lastActive;

        if (timeAwayMs < AscendConstants.PASSIVE_MIN_TIME_MS) {
            return null; // Less than 1 minute away, skip
        }

        // Cap at 24 hours
        timeAwayMs = Math.min(timeAwayMs, AscendConstants.PASSIVE_MAX_TIME_MS);

        // Get all active runners for this player
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return null;
        }

        List<AscendMap> allMaps = mapStore.listMapsSorted();
        Map<String, PassiveRunnerEarnings> runnerEarnings = new HashMap<>();

        BigNumber totalVolt = BigNumber.ZERO;
        BigNumber totalMultiplierGain = BigNumber.ZERO;

        // Calculate for each map with an active runner
        for (AscendMap map : allMaps) {
            String mapId = map.getId();
            AscendPlayerProgress.MapProgress mapProgress = progress.getMapProgress().get(mapId);

            if (mapProgress == null || !mapProgress.hasRobot()) {
                continue; // No runner on this map
            }

            // Get ghost recording to calculate completion time (fallback to map base time)
            long baseTimeMs;
            GhostRecording ghost = ghostStore.getRecording(playerId, mapId);
            if (ghost != null) {
                baseTimeMs = ghost.getCompletionTimeMs();
            } else {
                baseTimeMs = map.getEffectiveBaseRunTimeMs();
            }
            if (baseTimeMs <= 0) {
                continue;
            }
            int speedLevel = mapProgress.getRobotSpeedLevel();
            int stars = mapProgress.getRobotStars();

            // Calculate completion time (same logic as RobotManager)
            double speedMultiplier = RobotManager.calculateSpeedMultiplier(
                map, speedLevel, playerId
            );
            long completionTimeMs = (long) (baseTimeMs / speedMultiplier);

            // Calculate number of theoretical runs
            double theoreticalRuns = (double) timeAwayMs / completionTimeMs;

            // Get Summit bonuses (Multiplier Gain + Evolution Power)
            SummitManager.BonusTriplet bonuses = SummitManager.getSafeBonuses(playerId);

            // Offline rate: always base 10%
            long effectiveOfflineRate = AscendConstants.PASSIVE_OFFLINE_RATE_PERCENT;

            // Multiplier gain per run (with Summit bonuses) - at offline rate
            BigNumber multiplierIncrement = AscendConstants.getRunnerMultiplierIncrement(stars, bonuses.multiplierGain(), bonuses.evolutionPower(), bonuses.baseMultiplier());

            BigNumber mapMultiplierGain = multiplierIncrement
                .multiply(BigNumber.fromDouble(theoreticalRuns))
                .multiply(BigNumber.fromDouble(effectiveOfflineRate / 100.0));

            // Volt per run (same logic as RobotManager)
            BigNumber payoutPerRun = playerStore.getCompletionPayout(
                playerId, allMaps, AscendConstants.MULTIPLIER_SLOTS, mapId, BigNumber.ZERO
            );

            // Calculate total volt for this runner
            BigNumber mapVolt = payoutPerRun
                .multiply(BigNumber.fromDouble(theoreticalRuns))
                .multiply(BigNumber.fromDouble(effectiveOfflineRate / 100.0));

            // Store runner earnings
            runnerEarnings.put(mapId, new PassiveRunnerEarnings(
                map.getName(),
                (int) theoreticalRuns,
                mapVolt,
                mapMultiplierGain,
                speedLevel,
                stars
            ));

            totalVolt = totalVolt.add(mapVolt);
            totalMultiplierGain = totalMultiplierGain.add(mapMultiplierGain);
        }

        if (totalVolt.lte(BigNumber.ZERO)) {
            return null; // No earnings (no runners)
        }

        // Apply earnings to player account
        if (!playerStore.atomicAddVolt(playerId, totalVolt)) {
            LOGGER.atSevere().log("Failed to add passive volt for " + playerId + " (amount: " + totalVolt + ")");
        }
        if (!playerStore.atomicAddTotalVoltEarned(playerId, totalVolt)) {
            LOGGER.atSevere().log("Failed to add total volt earned for " + playerId + " (amount: " + totalVolt + ")");
        }

        // Apply multiplier gains to each map (at offline rate)
        for (Map.Entry<String, PassiveRunnerEarnings> entry : runnerEarnings.entrySet()) {
            if (!playerStore.atomicAddMapMultiplier(
                playerId,
                entry.getKey(),
                entry.getValue().multiplierGain()
            )) {
                LOGGER.atSevere().log("Failed to add passive map multiplier for " + playerId + " on map " + entry.getKey());
            }
        }

        LOGGER.atInfo().log(
            "Passive earnings for " + playerId + ": " +
            totalVolt + " volt, +" + totalMultiplierGain + " multiplier over " + (timeAwayMs / 1000 / 60) + " minutes"
        );

        return new PassiveEarningsResult(
            timeAwayMs,
            totalVolt,
            totalMultiplierGain,
            runnerEarnings
        );
    }

    public void onPlayerLeaveAscend(UUID playerId) {
        playerStore.setLastActiveTimestamp(playerId, System.currentTimeMillis());
        playerStore.setHasUnclaimedPassive(playerId, true);
    }

    public void checkPassiveEarningsOnJoin(UUID playerId) {
        if (!playerStore.hasUnclaimedPassive(playerId)) {
            return; // No unclaimed earnings
        }

        PassiveEarningsResult result = calculateAndApplyPassiveEarnings(playerId);
        if (result == null) {
            // No actual earnings — still clear the flag
            playerStore.setHasUnclaimedPassive(playerId, false);
            playerStore.setLastActiveTimestamp(playerId, System.currentTimeMillis());
            return;
        }

        // Mark as claimed
        Long previousTimestamp = playerStore.getLastActiveTimestamp(playerId);
        playerStore.setHasUnclaimedPassive(playerId, false);
        playerStore.setLastActiveTimestamp(playerId, System.currentTimeMillis());

        // Persist immediately to prevent duplicate payouts on crash
        if (!playerStore.savePlayerSync(playerId)) {
            // Save failed — roll back claim markers so the next login retries
            playerStore.setHasUnclaimedPassive(playerId, true);
            playerStore.setLastActiveTimestamp(playerId, previousTimestamp);
            LOGGER.atWarning().log("Failed to persist passive earnings claim for " + playerId);
            return;
        }

        // Open PassiveEarningsPage UI
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            PlayerRef playerRef = plugin.getPlayerRef(playerId);
            if (playerRef != null) {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        PassiveEarningsPage page = new PassiveEarningsPage(playerRef, result);
                        player.getPageManager().openCustomPage(ref, store, page);
                    }
                }
            }
        }
    }

    // Data classes
    public record PassiveEarningsResult(
        long timeAwayMs,
        BigNumber totalVolt,
        BigNumber totalMultiplier,
        Map<String, PassiveRunnerEarnings> runnerBreakdown
    ) {}

    public record PassiveRunnerEarnings(
        String mapName,
        int runsCompleted,
        BigNumber voltEarned,
        BigNumber multiplierGain,
        int speedLevel,
        int stars
    ) {}
}
