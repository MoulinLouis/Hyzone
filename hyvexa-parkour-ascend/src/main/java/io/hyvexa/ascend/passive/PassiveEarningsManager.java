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
import io.hyvexa.ascend.ghost.GhostRecording;
import io.hyvexa.ascend.ghost.GhostStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.ui.PassiveEarningsPage;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PassiveEarningsManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long OFFLINE_RATE_PERCENT = AscendConstants.PASSIVE_OFFLINE_RATE_PERCENT;
    private static final long MAX_OFFLINE_TIME_MS = AscendConstants.PASSIVE_MAX_TIME_MS;
    private static final long MIN_AWAY_TIME_MS = AscendConstants.PASSIVE_MIN_TIME_MS;

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

        if (timeAwayMs < MIN_AWAY_TIME_MS) {
            return null; // Less than 1 minute away, skip
        }

        // Cap at 24 hours
        timeAwayMs = Math.min(timeAwayMs, MAX_OFFLINE_TIME_MS);

        // Get all active runners for this player
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return null;
        }

        List<AscendMap> allMaps = mapStore.listMapsSorted();
        Map<String, PassiveRunnerEarnings> runnerEarnings = new HashMap<>();

        BigDecimal totalCoins = BigDecimal.ZERO;
        BigDecimal totalMultiplierGain = BigDecimal.ZERO;
        MathContext ctx = new MathContext(30, RoundingMode.HALF_UP);

        // Calculate for each map with an active runner
        for (AscendMap map : allMaps) {
            String mapId = map.getId();
            AscendPlayerProgress.MapProgress mapProgress = progress.getMapProgress().get(mapId);

            if (mapProgress == null || !mapProgress.hasRobot()) {
                continue; // No runner on this map
            }

            // Get ghost recording to calculate completion time
            GhostRecording ghost = ghostStore.getRecording(playerId, mapId);
            if (ghost == null) {
                continue; // No ghost = can't calculate
            }

            long baseTimeMs = ghost.getCompletionTimeMs();
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
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            double multiplierGainBonus = 1.0;
            double evolutionPowerBonus = 3.0;
            if (plugin != null && plugin.getSummitManager() != null) {
                multiplierGainBonus = plugin.getSummitManager().getMultiplierGainBonus(playerId).doubleValue();
                evolutionPowerBonus = plugin.getSummitManager().getEvolutionPowerBonus(playerId).doubleValue();
            }

            // Determine offline rate (skill tree boost: 25% → 40%)
            long effectiveOfflineRate = OFFLINE_RATE_PERCENT;
            ParkourAscendPlugin ascendPlugin = ParkourAscendPlugin.getInstance();
            if (ascendPlugin != null && ascendPlugin.getAscensionManager() != null
                    && ascendPlugin.getAscensionManager().hasOfflineBoost(playerId)) {
                effectiveOfflineRate = 40L;
            }

            // Multiplier gain per run (with Summit bonuses) - at offline rate
            BigDecimal multiplierIncrement = AscendConstants.getRunnerMultiplierIncrement(stars, multiplierGainBonus, evolutionPowerBonus);
            BigDecimal mapMultiplierGain = multiplierIncrement
                .multiply(BigDecimal.valueOf(theoreticalRuns), ctx)
                .multiply(BigDecimal.valueOf(effectiveOfflineRate).divide(
                    BigDecimal.valueOf(100), ctx
                ), ctx); // Apply offline rate to multiplier gains

            // Coins per run (same logic as RobotManager)
            BigDecimal payoutPerRun = playerStore.getCompletionPayout(
                playerId, allMaps, AscendConstants.MULTIPLIER_SLOTS, mapId, BigDecimal.ZERO
            );

            // Calculate total coins for this runner
            BigDecimal mapCoins = payoutPerRun
                .multiply(BigDecimal.valueOf(theoreticalRuns), ctx)
                .multiply(BigDecimal.valueOf(effectiveOfflineRate).divide(
                    BigDecimal.valueOf(100), ctx
                ), ctx) // Apply offline rate
                .setScale(2, RoundingMode.HALF_UP);

            // Store runner earnings
            runnerEarnings.put(mapId, new PassiveRunnerEarnings(
                map.getName(),
                (int) theoreticalRuns,
                mapCoins,
                mapMultiplierGain,
                speedLevel,
                stars
            ));

            totalCoins = totalCoins.add(mapCoins, ctx);
            totalMultiplierGain = totalMultiplierGain.add(mapMultiplierGain, ctx);
        }

        if (totalCoins.compareTo(BigDecimal.ZERO) <= 0) {
            return null; // No earnings (no runners)
        }

        // Apply earnings to player account
        if (!playerStore.atomicAddCoins(playerId, totalCoins)) {
            LOGGER.at(Level.SEVERE).log("Failed to add passive coins for " + playerId + " (amount: " + totalCoins + ")");
        }
        if (!playerStore.atomicAddTotalCoinsEarned(playerId, totalCoins)) {
            LOGGER.at(Level.SEVERE).log("Failed to add total coins earned for " + playerId + " (amount: " + totalCoins + ")");
        }

        // Apply multiplier gains to each map (at offline rate)
        for (Map.Entry<String, PassiveRunnerEarnings> entry : runnerEarnings.entrySet()) {
            if (!playerStore.atomicAddMapMultiplier(
                playerId,
                entry.getKey(),
                entry.getValue().multiplierGain()
            )) {
                LOGGER.at(Level.SEVERE).log("Failed to add passive map multiplier for " + playerId + " on map " + entry.getKey());
            }
        }

        LOGGER.at(Level.INFO).log(
            "Passive earnings for " + playerId + ": " +
            totalCoins + " coins, +" + totalMultiplierGain + " multiplier over " + (timeAwayMs / 1000 / 60) + " minutes"
        );

        return new PassiveEarningsResult(
            timeAwayMs,
            totalCoins,
            totalMultiplierGain,
            runnerEarnings
        );
    }

    /**
     * Mark player as having left Ascend world
     */
    public void onPlayerLeaveAscend(UUID playerId) {
        playerStore.setLastActiveTimestamp(playerId, System.currentTimeMillis());
        playerStore.setHasUnclaimedPassive(playerId, true);
    }

    /**
     * Check and show passive earnings popup if needed
     */
    public void checkPassiveEarningsOnJoin(UUID playerId) {
        if (!playerStore.hasUnclaimedPassive(playerId)) {
            return; // No unclaimed earnings
        }

        PassiveEarningsResult result = calculateAndApplyPassiveEarnings(playerId);

        // Mark as claimed
        playerStore.setHasUnclaimedPassive(playerId, false);
        playerStore.setLastActiveTimestamp(playerId, System.currentTimeMillis());

        if (result != null) {
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
    }

    // Data classes
    public record PassiveEarningsResult(
        long timeAwayMs,
        BigDecimal totalCoins,
        BigDecimal totalMultiplier,
        Map<String, PassiveRunnerEarnings> runnerBreakdown
    ) {}

    public record PassiveRunnerEarnings(
        String mapName,
        int runsCompleted,
        BigDecimal coinsEarned,
        BigDecimal multiplierGain,
        int speedLevel,
        int stars
    ) {}
}
