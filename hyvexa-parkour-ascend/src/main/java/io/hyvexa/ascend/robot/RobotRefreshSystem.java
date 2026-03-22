package io.hyvexa.ascend.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.ghost.GhostRecording;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.common.util.ModeGate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.hyvexa.ascend.AscendConstants;

class RobotRefreshSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long CACHE_REFRESH_INTERVAL_MS = 1000L;
    private static final long VIEWER_CONTEXT_REFRESH_INTERVAL_MS = 250L;
    private static final long FULL_REFRESH_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30L);
    private static final double RELEVANT_MAP_DISTANCE = 96.0d;
    private static final double FAST_MAP_DISTANCE = 48.0d;
    private static final long HEALTH_LOG_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5L);

    private final RobotManager manager;
    private volatile long lastRefreshMs;
    private volatile long lastFullRefreshMs;
    private volatile long lastViewerContextRefreshMs;
    private volatile long lastHealthLogMs = 0L;
    private volatile ViewerContext viewerContext = ViewerContext.empty();
    private volatile boolean viewerContextRefreshPending = false;

    RobotRefreshSystem(RobotManager manager) {
        this.manager = manager;
    }

    void refreshRobots(long now) {
        AscendPlayerStore playerStore = manager.getPlayerStore();
        AscendMapStore mapStore = manager.getMapStore();
        if (playerStore == null || mapStore == null) {
            return;
        }
        if (now - lastRefreshMs < AscendConstants.RUNNER_REFRESH_INTERVAL_MS) {
            return;
        }
        lastRefreshMs = now;

        Set<UUID> playersToRefresh = drainDirtyPlayers();
        if (now - lastFullRefreshMs >= FULL_REFRESH_INTERVAL_MS) {
            playersToRefresh.addAll(manager.getOnlinePlayers());
            lastFullRefreshMs = now;
        }
        if (playersToRefresh.isEmpty()) {
            logRobotHealth(now);
            return;
        }

        for (UUID playerId : playersToRefresh) {
            refreshPlayerRobots(playerId, now);
        }
        logRobotHealth(now);
    }

    private Set<UUID> drainDirtyPlayers() {
        Set<UUID> drained = new HashSet<>();
        Set<UUID> dirtyPlayers = manager.getDirtyPlayers();
        for (UUID playerId : new HashSet<>(dirtyPlayers)) {
            if (playerId != null && dirtyPlayers.remove(playerId)) {
                drained.add(playerId);
            }
        }
        return drained;
    }

    private void refreshPlayerRobots(UUID playerId, long now) {
        if (playerId == null) {
            return;
        }
        if (!manager.getOnlinePlayers().contains(playerId) || !manager.isPlayerInAscendWorld(playerId)) {
            manager.removeTrackedRobotsForPlayer(playerId);
            return;
        }

        AscendPlayerStore playerStore = manager.getPlayerStore();
        AscendMapStore mapStore = manager.getMapStore();
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            manager.removeTrackedRobotsForPlayer(playerId);
            return;
        }

        Map<String, RobotState> robots = manager.getRobots();
        GhostStore ghostStore = manager.getGhostStore();
        Set<String> activeKeys = new HashSet<>();
        for (Map.Entry<String, AscendPlayerProgress.MapProgress> mapEntry : progress.getMapProgress().entrySet()) {
            String mapId = mapEntry.getKey();
            AscendPlayerProgress.MapProgress mapProgress = mapEntry.getValue();
            if (mapId == null || mapProgress == null || !mapProgress.hasRobot()) {
                continue;
            }

            AscendMap map = mapStore.getMap(mapId);
            if (map == null) {
                continue;
            }

            String key = manager.robotKey(playerId, mapId);
            RobotState state = robots.computeIfAbsent(key, ignored -> new RobotState(playerId, mapId));
            state.setSpeedLevel(mapProgress.getRobotSpeedLevel());
            state.setStars(mapProgress.getRobotStars());
            if (state.getLastCompletionMs() <= 0L) {
                state.setLastCompletionMs(now);
            }
            refreshRobotCache(state, map, now);

            Ref<EntityStore> existingRef = state.getEntityRef();
            boolean refIsValid = existingRef != null && existingRef.isValid();
            boolean hasExistingEntity = state.getEntityUuid() != null;
            if (refIsValid) {
                state.clearInvalid();
            } else if (hasExistingEntity) {
                state.markInvalid(now);
                if (state.isInvalidForTooLong(now, AscendConstants.RUNNER_INVALID_RECOVERY_MS)) {
                    // Clear immediately to prevent repeated dispatch (re-arms after 3s if check fails)
                    state.clearInvalid();
                    World cachedWorld = state.getCachedWorld();
                    if (cachedWorld != null) {
                        cachedWorld.execute(() -> {
                            if (manager.isPlayerNearMapSpawn(playerId, map)) {
                                UUID oldUuid = state.getEntityUuid();
                                if (oldUuid != null) {
                                    manager.getOrphanCleanup().addOrphan(oldUuid);
                                }
                                state.setEntityRef(null);
                                state.setEntityUuid(null);
                            }
                        });
                    }
                }
            }

            activeKeys.add(key);
        }

        pruneStaleRobotsForPlayer(playerId, activeKeys);
    }

    private void pruneStaleRobotsForPlayer(UUID playerId, Set<String> activeKeys) {
        Map<String, RobotState> robots = manager.getRobots();
        String prefix = playerId.toString() + ":";
        for (String key : List.copyOf(robots.keySet())) {
            if (!key.startsWith(prefix) || activeKeys.contains(key)) {
                continue;
            }
            manager.removeRobotState(key);
        }
    }

    void refreshRobotCache(RobotState robot, AscendMap resolvedMap, long now) {
        if (robot == null) {
            return;
        }
        if (resolvedMap == null && now - robot.getCacheRefreshedAtMs() < CACHE_REFRESH_INTERVAL_MS) {
            return;
        }

        AscendMapStore mapStore = manager.getMapStore();
        GhostStore ghostStore = manager.getGhostStore();
        AscendMap map = resolvedMap != null ? resolvedMap : mapStore.getMap(robot.getMapId());
        robot.setCachedMap(map);
        if (map == null || map.getWorld() == null || map.getWorld().isEmpty()) {
            robot.setCachedWorld(null);
        } else {
            robot.setCachedWorld(Universe.get().getWorld(map.getWorld()));
        }
        robot.setCachedGhost(ghostStore.getRecording(robot.getOwnerId(), robot.getMapId()));
        robot.setCacheRefreshedAtMs(now);
    }

    AscendMap getCachedMap(RobotState robot, long now) {
        refreshRobotCache(robot, null, now);
        return robot.getCachedMap();
    }

    World getCachedWorld(RobotState robot, long now) {
        refreshRobotCache(robot, null, now);
        return robot.getCachedWorld();
    }

    GhostRecording getCachedGhost(RobotState robot, long now) {
        refreshRobotCache(robot, null, now);
        return robot.getCachedGhost();
    }

    ViewerContext getViewerContext(long now) {
        ViewerContext current = viewerContext;
        if (current != null && now - lastViewerContextRefreshMs < VIEWER_CONTEXT_REFRESH_INTERVAL_MS) {
            return current;
        }
        if (!viewerContextRefreshPending) {
            viewerContextRefreshPending = true;
            World ascendWorld = Universe.get().getWorld("Ascend");
            if (ascendWorld != null) {
                ascendWorld.execute(() -> {
                    try {
                        ViewerContext refreshed = buildViewerContext();
                        viewerContext = refreshed;
                        lastViewerContextRefreshMs = System.currentTimeMillis();
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Error refreshing viewer context");
                    } finally {
                        viewerContextRefreshPending = false;
                    }
                });
            } else {
                viewerContextRefreshPending = false;
            }
        }
        return current != null ? current : ViewerContext.empty();
    }

    private ViewerContext buildViewerContext() {
        AscendMapStore mapStore = manager.getMapStore();
        Set<UUID> onlinePlayers = manager.getOnlinePlayers();
        if (mapStore == null || onlinePlayers.isEmpty()) {
            return ViewerContext.empty();
        }
        AscendRunTracker runTracker = manager.getRunTracker();
        List<AscendMap> maps = mapStore.listMapsSorted();
        Set<String> relevantMapIds = new HashSet<>();
        Set<String> highFrequencyMapIds = new HashSet<>();
        double relevantDistanceSq = RELEVANT_MAP_DISTANCE * RELEVANT_MAP_DISTANCE;
        double fastDistanceSq = FAST_MAP_DISTANCE * FAST_MAP_DISTANCE;

        for (UUID playerId : onlinePlayers) {
            PlayerRef playerRef = manager.getPlayerRef(playerId);
            if (playerRef == null) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                continue;
            }
            World world = store.getExternalData().getWorld();
            if (!ModeGate.isAscendWorld(world)) {
                continue;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null || transform.getPosition() == null) {
                continue;
            }

            String activeMapId = runTracker != null ? runTracker.getActiveMapId(playerId) : null;
            if (activeMapId != null) {
                relevantMapIds.add(activeMapId);
                highFrequencyMapIds.add(activeMapId);
            }

            Vector3d position = transform.getPosition();
            String worldName = world != null ? world.getName() : null;
            for (AscendMap map : maps) {
                if (map == null || map.getId() == null) {
                    continue;
                }
                if (worldName == null || !worldName.equals(map.getWorld())) {
                    continue;
                }
                double dx = position.getX() - map.getStartX();
                double dy = position.getY() - map.getStartY();
                double dz = position.getZ() - map.getStartZ();
                double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                if (distSq <= relevantDistanceSq) {
                    relevantMapIds.add(map.getId());
                }
                if (distSq <= fastDistanceSq) {
                    highFrequencyMapIds.add(map.getId());
                }
            }
        }

        return new ViewerContext(relevantMapIds, highFrequencyMapIds);
    }

    void syncRobotNpcState(ViewerContext currentViewerContext, long now) {
        for (RobotState robot : manager.getRobots().values()) {
            syncRobotNpcState(robot, currentViewerContext, now);
        }
    }

    private void syncRobotNpcState(RobotState robot, ViewerContext currentViewerContext, long now) {
        AscendMap map = getCachedMap(robot, now);
        World world = getCachedWorld(robot, now);
        boolean shouldHaveNpc = manager.getSpawner().isNpcAvailable()
                && map != null
                && world != null
                && currentViewerContext.isRelevantMap(robot.getMapId());
        robot.setEntityDesired(shouldHaveNpc);

        Ref<EntityStore> entityRef = robot.getEntityRef();
        boolean refIsValid = entityRef != null && entityRef.isValid();
        boolean hasEntityUuid = robot.getEntityUuid() != null;

        if (shouldHaveNpc) {
            if (refIsValid) {
                robot.clearInvalid();
                return;
            }
            if (!hasEntityUuid && !robot.isSpawning()) {
                manager.getSpawner().spawnNpcForRobot(robot, map);
            }
            return;
        }

        if (!refIsValid && !hasEntityUuid) {
            return;
        }
        UUID entityUuid = robot.getEntityUuid();
        manager.clearTeleportWarning(robot);
        manager.getSpawner().despawnNpcForRobot(robot);
        manager.queueOrphanIfDespawnFailed(entityUuid, robot);
    }

    private void logRobotHealth(long now) {
        if (now - lastHealthLogMs < HEALTH_LOG_INTERVAL_MS) {
            return;
        }
        lastHealthLogMs = now;

        Map<String, RobotState> robots = manager.getRobots();
        Set<UUID> onlinePlayers = manager.getOnlinePlayers();
        Set<UUID> dirtyPlayers = manager.getDirtyPlayers();
        int ghostCount = 0;
        int visibleNpcCount = 0;
        for (RobotState robot : robots.values()) {
            if (getCachedGhost(robot, now) != null) {
                ghostCount++;
            }
            if (robot.getEntityUuid() != null) {
                visibleNpcCount++;
            }
        }
        LOGGER.atInfo().log("Runner health: robots=%d, visibleNpcs=%d, online=%d, dirty=%d, withGhost=%d, npc=%s",
                robots.size(), visibleNpcCount, onlinePlayers.size(), dirtyPlayers.size(),
                ghostCount, manager.getSpawner().isNpcAvailable() ? "available" : "NULL");
    }

    void resetViewerContext() {
        viewerContext = ViewerContext.empty();
    }

    record ViewerContext(Set<String> relevantMapIds, Set<String> highFrequencyMapIds) {
        static ViewerContext empty() {
            return new ViewerContext(Set.of(), Set.of());
        }

        boolean isRelevantMap(String mapId) {
            return mapId != null && relevantMapIds.contains(mapId);
        }

        boolean isHighFrequencyMap(String mapId) {
            return mapId != null && highFrequencyMapIds.contains(mapId);
        }
    }
}
