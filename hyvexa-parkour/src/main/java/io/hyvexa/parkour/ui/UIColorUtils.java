package io.hyvexa.parkour.ui;

public final class UIColorUtils {

    private UIColorUtils() {
    }

    public static final String COLOR_EASY = "#10b981";
    public static final String COLOR_MEDIUM = "#3b82f6";
    public static final String COLOR_HARD = "#f59e0b";
    public static final String COLOR_INSANE = "#ef4444";
    public static final String COLOR_DEFAULT = "#9fb0ba";
    public static final String COLOR_RANK_1 = "#ffd700";
    public static final String COLOR_RANK_2 = "#c0c0c0";
    public static final String COLOR_RANK_3 = "#cd7f32";
    public static final String COLOR_RANK_DEFAULT = "#9fb0ba";
    public static final String COLOR_GLOBAL = "#8b5cf6";

    public static String getCategoryAccentColor(String category) {
        if (category == null) {
            return COLOR_DEFAULT;
        }
        String normalized = category.trim().toLowerCase();
        return switch (normalized) {
            case "easy", "beginner" -> COLOR_EASY;
            case "medium", "normal", "intermediate" -> COLOR_MEDIUM;
            case "hard", "difficult" -> COLOR_HARD;
            case "insane", "extreme", "expert" -> COLOR_INSANE;
            default -> COLOR_DEFAULT;
        };
    }

    public static String getRankAccentColor(int rank) {
        return switch (rank) {
            case 1 -> COLOR_RANK_1;
            case 2 -> COLOR_RANK_2;
            case 3 -> COLOR_RANK_3;
            default -> COLOR_RANK_DEFAULT;
        };
    }
}
