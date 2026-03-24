package io.hyvexa.parkour;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ParkourTimingConstantsTest {

    @Test
    void allIntervalsArePositive() {
        assertTrue(ParkourTimingConstants.HUD_UPDATE_INTERVAL_MS > 0);
        assertTrue(ParkourTimingConstants.PLAYTIME_TICK_INTERVAL_SECONDS > 0);
        assertTrue(ParkourTimingConstants.COLLISION_REMOVAL_INTERVAL_SECONDS > 0);
        assertTrue(ParkourTimingConstants.STALE_PLAYER_SWEEP_INTERVAL_SECONDS > 0);
        assertTrue(ParkourTimingConstants.TELEPORT_DEBUG_INTERVAL_SECONDS > 0);
        assertTrue(ParkourTimingConstants.DUEL_TICK_INTERVAL_MS > 0);
        assertTrue(ParkourTimingConstants.LEADERBOARD_HOLOGRAM_REFRESH_DELAY_SECONDS > 0);
        assertTrue(ParkourTimingConstants.HUD_READY_DELAY_MS > 0);
        assertTrue(ParkourTimingConstants.CHECKPOINT_SPLIT_HUD_DURATION_MS > 0);
        assertTrue(ParkourTimingConstants.MEDAL_NOTIF_DURATION_MS > 0);
    }

    @Test
    void displayLimitsArePositive() {
        assertTrue(ParkourTimingConstants.LEADERBOARD_HOLOGRAM_ENTRIES > 0);
        assertTrue(ParkourTimingConstants.LEADERBOARD_NAME_MAX > 0);
        assertTrue(ParkourTimingConstants.MAP_HOLOGRAM_TOP_LIMIT > 0);
    }
}
