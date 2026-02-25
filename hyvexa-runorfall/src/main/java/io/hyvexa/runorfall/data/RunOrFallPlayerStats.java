package io.hyvexa.runorfall.data;

import java.util.UUID;

public class RunOrFallPlayerStats {
    private final UUID playerId;
    private String playerName;
    private int wins;
    private int losses;
    private int currentWinStreak;
    private int bestWinStreak;
    private long longestSurvivedMs;
    private long totalBlocksBroken;
    private long totalBlinksUsed;

    public RunOrFallPlayerStats(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = Math.max(0, wins);
    }

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = Math.max(0, losses);
    }

    public int getCurrentWinStreak() {
        return currentWinStreak;
    }

    public void setCurrentWinStreak(int currentWinStreak) {
        this.currentWinStreak = Math.max(0, currentWinStreak);
    }

    public int getBestWinStreak() {
        return bestWinStreak;
    }

    public void setBestWinStreak(int bestWinStreak) {
        this.bestWinStreak = Math.max(0, bestWinStreak);
    }

    public long getLongestSurvivedMs() {
        return longestSurvivedMs;
    }

    public void setLongestSurvivedMs(long longestSurvivedMs) {
        this.longestSurvivedMs = Math.max(0L, longestSurvivedMs);
    }

    public long getTotalBlocksBroken() {
        return totalBlocksBroken;
    }

    public void setTotalBlocksBroken(long totalBlocksBroken) {
        this.totalBlocksBroken = Math.max(0L, totalBlocksBroken);
    }

    public long getTotalBlinksUsed() {
        return totalBlinksUsed;
    }

    public void setTotalBlinksUsed(long totalBlinksUsed) {
        this.totalBlinksUsed = Math.max(0L, totalBlinksUsed);
    }

    public void applyWin(long survivedMs) {
        wins++;
        currentWinStreak++;
        if (currentWinStreak > bestWinStreak) {
            bestWinStreak = currentWinStreak;
        }
        if (survivedMs > longestSurvivedMs) {
            longestSurvivedMs = survivedMs;
        }
    }

    public void applyLoss(long survivedMs) {
        losses++;
        currentWinStreak = 0;
        if (survivedMs > longestSurvivedMs) {
            longestSurvivedMs = survivedMs;
        }
    }

    public void addBlocksBroken(int blocksBroken) {
        if (blocksBroken <= 0) {
            return;
        }
        totalBlocksBroken += blocksBroken;
    }

    public void addBlinksUsed(int blinksUsed) {
        if (blinksUsed <= 0) {
            return;
        }
        totalBlinksUsed += blinksUsed;
    }

    public int getTotalGames() {
        return wins + losses;
    }

    public double getWinRatePercent() {
        int total = getTotalGames();
        if (total <= 0) {
            return 0.0d;
        }
        return (wins * 100.0d) / total;
    }

    public RunOrFallPlayerStats copy() {
        RunOrFallPlayerStats copy = new RunOrFallPlayerStats(playerId, playerName);
        copy.wins = wins;
        copy.losses = losses;
        copy.currentWinStreak = currentWinStreak;
        copy.bestWinStreak = bestWinStreak;
        copy.longestSurvivedMs = longestSurvivedMs;
        copy.totalBlocksBroken = totalBlocksBroken;
        copy.totalBlinksUsed = totalBlinksUsed;
        return copy;
    }
}
