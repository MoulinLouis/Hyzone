package io.hyvexa.duel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class DuelQueue {

    private final CopyOnWriteArrayList<UUID> waitingPlayers = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();

    public boolean join(@Nonnull UUID playerId) {
        synchronized (lock) {
            if (waitingPlayers.contains(playerId)) {
                return false;
            }
            waitingPlayers.add(playerId);
            return true;
        }
    }

    public boolean leave(@Nonnull UUID playerId) {
        synchronized (lock) {
            return waitingPlayers.remove(playerId);
        }
    }

    public boolean isQueued(@Nonnull UUID playerId) {
        synchronized (lock) {
            return waitingPlayers.contains(playerId);
        }
    }

    public int getPosition(@Nonnull UUID playerId) {
        synchronized (lock) {
            int index = waitingPlayers.indexOf(playerId);
            return index >= 0 ? index + 1 : -1;
        }
    }

    public int size() {
        synchronized (lock) {
            return waitingPlayers.size();
        }
    }

    @Nullable
    public UUID[] tryMatch() {
        synchronized (lock) {
            if (waitingPlayers.size() < 2) {
                return null;
            }
            UUID player1 = waitingPlayers.remove(0);
            UUID player2 = waitingPlayers.remove(0);
            return new UUID[]{player1, player2};
        }
    }

    public void addToFront(@Nonnull UUID playerId) {
        synchronized (lock) {
            waitingPlayers.remove(playerId);
            waitingPlayers.add(0, playerId);
        }
    }

    public boolean removePair(@Nonnull UUID player1, @Nonnull UUID player2) {
        synchronized (lock) {
            boolean removed1 = waitingPlayers.remove(player1);
            boolean removed2 = waitingPlayers.remove(player2);
            return removed1 && removed2;
        }
    }

    @Nonnull
    public List<UUID> getWaitingPlayers() {
        synchronized (lock) {
            return new ArrayList<>(waitingPlayers);
        }
    }

    public void clear() {
        synchronized (lock) {
            waitingPlayers.clear();
        }
    }
}
