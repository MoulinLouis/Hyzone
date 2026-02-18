package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Session stats and recommendation logic extracted from RunTracker. */
class RunSessionTracker {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final ConcurrentHashMap<UUID, SessionStats> sessionStats = new ConcurrentHashMap<>();

    RunSessionTracker(MapStore mapStore, ProgressStore progressStore) {
        this.mapStore = mapStore;
        this.progressStore = progressStore;
    }

    void recordFailure(UUID playerId, String mapId) {
        SessionStats stats = sessionStats.computeIfAbsent(playerId, k -> new SessionStats());
        stats.recordFailure(mapId);
    }

    void recordAttempt(UUID playerId, String mapId) {
        SessionStats stats = sessionStats.computeIfAbsent(playerId, k -> new SessionStats());
        stats.recordAttempt(mapId);
    }

    int getAttempts(UUID playerId, String mapId) {
        SessionStats stats = sessionStats.get(playerId);
        if (stats == null) {
            return 0;
        }
        return stats.getStats(mapId).attemptCount;
    }

    void checkRecommendations(UUID playerId, RunTracker.ActiveRun run, Map map,
                              Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
                              RunTracker runTracker) {
        // Practice/recommendation popups intentionally disabled.
    }

    void clearPlayer(UUID playerId) {
        sessionStats.remove(playerId);
    }

    static class SessionStats {
        private final ConcurrentHashMap<String, MapSessionData> mapStats = new ConcurrentHashMap<>();

        MapSessionData getStats(String mapId) {
            return mapStats.computeIfAbsent(mapId, k -> new MapSessionData());
        }

        void recordFailure(String mapId) {
            MapSessionData data = getStats(mapId);
            data.failureCount++;
            if (data.firstFailureTimestamp == 0) {
                data.firstFailureTimestamp = System.currentTimeMillis();
            }
        }

        void recordAttempt(String mapId) {
            MapSessionData data = getStats(mapId);
            data.attemptCount++;
        }

        static class MapSessionData {
            int failureCount = 0;
            int attemptCount = 0;
            long firstFailureTimestamp = 0;
            boolean recommendationShown = false;
            boolean practiceHintShown = false;
        }
    }
}
