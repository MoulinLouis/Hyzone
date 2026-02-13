package io.hyvexa.parkour;

import com.hypixel.hytale.math.vector.Vector3d;
import io.hyvexa.common.WorldConstants;

public final class ParkourConstants {

    public static final String DEFAULT_CATEGORY = "Beginner";

    public static final String ITEM_RESET = "Ingredient_Ice_Essence";
    public static final String ITEM_RESTART_CHECKPOINT = "Ingredient_Lightning_Essence";
    public static final String ITEM_LEAVE = "Ingredient_Void_Essence";
    public static final String ITEM_PRACTICE = "Ingredient_Water_Essence";
    public static final String ITEM_PRACTICE_CHECKPOINT = "Ingredient_Fire_Essence";
    public static final String ITEM_MENU = "Ingredient_Bolt_Prismaloom";
    public static final String ITEM_LEADERBOARD = "WinterHoliday_Snowflake";
    public static final String ITEM_STATS = "Food_Candy_Cane";
    public static final String ITEM_TOGGLE_FLY = "Ingredient_Earth_Essence";
    public static final String ITEM_ADMIN_REMOTE = "Recipe_Book_Magic_Air";
    public static final String ITEM_HUB_MENU = WorldConstants.ITEM_SERVER_SELECTOR;
    public static final String ITEM_RUN_MITHRIL_SWORD = "Weapon_Sword_Mithril";
    public static final String ITEM_RUN_MITHRIL_DAGGERS = "Weapon_Daggers_Mithril";
    public static final String ITEM_RUN_GLIDER = "Glider";

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

    // XP required per level threshold.
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
    public static final double TOUCH_VERTICAL_BONUS = 1.0;

    /** Number of failures before showing map recommendation. */
    public static final int RECOMMENDATION_FAILURE_THRESHOLD = 5;

    /** Number of failures before showing practice mode hint. */
    public static final int PRACTICE_HINT_FAILURE_THRESHOLD = 3;

    /** Throttle for fly zone rollback messages/teleports (ms). */
    public static final long FLY_ZONE_ROLLBACK_THROTTLE_MS = 500L;

    private ParkourConstants() {
    }
}
