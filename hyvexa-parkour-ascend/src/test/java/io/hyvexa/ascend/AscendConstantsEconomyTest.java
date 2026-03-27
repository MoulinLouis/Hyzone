package io.hyvexa.ascend;

import io.hyvexa.common.math.BigNumber;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AscendConstantsEconomyTest {

    // ========================================
    // Map Balance Lookups
    // ========================================

    @Test
    void getMapBaseRunTimeMsReturnsCorrectValuesForAllOrders() {
        assertEquals(5000L, RunnerEconomyConstants.getMapBaseRunTimeMs(0));
        assertEquals(10000L, RunnerEconomyConstants.getMapBaseRunTimeMs(1));
        assertEquals(16000L, RunnerEconomyConstants.getMapBaseRunTimeMs(2));
        assertEquals(26000L, RunnerEconomyConstants.getMapBaseRunTimeMs(3));
        assertEquals(42000L, RunnerEconomyConstants.getMapBaseRunTimeMs(4));
        assertEquals(68000L, RunnerEconomyConstants.getMapBaseRunTimeMs(5));
    }

    @Test
    void getMapUnlockPriceReturnsCorrectValuesForAllOrders() {
        assertEquals(0L, RunnerEconomyConstants.getMapUnlockPrice(0));
        assertEquals(100L, RunnerEconomyConstants.getMapUnlockPrice(1));
        assertEquals(500L, RunnerEconomyConstants.getMapUnlockPrice(2));
        assertEquals(2500L, RunnerEconomyConstants.getMapUnlockPrice(3));
        assertEquals(10000L, RunnerEconomyConstants.getMapUnlockPrice(4));
        assertEquals(50000L, RunnerEconomyConstants.getMapUnlockPrice(5));
    }

    @Test
    void getMapBaseRewardReturnsCorrectValuesForAllOrders() {
        assertEquals(1L, RunnerEconomyConstants.getMapBaseReward(0));
        assertEquals(5L, RunnerEconomyConstants.getMapBaseReward(1));
        assertEquals(25L, RunnerEconomyConstants.getMapBaseReward(2));
        assertEquals(100L, RunnerEconomyConstants.getMapBaseReward(3));
        assertEquals(500L, RunnerEconomyConstants.getMapBaseReward(4));
        assertEquals(2500L, RunnerEconomyConstants.getMapBaseReward(5));
    }

    @Test
    void clampedLookupReturnsLastElementForOutOfBoundsIndex() {
        // Out-of-bounds returns last element
        assertEquals(2500L, RunnerEconomyConstants.getMapBaseReward(99));
        assertEquals(68000L, RunnerEconomyConstants.getMapBaseRunTimeMs(99));
        // Negative also returns last element
        assertEquals(2500L, RunnerEconomyConstants.getMapBaseReward(-1));
        assertEquals(68000L, RunnerEconomyConstants.getMapBaseRunTimeMs(-1));
    }

    // ========================================
    // Runner System
    // ========================================

    @Test
    void getRunnerUpgradeCostIsMonotonicallyIncreasingPerMap() {
        for (int map = 0; map <= 5; map++) {
            BigNumber prev = RunnerEconomyConstants.getRunnerUpgradeCost(0, map, 0);
            for (int level = 1; level < 20; level++) {
                BigNumber current = RunnerEconomyConstants.getRunnerUpgradeCost(level, map, 0);
                assertTrue(current.gte(prev),
                    "Cost not monotonic at map=" + map + " level=" + level);
                prev = current;
            }
        }
    }

    @Test
    void getRunnerUpgradeCostScalesWithLevelAndMap() {
        // Base case: level=0, map=0, stars=0 -> 5*2^0 + 0*10 = 5
        BigNumber base = RunnerEconomyConstants.getRunnerUpgradeCost(0, 0, 0);
        assertEquals(5.0, base.toDouble(), 1.0);

        // Higher level costs more
        BigNumber higher = RunnerEconomyConstants.getRunnerUpgradeCost(5, 0, 0);
        assertTrue(higher.gt(base));

        // Higher map costs more
        BigNumber higherMap = RunnerEconomyConstants.getRunnerUpgradeCost(0, 3, 0);
        assertTrue(higherMap.gt(base));
    }

    @Test
    void calculateEarlyLevelBoostDecaysToOneAtThreshold() {
        // Map 2, level 0, stars 0: boost should be 2.0
        assertEquals(2.0, RunnerEconomyConstants.calculateEarlyLevelBoost(0, 2, 0), 1e-9);
        // At threshold (10): boost = 1.0
        assertEquals(1.0, RunnerEconomyConstants.calculateEarlyLevelBoost(10, 2, 0), 1e-9);
        // Stars > 0: no boost
        assertEquals(1.0, RunnerEconomyConstants.calculateEarlyLevelBoost(0, 2, 1), 1e-9);
    }

    @Test
    void getRunnerMultiplierIncrementScalesWithStars() {
        // Stars=0: base 0.1
        BigNumber base = RunnerEconomyConstants.getRunnerMultiplierIncrement(0);
        assertEquals(0.1, base.toDouble(), 1e-6);

        // Stars=1: 0.1 * 3.0^1 = 0.3
        BigNumber stars1 = RunnerEconomyConstants.getRunnerMultiplierIncrement(1);
        assertEquals(0.3, stars1.toDouble(), 1e-6);

        // Stars=5: 0.1 * 3.0^5 = 24.3
        BigNumber stars5 = RunnerEconomyConstants.getRunnerMultiplierIncrement(5);
        assertEquals(24.3, stars5.toDouble(), 1e-3);

        // Exponential growth
        assertTrue(stars5.gt(stars1));
        assertTrue(stars1.gt(base));
    }

    @Test
    void getRunnerMultiplierIncrementWithBonuses() {
        // With multiplierGainBonus=1.5, evolutionPower=4.0, baseIncrement=0.25, stars=2
        // (0.1 + 0.25) * 4.0^2 * 1.5 = 0.35 * 16 * 1.5 = 8.4
        BigNumber result = RunnerEconomyConstants.getRunnerMultiplierIncrement(2, 1.5, 4.0, 0.25);
        assertEquals(8.4, result.toDouble(), 1e-9);
    }

    @Test
    void getRunnerEntityTypeReturnsCorrectKweebecForStars() {
        assertEquals("Kweebec_Seedling", RunnerEconomyConstants.getRunnerEntityType(0));
        assertEquals("Kweebec_Sapling", RunnerEconomyConstants.getRunnerEntityType(1));
        assertEquals("Kweebec_Sproutling", RunnerEconomyConstants.getRunnerEntityType(2));
        assertEquals("Kweebec_Sapling_Pink", RunnerEconomyConstants.getRunnerEntityType(3));
        assertEquals("Kweebec_Razorleaf", RunnerEconomyConstants.getRunnerEntityType(4));
        assertEquals("Kweebec_Rootling", RunnerEconomyConstants.getRunnerEntityType(5));
        // Out of range: fallback to Sapling
        assertEquals("Kweebec_Sapling", RunnerEconomyConstants.getRunnerEntityType(6));
        assertEquals("Kweebec_Sapling", RunnerEconomyConstants.getRunnerEntityType(-1));
    }

    // ========================================
    // Elevation System
    // ========================================

    @Test
    void getElevationLevelUpCostIsMonotonicallyIncreasing() {
        BigNumber prev = ElevationConstants.getElevationLevelUpCost(0);
        for (int level = 1; level <= 400; level++) {
            BigNumber current = ElevationConstants.getElevationLevelUpCost(level);
            assertTrue(current.gt(prev),
                "Elevation cost not increasing at level " + level);
            prev = current;
        }
    }

    @Test
    void getElevationLevelUpCostBelowSoftCapUsesFirstCurve() {
        // Level 0: 30000 * 1.15^(0^0.72) = 30000 * 1 = 30000
        BigNumber cost0 = ElevationConstants.getElevationLevelUpCost(0);
        assertEquals(30000.0, cost0.toDouble(), 1.0);

        // Level 100: verify formula = BASE * GROWTH^(level^CURVE)
        double expectedLog10 = Math.log10(30000) + Math.pow(100, 0.72) * Math.log10(1.15);
        int expectedExp = (int) Math.floor(expectedLog10);
        double expectedMantissa = Math.pow(10.0, expectedLog10 - expectedExp);
        BigNumber cost100 = ElevationConstants.getElevationLevelUpCost(100);
        // Compare in log space for large numbers
        double actualLog10 = Math.log10(cost100.getMantissa()) + cost100.getExponent();
        assertEquals(expectedLog10, actualLog10, 0.1);
    }

    @Test
    void getElevationLevelUpCostAboveSoftCapUsesLateCurve() {
        // At soft cap boundary: continuity
        BigNumber atCap = ElevationConstants.getElevationLevelUpCost(300);
        BigNumber afterCap = ElevationConstants.getElevationLevelUpCost(301);
        assertTrue(afterCap.gt(atCap), "Cost should increase after soft cap");
        double ratio = afterCap.toDouble() / atCap.toDouble();
        assertTrue(ratio < 2.0, "Transition should be smooth, ratio: " + ratio);

        // Level 400 should use the late curve (0.58 exponent)
        BigNumber cost400 = ElevationConstants.getElevationLevelUpCost(400);
        assertTrue(cost400.gt(atCap));
    }

    @Test
    void getElevationLevelUpCostWithMultiplier() {
        BigNumber full = ElevationConstants.getElevationLevelUpCost(10);
        BigNumber doubled = ElevationConstants.getElevationLevelUpCost(10, BigNumber.fromDouble(2.0));
        BigNumber halved = ElevationConstants.getElevationLevelUpCost(10, BigNumber.fromDouble(0.5));

        // 2x multiplier = 2x cost
        assertEquals(full.toDouble() * 2.0, doubled.toDouble(), full.toDouble() * 0.01);
        // 0.5x multiplier = half cost
        assertEquals(full.toDouble() * 0.5, halved.toDouble(), full.toDouble() * 0.01);
    }

    @Test
    void calculateElevationPurchaseReturnsCorrectLevelsAndCost() {
        // Zero volts: 0 levels
        var zero = ElevationConstants.calculateElevationPurchase(0, BigNumber.ZERO);
        assertEquals(0, zero.levels);

        // Enough for some levels (level 0 costs 30000)
        var result = ElevationConstants.calculateElevationPurchase(0, BigNumber.fromLong(100_000));
        assertTrue(result.levels > 0, "Should afford at least 1 level");

        // Verify cost matches expected sum
        BigNumber expectedCost = BigNumber.ZERO;
        for (int i = 0; i < result.levels; i++) {
            expectedCost = expectedCost.add(ElevationConstants.getElevationLevelUpCost(i));
        }
        assertEquals(expectedCost.toDouble(), result.cost.toDouble(), expectedCost.toDouble() * 0.001);
    }

    @Test
    void calculateElevationPurchaseNeverExceedsAvailableVolt() {
        long[] budgets = {30_000L, 100_000L, 1_000_000L, 100_000_000L};
        for (long budget : budgets) {
            BigNumber volt = BigNumber.fromLong(budget);
            var result = ElevationConstants.calculateElevationPurchase(0, volt);
            assertTrue(result.cost.lte(volt),
                "Cost " + result.cost + " exceeds budget " + budget);
        }
    }

    @Test
    void getElevationMultiplierReturnsLevel() {
        // Math.max(1, level) — minimum 1
        assertEquals(1.0, ElevationConstants.getElevationMultiplier(0), 1e-9);
        assertEquals(1.0, ElevationConstants.getElevationMultiplier(1), 1e-9);
        assertEquals(100.0, ElevationConstants.getElevationMultiplier(100), 1e-9);
    }

    @Test
    void formatElevationMultiplierFormatsCorrectly() {
        assertEquals("x1", ElevationConstants.formatElevationMultiplier(0));
        assertEquals("x5", ElevationConstants.formatElevationMultiplier(5));
        assertEquals("x1", ElevationConstants.formatElevationMultiplier(1));
    }

    // ========================================
    // Summit XP System
    // ========================================

    @Test
    void voltToXpIsMonotonicallyIncreasing() {
        BigNumber[] volts = {
            BigNumber.fromLong(1_000_000_000L),   // 1B
            BigNumber.of(1, 12),                    // 1T
            BigNumber.of(1, 15),                    // 1Q
            BigNumber.of(1, 24),                    // 1Sp
            BigNumber.of(1, 33),                    // 1Dc
        };
        double prev = 0;
        for (BigNumber volt : volts) {
            double xp = SummitConstants.voltToXp(volt);
            assertTrue(xp > prev, "voltToXp not increasing at " + volt);
            prev = xp;
        }
    }

    @Test
    void voltToXpBelowDecillionUsesPowerCurve() {
        // Test various volts below 10^33
        BigNumber volt1B = BigNumber.fromLong(1_000_000_000L);
        double xp = SummitConstants.voltToXp(volt1B);
        assertTrue(xp >= 0, "XP should be non-negative for 1B volt");

        // Higher volt = more XP
        BigNumber volt1T = BigNumber.of(1, 12);
        double xp2 = SummitConstants.voltToXp(volt1T);
        assertTrue(xp2 > xp);
    }

    @Test
    void voltToXpAboveDecillionUsesSoftGrowth() {
        BigNumber dc = BigNumber.of(1, 33);
        BigNumber dc10 = BigNumber.of(1, 34);
        double xpDc = SummitConstants.voltToXp(dc);
        double xpDc10 = SummitConstants.voltToXp(dc10);

        assertTrue(xpDc10 > xpDc, "XP should increase above 1Dc");
        // Growth should be sub-linear — 10x more volt should NOT give 10x more XP
        assertTrue(xpDc10 < xpDc * 10, "Post-Dc growth should be sub-linear");
    }

    @Test
    void xpToVoltIsInverseOfVoltToXp() {
        // Test round-trip for volt values below 1Dc
        // Use larger values where floor() in voltToXp causes less relative error
        BigNumber[] testVolts = {
            BigNumber.of(1, 15),  // 1Q
            BigNumber.of(1, 20),  // 100Qi
            BigNumber.of(1, 25),  // 1Oc
            BigNumber.of(1, 30),  // 1No
        };
        for (BigNumber volt : testVolts) {
            double xp = SummitConstants.voltToXp(volt);
            if (xp <= 0) continue;
            BigNumber recovered = SummitConstants.xpToVolt(xp);
            // Allow tolerance due to floor() in voltToXp and floating-point precision
            double ratio = recovered.toDouble() / volt.toDouble();
            assertTrue(ratio > 0.5 && ratio < 2.0,
                "Round-trip failed for " + volt + ": ratio=" + ratio);
        }
    }

    @Test
    void getXpForLevelBelowSoftcapUsesQuadratic() {
        assertEquals(0.0, SummitConstants.getXpForLevel(0), 1e-9);
        assertEquals(1.0, SummitConstants.getXpForLevel(1), 1e-9);
        assertEquals(100.0, SummitConstants.getXpForLevel(10), 1e-9);
        assertEquals(10000.0, SummitConstants.getXpForLevel(100), 1e-9);
    }

    @Test
    void getXpForLevelAboveSoftcapUsesQuartic() {
        // At softcap (1000): level^2 = 1,000,000
        double atSoftcap = SummitConstants.getXpForLevel(1000);
        assertEquals(1_000_000.0, atSoftcap, 1e-3);

        // Level 1001: 1001^4 / 1000^2 = ~1,004,006
        double above = SummitConstants.getXpForLevel(1001);
        assertTrue(above > atSoftcap, "XP should increase above softcap");

        // Continuity: transition should be smooth
        double ratio = above / atSoftcap;
        assertTrue(ratio < 1.01, "Transition should be smooth at softcap, ratio: " + ratio);
    }

    @Test
    void getCumulativeXpForLevelMatchesSumOfIndividualLevels() {
        double sum = 0;
        for (int level = 1; level <= 20; level++) {
            sum += SummitConstants.getXpForLevel(level);
            double cumulative = SummitConstants.getCumulativeXpForLevel(level);
            assertEquals(sum, cumulative, 1e-6,
                "Cumulative XP mismatch at level " + level);
        }
    }

    @Test
    void calculateLevelFromXpRoundTripsWithGetCumulativeXp() {
        for (int level : new int[]{1, 10, 50, 100, 500}) {
            double xp = SummitConstants.getCumulativeXpForLevel(level);
            int calculated = SummitConstants.calculateLevelFromXp(xp);
            assertEquals(level, calculated, "Round-trip failed for level " + level);
        }
    }

    @Test
    void getXpProgressReturnsCurrentAndNextLevelXp() {
        // At exactly a level boundary
        double cumXp5 = SummitConstants.getCumulativeXpForLevel(5);
        double[] progress = SummitConstants.getXpProgress(cumXp5);
        assertEquals(0.0, progress[0], 1e-6, "At boundary, currentXpInLevel should be 0");
        assertEquals(SummitConstants.getXpForLevel(6), progress[1], 1e-6);

        // Mid-level
        double midXp = cumXp5 + SummitConstants.getXpForLevel(6) / 2;
        double[] midProgress = SummitConstants.getXpProgress(midXp);
        assertTrue(midProgress[0] > 0, "Mid-level: currentXpInLevel should be > 0");
        assertTrue(midProgress[0] < midProgress[1], "Mid-level: current < next level XP");
    }
}
