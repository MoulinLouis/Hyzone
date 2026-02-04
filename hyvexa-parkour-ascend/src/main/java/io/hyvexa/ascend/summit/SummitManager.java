package io.hyvexa.ascend.summit;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.data.AscendPlayerStore;

import java.util.UUID;

/**
 * Manages the Summit prestige system.
 * Summit converts coins into permanent category upgrades, resetting coins and elevation.
 */
public class SummitManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AscendPlayerStore playerStore;

    public SummitManager(AscendPlayerStore playerStore) {
        this.playerStore = playerStore;
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
     * resets coins to 0 and elevation to 1.
     *
     * @return the new Summit level, or -1 if insufficient coins
     */
    public int performSummit(UUID playerId, SummitCategory category) {
        java.math.BigDecimal coins = playerStore.getCoins(playerId);
        java.math.BigDecimal threshold = java.math.BigDecimal.valueOf(AscendConstants.SUMMIT_MIN_COINS);
        if (coins.compareTo(threshold) < 0) {
            return -1;
        }

        int currentLevel = playerStore.getSummitLevel(playerId, category);
        java.math.BigDecimal alreadySpent = java.math.BigDecimal.valueOf(AscendConstants.getCoinsForSummitLevel(currentLevel));
        int newLevel = AscendConstants.calculateSummitLevel(alreadySpent.add(coins));
        int levelGain = newLevel - currentLevel;

        if (levelGain <= 0) {
            return currentLevel;
        }

        // Apply Summit: set new level, reset coins and elevation
        playerStore.addSummitLevel(playerId, category, levelGain);
        playerStore.setCoins(playerId, java.math.BigDecimal.ZERO);

        // Reset elevation to 1
        var progress = playerStore.getPlayer(playerId);
        if (progress != null) {
            progress.setElevationMultiplier(1);
        }

        playerStore.markDirty(playerId);

        LOGGER.atInfo().log("[Summit] Player " + playerId + " summited " + category.name()
            + " from Lv." + currentLevel + " to Lv." + newLevel + " (+" + levelGain + ")");

        return newLevel;
    }

    /**
     * Gets the total Summit bonus for coin earnings.
     * Used to modify base coin rewards.
     */
    public java.math.BigDecimal getCoinFlowBonus(UUID playerId) {
        return playerStore.getSummitBonus(playerId, SummitCategory.COIN_FLOW);
    }

    /**
     * Gets the total Summit bonus for runner speed.
     * Used to modify runner completion time.
     */
    public java.math.BigDecimal getRunnerSpeedBonus(UUID playerId) {
        return playerStore.getSummitBonus(playerId, SummitCategory.RUNNER_SPEED);
    }

    /**
     * Gets the total Summit bonus for manual run multiplier.
     * Used to modify manual run rewards.
     */
    public java.math.BigDecimal getManualMasteryBonus(UUID playerId) {
        return playerStore.getSummitBonus(playerId, SummitCategory.MANUAL_MASTERY);
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
