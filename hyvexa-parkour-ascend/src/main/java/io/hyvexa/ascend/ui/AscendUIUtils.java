package io.hyvexa.ascend.ui;

public final class AscendUIUtils {
    public static final String COLOR_RANK_1 = "#ffd700";
    public static final String COLOR_RANK_2 = "#c0c0c0";
    public static final String COLOR_RANK_3 = "#cd7f32";
    public static final String COLOR_RANK_DEFAULT = "#9fb0ba";

    public static String getRankAccentColor(int rank) {
        return switch (rank) {
            case 1 -> COLOR_RANK_1;
            case 2 -> COLOR_RANK_2;
            case 3 -> COLOR_RANK_3;
            default -> COLOR_RANK_DEFAULT;
        };
    }

    private AscendUIUtils() {}
}
