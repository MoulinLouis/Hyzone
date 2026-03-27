package io.hyvexa.ascend;

public final class AscendConstants {

    private AscendConstants() {
    }

    // Database
    public static final String TABLE_PREFIX = "ascend_";

    // Items - Menu
    public static final String ITEM_DEV_CINDERCLOTH = "Ingredient_Bolt_Cindercloth";
    public static final String ITEM_DEV_STORMSILK = "Ingredient_Bolt_Stormsilk";
    public static final String ITEM_DEV_COTTON = "Ingredient_Bolt_Cotton";
    public static final String ITEM_DEV_SHADOWEAVE = "Ingredient_Bolt_Shadoweave";
    public static final String ITEM_DEV_SILK = "Ingredient_Bolt_Silk";

    // Items - In-Run
    public static final String ITEM_RESET = "Ascend_Reset_Block";
    public static final String ITEM_LEAVE = "Ascend_Leave_Block";

    // Items - Mine
    public static final String ITEM_MINE_PICKAXE = "Tool_Pickaxe_Wood";
    public static final String ITEM_MINE_CHEST = "Mine_Chest";
    public static final String ITEM_MINE_LEADERBOARD = "Mine_Leaderboard";
    public static final String ITEM_MINE_EGG_CHEST = "Mine_Egg_Chest";
    public static final String[] EGG_CHEST_ITEMS = {
        "Mine_Egg_Chest", "Mine_Egg_Chest_Ancient", "Mine_Egg_Chest_Desert",
        "Mine_Egg_Chest_Jungle", "Mine_Egg_Chest_Kweebec", "Mine_Egg_Chest_Dark"
    };

    // Runner (internal tick system)
    public static final long RUNNER_TICK_INTERVAL_MS = 16L; // ~60 ticks/second for smooth movement
    public static final long RUNNER_REFRESH_INTERVAL_MS = 1000L;
    public static final long RUNNER_INVALID_RECOVERY_MS = 3000L; // Force respawn after entity invalid for 3s

    // Timing
    public static final long SAVE_DEBOUNCE_MS = 5000L;

    // Multiplier flash colors - lighter versions of each multiplier color
    // Single-cycle flash (200ms): flash -> restore on next HUD update
    // Format: [original, lighter] for each slot
    public static final String[][] MULTIPLIER_COLORS = {
        {"#7c3aed", "#b794f6"},  // Violet -> Light violet (Parkour 1)
        {"#ef4444", "#fca5a5"},  // Red -> Light red (Parkour 2)
        {"#f59e0b", "#fbbf24"},  // Orange -> Light orange (Parkour 3)
        {"#10b981", "#34d399"},  // Green -> Light green (Parkour 4)
        {"#3b82f6", "#93c5fd"},  // Blue -> Light blue (Parkour 5)
        {"#f59e0b", "#fcd34d"}   // Gold -> Light gold (Parkour 6 - Transcendence)
    };
}
