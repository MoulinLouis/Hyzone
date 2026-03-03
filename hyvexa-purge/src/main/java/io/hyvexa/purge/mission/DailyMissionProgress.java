package io.hyvexa.purge.mission;

import java.time.LocalDate;

public class DailyMissionProgress {

    private LocalDate date;
    private int totalKills;
    private int bestWave;
    private int bestCombo;
    private boolean claimedWave;
    private boolean claimedKill;
    private boolean claimedCombo;

    public DailyMissionProgress(LocalDate date) {
        this.date = date;
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public int getTotalKills() { return totalKills; }
    public void setTotalKills(int totalKills) { this.totalKills = totalKills; }

    public int getBestWave() { return bestWave; }
    public void setBestWave(int bestWave) { this.bestWave = bestWave; }

    public int getBestCombo() { return bestCombo; }
    public void setBestCombo(int bestCombo) { this.bestCombo = bestCombo; }

    public boolean isClaimedWave() { return claimedWave; }
    public void setClaimedWave(boolean claimedWave) { this.claimedWave = claimedWave; }

    public boolean isClaimedKill() { return claimedKill; }
    public void setClaimedKill(boolean claimedKill) { this.claimedKill = claimedKill; }

    public boolean isClaimedCombo() { return claimedCombo; }
    public void setClaimedCombo(boolean claimedCombo) { this.claimedCombo = claimedCombo; }

    public boolean isClaimed(MissionDefinition.MissionCategory category) {
        return switch (category) {
            case WAVE -> claimedWave;
            case KILL -> claimedKill;
            case COMBO -> claimedCombo;
        };
    }

    public int getProgressValue(MissionDefinition.MissionCategory category) {
        return switch (category) {
            case WAVE -> bestWave;
            case KILL -> totalKills;
            case COMBO -> bestCombo;
        };
    }
}
