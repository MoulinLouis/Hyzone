package io.hyvexa.parkour.data;

/** Result of recording a map completion — XP changes, level changes, and persistence status. */
public class ProgressionResult {
    public final boolean firstCompletion;
    public final boolean newBest;
    public final boolean personalBest;
    public final long xpAwarded;
    public final int oldLevel;
    public final int newLevel;
    /**
     * True when completion persistence was queued/attempted.
     * This is not a confirmation of durable DB success.
     */
    public final boolean completionSaved;

    public ProgressionResult(boolean firstCompletion, boolean newBest, boolean personalBest, long xpAwarded,
                             int oldLevel, int newLevel, boolean completionSaved) {
        this.firstCompletion = firstCompletion;
        this.newBest = newBest;
        this.personalBest = personalBest;
        this.xpAwarded = xpAwarded;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.completionSaved = completionSaved;
    }
}
