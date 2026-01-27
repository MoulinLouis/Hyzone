package io.hyvexa.duel.data;

import java.util.UUID;

public class DuelStats {

    private final UUID playerId;
    private String playerName;
    private int wins;
    private int losses;

    public DuelStats(UUID playerId, String playerName, int wins, int losses) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.wins = wins;
        this.losses = losses;
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

    public int getLosses() {
        return losses;
    }

    public void incrementWins() {
        wins++;
    }

    public void incrementLosses() {
        losses++;
    }

    public int getWinRate() {
        int total = wins + losses;
        if (total == 0) {
            return 0;
        }
        return (int) Math.round((wins * 100.0) / total);
    }
}
