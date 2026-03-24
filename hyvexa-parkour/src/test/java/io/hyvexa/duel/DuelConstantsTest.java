package io.hyvexa.duel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelConstantsTest {

    @Test
    void touchRadiusSquaredMatchesRadius() {
        assertEquals(
                DuelConstants.TOUCH_RADIUS * DuelConstants.TOUCH_RADIUS,
                DuelConstants.TOUCH_RADIUS_SQ
        );
    }

    @Test
    void countdownIsPositive() {
        assertTrue(DuelConstants.COUNTDOWN_SECONDS > 0);
    }

    @Test
    void postMatchDelayIsPositive() {
        assertTrue(DuelConstants.POST_MATCH_DELAY_MS > 0);
    }

    @Test
    void unlockRequirementIsReasonable() {
        assertTrue(DuelConstants.DUEL_UNLOCK_MIN_COMPLETED_MAPS > 0);
        assertTrue(DuelConstants.DUEL_UNLOCK_MIN_COMPLETED_MAPS <= 20);
    }
}
