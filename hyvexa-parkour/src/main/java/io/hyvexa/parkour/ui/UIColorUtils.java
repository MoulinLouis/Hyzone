package io.hyvexa.parkour.ui;

/**
 * Utility class for consistent UI accent colors across parkour interfaces.
 */
public final class UIColorUtils {

    private UIColorUtils() {
    }

    // Category difficulty accent colors
    public static final String COLOR_EASY = "#10b981";      // Green
    public static final String COLOR_MEDIUM = "#3b82f6";    // Blue
    public static final String COLOR_HARD = "#f59e0b";      // Orange
    public static final String COLOR_INSANE = "#ef4444";    // Red
    public static final String COLOR_DEFAULT = "#9fb0ba";   // Gray

    // Leaderboard rank accent colors
    public static final String COLOR_RANK_1 = "#ffd700";    // Gold
    public static final String COLOR_RANK_2 = "#c0c0c0";    // Silver
    public static final String COLOR_RANK_3 = "#cd7f32";    // Bronze
    public static final String COLOR_RANK_DEFAULT = "#9fb0ba"; // Gray

    // Special accent colors
    public static final String COLOR_GLOBAL = "#8b5cf6";    // Purple (for global leaderboard)

    /**
     * Returns the accent color for a category based on its difficulty name.
     *
     * @param category the category name (e.g., "Easy", "Medium", "Hard", "Insane")
     * @return the hex color string with # prefix
     */
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

    /**
     * Returns the accent color for a leaderboard rank.
     *
     * @param rank the 1-based rank position
     * @return the hex color string with # prefix
     */
    public static String getRankAccentColor(int rank) {
        return switch (rank) {
            case 1 -> COLOR_RANK_1;
            case 2 -> COLOR_RANK_2;
            case 3 -> COLOR_RANK_3;
            default -> COLOR_RANK_DEFAULT;
        };
    }
}
