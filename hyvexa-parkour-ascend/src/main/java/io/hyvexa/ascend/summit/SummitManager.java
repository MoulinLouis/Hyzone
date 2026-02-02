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
        double coins = playerStore.getCoins(playerId);
        return coins >= AscendConstants.SUMMIT_MIN_COINS;
    }

    /**
     * Calculates how many Summit levels a player would gain for a category
     * if they spent all their coins.
     */
    public SummitPreview previewSummit(UUID playerId, SummitCategory category) {
        double coins = playerStore.getCoins(playerId);
        int currentLevel = playerStore.getSummitLevel(playerId, category);
        int newLevel = AscendConstants.calculateSummitLevel(
            AscendConstants.getCoinsForSummitLevel(currentLevel) + coins
        );
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
            coins
        );
    }

    /**
     * Performs a Summit: converts coins to Summit level in the specified category,
     * resets coins to 0 and elevation to 1.
     *
     * @return the new Summit level, or -1 if insufficient coins
     */
    public int performSummit(UUID playerId, SummitCategory category) {
        double coins = playerStore.getCoins(playerId);
        if (coins < AscendConstants.SUMMIT_MIN_COINS) {
            LOGGER.atInfo().log("[Summit] Player " + playerId + " has insufficient coins: " + coins);
            return -1;
        }

        int currentLevel = playerStore.getSummitLevel(playerId, category);
        long alreadySpent = AscendConstants.getCoinsForSummitLevel(currentLevel);
        int newLevel = AscendConstants.calculateSummitLevel(alreadySpent + coins);
        int levelGain = newLevel - currentLevel;

        if (levelGain <= 0) {
            LOGGER.atInfo().log("[Summit] Player " + playerId + " would gain no levels with " + coins + " coins");
            return currentLevel;
        }

        // Apply Summit: set new level, reset coins and elevation
        playerStore.addSummitLevel(playerId, category, levelGain);
        playerStore.setCoins(playerId, 0.0);

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
    public double getCoinFlowBonus(UUID playerId) {
        return playerStore.getSummitBonus(playerId, SummitCategory.COIN_FLOW);
    }

    /**
     * Gets the total Summit bonus for runner speed.
     * Used to modify runner completion time.
     */
    public double getRunnerSpeedBonus(UUID playerId) {
        return playerStore.getSummitBonus(playerId, SummitCategory.RUNNER_SPEED);
    }

    /**
     * Gets the total Summit bonus for manual run multiplier.
     * Used to modify manual run rewards.
     */
    public double getManualMasteryBonus(UUID playerId) {
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
