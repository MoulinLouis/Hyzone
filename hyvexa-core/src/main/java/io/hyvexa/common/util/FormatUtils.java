package io.hyvexa.common.util;

import com.hypixel.hytale.server.core.Message;
import io.hyvexa.common.math.BigNumber;

import java.util.Locale;

public final class FormatUtils {
    /** Pre-parsed suffix table: {exponent, suffix} ordered ascending by exponent. */
    private static final int[] SUFFIX_EXPONENTS = {3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33};
    private static final String[] SUFFIX_LABELS = {"K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"};

    private record TimeComponents(long hours, long minutes, long seconds, long centis, long millis) {}

    private static TimeComponents extract(long durationMs) {
        long totalMs = Math.max(0L, durationMs);
        long totalSeconds = totalMs / 1000L;
        return new TimeComponents(
            totalSeconds / 3600L,
            (totalSeconds % 3600L) / 60L,
            totalSeconds % 60L,
            (totalMs % 1000L) / 10L,
            totalMs % 1000L
        );
    }

    private FormatUtils() {
    }

    public static String formatDuration(long durationMs) {
        TimeComponents t = extract(durationMs);
        return String.format(Locale.ROOT, "%d:%02d.%02d", t.minutes(), t.seconds(), t.centis());
    }

    public static String formatDurationPadded(long durationMs) {
        TimeComponents t = extract(durationMs);
        return String.format(Locale.ROOT, "%02d:%02d.%02d", t.minutes(), t.seconds(), t.centis());
    }

    public static String formatDurationLong(long durationMs) {
        TimeComponents t = extract(durationMs);

        if (t.hours() > 0L) {
            return String.format(Locale.ROOT, "%dh %02dm %02d.%03ds", t.hours(), t.minutes(), t.seconds(), t.millis());
        }
        if (t.minutes() > 0L) {
            return String.format(Locale.ROOT, "%dm %02d.%03ds", t.minutes(), t.seconds(), t.millis());
        }
        return String.format(Locale.ROOT, "%d.%03ds", t.seconds(), t.millis());
    }

    public static String formatPlaytime(long durationMs) {
        TimeComponents t = extract(durationMs);
        if (t.hours() > 0) {
            return String.format(Locale.ROOT, "%dh %dm %ds", t.hours(), t.minutes(), t.seconds());
        }
        if (t.minutes() > 0) {
            return String.format(Locale.ROOT, "%dm %ds", t.minutes(), t.seconds());
        }
        return String.format(Locale.ROOT, "%ds", t.seconds());
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        if (maxLength <= 3) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed.substring(0, maxLength - 3) + "...";
    }

    /**
     * Format a BigNumber for HUD display with suffix notation.
     * Uses mantissa/exponent directly — no double overflow for large values.
     */
    public static String formatBigNumber(BigNumber value) {
        if (value == null || value.isZero()) {
            return "0";
        }

        BigNumber safe = value.isNegative() ? BigNumber.ZERO : value;
        if (safe.isZero()) {
            return "0";
        }

        int exp = safe.getExponent();
        double mantissa = safe.getMantissa();

        // Below 1 thousand: show full number
        if (exp < 3) {
            double num = safe.toDouble();
            if (num == Math.floor(num) && num < 1e15) {
                return String.valueOf((long) num);
            }
            return String.format(Locale.ROOT, "%.2f", num);
        }

        for (int i = SUFFIX_EXPONENTS.length - 1; i >= 0; i--) {
            int suffixExp = SUFFIX_EXPONENTS[i];
            if (exp >= suffixExp && exp < suffixExp + 3) {
                int shift = exp - suffixExp;
                double display = mantissa * Math.pow(10.0, shift);
                return stripTrailingZeros(String.format(Locale.ROOT, "%.2f%s", display, SUFFIX_LABELS[i]));
            }
        }

        // Beyond Decillion (exp >= 36): scientific notation
        return String.format(Locale.ROOT, "%.2fe%d", mantissa, exp);
    }

    /**
     * Format a plain long value with suffix notation (K, M, B, ...).
     */
    /** Max exponent that fits in a long: 10^18 = 1,000,000,000,000,000,000. */
    private static final int MAX_LONG_EXPONENT = 18;

    public static String formatDouble(double value) {
        if (value < 1_000.0) {
            if (value == Math.floor(value) && value < 1e15) {
                return String.valueOf((long) value);
            }
            return stripTrailingZeros(String.format(Locale.ROOT, "%.2f", value));
        }
        return formatLong(Math.round(value));
    }

    public static String formatLong(long value) {
        if (value < 1_000L) {
            return String.valueOf(value);
        }
        for (int i = SUFFIX_EXPONENTS.length - 1; i >= 0; i--) {
            if (SUFFIX_EXPONENTS[i] > MAX_LONG_EXPONENT) {
                continue;
            }
            long threshold = (long) Math.pow(10, SUFFIX_EXPONENTS[i]);
            if (value >= threshold) {
                double display = value / (double) threshold;
                return stripTrailingZeros(String.format(Locale.ROOT, "%.2f%s", display, SUFFIX_LABELS[i]));
            }
        }
        return String.valueOf(value);
    }

    public static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "Beginner";
        }
        String trimmed = category.trim();
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
    }

    public static String getRankColor(String rank) {
        if (rank == null) {
            return "#b2c0c7";
        }
        return switch (rank) {
            case "Unranked" -> "#b2c0c7";
            case "Iron" -> "#8d8f94";
            case "Bronze" -> "#cd7f32";
            case "Silver" -> "#c0c0c0";
            case "Gold" -> "#ffd700";
            case "Platinum" -> "#9fd3c7";
            case "Emerald" -> "#39c97a";
            case "Diamond" -> "#4dd7ff";
            case "Master" -> "#9b5de5";
            case "Grandmaster" -> "#ff7a7a";
            case "Challenger" -> "#1f4aa8";
            case "VexaGod" -> "#ffb347";
            default -> "#b2c0c7";
        };
    }

    public static Message getRankMessage(String rank) {
        if (rank == null) {
            return Message.raw("");
        }
        if (!"VexaGod".equals(rank)) {
            return Message.raw(rank).color(getRankColor(rank));
        }
        String[] colors = {
                "#ff4d4d",
                "#ffa94d",
                "#ffe66d",
                "#4cd964",
                "#5ac8fa",
                "#5e5ce6",
                "#b76cff"
        };
        Message[] parts = new Message[rank.length()];
        for (int i = 0; i < rank.length(); i++) {
            String letter = String.valueOf(rank.charAt(i));
            parts[i] = Message.raw(letter).color(colors[i % colors.length]);
        }
        return Message.join(parts);
    }

    private static String stripTrailingZeros(String formatted) {
        return formatted.replaceAll("\\.?0+(\\D*)$", "$1");
    }
}
