package io.hyvexa.ascend.summit;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Manages the Summit prestige system.
 * Summit converts coins into XP-based category upgrades, resetting coins, elevation,
 * multipliers, runners, and map unlocks (preserves best times only).
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
     * Requires at least 1 XP worth of coins (1T coins = 1 XP).
     */
    public boolean canSummit(UUID playerId) {
        BigDecimal coins = playerStore.getCoins(playerId);
        long potentialXp = AscendConstants.coinsToXp(coins);
        return potentialXp >= 1;
    }

    /**
     * Calculates the XP-based preview for a Summit in the given category.
     * Shows current level, projected level, XP gain, and bonus changes.
     */
    public SummitPreview previewSummit(UUID playerId, SummitCategory category) {
        BigDecimal coins = playerStore.getCoins(playerId);
        long xpToGain = AscendConstants.coinsToXp(coins);

        long currentXp = playerStore.getSummitXp(playerId, category);
        int currentLevel = AscendConstants.calculateLevelFromXp(currentXp);

        long newXp = currentXp + xpToGain;
        int newLevel = AscendConstants.calculateLevelFromXp(newXp);

        double currentBonus = category.getBonusForLevel(currentLevel);
        double newBonus = category.getBonusForLevel(newLevel);

        long[] currentProgress = AscendConstants.getXpProgress(currentXp);
        long[] newProgress = AscendConstants.getXpProgress(newXp);

        return new SummitPreview(
            category,
            currentLevel,
            newLevel,
            newLevel - currentLevel,
            currentBonus,
            newBonus,
            coins.doubleValue(),
            xpToGain,
            currentProgress[0],
            currentProgress[1],
            newProgress[0],
            newProgress[1]
        );
    }

    /**
     * Performs a Summit: converts coins to XP in the specified category,
     * resets coins, elevation, multipliers, runners, and map unlocks (keeps best times only).
     *
     * @return SummitResult containing the new level, list of maps with runners (for despawn), and XP gained
     */
    public SummitResult performSummit(UUID playerId, SummitCategory category) {
        BigDecimal coins = playerStore.getCoins(playerId);
        long xpToGain = AscendConstants.coinsToXp(coins);

        if (xpToGain < 1) {
            return new SummitResult(-1, List.of(), 0);
        }

        // Verify level gain before performing summit
        SummitPreview preview = previewSummit(playerId, category);
        if (!preview.hasGain()) {
            return new SummitResult(-1, List.of(), 0);
        }

        int previousLevel = playerStore.getSummitLevel(playerId, category);

        // Add XP to category
        playerStore.addSummitXp(playerId, category, xpToGain);

        int newLevel = playerStore.getSummitLevel(playerId, category);

        // Get first map ID for reset
        String firstMapId = null;
        if (mapStore != null) {
            List<AscendMap> maps = mapStore.listMapsSorted();
            if (!maps.isEmpty()) {
                firstMapId = maps.get(0).getId();
            }
        }

        // Reset coins, elevation, multipliers, and runners (keeps unlocks)
        List<String> mapsWithRunners = playerStore.resetProgressForSummit(playerId, firstMapId);

        LOGGER.atInfo().log("[Summit] Player " + playerId + " summited " + category.name()
            + " +" + xpToGain + " XP, Lv." + previousLevel + " -> Lv." + newLevel);

        return new SummitResult(newLevel, mapsWithRunners, xpToGain);
    }

    /**
     * Result of a Summit operation.
     */
    public record SummitResult(int newLevel, List<String> mapsWithRunners, long xpGained) {
        public boolean succeeded() {
            return newLevel >= 0;
        }
    }

    /**
     * Gets the runner speed bonus multiplier.
     * Formula: 1 + 0.45 * sqrt(level)
     * Used to multiply runner movement speed.
     * @return Multiplier value (1.0 at level 0, ~2.42 at level 10)
     */
    public BigDecimal getRunnerSpeedBonus(UUID playerId) {
        return playerStore.getSummitBonus(playerId, SummitCategory.RUNNER_SPEED);
    }

    /**
     * Gets the multiplier gain bonus.
     * Formula: 1 + 0.5 * level^0.8
     * Used to boost multiplier gains from runs.
     * @return Multiplier value (1.0 at level 0, ~4.16 at level 10)
     */
    public BigDecimal getMultiplierGainBonus(UUID playerId) {
        return playerStore.getSummitBonus(playerId, SummitCategory.MULTIPLIER_GAIN);
    }

    /**
     * Gets the Evolution Power bonus for runner evolution.
     * Formula: 2 + 0.5 * level^0.8
     * Increases the evolution base multiplier.
     * TODO: Effect not yet applied anywhere - XP can be invested but has no gameplay effect.
     * @return Evolution bonus (2.0 at level 0, ~5.15 at level 10)
     */
    public BigDecimal getEvolutionPowerBonus(UUID playerId) {
        return playerStore.getSummitBonus(playerId, SummitCategory.EVOLUTION_POWER);
    }

    /**
     * Preview data for a potential Summit with XP-based progression.
     */
    public record SummitPreview(
        SummitCategory category,
        int currentLevel,
        int newLevel,
        int levelGain,
        double currentBonus,
        double newBonus,
        double coinsToSpend,
        long xpToGain,
        long currentXpInLevel,
        long currentXpRequired,
        long newXpInLevel,
        long newXpRequired
    ) {
        public double bonusGain() {
            return newBonus - currentBonus;
        }

        public boolean hasGain() {
            return levelGain > 0;
        }
    }
}
