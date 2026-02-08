package io.hyvexa.common.util;

import com.hypixel.hytale.server.core.Message;
import io.hyvexa.common.math.BigNumber;

import java.util.Locale;

public final class FormatUtils {
    private FormatUtils() {
    }

    public static String formatDuration(long durationMs) {
        double seconds = durationMs / 1000.0;
        return String.format(Locale.ROOT, "%.2fs", seconds);
    }

    public static String formatDurationPrecise(long durationMs) {
        double seconds = durationMs / 1000.0;
        return String.format(Locale.ROOT, "%.3fs", seconds);
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

    public static String formatCoinsForHud(long coins) {
        long safeCoins = Math.max(0L, coins);
        if (safeCoins <= 1_000_000_000L) {
            return String.valueOf(safeCoins);
        }
        double value = safeCoins;
        int exponent = (int) Math.floor(Math.log10(value));
        double mantissa = value / Math.pow(10.0, exponent);
        double rounded = Math.round(mantissa * 100.0) / 100.0;
        if (rounded >= 10.0) {
            rounded /= 10.0;
            exponent += 1;
        }
        String mantissaText = String.format(Locale.ROOT, "%.2f", rounded);
        mantissaText = mantissaText.replaceAll("\\.?0+$", "");
        return mantissaText + "e" + exponent;
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

        // Suffix table: [minExponent, suffix]
        String[][] suffixes = {
            {"3", "K"}, {"6", "M"}, {"9", "B"}, {"12", "T"},
            {"15", "Qa"}, {"18", "Qi"}, {"21", "Sx"}, {"24", "Sp"},
            {"27", "Oc"}, {"30", "No"}, {"33", "Dc"}
        };

        for (int i = suffixes.length - 1; i >= 0; i--) {
            int suffixExp = Integer.parseInt(suffixes[i][0]);
            if (exp >= suffixExp && exp < suffixExp + 3) {
                // Calculate display value: mantissa * 10^(exp - suffixExp)
                int shift = exp - suffixExp;
                double display = mantissa * Math.pow(10.0, shift);
                return stripTrailingZeros(String.format(Locale.ROOT, "%.2f%s", display, suffixes[i][1]));
            }
        }

        // Beyond Decillion (exp >= 36): scientific notation
        return String.format(Locale.ROOT, "%.2fe%d", mantissa, exp);
    }

    @Deprecated
    public static String formatCoinsForHudDecimal(java.math.BigDecimal coins) {
        // Convert to double for formatting (sufficient precision for display)
        double safeCoins = Math.max(0.0, coins.doubleValue());
        // Below 1 thousand: show full number without decimals if it's a whole number
        if (safeCoins < 1e3) {
            if (safeCoins == Math.floor(safeCoins)) {
                return String.valueOf((long) safeCoins);
            }
            return String.format(Locale.ROOT, "%.2f", safeCoins);
        }
        // Thousand (10^3)
        if (safeCoins < 1e6) {
            return stripTrailingZeros(String.format(Locale.ROOT, "%.2fK", safeCoins / 1e3));
        }
        // Million (10^6)
        if (safeCoins < 1e9) {
            return stripTrailingZeros(String.format(Locale.ROOT, "%.2fM", safeCoins / 1e6));
        }
        // Billion (10^9)
        if (safeCoins < 1e12) {
            return stripTrailingZeros(String.format(Locale.ROOT, "%.2fB", safeCoins / 1e9));
        }
        // Trillion (10^12)
        if (safeCoins < 1e15) {
            return stripTrailingZeros(String.format(Locale.ROOT, "%.2fT", safeCoins / 1e12));
        }
        // Quadrillion (10^15)
        if (safeCoins < 1e18) {
            return stripTrailingZeros(String.format(Locale.ROOT, "%.2fQa", safeCoins / 1e15));
        }
        // Quintillion (10^18)
        if (safeCoins < 1e21) {
            return stripTrailingZeros(String.format(Locale.ROOT, "%.2fQi", safeCoins / 1e18));
        }
        // Sextillion (10^21)
        if (safeCoins < 1e24) {
            return stripTrailingZeros(String.format(Locale.ROOT, "%.2fSx", safeCoins / 1e21));
        }
        // Septillion (10^24)
        if (safeCoins < 1e27) {
            return stripTrailingZeros(String.format(Locale.ROOT, "%.2fSp", safeCoins / 1e24));
        }
        // Octillion (10^27)
        if (safeCoins < 1e30) {
            return stripTrailingZeros(String.format(Locale.ROOT, "%.2fOc", safeCoins / 1e27));
        }
        // Nonillion (10^30)
        if (safeCoins < 1e33) {
            return stripTrailingZeros(String.format(Locale.ROOT, "%.2fNo", safeCoins / 1e30));
        }
        // Decillion (10^33)
        if (safeCoins < 1e36) {
            return stripTrailingZeros(String.format(Locale.ROOT, "%.2fDc", safeCoins / 1e33));
        }
        // Beyond Decillion: scientific notation
        double value = safeCoins;
        int exponent = (int) Math.floor(Math.log10(value));
        double mantissa = value / Math.pow(10.0, exponent);
        double rounded = Math.round(mantissa * 100.0) / 100.0;
        if (rounded >= 10.0) {
            rounded /= 10.0;
            exponent += 1;
        }
        return String.format(Locale.ROOT, "%.2fe%d", rounded, exponent);
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
        if (!"VexaGod".equals(rank)) {
            return Message.raw(rank != null ? rank : "").color(getRankColor(rank));
        }
        String safeRank = rank != null ? rank : "";
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
