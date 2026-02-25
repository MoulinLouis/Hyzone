package io.hyvexa.parkour;

public final class ParkourTimingConstants {

    public static final long HUD_UPDATE_INTERVAL_MS = 100L;
    public static final long PLAYTIME_TICK_INTERVAL_SECONDS = 60L;
    public static final long COLLISION_REMOVAL_INTERVAL_SECONDS = 2L;
    public static final long STALE_PLAYER_SWEEP_INTERVAL_SECONDS = 120L;
    public static final long TELEPORT_DEBUG_INTERVAL_SECONDS = 120L;
    public static final long DUEL_TICK_INTERVAL_MS = 100L;
    public static final long LEADERBOARD_HOLOGRAM_REFRESH_DELAY_SECONDS = 2L;
    public static final long HUD_READY_DELAY_MS = 250L;
    public static final long CHECKPOINT_SPLIT_HUD_DURATION_MS = 2500L;
    public static final int LEADERBOARD_HOLOGRAM_ENTRIES = 10;
    public static final int LEADERBOARD_NAME_MAX = 16;
    public static final int LEADERBOARD_POSITION_WIDTH = 4;
    public static final int LEADERBOARD_COUNT_WIDTH = 4;
    public static final int MAP_HOLOGRAM_TOP_LIMIT = 5;
    public static final int MAP_HOLOGRAM_NAME_MAX = 16;
    public static final int MAP_HOLOGRAM_POS_WIDTH = 3;
    public static final long MEDAL_NOTIF_DURATION_MS = 4000L;

    private ParkourTimingConstants() {
    }
}
