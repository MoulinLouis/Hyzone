package io.hyvexa.common.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import java.util.Map;

public final class AccentOverlayUtils {

    private AccentOverlayUtils() {}

    // NOTE: Map keys are LOWERCASE - normalize input with toLowerCase() before lookup
    private static final Map<String, String> COLOR_TO_OVERLAY = Map.ofEntries(
        // Category colors
        Map.entry("#10b981", "AccentGreen"),
        Map.entry("#3b82f6", "AccentBlue"),
        Map.entry("#f59e0b", "AccentOrange"),
        Map.entry("#ef4444", "AccentRed"),
        Map.entry("#8b5cf6", "AccentViolet"),
        Map.entry("#7c3aed", "AccentViolet"),
        Map.entry("#9fb0ba", "AccentDefault"),
        // Challenge/extended colors
        Map.entry("#ec4899", "AccentPink"),
        Map.entry("#06b6d4", "AccentCyan"),
        // Tab/extra colors
        Map.entry("#eab308", "AccentYellow"),
        // State colors
        Map.entry("#4b5563", "AccentLocked"),
        Map.entry("#aaaaaa", "AccentGray"),
        Map.entry("#a855f7", "AccentPurple"),
        // Rank colors
        Map.entry("#ffd700", "AccentGold"),
        Map.entry("#c0c0c0", "AccentSilver"),
        Map.entry("#cd7f32", "AccentBronze"),
        // Wardrobe tab colors
        Map.entry("#e879f9", "AccentFuchsia"),
        Map.entry("#6366f1", "AccentIndigo"),
        // Cosmetic colors
        Map.entry("#22c55e", "AccentLime"),
        Map.entry("#888c8d", "AccentStone"),
        Map.entry("#f472b6", "AccentRose")
    );

    private static final String[] ALL_ACCENT_IDS = {
        "AccentGreen", "AccentBlue", "AccentOrange", "AccentRed",
        "AccentViolet", "AccentDefault", "AccentPink", "AccentCyan",
        "AccentYellow", "AccentLocked", "AccentGray", "AccentPurple",
        "AccentGold", "AccentSilver", "AccentBronze",
        "AccentFuchsia", "AccentIndigo",
        "AccentLime", "AccentStone", "AccentRose"
    };

    /**
     * Toggle visibility of pre-defined color overlays within a parent element.
     * Only the overlay matching hexColor is made visible; all others are hidden.
     */
    public static void applyAccent(UICommandBuilder cmd, String selector,
                                    String hexColor, String[] overlayIds) {
        String normalized = hexColor != null ? hexColor.toLowerCase() : "";
        String target = COLOR_TO_OVERLAY.getOrDefault(normalized, "AccentDefault");
        for (String id : overlayIds) {
            cmd.set(selector + " #" + id + ".Visible", id.equals(target));
        }
    }

    /** Convenience: apply using the full standard palette. */
    public static void applyAccent(UICommandBuilder cmd, String selector, String hexColor) {
        applyAccent(cmd, selector, hexColor, ALL_ACCENT_IDS);
    }

    // Pre-built subsets for templates that only use a few colors
    public static final String[] CATEGORY_ACCENTS = {
        "AccentGreen", "AccentBlue", "AccentOrange", "AccentRed",
        "AccentViolet", "AccentDefault"
    };

    public static final String[] RANK_ACCENTS = {
        "AccentGold", "AccentSilver", "AccentBronze", "AccentDefault"
    };

    public static final String[] RARITY_ACCENTS = {
        "AccentGray", "AccentGreen", "AccentBlue", "AccentPurple", "AccentOrange"
    };

    public static final String[] BINARY_ACCENTS = {
        "AccentGreen", "AccentLocked"
    };

    public static final String[] STATUS_DOT = {
        "AccentLime", "AccentRed"
    };

    public static final String[] TAB_ACCENTS = {
        "AccentRed", "AccentOrange", "AccentYellow", "AccentGreen",
        "AccentBlue", "AccentViolet", "AccentPink", "AccentCyan"
    };

    /** Map leaderboard tabs: only 5 colors (no violet/pink/cyan). */
    public static final String[] MAP_TAB_ACCENTS = {
        "AccentRed", "AccentOrange", "AccentYellow", "AccentGreen", "AccentBlue"
    };

    /** Challenge leaderboard tabs: 7 colors (no yellow). */
    public static final String[] CHALLENGE_TAB_ACCENTS = {
        "AccentRed", "AccentBlue", "AccentGreen", "AccentOrange",
        "AccentViolet", "AccentPink", "AccentCyan"
    };

    public static final String[] SHOP_TAB_ACCENTS = {
        "AccentFuchsia", "AccentIndigo", "AccentOrange", "AccentDefault"
    };

    public static final String[] COSMETIC_ACCENTS = {
        "AccentGold", "AccentPurple", "AccentBlue", "AccentLime",
        "AccentCyan", "AccentStone", "AccentRose", "AccentDefault"
    };
}
