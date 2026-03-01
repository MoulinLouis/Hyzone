package io.hyvexa.common.skin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DailyShopRotationTest {

    // --- formatTimeRemaining ---

    @Test
    void formatTimeRemainingZero() {
        assertEquals("0h 0m", DailyShopRotation.formatTimeRemaining(0));
    }

    @Test
    void formatTimeRemainingOneHourOneMinute() {
        assertEquals("1h 1m", DailyShopRotation.formatTimeRemaining(3661));
    }

    @Test
    void formatTimeRemainingExactHours() {
        assertEquals("2h 0m", DailyShopRotation.formatTimeRemaining(7200));
    }

    @Test
    void formatTimeRemainingMinutesOnly() {
        assertEquals("0h 45m", DailyShopRotation.formatTimeRemaining(2700));
    }

    // --- getSecondsUntilReset ---

    @Test
    void getSecondsUntilResetInRange() {
        long seconds = DailyShopRotation.getSecondsUntilReset();
        assertTrue(seconds >= 0, "Should be non-negative, got: " + seconds);
        assertTrue(seconds < 86400, "Should be less than 24h, got: " + seconds);
    }
}
