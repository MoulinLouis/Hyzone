package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.parkour.ParkourConstants;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.ui.MapRecommendationPage;
import io.hyvexa.parkour.ui.PracticeModeHintPage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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
        SessionStats stats = sessionStats.get(playerId);
        if (stats == null) {
            return;
        }
        SessionStats.MapSessionData mapData = stats.getStats(run.mapId);

        if (mapData.failureCount >= ParkourConstants.RECOMMENDATION_FAILURE_THRESHOLD
                && !mapData.recommendationShown && run.lastCheckpointIndex < 0 && !run.practiceEnabled) {
            mapData.recommendationShown = true;

            World world = store.getExternalData().getWorld();
            CompletableFuture.runAsync(() -> {
                if (ref == null || !ref.isValid()) {
                    return;
                }
                if (Universe.get().getPlayer(playerId) == null) {
                    return;
                }
                Player p = store.getComponent(ref, Player.getComponentType());
                if (p == null) {
                    return;
                }
                String category = map.getCategory() != null ? map.getCategory() : "Easy";
                p.getPageManager().openCustomPage(ref, store,
                        new MapRecommendationPage(playerRef, mapStore, progressStore, runTracker, run.mapId, category));
            }, world).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> null);
        }

        if (mapData.failureCount == ParkourConstants.PRACTICE_HINT_FAILURE_THRESHOLD
                && !mapData.practiceHintShown && !run.practiceEnabled) {
            mapData.practiceHintShown = true;

            World world = store.getExternalData().getWorld();
            CompletableFuture.runAsync(() -> {
                if (ref == null || !ref.isValid()) {
                    return;
                }
                if (Universe.get().getPlayer(playerId) == null) {
                    return;
                }
                Player p = store.getComponent(ref, Player.getComponentType());
                if (p == null) {
                    return;
                }
                p.getPageManager().openCustomPage(ref, store,
                        new PracticeModeHintPage(playerRef, runTracker));
            }, world).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> null);
        }
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
