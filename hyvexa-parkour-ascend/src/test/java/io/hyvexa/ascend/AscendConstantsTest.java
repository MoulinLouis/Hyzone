package io.hyvexa.ascend;

import io.hyvexa.common.math.BigNumber;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AscendConstantsTest {

    // --- Map accessors with bounds ---

    @Test
    void getMapSpeedMultiplierValid() {
        assertEquals(0.10, RunnerEconomyConstants.getMapSpeedMultiplier(0), 1e-9);
    }

    @Test
    void getMapSpeedMultiplierOutOfBounds() {
        // With single-mine simplification, all display orders return the same constant
        assertEquals(RunnerEconomyConstants.MAP_SPEED_MULTIPLIER, RunnerEconomyConstants.getMapSpeedMultiplier(99), 1e-9);
    }

    @Test
    void getMapSpeedMultiplierNegative() {
        assertEquals(RunnerEconomyConstants.MAP_SPEED_MULTIPLIER, RunnerEconomyConstants.getMapSpeedMultiplier(-1), 1e-9);
    }

    // --- Early level boost ---

    @Test
    void earlyLevelBoostFullAtLevelZero() {
        // Map 2, level 0, 0 stars -> max boost = 2.0, result = 1.0 + (2.0-1.0)*1.0 = 2.0
        assertEquals(2.0, RunnerEconomyConstants.calculateEarlyLevelBoost(0, 2, 0), 1e-9);
    }

    @Test
    void earlyLevelBoostAtThreshold() {
        // At EARLY_LEVEL_BOOST_THRESHOLD (10), boost = 1.0
        assertEquals(1.0, RunnerEconomyConstants.calculateEarlyLevelBoost(10, 2, 0), 1e-9);
    }

    @Test
    void earlyLevelBoostNearThresholdUsesLinearDecay() {
        assertEquals(1.1, RunnerEconomyConstants.calculateEarlyLevelBoost(9, 2, 0), 1e-9);
    }

    @Test
    void earlyLevelBoostWithStars() {
        // Stars > 0 means no boost
        assertEquals(1.0, RunnerEconomyConstants.calculateEarlyLevelBoost(0, 2, 1), 1e-9);
    }

    @Test
    void earlyLevelBoostMapZeroNoBoost() {
        // Map 0 has boost = 1.0, so result = 1.0
        assertEquals(1.0, RunnerEconomyConstants.calculateEarlyLevelBoost(0, 0, 0), 1e-9);
    }

    @Test
    void mapUpgradeOffsetNegativeUsesLastConfiguredEntry() {
        assertEquals(RunnerEconomyConstants.MAP_UPGRADE_OFFSET[RunnerEconomyConstants.MAP_UPGRADE_OFFSET.length - 1],
            RunnerEconomyConstants.getMapUpgradeOffset(-1));
    }

    @Test
    void mapUpgradeMultiplierOutOfBoundsUsesLastConfiguredEntry() {
        assertEquals(RunnerEconomyConstants.MAP_UPGRADE_MULTIPLIER[RunnerEconomyConstants.MAP_UPGRADE_MULTIPLIER.length - 1],
            RunnerEconomyConstants.getMapUpgradeMultiplier(99), 1e-9);
    }

    // --- Runner multiplier increment ---

    @Test
    void runnerMultiplierIncrementZeroStars() {
        BigNumber result = RunnerEconomyConstants.getRunnerMultiplierIncrement(0);
        assertEquals(0.1, result.toDouble(), 1e-6);
    }

    @Test
    void runnerMultiplierIncrementStarsIncrease() {
        BigNumber base = RunnerEconomyConstants.getRunnerMultiplierIncrement(0);
        BigNumber withStars = RunnerEconomyConstants.getRunnerMultiplierIncrement(1);
        assertTrue(withStars.gt(base), "Stars should increase multiplier increment");
    }

    @Test
    void runnerMultiplierIncrementUsesConfiguredBonuses() {
        BigNumber result = RunnerEconomyConstants.getRunnerMultiplierIncrement(2, 1.5, 4.0, 0.25);
        assertEquals(8.4, result.toDouble(), 1e-9);
    }

    @Test
    void runnerMultiplierIncrementOverflowCapped() {
        // Very high stars should not produce NaN/Infinity
        BigNumber result = RunnerEconomyConstants.getRunnerMultiplierIncrement(1000);
        assertFalse(Double.isNaN(result.toDouble()));
        assertFalse(Double.isInfinite(result.toDouble()));
    }

    @Test
    void runnerMultiplierIncrementGuardsAgainstNaNAndInfinityInputs() {
        BigNumber infinitePower = RunnerEconomyConstants.getRunnerMultiplierIncrement(2, 1.0, Double.POSITIVE_INFINITY, 0.0);
        BigNumber nanBonus = RunnerEconomyConstants.getRunnerMultiplierIncrement(0, Double.NaN, 3.0, 0.0);

        assertFalse(Double.isNaN(infinitePower.toDouble()));
        assertFalse(Double.isInfinite(infinitePower.toDouble()));
        assertTrue(infinitePower.toDouble() > 1e306);
        assertEquals(Double.MAX_VALUE, nanBonus.toDouble());
    }

    // --- Runner upgrade cost ---

    @Test
    void runnerUpgradeCostBaseCase() {
        // level=0, map=0, stars=0 -> 5*2^0 + 0*10 = 5, * mapMult(1.0) = 5
        BigNumber cost = RunnerEconomyConstants.getRunnerUpgradeCost(0, 0, 0);
        assertEquals(5.0, cost.toDouble(), 1.0);
    }

    @Test
    void runnerUpgradeCostHigherLevelCostsMore() {
        BigNumber cost0 = RunnerEconomyConstants.getRunnerUpgradeCost(0, 0, 0);
        BigNumber cost5 = RunnerEconomyConstants.getRunnerUpgradeCost(5, 0, 0);
        assertTrue(cost5.gt(cost0));
    }

    @Test
    void runnerUpgradeCostHigherMapCostsMore() {
        BigNumber costMap0 = RunnerEconomyConstants.getRunnerUpgradeCost(0, 0, 0);
        BigNumber costMap3 = RunnerEconomyConstants.getRunnerUpgradeCost(0, 3, 0);
        assertTrue(costMap3.gt(costMap0));
    }

    @Test
    void runnerUpgradeCostHigherStarsCostsMore() {
        BigNumber base = RunnerEconomyConstants.getRunnerUpgradeCost(0, 0, 0);
        BigNumber withStars = RunnerEconomyConstants.getRunnerUpgradeCost(0, 0, 1);
        assertTrue(withStars.gt(base));
    }

    @Test
    void runnerUpgradeCostMatchesFormulaWithEarlyBoost() {
        BigNumber cost = RunnerEconomyConstants.getRunnerUpgradeCost(0, 2, 0);
        assertEquals(152.0, cost.toDouble(), 1e-6);
    }

    @Test
    void runnerUpgradeCostMatchesFormulaAcrossEvolutionCycles() {
        BigNumber cost = RunnerEconomyConstants.getRunnerUpgradeCost(0, 0, 1);
        assertEquals(5_243_080.0, cost.toDouble(), 1e-3);
    }

    // --- Elevation cost ---

    @Test
    void elevationCostLevelZero() {
        BigNumber cost = ElevationConstants.getElevationLevelUpCost(0);
        // baseCost * growth^(0^0.72) = 30000 * 1.15^0 = 30000
        assertEquals(30000.0, cost.toDouble(), 1.0);
    }

    @Test
    void elevationCostIncreases() {
        BigNumber cost0 = ElevationConstants.getElevationLevelUpCost(0);
        BigNumber cost1 = ElevationConstants.getElevationLevelUpCost(1);
        assertTrue(cost1.gt(cost0));
    }

    @Test
    void elevationCostSoftCapContinuity() {
        // Cost at SOFT_CAP and SOFT_CAP+1 should not jump drastically
        BigNumber atCap = ElevationConstants.getElevationLevelUpCost(ElevationConstants.ELEVATION_SOFT_CAP);
        BigNumber afterCap = ElevationConstants.getElevationLevelUpCost(ElevationConstants.ELEVATION_SOFT_CAP + 1);
        assertTrue(afterCap.gt(atCap), "Cost should still increase after soft cap");
        // The ratio should be reasonable (not a huge discontinuity)
        double ratio = afterCap.toDouble() / atCap.toDouble();
        assertTrue(ratio < 2.0, "Cost ratio across soft cap should be < 2x, got: " + ratio);
    }

    @Test
    void elevationCostWithDiscount() {
        BigNumber full = ElevationConstants.getElevationLevelUpCost(10);
        BigNumber discounted = ElevationConstants.getElevationLevelUpCost(10, BigNumber.fromDouble(0.8));
        assertTrue(full.gt(discounted), "Discounted cost should be less than full");
    }

    // --- Elevation purchase ---

    @Test
    void elevationPurchaseWithZeroBudget() {
        var result = ElevationConstants.calculateElevationPurchase(0, BigNumber.ZERO);
        assertEquals(0, result.levels);
    }

    @Test
    void elevationPurchaseWithBudget() {
        // Give enough to buy at least 1 level (level 0 costs 30000)
        var result = ElevationConstants.calculateElevationPurchase(0, BigNumber.fromLong(100_000));
        assertTrue(result.levels > 0, "Should afford at least 1 level");
        assertTrue(result.cost.lte(BigNumber.fromLong(100_000)), "Total cost should not exceed budget");
    }

    @Test
    void elevationPurchaseCostDoesNotExceedBudget() {
        BigNumber budget = BigNumber.fromLong(1_000_000);
        var result = ElevationConstants.calculateElevationPurchase(0, budget);
        assertTrue(result.cost.lte(budget));
    }

    // --- Summit categories ---

    @Test
    void multiplierGainBonusAtLevelZero() {
        assertEquals(1.0, SummitConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(0), 1e-9);
    }

    @Test
    void multiplierGainLinearZone() {
        // Level 10: base(1.0) + increment(0.30) * 10 = 4.0
        assertEquals(4.0, SummitConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(10), 1e-9);
    }

    @Test
    void multiplierGainSqrtZone() {
        // Level 30 is in sqrt zone (> soft cap 25)
        double bonus = SummitConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(30);
        double linearPart = 1.0 + 0.30 * 25;
        assertTrue(bonus > linearPart, "Sqrt zone should add beyond linear");
    }

    @Test
    void multiplierGainFourthRootZone() {
        // Level 600 is in fourth-root zone (> deep cap 500)
        double bonus = SummitConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(600);
        double atDeepCap = SummitConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(500);
        assertTrue(bonus > atDeepCap);
    }

    @Test
    void multiplierGainPostCapZone() {
        // Level 1500 is in post-cap zone (> XP softcap 1000)
        double at1000 = SummitConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(1000);
        double at1500 = SummitConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(1500);
        assertTrue(at1500 > at1000, "Post-cap zone should still grow");
    }

    // --- XP system ---

    @Test
    void xpForLevelZero() {
        assertEquals(0.0, SummitConstants.getXpForLevel(0), 1e-9);
    }

    @Test
    void xpForLevelOne() {
        // level^2 = 1
        assertEquals(1.0, SummitConstants.getXpForLevel(1), 1e-9);
    }

    @Test
    void cumulativeXpMonotonicallyIncreasing() {
        double prev = 0;
        for (int level = 1; level <= 20; level++) {
            double cum = SummitConstants.getCumulativeXpForLevel(level);
            assertTrue(cum > prev, "Cumulative XP should increase at level " + level);
            prev = cum;
        }
    }

    @Test
    void calculateLevelFromXpInverse() {
        // Level -> cumXp -> level should round-trip
        for (int level : new int[]{0, 1, 10, 100, 500}) {
            double cumXp = SummitConstants.getCumulativeXpForLevel(level);
            int calculated = SummitConstants.calculateLevelFromXp(cumXp);
            assertEquals(level, calculated, "Round-trip failed for level " + level);
        }
    }

    @Test
    void voltToXpZero() {
        assertEquals(0.0, SummitConstants.voltToXp(BigNumber.ZERO));
    }

    @Test
    void voltToXpNegative() {
        assertEquals(0.0, SummitConstants.voltToXp(BigNumber.fromDouble(-100)));
    }

    // --- Skill tree prerequisites ---

    @Test
    void autoRunnersNoPrerequisites() {
        assertTrue(AscensionConstants.SkillTreeNode.AUTO_RUNNERS.hasPrerequisitesSatisfied(Set.of()));
    }

    @Test
    void autoEvolutionRequiresAutoRunners() {
        assertFalse(AscensionConstants.SkillTreeNode.AUTO_EVOLUTION.hasPrerequisitesSatisfied(Set.of()));
        assertTrue(AscensionConstants.SkillTreeNode.AUTO_EVOLUTION.hasPrerequisitesSatisfied(
            EnumSet.of(AscensionConstants.SkillTreeNode.AUTO_RUNNERS)));
    }

    // --- Challenge lookup ---

    @Test
    void challengeFromIdValid() {
        assertEquals(AscensionConstants.ChallengeType.CHALLENGE_1, AscensionConstants.ChallengeType.fromId(1));
    }

    @Test
    void challengeFromIdInvalid() {
        assertNull(AscensionConstants.ChallengeType.fromId(99));
    }
}
