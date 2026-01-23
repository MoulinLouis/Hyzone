package io.hyvexa.common.util;

import com.hypixel.hytale.server.core.Message;

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
}
