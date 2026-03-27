package io.hyvexa.ascend.mine.system;

import io.hyvexa.ascend.mine.system.BlockDamageTracker.HitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockDamageTrackerTest {

    private BlockDamageTracker tracker;
    private UUID playerA;
    private UUID playerB;

    @BeforeEach
    void setUp() {
        tracker = new BlockDamageTracker();
        playerA = UUID.randomUUID();
        playerB = UUID.randomUUID();
    }

    @Test
    void instantBreakWhenMaxHpIsOne() {
        HitResult result = tracker.recordHit(playerA, 0, 0, 0, "stone", 1);
        assertSame(HitResult.INSTANT_BREAK, result);
        assertTrue(result.shouldBreak());
    }

    @Test
    void instantBreakWhenMaxHpIsZero() {
        HitResult result = tracker.recordHit(playerA, 0, 0, 0, "stone", 0);
        assertSame(HitResult.INSTANT_BREAK, result);
    }

    @Test
    void damageAccumulatesAcrossMultipleHits() {
        for (int i = 0; i < 4; i++) {
            HitResult result = tracker.recordHit(playerA, 0, 0, 0, "stone", 5, 1.0);
            assertFalse(result.shouldBreak());
            assertEquals(5 - (i + 1), result.remainingHp(), 1e-9);
        }
        HitResult last = tracker.recordHit(playerA, 0, 0, 0, "stone", 5, 1.0);
        assertTrue(last.shouldBreak());
        assertEquals(0, last.remainingHp(), 1e-9);
    }

    @Test
    void damageMultiplierScalesDamagePerHit() {
        HitResult first = tracker.recordHit(playerA, 0, 0, 0, "stone", 10, 3.0);
        assertFalse(first.shouldBreak());
        assertEquals(7.0, first.remainingHp(), 1e-9);

        // 3 more hits: 7 - 3 = 4, 4 - 3 = 1, 1 - 3 = -2 -> breaks
        tracker.recordHit(playerA, 0, 0, 0, "stone", 10, 3.0); // 4
        tracker.recordHit(playerA, 0, 0, 0, "stone", 10, 3.0); // 1
        HitResult breaking = tracker.recordHit(playerA, 0, 0, 0, "stone", 10, 3.0);
        assertTrue(breaking.shouldBreak());
    }

    @Test
    void minimumDamageIsOneRegardlessOfMultiplier() {
        HitResult result = tracker.recordHit(playerA, 0, 0, 0, "stone", 10, 0.001);
        assertFalse(result.shouldBreak());
        assertEquals(9.0, result.remainingHp(), 1e-9);
    }

    @Test
    void blockTypeChangeResetsDamage() {
        tracker.recordHit(playerA, 0, 0, 0, "stone", 10, 1.0); // hp=9
        tracker.recordHit(playerA, 0, 0, 0, "stone", 10, 1.0); // hp=8

        // Different block type at same position
        HitResult result = tracker.recordHit(playerA, 0, 0, 0, "gold", 10, 1.0);
        assertFalse(result.shouldBreak());
        assertEquals(9.0, result.remainingHp(), 1e-9);
    }

    @Test
    void differentPlayersHaveIndependentDamageState() {
        // Player A hits 3 times
        for (int i = 0; i < 3; i++) {
            tracker.recordHit(playerA, 0, 0, 0, "stone", 5, 1.0);
        }
        // Player B hits 1 time
        HitResult bResult = tracker.recordHit(playerB, 0, 0, 0, "stone", 5, 1.0);

        // Player A: 5-3=2, Player B: 5-1=4
        HitResult aResult = tracker.recordHit(playerA, 0, 0, 0, "stone", 5, 1.0);
        assertEquals(1.0, aResult.remainingHp(), 1e-9);
        assertEquals(4.0, bResult.remainingHp(), 1e-9);
    }

    @Test
    void differentPositionsHaveIndependentDamageState() {
        for (int i = 0; i < 3; i++) {
            tracker.recordHit(playerA, 0, 0, 0, "stone", 5, 1.0);
        }
        HitResult pos2 = tracker.recordHit(playerA, 1, 1, 1, "stone", 5, 1.0);

        assertEquals(4.0, pos2.remainingHp(), 1e-9);
    }

    @Test
    void evictRemovesAllPlayerState() {
        tracker.recordHit(playerA, 0, 0, 0, "stone", 10, 1.0); // hp=9
        tracker.recordHit(playerA, 1, 1, 1, "stone", 10, 1.0); // hp=9

        tracker.evict(playerA);

        // Hitting again should start fresh
        HitResult result = tracker.recordHit(playerA, 0, 0, 0, "stone", 10, 1.0);
        assertEquals(9.0, result.remainingHp(), 1e-9);
    }

    @Test
    void healthFractionCalculatesCorrectly() {
        assertEquals(0.5f, new HitResult(5.0, 10, false).healthFraction(), 1e-6f);
        assertEquals(0.0f, new HitResult(0, 10, true).healthFraction(), 1e-6f);
        assertEquals(1.0f, new HitResult(10, 10, false).healthFraction(), 1e-6f);
    }
}
