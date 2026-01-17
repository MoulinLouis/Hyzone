package io.hyvexa.common.util;

import java.util.Locale;

public final class FormatUtils {
    private FormatUtils() {
    }

    public static String formatDuration(long durationMs) {
        double seconds = durationMs / 1000.0;
        return String.format(Locale.ROOT, "%.2fs", seconds);
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
            case "Bronze" -> "#cd7f32";
            case "Silver" -> "#c0c0c0";
            case "Gold" -> "#ffd700";
            case "Platinum" -> "#9fd3c7";
            case "Diamond" -> "#4dd7ff";
            case "Master" -> "#ff7fb0";
            default -> "#b2c0c7";
        };
    }
}
