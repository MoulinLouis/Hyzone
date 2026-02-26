package io.hyvexa.common.util;

import com.hypixel.hytale.server.core.Message;
import io.hyvexa.common.math.BigNumber;

import java.util.Locale;

public final class FormatUtils {
    /** Pre-parsed suffix table: {exponent, suffix} ordered ascending by exponent. */
    private static final int[] SUFFIX_EXPONENTS = {3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33};
    private static final String[] SUFFIX_LABELS = {"K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"};

    private FormatUtils() {
    }

    public static String formatDuration(long durationMs) {
        long totalMs = Math.max(0L, durationMs);
        long totalSeconds = totalMs / 1000L;
        long centis = (totalMs % 1000L) / 10L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%d:%02d.%02d", minutes, seconds, centis);
    }

    public static String formatDurationLong(long durationMs) {
        long totalMs = Math.max(0L, durationMs);
        long totalSeconds = totalMs / 1000L;
        long ms = totalMs % 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0L) {
            return String.format(Locale.ROOT, "%dh %02dm %02d.%03ds", hours, minutes, seconds, ms);
        }
        if (minutes > 0L) {
            return String.format(Locale.ROOT, "%dm %02d.%03ds", minutes, seconds, ms);
        }
        return String.format(Locale.ROOT, "%d.%03ds", seconds, ms);
    }

    public static String formatPlaytime(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %dm %ds", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm %ds", minutes, seconds);
        }
        return String.format(Locale.ROOT, "%ds", seconds);
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
    public static String formatLong(long value) {
        if (value < 1_000L) {
            return String.valueOf(value);
        }
        for (int i = SUFFIX_EXPONENTS.length - 1; i >= 0; i--) {
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
        if (trimmed.isEmpty()) {
            return "Beginner";
        }
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
        if (!"VexaGod".equals(rank)) {
            return Message.raw(rank != null ? rank : "").color(getRankColor(rank));
        }
        String safeRank = rank;
        String[] colors = {
                "#ff4d4d",
                "#ffa94d",
                "#ffe66d",
                "#4cd964",
                "#5ac8fa",
                "#5e5ce6",
                "#b76cff"
        };
        Message[] parts = new Message[safeRank.length()];
        for (int i = 0; i < safeRank.length(); i++) {
            String letter = String.valueOf(safeRank.charAt(i));
            parts[i] = Message.raw(letter).color(colors[i % colors.length]);
        }
        return Message.join(parts);
    }

    private static String stripTrailingZeros(String formatted) {
        return formatted.replaceAll("\\.?0+(\\D*)$", "$1");
    }
}
