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
        double potentialXp = AscendConstants.vexaToXp(accumulatedVexa);
        return potentialXp >= 1.0;
    }

    /**
     * Calculates the XP-based preview for a Summit in the given category.
     * Shows current level, projected level, XP gain, and bonus changes.
     * XP is based on accumulated vexa since last Summit/Elevation.
     */
    public SummitPreview previewSummit(UUID playerId, SummitCategory category) {
        BigNumber vexa = playerStore.getSummitAccumulatedVexa(playerId);
        double xpToGain = AscendConstants.vexaToXp(vexa);

        double currentXp = playerStore.getSummitXp(playerId, category);
        int currentLevel = AscendConstants.calculateLevelFromXp(currentXp);

        double newXp = currentXp + xpToGain;
        int newLevel = AscendConstants.calculateLevelFromXp(newXp);

        double currentBonus = category.getBonusForLevel(currentLevel);
        double newBonus = category.getBonusForLevel(newLevel);

        double[] currentProgress = AscendConstants.getXpProgress(currentXp);
        double[] newProgress = AscendConstants.getXpProgress(newXp);

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
        // Block summiting in categories locked by an active challenge
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getChallengeManager() != null
                && plugin.getChallengeManager().isSummitBlocked(playerId, category)) {
            return new SummitResult(-1, List.of(), 0.0);
        }

        BigNumber vexa = playerStore.getSummitAccumulatedVexa(playerId);
        double xpToGain = AscendConstants.vexaToXp(vexa);

        if (xpToGain < 1.0) {
            return new SummitResult(-1, List.of(), 0.0);
        }

        // Verify level gain before performing summit
        SummitPreview preview = previewSummit(playerId, category);
        if (!preview.hasGain()) {
            return new SummitResult(-1, List.of(), 0.0);
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

        try {
            io.hyvexa.core.analytics.AnalyticsStore.getInstance().logEvent(playerId, "ascend_summit_up",
                    "{\"category\":\"" + category.name() + "\",\"new_level\":" + newLevel + "}");
        } catch (Exception e) { /* silent */ }

        return new SummitResult(newLevel, mapsWithRunners, xpToGain);
    }

    /**
     * Result of a Summit operation.
     */
    public record SummitResult(int newLevel, List<String> mapsWithRunners, double xpGained) {
        public boolean succeeded() {
            return newLevel >= 0;
        }
    }

    /**
     * Gets the runner speed bonus multiplier.
     * Formula: 1.0 + 0.15 * level (linear below soft cap, sqrt growth above).
     * During active challenges, the full value is divided by the challenge's speed divisor.
     * @return Multiplier value (1.0 at level 0, 2.5 at level 10)
     */
    public double getRunnerSpeedBonus(UUID playerId) {
        double fullBonus = playerStore.getSummitBonusDouble(playerId, SummitCategory.RUNNER_SPEED);

        // Challenge malus: divide full value by speed divisor
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getChallengeManager() != null) {
            double divisor = plugin.getChallengeManager().getSpeedDivisor(playerId);
            if (divisor > 1.0) {
                fullBonus /= divisor;
            }
            // Challenge reward: permanent speed bonus from completed challenges
            fullBonus *= plugin.getChallengeManager().getChallengeSpeedMultiplier(playerId);
        }

        return fullBonus;
    }

    /**
     * Gets the multiplier gain bonus.
     * Formula: 1.0 + 0.30 * level (linear below soft cap, sqrt growth above).
     * During active challenges, the full value is divided by the challenge's multiplier gain divisor.
     * @return Multiplier value (1.0 at level 0, 4.0 at level 10)
     */
    public double getMultiplierGainBonus(UUID playerId) {
        double fullBonus = playerStore.getSummitBonusDouble(playerId, SummitCategory.MULTIPLIER_GAIN);

        // Challenge malus: divide full value by multiplier gain divisor
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getChallengeManager() != null) {
            double divisor = plugin.getChallengeManager().getMultiplierGainDivisor(playerId);
            if (divisor > 1.0) {
                fullBonus /= divisor;
            }
            // Challenge reward: permanent mult gain bonus from completed challenges
            fullBonus *= plugin.getChallengeManager().getChallengeMultiplierGainBonus(playerId);
        }

        return fullBonus;
    }

    /**
     * Get additive bonus to base multiplier increment from Multiplier Boost skill.
     * @return 0.10 if skill unlocked, 0.0 otherwise
     */
    public double getBaseMultiplierBonus(UUID playerId) {
        double bonus = 0.0;
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getAscensionManager() != null) {
            if (plugin.getAscensionManager().hasMultiplierBoost(playerId)) {
                bonus += 0.10;
            }
            if (plugin.getAscensionManager().hasMultiplierBoost2(playerId)) {
                bonus += 0.25;
            }
        }
        return bonus;
    }

    /**
     * Gets the Evolution Power bonus for runner evolution.
     * Formula: 3.0 + 0.10 * level (linear below soft cap, sqrt growth above).
     * Applied per star: multiplier_increment = 0.1 * evolutionPower^stars
     * During active challenges, the full value is divided by the challenge's evolution power divisor.
     * @return Evolution bonus (3.0 at level 0, 4.0 at level 10)
     */
    public double getEvolutionPowerBonus(UUID playerId) {
        double fullBonus = playerStore.getSummitBonusDouble(playerId, SummitCategory.EVOLUTION_POWER);

        // Challenge malus: divide full value by evolution power divisor
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getChallengeManager() != null) {
            double divisor = plugin.getChallengeManager().getEvolutionPowerDivisor(playerId);
            if (divisor > 1.0) {
                fullBonus /= divisor;
            }
        }

        // Skill tree: Evolution Power+ adds +1.0 to base evolution power
        if (plugin != null && plugin.getAscensionManager() != null
                && plugin.getAscensionManager().hasEvolutionPowerBoost(playerId)) {
            fullBonus += 1.0;
        }
        // Skill tree: Evolution Power II adds +1.0 to base evolution power
        if (plugin != null && plugin.getAscensionManager() != null
                && plugin.getAscensionManager().hasEvolutionPowerBoost2(playerId)) {
            fullBonus += 1.0;
        }
        // Skill tree: Evolution Power III adds +2.0 to base evolution power
        if (plugin != null && plugin.getAscensionManager() != null
                && plugin.getAscensionManager().hasEvolutionPowerBoost3(playerId)) {
            fullBonus += 2.0;
        }
        // Challenge reward: permanent evo power bonus from completed challenges
        if (plugin != null && plugin.getChallengeManager() != null) {
            fullBonus += plugin.getChallengeManager().getChallengeEvolutionPowerBonus(playerId);
        }
        return fullBonus;
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
        double xpToGain,
        double currentXpInLevel,
        double currentXpRequired,
        double newXpInLevel,
        double newXpRequired
    ) {
        public double bonusGain() {
            return newBonus - currentBonus;
        }

        public boolean hasGain() {
            return levelGain > 0;
        }
    }
}
