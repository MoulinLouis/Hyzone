package io.hyvexa.common.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Shared utilities for daily UTC-midnight reset timing and formatting.
 */
public final class DailyResetUtils {

    private DailyResetUtils() {
    }

    /** Seconds remaining until the next UTC midnight. */
    public static long getSecondsUntilReset() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return java.time.Duration.between(now, midnight).getSeconds();
    }

    /** Format seconds as "Xh Ym". */
    public static String formatTimeRemaining(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }
}
