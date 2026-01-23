package io.hyvexa.parkour;

import com.hypixel.hytale.math.vector.Vector3d;

public final class ParkourConstants {

    public static final String DEFAULT_CATEGORY = "Beginner";

    public static final String ITEM_RESET = "Ingredient_Ice_Essence";
    public static final String ITEM_RESTART_CHECKPOINT = "Ingredient_Lightning_Essence";
    public static final String ITEM_LEAVE = "Ingredient_Void_Essence";
    public static final String ITEM_MENU = "Ingredient_Bolt_Prismaloom";
    public static final String ITEM_LEADERBOARD = "WinterHoliday_Snowflake";
    public static final String ITEM_STATS = "Food_Candy_Cane";
    public static final String ITEM_ADMIN_REMOTE = "Recipe_Book_Magic_Air";
    public static final String ITEM_RUN_MITHRIL_SWORD = "Weapon_Sword_Mithril";
    public static final String ITEM_RUN_MITHRIL_DAGGERS = "Weapon_Daggers_Mithril";


    public static final String[] RANK_NAMES = {
            "Bronze",
            "Silver",
            "Gold",
            "Platinum",
            "Diamond"
    };

    public static final String[] COMPLETION_RANK_NAMES = {
            "Unranked",
            "Iron",
            "Bronze",
            "Silver",
            "Gold",
            "Platinum",
            "Emerald",
            "Diamond",
            "Master",
            "Grandmaster",
            "Challenger",
            "VexaGod"
    };
    // XP required to reach each rank, aligned by index with RANK_NAMES.
    public static final long[] RANK_XP_REQUIREMENTS = {
            0L,
            100L,
            200L,
            300L,
            400L,
            500L,
            600L
    };

    public static final long MAP_XP_EASY = 15L;
    public static final long MAP_XP_MEDIUM = 30L;
    public static final long MAP_XP_HARD = 60L;
    public static final long MAP_XP_INSANE = 100L;
    public static final int DEFAULT_MAP_ORDER = 1000;

    public static final double DEFAULT_FALL_RESPAWN_SECONDS = 3.0;
    public static final double FALL_FAILSAFE_VOID_Y = -10.0;
    public static final Vector3d DEFAULT_SPAWN_POSITION = new Vector3d(-484.70, 306.00, 627.48);
    public static final double TOUCH_RADIUS = 1.5;

    private ParkourConstants() {
    }
}
