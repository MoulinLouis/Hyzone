package io.hyvexa.ascend.summit;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;

import java.util.List;
import java.util.UUID;

/**
 * Manages the Summit prestige system.
 * Summit converts coins into permanent category upgrades, resetting coins, elevation,
 * multipliers, map unlocks, and runners (like elevation reset).
 */
public class SummitManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AscendPlayerStore playerStore;
    private final AscendMapStore mapStore;

    public SummitManager(AscendPlayerStore playerStore, AscendMapStore mapStore) {
        this.playerStore = playerStore;
        this.mapStore = mapStore;
    }

    /**
     * Checks if a player can perform a Summit in any category.
     */
    public boolean canSummit(UUID playerId) {
        java.math.BigDecimal coins = playerStore.getCoins(playerId);
        java.math.BigDecimal threshold = java.math.BigDecimal.valueOf(AscendConstants.SUMMIT_MIN_COINS);
        return coins.compareTo(threshold) >= 0;
    }

    /**
     * Calculates how many Summit levels a player would gain for a category
     * if they spent all their coins.
     */
    public SummitPreview previewSummit(UUID playerId, SummitCategory category) {
        java.math.BigDecimal coins = playerStore.getCoins(playerId);
        int currentLevel = playerStore.getSummitLevel(playerId, category);
        java.math.BigDecimal alreadySpent = java.math.BigDecimal.valueOf(AscendConstants.getCoinsForSummitLevel(currentLevel));
        int newLevel = AscendConstants.calculateSummitLevel(alreadySpent.add(coins));
        int levelGain = newLevel - currentLevel;
        double currentBonus = category.getBonusForLevel(currentLevel);
        double newBonus = category.getBonusForLevel(newLevel);

        return new SummitPreview(
            category,
            currentLevel,
            newLevel,
            levelGain,
            currentBonus,
            newBonus,
            coins.doubleValue() // Convert for display in record
        );
    }

    /**
     * Performs a Summit: converts coins to Summit level in the specified category,
     * resets coins to 0, elevation to 1, and all map progress (like elevation reset).
     *
     * @return SummitResult containing the new level and list of maps with runners to despawn
     */
    public SummitResult performSummit(UUID playerId, SummitCategory category) {
        java.math.BigDecimal coins = playerStore.getCoins(playerId);
        java.math.BigDecimal threshold = java.math.BigDecimal.valueOf(AscendConstants.SUMMIT_MIN_COINS);
        if (coins.compareTo(threshold) < 0) {
            return new SummitResult(-1, List.of());
        }

        int currentLevel = playerStore.getSummitLevel(playerId, category);
        java.math.BigDecimal alreadySpent = java.math.BigDecimal.valueOf(AscendConstants.getCoinsForSummitLevel(currentLevel));
        int newLevel = AscendConstants.calculateSummitLevel(alreadySpent.add(coins));
        int levelGain = newLevel - currentLevel;

        if (levelGain <= 0) {
            return new SummitResult(currentLevel, List.of());
        }

        // Apply Summit level gain
        playerStore.addSummitLevel(playerId, category, levelGain);

        // Get first map ID for reset
        String firstMapId = null;
        if (mapStore != null) {
            List<AscendMap> maps = mapStore.listMapsSorted();
            if (!maps.isEmpty()) {
                firstMapId = maps.get(0).getId();
            }
        }

        // Full reset: coins, elevation, multipliers, map unlocks, runners (like elevation)
        List<String> mapsWithRunners = playerStore.resetProgressForElevation(playerId, firstMapId);

        // Also reset elevation to 1 (resetProgressForElevation doesn't touch elevation)
        var progress = playerStore.getPlayer(playerId);
        if (progress != null) {
            progress.setElevationMultiplier(1);
        }

        playerStore.markDirty(playerId);

        LOGGER.atInfo().log("[Summit] Player " + playerId + " summited " + category.name()
            + " from Lv." + currentLevel + " to Lv." + newLevel + " (+" + levelGain + ")"
            + " - Reset: " + mapsWithRunners.size() + " runners despawned");

        return new SummitResult(newLevel, mapsWithRunners);
    }

    /**
     * Result of a Summit operation.
     */
    public record SummitResult(int newLevel, List<String> mapsWithRunners) {
        public boolean succeeded() {
            return newLevel >= 0;
        }
    }

    /**
     * Gets the coin flow multiplier for coin earnings.
     * COIN_FLOW is now multiplicative: 1.20^level
     * Used as a direct multiplier on coin rewards.
     * @return Multiplier value (1.0 at level 0, 2.49 at level 5, 6.19 at level 10)
     */
    public java.math.BigDecimal getCoinFlowMultiplier(UUID playerId) {
        return playerStore.getSummitBonus(playerId, SummitCategory.COIN_FLOW);
    }

    /**
     * Gets the total Summit bonus for runner speed.
     * Used to modify runner completion time (additive).
     */
    public java.math.BigDecimal getRunnerSpeedBonus(UUID playerId) {
        return playerStore.getSummitBonus(playerId, SummitCategory.RUNNER_SPEED);
    }

    /**
     * Gets the Evolution Power bonus for runner evolution.
     * Increases the evolution base from 2 to (2 + bonus).
     * Formula: runner multiplier = 0.1 × (2 + evolutionBonus)^stars
     * @return Evolution base bonus (0.20 per Summit level)
     */
    public java.math.BigDecimal getEvolutionPowerBonus(UUID playerId) {
        return playerStore.getSummitBonus(playerId, SummitCategory.EVOLUTION_POWER);
    }

    /**
     * Preview data for a potential Summit.
     */
    public record SummitPreview(
        SummitCategory category,
        int currentLevel,
        int newLevel,
        int levelGain,
        double currentBonus,
        double newBonus,
        double coinsToSpend
    ) {
        public double bonusGain() {
            return newBonus - currentBonus;
        }

        public boolean hasGain() {
            return levelGain > 0;
        }
    }
}
