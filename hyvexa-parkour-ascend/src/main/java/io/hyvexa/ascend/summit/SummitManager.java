package io.hyvexa.ascend.summit;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.math.BigNumber;

import java.util.List;
import java.util.UUID;

/**
 * Manages the Summit prestige system.
 * Summit converts vexa into XP-based category upgrades, resetting vexa, elevation,
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
     * Requires at least 1 XP worth of vexa (1B vexa = 1 XP).
     */
    public boolean canSummit(UUID playerId) {
        BigNumber accumulatedVexa = playerStore.getSummitAccumulatedVexa(playerId);
        long potentialXp = AscendConstants.vexaToXp(accumulatedVexa);
        return potentialXp >= 1;
    }

    /**
     * Calculates the XP-based preview for a Summit in the given category.
     * Shows current level, projected level, XP gain, and bonus changes.
     * XP is based on accumulated vexa since last Summit/Elevation.
     */
    public SummitPreview previewSummit(UUID playerId, SummitCategory category) {
        BigNumber vexa = playerStore.getSummitAccumulatedVexa(playerId);
        long xpToGain = AscendConstants.vexaToXp(vexa);

        long currentXp = playerStore.getSummitXp(playerId, category);
        int currentLevel = AscendConstants.calculateLevelFromXp(currentXp);

        long newXp = AscendConstants.saturatingAdd(currentXp, xpToGain);
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
            vexa,
            xpToGain,
            currentProgress[0],
            currentProgress[1],
            newProgress[0],
            newProgress[1]
        );
    }

    /**
     * Performs a Summit: converts vexa to XP in the specified category,
     * resets vexa, elevation, multipliers, runners, and map unlocks (keeps best times only).
     *
     * @return SummitResult containing the new level, list of maps with runners (for despawn), and XP gained
     */
    public SummitResult performSummit(UUID playerId, SummitCategory category) {
        BigNumber vexa = playerStore.getSummitAccumulatedVexa(playerId);
        long xpToGain = AscendConstants.vexaToXp(vexa);

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

        // Reset vexa, elevation, multipliers, and runners (keeps unlocks)
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
     * Formula: 1.0 + 0.15 * level (linear below soft cap, sqrt growth above).
     * @return Multiplier value (1.0 at level 0, 2.5 at level 10)
     */
    public double getRunnerSpeedBonus(UUID playerId) {
        return playerStore.getSummitBonusDouble(playerId, SummitCategory.RUNNER_SPEED);
    }

    /**
     * Gets the multiplier gain bonus.
     * Formula: 1.0 + 0.30 * level (linear below soft cap, sqrt growth above).
     * @return Multiplier value (1.0 at level 0, 4.0 at level 10)
     */
    public double getMultiplierGainBonus(UUID playerId) {
        return playerStore.getSummitBonusDouble(playerId, SummitCategory.MULTIPLIER_GAIN);
    }

    /**
     * Gets the Evolution Power bonus for runner evolution.
     * Formula: 3.0 + 0.10 * level (linear below soft cap, sqrt growth above).
     * Applied per star: multiplier_increment = 0.1 * evolutionPower^stars
     * @return Evolution bonus (3.0 at level 0, 4.0 at level 10)
     */
    public double getEvolutionPowerBonus(UUID playerId) {
        double base = playerStore.getSummitBonusDouble(playerId, SummitCategory.EVOLUTION_POWER);
        // Skill tree: Evolution Power+ adds +1.0 to base evolution power
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getAscensionManager() != null
                && plugin.getAscensionManager().hasEvolutionPowerBoost(playerId)) {
            base += 1.0;
        }
        return base;
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
        BigNumber vexaToSpend,
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
