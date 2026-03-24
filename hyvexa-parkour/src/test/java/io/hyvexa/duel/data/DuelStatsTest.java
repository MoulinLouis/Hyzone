package io.hyvexa.duel.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuelStatsTest {

    @Test
    void getWinRateReturnsZeroWhenNoGamesPlayed() {
        DuelStats stats = new DuelStats(UUID.randomUUID(), "Player", 0, 0);

        assertEquals(0, stats.getWinRate());
    }

    @Test
    void getWinRateReturnsHundredWhenAllGamesAreWins() {
        DuelStats stats = new DuelStats(UUID.randomUUID(), "Player", 5, 0);

        assertEquals(100, stats.getWinRate());
    }

    @Test
    void getWinRateReturnsHalfForEvenRecord() {
        DuelStats stats = new DuelStats(UUID.randomUUID(), "Player", 3, 3);

        assertEquals(50, stats.getWinRate());
    }

    @Test
    void getWinRateRoundsToNearestInteger() {
        DuelStats stats = new DuelStats(UUID.randomUUID(), "Player", 1, 2);

        assertEquals(33, stats.getWinRate());
    }

    @Test
    void getWinRateReturnsZeroWithOnlyLosses() {
        DuelStats stats = new DuelStats(UUID.randomUUID(), "Player", 0, 10);

        assertEquals(0, stats.getWinRate());
    }

    @Test
    void incrementMethodsUpdateRecord() {
        DuelStats stats = new DuelStats(UUID.randomUUID(), "Player", 0, 0);

        stats.incrementWins();
        stats.incrementWins();
        stats.incrementLosses();

        assertEquals(2, stats.getWins());
        assertEquals(1, stats.getLosses());
        assertEquals(67, stats.getWinRate());
    }
}
