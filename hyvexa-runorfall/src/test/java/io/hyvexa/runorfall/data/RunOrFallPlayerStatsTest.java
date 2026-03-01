package io.hyvexa.runorfall.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RunOrFallPlayerStatsTest {

    private RunOrFallPlayerStats stats;

    @BeforeEach
    void setUp() {
        stats = new RunOrFallPlayerStats(UUID.randomUUID(), "TestPlayer");
    }

    @Test
    void applyWinIncrementsWinsAndStreak() {
        stats.applyWin(1000L);
        assertEquals(1, stats.getWins());
        assertEquals(1, stats.getCurrentWinStreak());
        assertEquals(1, stats.getBestWinStreak());
    }

    @Test
    void applyWinUpdatesBestStreakWhenHigher() {
        stats.applyWin(100L);
        stats.applyWin(200L);
        assertEquals(2, stats.getBestWinStreak());
        assertEquals(2, stats.getCurrentWinStreak());
    }

    @Test
    void applyLossIncrementsLossesAndResetsStreak() {
        stats.applyWin(100L);
        stats.applyWin(200L);
        stats.applyLoss(300L);
        assertEquals(1, stats.getLosses());
        assertEquals(0, stats.getCurrentWinStreak());
        assertEquals(2, stats.getBestWinStreak()); // preserved
    }

    @Test
    void applyWinAndLossUpdateLongestSurvived() {
        stats.applyWin(500L);
        assertEquals(500L, stats.getLongestSurvivedMs());
        stats.applyLoss(300L); // lower, should not update
        assertEquals(500L, stats.getLongestSurvivedMs());
        stats.applyLoss(800L); // higher, should update
        assertEquals(800L, stats.getLongestSurvivedMs());
    }

    @Test
    void winStreakSequence() {
        stats.applyWin(100L);  // streak=1, best=1
        stats.applyWin(100L);  // streak=2, best=2
        stats.applyLoss(100L); // streak=0, best=2
        stats.applyWin(100L);  // streak=1, best=2
        assertEquals(2, stats.getBestWinStreak());
        assertEquals(1, stats.getCurrentWinStreak());
    }

    @Test
    void getWinRatePercentNoGames() {
        assertEquals(0.0, stats.getWinRatePercent());
    }

    @Test
    void getWinRatePercent() {
        stats.applyWin(100L);
        stats.applyWin(100L);
        stats.applyLoss(100L);
        // 2 wins, 1 loss = 66.67%
        assertEquals(200.0 / 3.0, stats.getWinRatePercent(), 0.01);
    }

    @Test
    void getTotalGames() {
        stats.applyWin(100L);
        stats.applyLoss(100L);
        stats.applyWin(100L);
        assertEquals(3, stats.getTotalGames());
    }

    @Test
    void addBlocksBrokenZeroIsNoOp() {
        stats.addBlocksBroken(0);
        assertEquals(0, stats.getTotalBlocksBroken());
    }

    @Test
    void addBlocksBrokenNegativeIsNoOp() {
        stats.addBlocksBroken(-5);
        assertEquals(0, stats.getTotalBlocksBroken());
    }

    @Test
    void addBlocksBrokenPositive() {
        stats.addBlocksBroken(10);
        stats.addBlocksBroken(5);
        assertEquals(15, stats.getTotalBlocksBroken());
    }

    @Test
    void addBlinksUsedZeroIsNoOp() {
        stats.addBlinksUsed(0);
        assertEquals(0, stats.getTotalBlinksUsed());
    }

    @Test
    void addBlinksUsedNegativeIsNoOp() {
        stats.addBlinksUsed(-3);
        assertEquals(0, stats.getTotalBlinksUsed());
    }

    @Test
    void addBlinksUsedPositive() {
        stats.addBlinksUsed(7);
        assertEquals(7, stats.getTotalBlinksUsed());
    }

    @Test
    void copyCreatesIndependentCopy() {
        stats.applyWin(500L);
        stats.addBlocksBroken(10);
        RunOrFallPlayerStats copy = stats.copy();

        assertEquals(stats.getWins(), copy.getWins());
        assertEquals(stats.getLongestSurvivedMs(), copy.getLongestSurvivedMs());
        assertEquals(stats.getTotalBlocksBroken(), copy.getTotalBlocksBroken());

        // Mutating copy doesn't affect original
        copy.applyWin(1000L);
        assertEquals(1, stats.getWins());
        assertEquals(2, copy.getWins());
    }

    @Test
    void settersClampNegativeValues() {
        stats.setWins(-5);
        assertEquals(0, stats.getWins());
        stats.setLosses(-3);
        assertEquals(0, stats.getLosses());
        stats.setCurrentWinStreak(-1);
        assertEquals(0, stats.getCurrentWinStreak());
        stats.setBestWinStreak(-2);
        assertEquals(0, stats.getBestWinStreak());
        stats.setLongestSurvivedMs(-100L);
        assertEquals(0L, stats.getLongestSurvivedMs());
        stats.setTotalBlocksBroken(-10L);
        assertEquals(0L, stats.getTotalBlocksBroken());
        stats.setTotalBlinksUsed(-7L);
        assertEquals(0L, stats.getTotalBlinksUsed());
    }
}
