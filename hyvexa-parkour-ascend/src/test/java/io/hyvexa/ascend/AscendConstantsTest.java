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
        assertEquals(0.10, AscendConstants.getMapSpeedMultiplier(0), 1e-9);
    }

    @Test
    void getMapSpeedMultiplierOutOfBounds() {
        // With single-mine simplification, all display orders return the same constant
        assertEquals(AscendConstants.MAP_SPEED_MULTIPLIER, AscendConstants.getMapSpeedMultiplier(99), 1e-9);
    }

    @Test
    void getMapSpeedMultiplierNegative() {
        assertEquals(AscendConstants.MAP_SPEED_MULTIPLIER, AscendConstants.getMapSpeedMultiplier(-1), 1e-9);
    }

    // --- Early level boost ---

    @Test
    void earlyLevelBoostFullAtLevelZero() {
        // Map 2, level 0, 0 stars -> max boost = 2.0, result = 1.0 + (2.0-1.0)*1.0 = 2.0
        assertEquals(2.0, AscendConstants.calculateEarlyLevelBoost(0, 2, 0), 1e-9);
    }

    @Test
    void earlyLevelBoostAtThreshold() {
        // At EARLY_LEVEL_BOOST_THRESHOLD (10), boost = 1.0
        assertEquals(1.0, AscendConstants.calculateEarlyLevelBoost(10, 2, 0), 1e-9);
    }

    @Test
    void earlyLevelBoostWithStars() {
        // Stars > 0 means no boost
        assertEquals(1.0, AscendConstants.calculateEarlyLevelBoost(0, 2, 1), 1e-9);
    }

    @Test
    void earlyLevelBoostMapZeroNoBoost() {
        // Map 0 has boost = 1.0, so result = 1.0
        assertEquals(1.0, AscendConstants.calculateEarlyLevelBoost(0, 0, 0), 1e-9);
    }

    // --- Runner multiplier increment ---

    @Test
    void runnerMultiplierIncrementZeroStars() {
        BigNumber result = AscendConstants.getRunnerMultiplierIncrement(0);
        assertEquals(0.1, result.toDouble(), 1e-6);
    }

    @Test
    void runnerMultiplierIncrementStarsIncrease() {
        BigNumber base = AscendConstants.getRunnerMultiplierIncrement(0);
        BigNumber withStars = AscendConstants.getRunnerMultiplierIncrement(1);
        assertTrue(withStars.gt(base), "Stars should increase multiplier increment");
    }

    @Test
    void runnerMultiplierIncrementOverflowCapped() {
        // Very high stars should not produce NaN/Infinity
        BigNumber result = AscendConstants.getRunnerMultiplierIncrement(1000);
        assertFalse(Double.isNaN(result.toDouble()));
        assertFalse(Double.isInfinite(result.toDouble()));
    }

    // --- Runner upgrade cost ---

    @Test
    void runnerUpgradeCostBaseCase() {
        // level=0, map=0, stars=0 -> 5*2^0 + 0*10 = 5, * mapMult(1.0) = 5
        BigNumber cost = AscendConstants.getRunnerUpgradeCost(0, 0, 0);
        assertEquals(5.0, cost.toDouble(), 1.0);
    }

    @Test
    void runnerUpgradeCostHigherLevelCostsMore() {
        BigNumber cost0 = AscendConstants.getRunnerUpgradeCost(0, 0, 0);
        BigNumber cost5 = AscendConstants.getRunnerUpgradeCost(5, 0, 0);
        assertTrue(cost5.gt(cost0));
    }

    @Test
    void runnerUpgradeCostHigherMapCostsMore() {
        BigNumber costMap0 = AscendConstants.getRunnerUpgradeCost(0, 0, 0);
        BigNumber costMap3 = AscendConstants.getRunnerUpgradeCost(0, 3, 0);
        assertTrue(costMap3.gt(costMap0));
    }

    // --- Elevation cost ---

    @Test
    void elevationCostLevelZero() {
        BigNumber cost = AscendConstants.getElevationLevelUpCost(0);
        // baseCost * growth^(0^0.72) = 30000 * 1.15^0 = 30000
        assertEquals(30000.0, cost.toDouble(), 1.0);
    }

    @Test
    void elevationCostIncreases() {
        BigNumber cost0 = AscendConstants.getElevationLevelUpCost(0);
        BigNumber cost1 = AscendConstants.getElevationLevelUpCost(1);
        assertTrue(cost1.gt(cost0));
    }

    @Test
    void elevationCostSoftCapContinuity() {
        // Cost at SOFT_CAP and SOFT_CAP+1 should not jump drastically
        BigNumber atCap = AscendConstants.getElevationLevelUpCost(AscendConstants.ELEVATION_SOFT_CAP);
        BigNumber afterCap = AscendConstants.getElevationLevelUpCost(AscendConstants.ELEVATION_SOFT_CAP + 1);
        assertTrue(afterCap.gt(atCap), "Cost should still increase after soft cap");
        // The ratio should be reasonable (not a huge discontinuity)
        double ratio = afterCap.toDouble() / atCap.toDouble();
        assertTrue(ratio < 2.0, "Cost ratio across soft cap should be < 2x, got: " + ratio);
    }

    @Test
    void elevationCostWithDiscount() {
        BigNumber full = AscendConstants.getElevationLevelUpCost(10);
        BigNumber discounted = AscendConstants.getElevationLevelUpCost(10, BigNumber.fromDouble(0.8));
        assertTrue(full.gt(discounted), "Discounted cost should be less than full");
    }

    // --- Elevation purchase ---

    @Test
    void elevationPurchaseWithZeroBudget() {
        var result = AscendConstants.calculateElevationPurchase(0, BigNumber.ZERO);
        assertEquals(0, result.levels);
    }

    @Test
    void elevationPurchaseWithBudget() {
        // Give enough to buy at least 1 level (level 0 costs 30000)
        var result = AscendConstants.calculateElevationPurchase(0, BigNumber.fromLong(100_000));
        assertTrue(result.levels > 0, "Should afford at least 1 level");
        assertTrue(result.cost.lte(BigNumber.fromLong(100_000)), "Total cost should not exceed budget");
    }

    @Test
    void elevationPurchaseCostDoesNotExceedBudget() {
        BigNumber budget = BigNumber.fromLong(1_000_000);
        var result = AscendConstants.calculateElevationPurchase(0, budget);
        assertTrue(result.cost.lte(budget));
    }

    // --- Summit categories ---

    @Test
    void multiplierGainBonusAtLevelZero() {
        assertEquals(1.0, AscendConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(0), 1e-9);
    }

    @Test
    void multiplierGainLinearZone() {
        // Level 10: base(1.0) + increment(0.30) * 10 = 4.0
        assertEquals(4.0, AscendConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(10), 1e-9);
    }

    @Test
    void multiplierGainSqrtZone() {
        // Level 30 is in sqrt zone (> soft cap 25)
        double bonus = AscendConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(30);
        double linearPart = 1.0 + 0.30 * 25;
        assertTrue(bonus > linearPart, "Sqrt zone should add beyond linear");
    }

    @Test
    void multiplierGainFourthRootZone() {
        // Level 600 is in fourth-root zone (> deep cap 500)
        double bonus = AscendConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(600);
        double atDeepCap = AscendConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(500);
        assertTrue(bonus > atDeepCap);
    }

    @Test
    void multiplierGainPostCapZone() {
        // Level 1500 is in post-cap zone (> XP softcap 1000)
        double at1000 = AscendConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(1000);
        double at1500 = AscendConstants.SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(1500);
        assertTrue(at1500 > at1000, "Post-cap zone should still grow");
    }

    // --- XP system ---

    @Test
    void xpForLevelZero() {
        assertEquals(0.0, AscendConstants.getXpForLevel(0), 1e-9);
    }

    @Test
    void xpForLevelOne() {
        // level^2 = 1
        assertEquals(1.0, AscendConstants.getXpForLevel(1), 1e-9);
    }

    @Test
    void cumulativeXpMonotonicallyIncreasing() {
        double prev = 0;
        for (int level = 1; level <= 20; level++) {
            double cum = AscendConstants.getCumulativeXpForLevel(level);
            assertTrue(cum > prev, "Cumulative XP should increase at level " + level);
            prev = cum;
        }
    }

    @Test
    void calculateLevelFromXpInverse() {
        // Level -> cumXp -> level should round-trip
        for (int level : new int[]{0, 1, 10, 100, 500}) {
            double cumXp = AscendConstants.getCumulativeXpForLevel(level);
            int calculated = AscendConstants.calculateLevelFromXp(cumXp);
            assertEquals(level, calculated, "Round-trip failed for level " + level);
        }
    }

    @Test
    void voltToXpZero() {
        assertEquals(0.0, AscendConstants.voltToXp(BigNumber.ZERO));
    }

    @Test
    void voltToXpNegative() {
        assertEquals(0.0, AscendConstants.voltToXp(BigNumber.fromDouble(-100)));
    }

    // --- Skill tree prerequisites ---

    @Test
    void autoRunnersNoPrerequisites() {
        assertTrue(AscendConstants.SkillTreeNode.AUTO_RUNNERS.hasPrerequisitesSatisfied(Set.of()));
    }

    @Test
    void autoEvolutionRequiresAutoRunners() {
        assertFalse(AscendConstants.SkillTreeNode.AUTO_EVOLUTION.hasPrerequisitesSatisfied(Set.of()));
        assertTrue(AscendConstants.SkillTreeNode.AUTO_EVOLUTION.hasPrerequisitesSatisfied(
            EnumSet.of(AscendConstants.SkillTreeNode.AUTO_RUNNERS)));
    }

    // --- Challenge lookup ---

    @Test
    void challengeFromIdValid() {
        assertEquals(AscendConstants.ChallengeType.CHALLENGE_1, AscendConstants.ChallengeType.fromId(1));
    }

    @Test
    void challengeFromIdInvalid() {
        assertNull(AscendConstants.ChallengeType.fromId(99));
    }
}
