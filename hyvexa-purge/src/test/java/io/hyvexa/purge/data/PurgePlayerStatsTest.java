package io.hyvexa.purge.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PurgePlayerStatsTest {

    @Test
    void updateBestWaveOnlyIncreases() {
        PurgePlayerStats stats = new PurgePlayerStats(0, 0, 0);
        stats.updateBestWave(5);
        assertEquals(5, stats.getBestWave());
        stats.updateBestWave(3); // lower, should not change
        assertEquals(5, stats.getBestWave());
        stats.updateBestWave(8); // higher, should update
        assertEquals(8, stats.getBestWave());
    }

    @Test
    void incrementKills() {
        PurgePlayerStats stats = new PurgePlayerStats(0, 0, 0);
        stats.incrementKills(5);
        assertEquals(5, stats.getTotalKills());
        stats.incrementKills(3);
        assertEquals(8, stats.getTotalKills());
    }

    @Test
    void incrementSessions() {
        PurgePlayerStats stats = new PurgePlayerStats(0, 0, 0);
        stats.incrementSessions();
        assertEquals(1, stats.getTotalSessions());
        stats.incrementSessions();
        assertEquals(2, stats.getTotalSessions());
    }

    @Test
    void constructorSetsInitialValues() {
        PurgePlayerStats stats = new PurgePlayerStats(10, 50, 3);
        assertEquals(10, stats.getBestWave());
        assertEquals(50, stats.getTotalKills());
        assertEquals(3, stats.getTotalSessions());
    }
}
