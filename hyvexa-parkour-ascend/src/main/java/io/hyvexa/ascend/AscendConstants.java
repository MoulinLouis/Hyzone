package io.hyvexa.ascend;

public final class AscendConstants {

    private AscendConstants() {
    }

    // Database
    public static final String TABLE_PREFIX = "ascend_";

    // Items
    public static final String ITEM_DEV_CINDERCLOTH = "Ingredient_Bolt_Cindercloth";
    public static final String ITEM_DEV_STORMSILK = "Ingredient_Bolt_Stormsilk";
    public static final String ITEM_DEV_COTTON = "Ingredient_Bolt_Cotton";

    // Economy
    public static final long DEFAULT_ROBOT_STORAGE = 100L;
    public static final double SPEED_UPGRADE_MULTIPLIER = 0.10; // +10% per level
    public static final double GAINS_UPGRADE_MULTIPLIER = 0.15; // +15% per level
    public static final int MAX_UPGRADE_LEVEL = 5;
    public static final int MULTIPLIER_SLOTS = 5;

    // Robot
    public static final String ROBOT_ENTITY_TYPE = "Kweebec_Sapling";
    public static final long ROBOT_TICK_INTERVAL_MS = 200L; // 5 ticks/second
    public static final long ROBOT_REFRESH_INTERVAL_MS = 1000L;
    public static final double ROBOT_BASE_SPEED = 5.0;
    public static final double ROBOT_JUMP_FORCE = 8.0;
    public static final double WAYPOINT_REACH_DISTANCE = 1.5;

    // Timing
    public static final long SAVE_DEBOUNCE_MS = 5000L;
}
