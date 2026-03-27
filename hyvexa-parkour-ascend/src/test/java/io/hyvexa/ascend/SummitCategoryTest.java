package io.hyvexa.ascend;

import io.hyvexa.ascend.SummitConstants.SummitCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SummitCategoryTest {

    // --- Level zero returns base ---

    @Test
    void getBonusForLevelZeroReturnsBaseValue() {
        assertEquals(1.0, SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(0), 1e-9);
        assertEquals(1.0, SummitCategory.RUNNER_SPEED.getBonusForLevel(0), 1e-9);
        assertEquals(3.0, SummitCategory.EVOLUTION_POWER.getBonusForLevel(0), 1e-9);
    }

    // --- Linear zone (0-25) ---

    @Test
    void getBonusForLevelInLinearZoneScalesLinearly() {
        // MULTIPLIER_GAIN level 10: 1.0 + 10 * 0.30 = 4.0
        assertEquals(4.0, SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(10), 1e-9);
        // RUNNER_SPEED level 25: 1.0 + 25 * 0.15 = 4.75
        assertEquals(4.75, SummitCategory.RUNNER_SPEED.getBonusForLevel(25), 1e-9);
        // EVOLUTION_POWER level 25: 3.0 + 25 * 0.10 = 5.5
        assertEquals(5.5, SummitCategory.EVOLUTION_POWER.getBonusForLevel(25), 1e-9);
    }

    // --- Sqrt zone (25-500) ---

    @Test
    void getBonusForLevelBeyondSoftCapGrowsSublinearly() {
        double atSoftCap = SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(25);
        double at30 = SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(30);
        double linearExtrapolation = atSoftCap + 0.30 * 5; // what linear would give

        assertTrue(at30 > atSoftCap, "Should grow beyond soft cap");
        assertTrue(at30 < linearExtrapolation, "Growth should be sublinear (sqrt)");
    }

    @Test
    void getBonusForLevelInFourthRootZone() {
        double atDeepCap = SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(500);
        double at600 = SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(600);
        assertTrue(at600 > atDeepCap, "Should still grow in fourth-root zone");

        // Growth in fourth-root zone should be slower than sqrt zone
        double sqrtGrowth = SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(126)
            - SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(26);
        double fourthRootGrowth = SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(600)
            - SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(500);
        assertTrue(fourthRootGrowth < sqrtGrowth,
            "Fourth-root growth should be slower than sqrt growth for same delta");
    }

    @Test
    void getBonusForLevelInPostCapZone() {
        double at1000 = SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(1000);
        double at1500 = SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(1500);
        assertTrue(at1500 > at1000, "Post-cap zone should still grow");

        // Post-cap uses power 0.3 — very diminishing returns
        double at2000 = SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(2000);
        double growth1 = at1500 - at1000;
        double growth2 = at2000 - at1500;
        assertTrue(growth2 < growth1, "Growth should decelerate in post-cap zone");
    }

    // --- Monotonicity ---

    @Test
    void getBonusForLevelIsMonotonicallyIncreasingAcrossAllZones() {
        for (SummitCategory cat : SummitCategory.values()) {
            double prev = cat.getBonusForLevel(0);
            for (int level = 1; level <= 2000; level++) {
                double current = cat.getBonusForLevel(level);
                assertTrue(current >= prev,
                    cat.name() + " not monotonic at level " + level
                        + ": " + prev + " -> " + current);
                prev = current;
            }
        }
    }

    // --- Continuity at zone boundaries ---

    @Test
    void getBonusForLevelIsContinuousAtZoneBoundaries() {
        for (SummitCategory cat : SummitCategory.values()) {
            // Soft cap boundary (25 -> 26)
            double at25 = cat.getBonusForLevel(25);
            double at26 = cat.getBonusForLevel(26);
            assertTrue(Math.abs(at26 - at25) < 1.0,
                cat.name() + " discontinuity at soft cap: " + at25 + " -> " + at26);

            // Deep cap boundary (500 -> 501)
            double at500 = cat.getBonusForLevel(500);
            double at501 = cat.getBonusForLevel(501);
            assertTrue(Math.abs(at501 - at500) < 1.0,
                cat.name() + " discontinuity at deep cap: " + at500 + " -> " + at501);

            // XP softcap boundary (1000 -> 1001)
            double at1000 = cat.getBonusForLevel(1000);
            double at1001 = cat.getBonusForLevel(1001);
            assertTrue(Math.abs(at1001 - at1000) < 1.0,
                cat.name() + " discontinuity at XP softcap: " + at1000 + " -> " + at1001);
        }
    }

    // --- Negative level ---

    @Test
    void getBonusForNegativeLevelReturnsBonusAtZero() {
        assertEquals(SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(0),
            SummitCategory.MULTIPLIER_GAIN.getBonusForLevel(-5), 1e-9);
    }
}
