package io.hyvexa.purge.data;

public class PurgePlayerStats {

    private int bestWave;
    private int totalKills;
    private int totalSessions;

    public PurgePlayerStats(int bestWave, int totalKills, int totalSessions) {
        this.bestWave = bestWave;
        this.totalKills = totalKills;
        this.totalSessions = totalSessions;
    }

    public int getBestWave() {
        return bestWave;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public int getTotalSessions() {
        return totalSessions;
    }

    public void updateBestWave(int wave) {
        this.bestWave = Math.max(this.bestWave, wave);
    }

    public void incrementKills(int amount) {
        this.totalKills += amount;
    }

    public void incrementSessions() {
        this.totalSessions++;
    }
}
