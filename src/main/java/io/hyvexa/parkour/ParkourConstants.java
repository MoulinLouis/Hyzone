package io.hyvexa.parkour;

import com.hypixel.hytale.math.vector.Vector3d;

public final class ParkourConstants {

    public static final String DEFAULT_CATEGORY = "Beginner";

    public static final String ITEM_RESET = "Parkour_Reset_Block";
    public static final String ITEM_RESTART_CHECKPOINT = "Parkour_Restart_Checkpoint_Block";
    public static final String ITEM_LEAVE = "Parkour_Leave_Block";
    public static final String ITEM_MENU = "Parkour_Menu_Block";
    public static final String ITEM_LEADERBOARD = "Parkour_Leaderboard_Block";
    public static final String ITEM_STATS = "Parkour_Stats_Block";

    public static final String TITLE_NOVICE = "Parkour Novice";
    public static final String TITLE_PRO = "Parkour Pro";
    public static final String TITLE_MASTER = "Parkour Master";

    public static final String[] RANK_NAMES = {
            "Bronze",
            "Silver",
            "Gold",
            "Platinum",
            "Diamond"
    };

    public static final String[] COMPLETION_RANK_NAMES = {
            "Bronze",
            "Silver",
            "Gold",
            "Platinum",
            "Diamond",
            "Master"
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
    public static final int RANK_TITLE_NOVICE_LEVEL = 2;
    public static final int RANK_TITLE_PRO_LEVEL = 4;
    public static final int RANK_TITLE_MASTER_LEVEL = 6;

    public static final long XP_BONUS_FIRST_COMPLETION = 50;
    public static final long XP_BONUS_PERSONAL_BEST = 25;

    public static final double DEFAULT_FALL_RESPAWN_SECONDS = 3.0;
    public static final Vector3d DEFAULT_SPAWN_POSITION = new Vector3d(-484.70, 306.00, 627.48);
    public static final double TOUCH_RADIUS = 1.5;

    private ParkourConstants() {
    }
}
