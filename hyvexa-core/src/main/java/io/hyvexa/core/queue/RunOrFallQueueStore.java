package io.hyvexa.core.queue;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RunOrFallQueueStore {

    private static final RunOrFallQueueStore INSTANCE = new RunOrFallQueueStore();

    public record QueueEntry(UUID playerId, long queuedAtMs, String sourceWorldName) {}

    public interface LobbyInfoProvider {
        int getLobbySize();
        int getQueueSize();
        String getGameState();
    }

    private final ConcurrentHashMap<UUID, QueueEntry> queue = new ConcurrentHashMap<>();
    private volatile Runnable onQueueChanged;
    private volatile LobbyInfoProvider lobbyInfoProvider;

    private RunOrFallQueueStore() {}

    public static RunOrFallQueueStore getInstance() {
        return INSTANCE;
    }

    public boolean enqueue(UUID playerId, String sourceWorldName) {
        if (playerId == null) return false;
        QueueEntry entry = new QueueEntry(playerId, System.currentTimeMillis(), sourceWorldName);
        if (queue.putIfAbsent(playerId, entry) != null) return false;
        fireQueueChanged();
        return true;
    }

    public boolean dequeue(UUID playerId) {
        if (playerId == null) return false;
        if (queue.remove(playerId) == null) return false;
        fireQueueChanged();
        return true;
    }

    public boolean isQueued(UUID playerId) {
        return playerId != null && queue.containsKey(playerId);
    }

    public Set<UUID> getQueuedPlayerIds() {
        return Set.copyOf(queue.keySet());
    }

    public int getQueueSize() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }

    public void setOnQueueChanged(Runnable callback) {
        this.onQueueChanged = callback;
    }

    public void setLobbyInfoProvider(LobbyInfoProvider provider) {
        this.lobbyInfoProvider = provider;
    }

    public LobbyInfoProvider getLobbyInfoProvider() {
        return lobbyInfoProvider;
    }

    private void fireQueueChanged() {
        Runnable callback = onQueueChanged;
        if (callback != null) {
            callback.run();
        }
    }
}
