package io.hyvexa.manager;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.parkour.data.PlayerCountStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.util.ParkourUtils;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Tracks player session time and online player counts. */
public class PlaytimeManager {

    private final ConcurrentHashMap<UUID, Long> playtimeSessionStart = new ConcurrentHashMap<>();
    private final AtomicInteger onlinePlayerCount = new AtomicInteger(0);

    private final ProgressStore progressStore;
    private final PlayerCountStore playerCountStore;

    public PlaytimeManager(ProgressStore progressStore, PlayerCountStore playerCountStore) {
        this.progressStore = progressStore;
        this.playerCountStore = playerCountStore;
    }

    public void setOnlineCount(int count) {
        onlinePlayerCount.set(Math.max(0, count));
    }

    public void incrementOnlineCount() {
        onlinePlayerCount.incrementAndGet();
    }

    public void decrementOnlineCount() {
        onlinePlayerCount.updateAndGet(current -> Math.max(0, current - 1));
    }

    public void tickPlaytime() {
        if (progressStore == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) {
                continue;
            }
            UUID playerId = playerRef.getUuid();
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                playtimeSessionStart.remove(playerId);
                continue;
            }
            long[] deltaMs = new long[1];
            playtimeSessionStart.compute(playerId, (key, start) -> {
                if (start == null) {
                    return now;
                }
                long delta = Math.max(0L, now - start);
                if (delta > 0L) {
                    deltaMs[0] = delta;
                }
                return now;
            });
            if (deltaMs[0] > 0L) {
                progressStore.addPlaytime(playerId, playerRef.getUsername(), deltaMs[0]);
            }
        }
    }

    public void startPlaytimeSession(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        playtimeSessionStart.putIfAbsent(playerRef.getUuid(), System.currentTimeMillis());
    }

    public void finishPlaytimeSession(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        Long start = playtimeSessionStart.remove(playerId);
        if (start == null || progressStore == null) {
            return;
        }
        long deltaMs = Math.max(0L, System.currentTimeMillis() - start);
        if (deltaMs <= 0L) {
            return;
        }
        progressStore.addPlaytime(playerId, playerRef.getUsername(), deltaMs);
    }

    public void tickPlayerCounts() {
        if (playerCountStore == null) {
            return;
        }
        int actualCount = Universe.get().getPlayers().size();
        onlinePlayerCount.set(Math.max(0, actualCount));
        playerCountStore.recordSample(System.currentTimeMillis(), actualCount);
    }

    public void broadcastPresence(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        String name = playerRef.getUsername();
        if (name == null || name.isBlank()) {
            name = ParkourUtils.resolveName(playerRef.getUuid(), progressStore);
        }
        Message message = Message.join(
                Message.raw("[").color(SystemMessageUtils.SECONDARY),
                Message.raw("+").color(SystemMessageUtils.SUCCESS),
                Message.raw("] ").color(SystemMessageUtils.SECONDARY),
                Message.raw(name).color(SystemMessageUtils.PRIMARY_TEXT)
        );
        for (PlayerRef target : Universe.get().getPlayers()) {
            target.sendMessage(message);
        }
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        playtimeSessionStart.remove(playerId);
    }

    public void sweepStalePlayers(Set<UUID> onlinePlayers) {
        playtimeSessionStart.keySet().removeIf(id -> !onlinePlayers.contains(id));
    }
}
