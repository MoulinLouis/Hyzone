package io.hyvexa.duel;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class DuelMatch {

    private final String matchId;
    private final UUID player1;
    private final UUID player2;
    private final String mapId;
    private volatile DuelState state;
    private volatile long countdownStartMs;
    private volatile long raceStartMs;
    private volatile long player1FinishMs;
    private volatile long player2FinishMs;
    private final AtomicReference<UUID> winnerId = new AtomicReference<>(null);
    private volatile FinishReason finishReason;
    private volatile int lastCountdownSecond = -1;

    public DuelMatch(String matchId, UUID player1, UUID player2, String mapId) {
        this.matchId = matchId;
        this.player1 = player1;
        this.player2 = player2;
        this.mapId = mapId;
        this.state = DuelState.STARTING;
        this.countdownStartMs = System.currentTimeMillis();
    }

    public String getMatchId() {
        return matchId;
    }

    public UUID getPlayer1() {
        return player1;
    }

    public UUID getPlayer2() {
        return player2;
    }

    public String getMapId() {
        return mapId;
    }

    public DuelState getState() {
        return state;
    }

    public void setState(DuelState state) {
        this.state = state;
    }

    public long getCountdownStartMs() {
        return countdownStartMs;
    }

    public long getRaceStartMs() {
        return raceStartMs;
    }

    public void setRaceStartMs(long raceStartMs) {
        this.raceStartMs = raceStartMs;
    }

    public long getPlayer1FinishMs() {
        return player1FinishMs;
    }

    public void setPlayer1FinishMs(long player1FinishMs) {
        this.player1FinishMs = player1FinishMs;
    }

    public long getPlayer2FinishMs() {
        return player2FinishMs;
    }

    public void setPlayer2FinishMs(long player2FinishMs) {
        this.player2FinishMs = player2FinishMs;
    }

    public UUID getWinnerId() {
        return winnerId.get();
    }

    public boolean trySetWinner(UUID winner) {
        return winnerId.compareAndSet(null, winner);
    }

    public FinishReason getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(FinishReason finishReason) {
        this.finishReason = finishReason;
    }

    public int getLastCountdownSecond() {
        return lastCountdownSecond;
    }

    public void setLastCountdownSecond(int lastCountdownSecond) {
        this.lastCountdownSecond = lastCountdownSecond;
    }

    public UUID getOpponent(UUID playerId) {
        if (player1.equals(playerId)) {
            return player2;
        }
        if (player2.equals(playerId)) {
            return player1;
        }
        return null;
    }

    public boolean hasPlayer(UUID playerId) {
        return player1.equals(playerId) || player2.equals(playerId);
    }

    public long getFinishTimeFor(UUID playerId) {
        if (player1.equals(playerId)) {
            return player1FinishMs;
        }
        if (player2.equals(playerId)) {
            return player2FinishMs;
        }
        return 0L;
    }

    public void setFinishTimeFor(UUID playerId, long timeMs) {
        if (player1.equals(playerId)) {
            player1FinishMs = timeMs;
        } else if (player2.equals(playerId)) {
            player2FinishMs = timeMs;
        }
    }
}
