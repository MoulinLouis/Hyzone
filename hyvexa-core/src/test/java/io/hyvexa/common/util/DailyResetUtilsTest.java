package io.hyvexa.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyResetUtilsTest {

    @Test
    void formatTimeRemainingFormatsHourMinutePairs() {
        assertEquals("1h 1m", DailyResetUtils.formatTimeRemaining(3661));
    }

    @Test
    void formatTimeRemainingFormatsZero() {
        assertEquals("0h 0m", DailyResetUtils.formatTimeRemaining(0));
    }

    @Test
    void getSecondsUntilResetStaysWithinUtcDayRange() {
        long seconds = DailyResetUtils.getSecondsUntilReset();

        assertTrue(seconds >= 0, "Expected non-negative seconds until reset");
        assertTrue(seconds < 86_400, "Expected fewer than 24h until reset");
    }
}
