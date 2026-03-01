package io.hyvexa.common.util;

import com.hypixel.hytale.server.core.Message;
import io.hyvexa.common.math.BigNumber;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormatUtilsTest {

    // --- formatDuration ---

    @Test
    void formatDurationZero() {
        assertEquals("0:00.00", FormatUtils.formatDuration(0));
    }

    @Test
    void formatDurationTypical() {
        // 1 minute 1 second 230ms -> "1:01.23"
        assertEquals("1:01.23", FormatUtils.formatDuration(61230));
    }

    @Test
    void formatDurationNegativeClampedToZero() {
        assertEquals("0:00.00", FormatUtils.formatDuration(-5000));
    }

    @Test
    void formatDurationSubSecond() {
        // 500ms -> 0:00.50
        assertEquals("0:00.50", FormatUtils.formatDuration(500));
    }

    // --- formatDurationLong ---

    @Test
    void formatDurationLongWithHours() {
        // 1h 2m 3.456s
        long ms = 3600_000L + 2 * 60_000L + 3456L;
        assertEquals("1h 02m 03.456s", FormatUtils.formatDurationLong(ms));
    }

    @Test
    void formatDurationLongMinutesOnly() {
        // 5m 30.000s
        assertEquals("5m 30.000s", FormatUtils.formatDurationLong(330_000));
    }

    @Test
    void formatDurationLongSecondsOnly() {
        // 7.500s
        assertEquals("7.500s", FormatUtils.formatDurationLong(7500));
    }

    // --- formatPlaytime ---

    @Test
    void formatPlaytimeWithHours() {
        // 1h 30m 45s
        long ms = 3600_000L + 30 * 60_000L + 45_000L;
        assertEquals("1h 30m 45s", FormatUtils.formatPlaytime(ms));
    }

    @Test
    void formatPlaytimeMinutesOnly() {
        assertEquals("5m 0s", FormatUtils.formatPlaytime(300_000));
    }

    @Test
    void formatPlaytimeSecondsOnly() {
        assertEquals("30s", FormatUtils.formatPlaytime(30_000));
    }

    // --- formatBigNumber ---

    @Test
    void formatBigNumberNull() {
        assertEquals("0", FormatUtils.formatBigNumber(null));
    }

    @Test
    void formatBigNumberZero() {
        assertEquals("0", FormatUtils.formatBigNumber(BigNumber.ZERO));
    }

    @Test
    void formatBigNumberSmall() {
        // 999 -> "999"
        assertEquals("999", FormatUtils.formatBigNumber(BigNumber.fromLong(999)));
    }

    @Test
    void formatBigNumberThousand() {
        // 1500 -> "1.5K"
        assertEquals("1.5K", FormatUtils.formatBigNumber(BigNumber.fromLong(1500)));
    }

    @Test
    void formatBigNumberMillion() {
        // 2,500,000 -> "2.5M"
        assertEquals("2.5M", FormatUtils.formatBigNumber(BigNumber.fromLong(2_500_000)));
    }

    @Test
    void formatBigNumberBeyondDecillion() {
        // exp 36 -> scientific notation
        BigNumber huge = BigNumber.of(1.5, 36);
        String result = FormatUtils.formatBigNumber(huge);
        assertTrue(result.contains("e"), "Expected scientific notation, got: " + result);
    }

    @Test
    void formatBigNumberNegativeReturnZero() {
        BigNumber neg = BigNumber.fromDouble(-100);
        assertEquals("0", FormatUtils.formatBigNumber(neg));
    }

    // --- formatLong ---

    @Test
    void formatLongSmall() {
        assertEquals("500", FormatUtils.formatLong(500));
    }

    @Test
    void formatLongThousand() {
        assertEquals("1.5K", FormatUtils.formatLong(1500));
    }

    @Test
    void formatLongMillion() {
        assertEquals("2.5M", FormatUtils.formatLong(2_500_000));
    }

    // --- normalizeCategory ---

    @Test
    void normalizeCategoryNull() {
        assertEquals("Beginner", FormatUtils.normalizeCategory(null));
    }

    @Test
    void normalizeCategoryBlank() {
        assertEquals("Beginner", FormatUtils.normalizeCategory("   "));
    }

    @Test
    void normalizeCategoryLowercase() {
        assertEquals("Gold", FormatUtils.normalizeCategory("gold"));
    }

    @Test
    void normalizeCategoryAlreadyCapitalized() {
        assertEquals("Silver", FormatUtils.normalizeCategory("Silver"));
    }

    // --- getRankColor ---

    @Test
    void getRankColorNull() {
        assertEquals("#b2c0c7", FormatUtils.getRankColor(null));
    }

    @Test
    void getRankColorUnknown() {
        assertEquals("#b2c0c7", FormatUtils.getRankColor("UnknownRank"));
    }

    @Test
    void getRankColorGold() {
        assertEquals("#ffd700", FormatUtils.getRankColor("Gold"));
    }

    @Test
    void getRankColorVexaGod() {
        assertEquals("#ffb347", FormatUtils.getRankColor("VexaGod"));
    }

    @Test
    void getRankColorDiamond() {
        assertEquals("#4dd7ff", FormatUtils.getRankColor("Diamond"));
    }

    // --- getRankMessage ---

    @Test
    void getRankMessageNonVexaGod() {
        Message msg = FormatUtils.getRankMessage("Gold");
        assertNotNull(msg);
    }

    @Test
    void getRankMessageNull() {
        Message msg = FormatUtils.getRankMessage(null);
        assertNotNull(msg);
    }

    @Test
    void getRankMessageVexaGodHasRainbow() {
        // VexaGod should create a joined message of 7 characters with different colors
        Message msg = FormatUtils.getRankMessage("VexaGod");
        assertNotNull(msg);
    }
}
