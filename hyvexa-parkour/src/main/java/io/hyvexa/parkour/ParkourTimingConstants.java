package io.hyvexa.parkour;

/**
 * Timing and interval constants for the parkour plugin.
 * All values are documented with their purpose and rationale.
 */
public final class ParkourTimingConstants {

    private ParkourTimingConstants() {
    }

    // === Scheduled Task Intervals ===

    /**
     * Interval for HUD updates in milliseconds.
     * Set to 100ms for smooth timer display without excessive CPU usage.
     */
    public static final long HUD_UPDATE_INTERVAL_MS = 100L;

    /**
     * Interval for playtime accumulation in seconds.
     * Set to 60s as a balance between accuracy and database write frequency.
     */
    public static final long PLAYTIME_TICK_INTERVAL_SECONDS = 60L;

    /**
     * Interval for player collision removal re-application in seconds.
     * Set to 2s to ensure collision stays disabled even after respawns.
     */
    public static final long COLLISION_REMOVAL_INTERVAL_SECONDS = 2L;

    /**
     * Interval for stale player cleanup sweep in seconds.
     * Set to 120s (2 minutes) to avoid frequent HashMap iterations.
     */
    public static final long STALE_PLAYER_SWEEP_INTERVAL_SECONDS = 120L;

    /**
     * Interval for teleport debug logging in seconds.
     * Set to 120s to aggregate teleport stats without flooding logs.
     */
    public static final long TELEPORT_DEBUG_INTERVAL_SECONDS = 120L;

    /**
     * Interval for duel tick processing in milliseconds.
     * Set to 100ms for responsive duel state updates.
     */
    public static final long DUEL_TICK_INTERVAL_MS = 100L;

    /**
     * Delay before refreshing leaderboard hologram on startup in seconds.
     * Set to 2s to allow world and player data to initialize first.
     */
    public static final long LEADERBOARD_HOLOGRAM_REFRESH_DELAY_SECONDS = 2L;

    // === HUD Display Constants ===

    /**
     * Delay before HUD becomes ready after attachment in milliseconds.
     * Set to 250ms to allow UI elements to initialize before updates.
     */
    public static final long HUD_READY_DELAY_MS = 250L;

    /**
     * Duration to show checkpoint split overlay in milliseconds.
     * Set to 2500ms (2.5s) for readable split time display.
     */
    public static final long CHECKPOINT_SPLIT_HUD_DURATION_MS = 2500L;

    // === Leaderboard Display Constants ===

    /**
     * Number of entries to show in the global leaderboard hologram.
     */
    public static final int LEADERBOARD_HOLOGRAM_ENTRIES = 10;

    /**
     * Maximum character width for player names in leaderboard display.
     */
    public static final int LEADERBOARD_NAME_MAX = 16;

    /**
     * Character width for position column in leaderboard display.
     */
    public static final int LEADERBOARD_POSITION_WIDTH = 4;

    /**
     * Character width for count column in leaderboard display.
     */
    public static final int LEADERBOARD_COUNT_WIDTH = 4;

    /**
     * Number of entries to show in per-map leaderboard holograms.
     */
    public static final int MAP_HOLOGRAM_TOP_LIMIT = 5;

    /**
     * Maximum character width for names in map leaderboard display.
     */
    public static final int MAP_HOLOGRAM_NAME_MAX = 16;

    /**
     * Character width for position column in map leaderboard display.
     */
    public static final int MAP_HOLOGRAM_POS_WIDTH = 3;
}
