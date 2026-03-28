package io.hyvexa.parkour.data;

/** Result of purging all player progress for a specific map. */
public class MapPurgeResult {
    public final int playersUpdated;
    public final long totalXpRemoved;

    public MapPurgeResult(int playersUpdated, long totalXpRemoved) {
        this.playersUpdated = playersUpdated;
        this.totalXpRemoved = totalXpRemoved;
    }
}
