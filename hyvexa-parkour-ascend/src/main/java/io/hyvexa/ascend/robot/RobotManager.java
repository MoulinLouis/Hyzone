package io.hyvexa.ascend.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.command.AscendCommand;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerEventHandler;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.AutomationConfig;
import io.hyvexa.ascend.data.GameplayState;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.common.ghost.GhostRecording;
import io.hyvexa.common.ghost.GhostSample;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.OrphanedEntityCleanup;
import io.hyvexa.common.visibility.EntityVisibilityManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class RobotManager {

    /*
     * Threading model:
     * - robots, onlinePlayers, dirtyPlayers, teleportWarningByRobot:
     *   ConcurrentHashMap/newKeySet collections (thread-safe by construction).
     * - orphanCleanup: owns orphan UUID + pending-removal concurrency state.
     * - spawner: owns npcPlugin lifecycle; set once in start(), thereafter read-only.
     * - lastRefreshMs, lastAutoUpgradeMs, viewerContext:
     *   volatile, read/written from scheduled executor tick.
     * - tickTask: only accessed in start()/stop() (single-threaded lifecycle).
     *
     * Entity operations (spawn/despawn) must run on the World thread via world.execute().
     */

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RUNNER_UUIDS_FILE = "runner_uuids.txt";
    private static final long AUTO_UPGRADE_INTERVAL_MS = 50L;
    private static final long HEADLESS_MOVEMENT_INTERVAL_MS = 50L;
    private static final long VISIBLE_MOVEMENT_INTERVAL_MS = AscendConstants.RUNNER_TICK_INTERVAL_MS;
    private static final long CACHE_REFRESH_INTERVAL_MS = 1000L;
    private static final long VIEWER_CONTEXT_REFRESH_INTERVAL_MS = 250L;
    private static final long FULL_REFRESH_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30L);
    private static final double RELEVANT_MAP_DISTANCE = 96.0d;
    private static final double FAST_MAP_DISTANCE = 48.0d;
    private static final long TELEPORT_WARNING_THROTTLE_MS = 10_000L;
    private static final long TELEPORT_WARNING_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(30L);
    private static final long TELEPORT_WARNING_CLEANUP_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5L);
    private static final double CHUNK_LOAD_DISTANCE = 128.0; // Chunks typically load within render distance

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final GhostStore ghostStore;
    private final RunnerSpeedCalculator speedCalculator;
    private final AscendRunTracker runTracker;
    private final AscensionManager ascensionManager;
    private final ChallengeManager challengeManager;
    private final SummitManager summitManager;
    private final AchievementManager achievementManager;
    private final Function<UUID, PlayerRef> playerRefResolver;
    private volatile AscendPlayerEventHandler eventHandler;
    private final Map<String, RobotState> robots = new ConcurrentHashMap<>();
    private final Set<UUID> activeEntityUuids = ConcurrentHashMap.newKeySet();
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final OrphanedEntityCleanup orphanCleanup;
    private final Map<String, Long> teleportWarningByRobot = new ConcurrentHashMap<>();
    private final RobotSpawner spawner = new RobotSpawner(this);
    private ScheduledFuture<?> tickTask;
    private volatile long lastRefreshMs;
    private volatile long lastFullRefreshMs;
    private volatile long lastViewerContextRefreshMs;
    private volatile long lastAutoUpgradeMs = 0;
    private volatile long lastTeleportWarningCleanupMs = 0L;
    private volatile long lastHealthLogMs = 0L;
    private static final long HEALTH_LOG_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5L);
    private final Map<UUID, Long> lastAutoElevationMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAutoSummitMs = new ConcurrentHashMap<>();
    private volatile ViewerContext viewerContext = ViewerContext.empty();
    private volatile boolean viewerContextRefreshPending = false;

    public RobotManager(AscendMapStore mapStore, AscendPlayerStore playerStore, GhostStore ghostStore,
                        RunnerSpeedCalculator speedCalculator, AscendRunTracker runTracker,
                        AscensionManager ascensionManager, ChallengeManager challengeManager,
                        SummitManager summitManager, AchievementManager achievementManager,
                        Function<UUID, PlayerRef> playerRefResolver) {
        this.mapStore = mapStore;
        this.playerStore = playerStore;
        this.ghostStore = ghostStore;
        this.speedCalculator = speedCalculator;
        this.runTracker = runTracker;
        this.ascensionManager = ascensionManager;
        this.challengeManager = challengeManager;
        this.summitManager = summitManager;
        this.achievementManager = achievementManager;
        this.playerRefResolver = playerRefResolver;
        this.orphanCleanup = new OrphanedEntityCleanup(LOGGER,
                Path.of("mods", "Parkour", RUNNER_UUIDS_FILE));
    }

    public void start() {
        orphanCleanup.loadOrphanedUuids();

        spawner.initNpcPlugin();

        registerCleanupSystem();

        tickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
            this::tick,
            AscendConstants.RUNNER_TICK_INTERVAL_MS,
            AscendConstants.RUNNER_TICK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
        // Save current runner UUIDs before despawning (in case despawn fails)
        Set<UUID> activeUuids = new HashSet<>();
        for (RobotState state : robots.values()) {
            UUID entityUuid = state.getEntityUuid();
            if (entityUuid != null) {
                activeUuids.add(entityUuid);
            }
        }
        orphanCleanup.saveUuidsForCleanup(activeUuids);
        despawnAllRobots();
        activeEntityUuids.clear();
        onlinePlayers.clear();
        dirtyPlayers.clear();
        viewerContext = ViewerContext.empty();
    }

    public void onPlayerJoin(UUID playerId) {
        if (playerId != null) {
            onlinePlayers.add(playerId);
            markPlayerDirty(playerId);
            applyRunnerVisibility(playerId);
        }
    }

    public void onPlayerLeave(UUID playerId) {
        if (playerId != null) {
            onlinePlayers.remove(playerId);
            dirtyPlayers.remove(playerId);
            lastAutoElevationMs.remove(playerId);
            lastAutoSummitMs.remove(playerId);
            // Despawn all robots for this player
            removeTrackedRobotsForPlayer(playerId);
        }
    }

    public void setEventHandler(AscendPlayerEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    public void markPlayerDirty(UUID playerId) {
        if (playerId != null) {
            dirtyPlayers.add(playerId);
        }
    }

    public void despawnRobotsForPlayer(UUID playerId) {
        markPlayerDirty(playerId);
        removeTrackedRobotsForPlayer(playerId);
    }

    public void spawnRobot(UUID ownerId, String mapId) {
        markPlayerDirty(ownerId);
        String key = robotKey(ownerId, mapId);
        RobotState state = new RobotState(ownerId, mapId);
        if (robots.putIfAbsent(key, state) != null) {
            return; // Already existed
        }

        // Spawn NPC entity if NPCPlugin is available
        if (spawner.isNpcAvailable() && mapStore != null) {
            AscendMap map = mapStore.getMap(mapId);
            if (map != null) {
                spawner.spawnNpcForRobot(state, map);
            }
        }
    }

    public void despawnRobot(UUID ownerId, String mapId) {
        markPlayerDirty(ownerId);
        String key = robotKey(ownerId, mapId);
        RobotState state = robots.remove(key);
        if (state != null) {
            UUID entityUuid = state.getEntityUuid();
            clearTeleportWarning(state);
            spawner.despawnNpcForRobot(state);
            queueOrphanIfDespawnFailed(entityUuid, state);
        }
    }

    public void despawnAllRobots() {
        for (RobotState state : robots.values()) {
            clearTeleportWarning(state);
            spawner.despawnNpcForRobot(state);
        }
        robots.clear();
    }

    public RobotState getRobot(UUID ownerId, String mapId) {
        return robots.get(robotKey(ownerId, mapId));
    }

    /**
     * Get all runner entity UUIDs for a specific map.
     * Used for visibility filtering during runs.
     */
    public List<UUID> getRunnerUuidsForMap(String mapId) {
        List<UUID> uuids = new ArrayList<>();
        for (RobotState state : robots.values()) {
            if (state.getMapId().equals(mapId)) {
                UUID entityUuid = state.getEntityUuid();
                if (entityUuid != null) {
                    uuids.add(entityUuid);
                }
            }
        }
        return uuids;
    }

    /**
     * Get runner entity UUIDs for a specific map, excluding runners owned by a specific player.
     * Used so a player can still see their own runners while playing a map.
     */
    public List<UUID> getRunnerUuidsForMapExcludingOwner(String mapId, UUID excludeOwner) {
        List<UUID> uuids = new ArrayList<>();
        for (RobotState state : robots.values()) {
            if (state.getMapId().equals(mapId) && !state.getOwnerId().equals(excludeOwner)) {
                UUID entityUuid = state.getEntityUuid();
                if (entityUuid != null) {
                    uuids.add(entityUuid);
                }
            }
        }
        return uuids;
    }

    public void respawnRobot(UUID ownerId, String mapId, int newStars) {
        markPlayerDirty(ownerId);
        String key = robotKey(ownerId, mapId);
        RobotState state = robots.get(key);
        if (state == null) {
            return;
        }
        // Update stars in the state
        state.setStars(newStars);
        state.setSpeedLevel(0);
        // Despawn old NPC and spawn new one with updated entity type
        spawner.despawnNpcForRobot(state);
        if (spawner.isNpcAvailable() && mapStore != null) {
            AscendMap map = mapStore.getMap(mapId);
            if (map != null) {
                long now = System.currentTimeMillis();
                refreshRobotCache(state, map, now);
                if (getViewerContext(now).isRelevantMap(mapId)) {
                    spawner.spawnNpcForRobot(state, map);
                }
            }
        }
    }

    private void tick() {
        try {
            long now = System.currentTimeMillis();
            if (now - lastTeleportWarningCleanupMs >= TELEPORT_WARNING_CLEANUP_INTERVAL_MS) {
                cleanupTeleportWarningCache(now);
                lastTeleportWarningCleanupMs = now;
            }
            orphanCleanup.processPendingRemovals();
            refreshRobots(now);

            boolean doAutoOps = now - lastAutoUpgradeMs >= AUTO_UPGRADE_INTERVAL_MS;
            if (doAutoOps) {
                try {
                    performAutoElevation(now);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error in auto-elevation");
                }
                try {
                    performAutoSummit(now);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error in auto-summit");
                }
            }

            ViewerContext currentViewerContext = getViewerContext(now);
            syncRobotNpcState(currentViewerContext, now);

            List<AscendMap> sortedMaps = mapStore != null ? mapStore.listMapsSorted() : List.of();
            Map<World, List<PendingTeleport>> teleportsByWorld = new HashMap<>();
            for (RobotState robot : robots.values()) {
                tickRobot(robot, now, sortedMaps, teleportsByWorld);
                tickRobotMovement(robot, now, currentViewerContext, teleportsByWorld);
            }
            flushTeleportBatches(teleportsByWorld);

            if (doAutoOps) {
                lastAutoUpgradeMs = now;
                try {
                    performAutoRunnerUpgrades();
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error in auto-upgrades");
                }
            }
        } catch (Throwable t) {
            LOGGER.atSevere().withCause(t).log("Critical error in robot tick: " + t.getClass().getSimpleName());
        }
    }

    private void tickRobotMovement(RobotState robot, long now, ViewerContext currentViewerContext,
                                   Map<World, List<PendingTeleport>> teleportsByWorld) {
        if (!robot.isEntityDesired()) {
            return;
        }
        Ref<EntityStore> entityRef = robot.getEntityRef();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        AscendMap map = getCachedMap(robot, now);
        GhostRecording ghost = getCachedGhost(robot, now);
        World world = getCachedWorld(robot, now);
        if (map == null || ghost == null || world == null) {
            return;
        }

        long targetMovementIntervalMs = currentViewerContext.isHighFrequencyMap(robot.getMapId())
                ? VISIBLE_MOVEMENT_INTERVAL_MS
                : HEADLESS_MOVEMENT_INTERVAL_MS;
        if (robot.getMovementIntervalMs() != targetMovementIntervalMs) {
            robot.setMovementIntervalMs(targetMovementIntervalMs);
            robot.setNextMovementAtMs(now);
        }
        if (now < robot.getNextMovementAtMs()) {
            return;
        }
        robot.setNextMovementAtMs(now + targetMovementIntervalMs);

        long intervalMs = computeCompletionIntervalMs(map, ghost, robot.getSpeedLevel(), robot.getOwnerId());
        if (intervalMs <= 0L) {
            return;
        }

        long lastCompletionMs = robot.getLastCompletionMs();
        if (lastCompletionMs <= 0L) {
            return;
        }

        long elapsed = now - lastCompletionMs;
        double progress = Math.min(1.0d, (double) elapsed / (double) intervalMs);
        GhostSample sample = ghost.interpolateAt(progress);
        queueTeleport(teleportsByWorld, world, robot, entityRef, sample.x(), sample.y(), sample.z(), sample.yaw());
        robot.setPreviousPosition(sample.x(), sample.y(), sample.z());
    }

    private void teleportNpcWithRecordedRotation(RobotState robot, Ref<EntityStore> entityRef, World world,
                                                 double x, double y, double z, float yaw) {
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store == null) {
                return;
            }

            Vector3d targetVec = new Vector3d(x, y, z);
            Vector3f rotation = new Vector3f(0, yaw, 0);
            store.addComponent(entityRef, Teleport.getComponentType(),
                    new Teleport(world, targetVec, rotation));
        } catch (Exception e) {
            logTeleportWarning(robot, world, x, y, z, yaw, e);
        }
    }

    private void logTeleportWarning(RobotState robot, World world, double x, double y, double z,
                                    float yaw, Exception error) {
        UUID ownerId = robot != null ? robot.getOwnerId() : null;
        String mapId = robot != null ? robot.getMapId() : null;
        String key = (ownerId != null ? ownerId.toString() : "unknown-owner")
                + ":" + (mapId != null ? mapId : "unknown-map");
        long now = System.currentTimeMillis();
        Long lastLogged = teleportWarningByRobot.get(key);
        if (lastLogged != null && now - lastLogged < TELEPORT_WARNING_THROTTLE_MS) {
            return;
        }
        teleportWarningByRobot.put(key, now);
        String worldName = world != null ? world.getName() : "unknown";
        LOGGER.atWarning().withCause(error).log(
                "Runner teleport failed owner=" + ownerId
                        + " map=" + mapId
                        + " world=" + worldName
                        + " target=" + formatPosition(x, y, z)
                        + " yaw=" + yaw
        );
    }

    void clearTeleportWarning(RobotState state) {
        if (state == null) {
            return;
        }
        teleportWarningByRobot.remove(robotKey(state.getOwnerId(), state.getMapId()));
    }

    private void cleanupTeleportWarningCache(long now) {
        teleportWarningByRobot.entrySet().removeIf(entry ->
                now - entry.getValue() >= TELEPORT_WARNING_CACHE_TTL_MS || !robots.containsKey(entry.getKey()));
    }

    private String formatPosition(double x, double y, double z) {
        return String.format("(%.2f, %.2f, %.2f)", x, y, z);
    }

    private void refreshRobots(long now) {
        if (playerStore == null || mapStore == null) {
            return;
        }
        if (now - lastRefreshMs < AscendConstants.RUNNER_REFRESH_INTERVAL_MS) {
            return;
        }
        lastRefreshMs = now;

        Set<UUID> playersToRefresh = drainDirtyPlayers();
        if (now - lastFullRefreshMs >= FULL_REFRESH_INTERVAL_MS) {
            playersToRefresh.addAll(onlinePlayers);
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
        var it = dirtyPlayers.iterator();
        while (it.hasNext()) {
            UUID playerId = it.next();
            if (playerId != null) {
                it.remove();
                drained.add(playerId);
            }
        }
        return drained;
    }

    private void refreshPlayerRobots(UUID playerId, long now) {
        if (playerId == null) {
            return;
        }
        if (!onlinePlayers.contains(playerId) || !isPlayerInAscendWorld(playerId)) {
            removeTrackedRobotsForPlayer(playerId);
            return;
        }

        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            removeTrackedRobotsForPlayer(playerId);
            return;
        }

        Set<String> activeKeys = new HashSet<>();
        for (Map.Entry<String, GameplayState.MapProgress> mapEntry : progress.gameplay().getMapProgress().entrySet()) {
            String mapId = mapEntry.getKey();
            GameplayState.MapProgress mapProgress = mapEntry.getValue();
            if (mapId == null || mapProgress == null || !mapProgress.hasRobot()) {
                continue;
            }

            AscendMap map = mapStore.getMap(mapId);
            if (map == null) {
                continue;
            }

            String key = robotKey(playerId, mapId);
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
                            if (isPlayerNearMapSpawn(playerId, map)) {
                                UUID oldUuid = state.getEntityUuid();
                                if (oldUuid != null) {
                                    orphanCleanup.addOrphan(oldUuid);
                                    activeEntityUuids.remove(oldUuid);
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
        String prefix = playerId.toString() + ":";
        for (String key : List.copyOf(robots.keySet())) {
            if (!key.startsWith(prefix) || activeKeys.contains(key)) {
                continue;
            }
            removeRobotState(key);
        }
    }

    void removeTrackedRobotsForPlayer(UUID playerId) {
        String prefix = playerId.toString() + ":";
        for (String key : List.copyOf(robots.keySet())) {
            if (key.startsWith(prefix)) {
                removeRobotState(key);
            }
        }
    }

    void removeRobotState(String key) {
        RobotState removed = robots.remove(key);
        if (removed == null) {
            return;
        }
        UUID entityUuid = removed.getEntityUuid();
        clearTeleportWarning(removed);
        spawner.despawnNpcForRobot(removed);
        queueOrphanIfDespawnFailed(entityUuid, removed);
    }

    private void logRobotHealth(long now) {
        if (now - lastHealthLogMs < HEALTH_LOG_INTERVAL_MS) {
            return;
        }
        lastHealthLogMs = now;

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
                ghostCount, spawner.isNpcAvailable() ? "available" : "NULL");
    }

    private ViewerContext getViewerContext(long now) {
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
        if (mapStore == null || onlinePlayers.isEmpty()) {
            return ViewerContext.empty();
        }
        List<AscendMap> maps = mapStore.listMapsSorted();
        Set<String> relevantMapIds = new HashSet<>();
        Set<String> highFrequencyMapIds = new HashSet<>();
        double relevantDistanceSq = RELEVANT_MAP_DISTANCE * RELEVANT_MAP_DISTANCE;
        double fastDistanceSq = FAST_MAP_DISTANCE * FAST_MAP_DISTANCE;

        for (UUID playerId : onlinePlayers) {
            PlayerRef playerRef = resolvePlayerRef(playerId);
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
            if (store.getExternalData() == null) continue;
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

    private void syncRobotNpcState(ViewerContext currentViewerContext, long now) {
        for (RobotState robot : robots.values()) {
            syncRobotNpcState(robot, currentViewerContext, now);
        }
    }

    private void syncRobotNpcState(RobotState robot, ViewerContext currentViewerContext, long now) {
        AscendMap map = getCachedMap(robot, now);
        World world = getCachedWorld(robot, now);
        boolean shouldHaveNpc = spawner.isNpcAvailable()
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
                spawner.spawnNpcForRobot(robot, map);
            }
            return;
        }

        if (!refIsValid && !hasEntityUuid) {
            return;
        }
        UUID entityUuid = robot.getEntityUuid();
        clearTeleportWarning(robot);
        spawner.despawnNpcForRobot(robot);
        queueOrphanIfDespawnFailed(entityUuid, robot);
    }

    private void flushTeleportBatches(Map<World, List<PendingTeleport>> teleportsByWorld) {
        for (Map.Entry<World, List<PendingTeleport>> entry : teleportsByWorld.entrySet()) {
            World world = entry.getKey();
            List<PendingTeleport> teleports = entry.getValue();
            if (world == null || teleports == null || teleports.isEmpty()) {
                continue;
            }
            world.execute(() -> {
                for (PendingTeleport teleport : teleports) {
                    teleportNpcWithRecordedRotation(teleport.robot(), teleport.entityRef(), world,
                            teleport.x(), teleport.y(), teleport.z(), teleport.yaw());
                }
            });
        }
    }

    private void queueTeleport(Map<World, List<PendingTeleport>> teleportsByWorld, World world, RobotState robot,
                               Ref<EntityStore> entityRef, double x, double y, double z, float yaw) {
        if (world == null || entityRef == null || !entityRef.isValid()) {
            return;
        }
        teleportsByWorld.computeIfAbsent(world, ignored -> new ArrayList<>())
                .add(new PendingTeleport(robot, entityRef, x, y, z, yaw));
    }

    private void refreshRobotCache(RobotState robot, AscendMap resolvedMap, long now) {
        if (robot == null) {
            return;
        }
        if (resolvedMap == null && now - robot.getCacheRefreshedAtMs() < CACHE_REFRESH_INTERVAL_MS) {
            return;
        }

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

    private AscendMap getCachedMap(RobotState robot, long now) {
        refreshRobotCache(robot, null, now);
        return robot.getCachedMap();
    }

    private World getCachedWorld(RobotState robot, long now) {
        refreshRobotCache(robot, null, now);
        return robot.getCachedWorld();
    }

    private GhostRecording getCachedGhost(RobotState robot, long now) {
        refreshRobotCache(robot, null, now);
        return robot.getCachedGhost();
    }

    void queueOrphanIfDespawnFailed(UUID previousEntityUuid, RobotState state) {
        if (previousEntityUuid == null || state == null) {
            return;
        }
        if (state.getEntityUuid() == null) {
            return;
        }
        orphanCleanup.addOrphan(previousEntityUuid);
    }

    private void tickRobot(RobotState robot, long now, List<AscendMap> maps,
                           Map<World, List<PendingTeleport>> teleportsByWorld) {
        if (robot.isWaiting()) {
            return;
        }
        AscendMap map = getCachedMap(robot, now);
        if (map == null) {
            return;
        }
        UUID ownerId = robot.getOwnerId();
        GhostRecording ghost = getCachedGhost(robot, now);
        long intervalMs = computeCompletionIntervalMs(map, ghost, robot.getSpeedLevel(), ownerId);
        if (intervalMs <= 0L) {
            return;
        }
        long lastCompletionMs = robot.getLastCompletionMs();
        if (lastCompletionMs <= 0L) {
            robot.setLastCompletionMs(now);
            return;
        }
        long elapsed = now - lastCompletionMs;
        if (elapsed < intervalMs) {
            return;
        }
        long completions = elapsed / intervalMs;
        if (completions <= 0L) {
            return;
        }

        String mapId = robot.getMapId();
        int stars = robot.getStars();

        // Get Summit bonuses (Multiplier Gain + Evolution Power)
        double multiplierGainBonus = 1.0;
        double evolutionPowerBonus = 3.0;
        double baseMultiplierBonus = 0.0;
        if (summitManager != null) {
            multiplierGainBonus = summitManager.getMultiplierGainBonus(ownerId);
            evolutionPowerBonus = summitManager.getEvolutionPowerBonus(ownerId);
            baseMultiplierBonus = summitManager.getBaseMultiplierBonus(ownerId);
        }
        BigNumber multiplierIncrement = AscendConstants.getRunnerMultiplierIncrement(stars, multiplierGainBonus, evolutionPowerBonus, baseMultiplierBonus);

        BigNumber totalMultiplierBonus = multiplierIncrement.multiply(BigNumber.fromLong(completions));

        // Calculate payout BEFORE adding multiplier (use current multiplier, not the new one)
        BigNumber payoutPerRun = playerStore.getCompletionPayout(ownerId, maps, AscendConstants.MULTIPLIER_SLOTS, mapId, BigNumber.ZERO);

        BigNumber totalPayout = payoutPerRun.multiply(BigNumber.fromLong(completions));

        // Use event handler for volt + side-effects (tutorial thresholds, ascension triggers)
        if (eventHandler != null) {
            eventHandler.addVoltWithEffects(ownerId, totalPayout);
        } else {
            playerStore.atomicAddVolt(ownerId, totalPayout);
        }
        if (!playerStore.atomicAddTotalVoltEarned(ownerId, totalPayout)) {
            LOGGER.atWarning().log("Failed to add total volt earned for " + ownerId);
        }
        if (!playerStore.atomicAddMapMultiplier(ownerId, mapId, totalMultiplierBonus)) {
            LOGGER.atWarning().log("Failed to add map multiplier for " + ownerId + " on map " + mapId);
        }

        robot.setLastCompletionMs(lastCompletionMs + (intervalMs * completions));
        robot.addRunsCompleted(completions);

        // Teleport NPC back to start after completion and reset previous position
        Ref<EntityStore> entityRef = robot.getEntityRef();
        World world = getCachedWorld(robot, now);
        if (entityRef != null && entityRef.isValid() && world != null) {
            queueTeleport(teleportsByWorld, world, robot, entityRef,
                    map.getStartX(), map.getStartY(), map.getStartZ(), map.getStartRotY());
            robot.clearPreviousPosition();
        }
    }

    // Auto Runner Upgrades (Skill Tree)

    private void performAutoRunnerUpgrades() {
        if (ascensionManager == null) return;

        for (UUID playerId : onlinePlayers) {
            if (!ascensionManager.hasAutoRunners(playerId)) continue;
            autoUpgradeRunners(playerId);
        }
    }

    private void autoUpgradeRunners(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) return;
        if (!progress.automation().isAutoUpgradeEnabled()) return;

        List<AscendMap> maps = mapStore.listMapsSorted();

        // First priority: buy runners on unlocked maps that have been completed (free)
        for (AscendMap map : maps) {
            GameplayState.MapProgress mp = progress.gameplay().getMapProgress().get(map.getId());
            if (mp == null || !mp.isUnlocked() || mp.hasRobot()) continue;

            // Accept ghost recording OR best time as proof of completion
            GhostRecording ghost = ghostStore.getRecording(playerId, map.getId());
            if (ghost == null && mp.getBestTimeMs() == null) continue;

            playerStore.setHasRobot(playerId, map.getId(), true);
            return; // One action per call for smooth visual
        }

        // Auto-evolve eligible maps (free, all at once — each map independent of others)
        // Runs before speed upgrades so a map at max level evolves immediately,
        // even while other maps still have affordable speed upgrades.
        if (progress.automation().isAutoEvolutionEnabled()) {
            if (ascensionManager != null && ascensionManager.hasAutoEvolution(playerId)) {
                boolean anyEvolved = false;
                for (AscendMap map : maps) {
                    GameplayState.MapProgress mp = progress.gameplay().getMapProgress().get(map.getId());
                    if (mp == null || !mp.hasRobot()) continue;
                    if (mp.getRobotSpeedLevel() >= AscendConstants.MAX_SPEED_LEVEL
                            && mp.getRobotStars() < AscendConstants.MAX_ROBOT_STARS) {
                        int newStars = playerStore.evolveRobot(playerId, map.getId());
                        respawnRobot(playerId, map.getId(), newStars);
                        anyEvolved = true;
                    }
                }
                if (anyEvolved && achievementManager != null) {
                    achievementManager.checkAndUnlockAchievements(playerId, null);
                }
            }
        }

        // Speed upgrade: find cheapest across all maps (one per call for smooth visual)
        BigNumber volt = progress.economy().getVolt();
        String cheapestMapId = null;
        BigNumber cheapestCost = null;

        for (AscendMap map : maps) {
            GameplayState.MapProgress mp = progress.gameplay().getMapProgress().get(map.getId());
            if (mp == null || !mp.hasRobot()) continue;

            int speedLevel = mp.getRobotSpeedLevel();
            if (speedLevel >= AscendConstants.MAX_SPEED_LEVEL) continue;

            BigNumber cost = AscendConstants.getRunnerUpgradeCost(
                speedLevel, map.getDisplayOrder(), mp.getRobotStars());

            if (cheapestCost == null || cost.lt(cheapestCost)) {
                cheapestCost = cost;
                cheapestMapId = map.getId();
            }
        }

        if (cheapestMapId != null && cheapestCost != null && volt.gte(cheapestCost)) {
            if (!playerStore.atomicSpendVolt(playerId, cheapestCost)) return;
            playerStore.incrementRobotSpeedLevel(playerId, cheapestMapId);
            playerStore.checkAndUnlockEligibleMaps(playerId, mapStore);
        }
    }

    // Auto-Elevation

    private void performAutoElevation(long now) {
        for (UUID playerId : onlinePlayers) {
            if (ascensionManager == null || !ascensionManager.hasAutoElevation(playerId)) continue;
            // Skip if player is actively playing a map — don't reset progress mid-run
            if (runTracker != null && runTracker.getActiveMapId(playerId) != null) continue;
            // Skip if elevation is blocked by active challenge
            if (challengeManager != null && challengeManager.isElevationBlocked(playerId)) continue;
            try {
                autoElevatePlayer(playerId, now);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Auto-elevation failed for " + playerId);
            }
        }
    }

    private void autoElevatePlayer(UUID playerId, long now) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) return;
        if (!progress.automation().isAutoElevationEnabled()) return;

        List<Long> targets = progress.automation().getAutoElevationTargets();
        int targetIndex = progress.automation().getAutoElevationTargetIndex();

        // Skip targets already surpassed by current multiplier
        int currentLevel = progress.economy().getElevationMultiplier();
        long currentActualMultiplier = Math.round(AscendConstants.getElevationMultiplier(currentLevel));
        while (targetIndex < targets.size() && targets.get(targetIndex) <= currentActualMultiplier) {
            targetIndex++;
        }
        if (targetIndex != progress.automation().getAutoElevationTargetIndex()) {
            playerStore.setAutoElevationTargetIndex(playerId, targetIndex);
        }

        if (targets.isEmpty() || targetIndex >= targets.size()) return;

        // Check timer
        int timerSeconds = progress.automation().getAutoElevationTimerSeconds();
        if (timerSeconds > 0) {
            Long lastMs = lastAutoElevationMs.get(playerId);
            if (lastMs != null && (now - lastMs) < (long) timerSeconds * 1000L) return;
        }

        // Calculate purchasable levels
        BigNumber accumulatedVolt = progress.economy().getElevationAccumulatedVolt();
        AscendConstants.ElevationPurchaseResult result = AscendConstants.calculateElevationPurchase(currentLevel, accumulatedVolt, BigNumber.ONE);
        if (result.levels <= 0) return;

        int newLevel = currentLevel + result.levels;
        long newMultiplier = Math.round(AscendConstants.getElevationMultiplier(newLevel));
        long nextTarget = targets.get(targetIndex);
        if (newMultiplier < nextTarget) return;

        // Execute elevation — reset progress first, then despawn robots.
        // resetProgressForElevation sets hasRobot=false, so refreshRobots() will clean up stale
        // robots on the next cycle. We despawn after to accelerate cleanup, but the critical part
        // is that the reset happens first — if despawn fails or throws, robots won't be re-created
        // because hasRobot is already false.
        playerStore.atomicSetElevationAndResetVolt(playerId, newLevel);

        // Get first map ID for reset
        List<AscendMap> maps = mapStore.listMapsSorted();
        String firstMapId = maps.isEmpty() ? null : maps.get(0).getId();

        playerStore.resetProgressForElevation(playerId, firstMapId);

        // Now despawn robot NPCs (safe — even if this fails, hasRobot=false prevents re-creation)
        despawnRobotsForPlayer(playerId);

        // Send chat message
        PlayerRef playerRef = resolvePlayerRef(playerId);
        if (playerRef != null) {
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "[Auto-Elevation] Elevated to x" + newMultiplier)
                .color(io.hyvexa.common.util.SystemMessageUtils.SUCCESS));
        }

        // Advance targetIndex past all surpassed targets
        int newIndex = targetIndex;
        while (newIndex < targets.size() && newMultiplier >= targets.get(newIndex)) {
            newIndex++;
        }
        playerStore.setAutoElevationTargetIndex(playerId, newIndex);

        lastAutoElevationMs.put(playerId, now);
        playerStore.markDirty(playerId);

        // Close the player's ascend page so they see fresh state on reopen
        AscendCommand.forceCloseActivePage(playerId);
    }

    // Auto-Summit

    private void performAutoSummit(long now) {
        for (UUID playerId : onlinePlayers) {
            if (ascensionManager == null || !ascensionManager.hasAutoSummit(playerId)) continue;
            // Skip if player is actively playing a map — don't reset progress mid-run
            if (runTracker != null && runTracker.getActiveMapId(playerId) != null) continue;
            // Skip if all summit is blocked by active challenge
            if (challengeManager != null && challengeManager.isAllSummitBlocked(playerId)) continue;
            try {
                autoSummitPlayer(playerId, now);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Auto-summit failed for " + playerId);
            }
        }
    }

    private void autoSummitPlayer(UUID playerId, long now) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) return;
        if (!progress.automation().isAutoSummitEnabled()) return;

        if (summitManager == null) return;

        if (!summitManager.canSummit(playerId)) return;

        // Check timer
        int timerSeconds = progress.automation().getAutoSummitTimerSeconds();
        if (timerSeconds > 0) {
            Long lastMs = lastAutoSummitMs.get(playerId);
            if (lastMs != null && (now - lastMs) < (long) timerSeconds * 1000L) return;
        }

        List<AutomationConfig.AutoSummitCategoryConfig> config = progress.automation().getAutoSummitConfig();
        AscendConstants.SummitCategory[] categories = AscendConstants.SummitCategory.values();

        // Target-based: iterate all categories and summit the first one that can reach its target
        for (int i = 0; i < categories.length; i++) {
            if (i >= config.size()) continue;

            AutomationConfig.AutoSummitCategoryConfig catConfig = config.get(i);
            if (!catConfig.isEnabled()) continue;
            int targetLevel = catConfig.getTargetLevel();
            if (targetLevel <= 0) continue;

            AscendConstants.SummitCategory category = categories[i];
            int currentLevel = playerStore.getSummitLevel(playerId, category);

            // Skip if target already reached
            if (currentLevel >= targetLevel) continue;

            // Preview the summit
            SummitManager.SummitPreview preview = summitManager.previewSummit(playerId, category);
            if (!preview.hasGain()) continue;

            // Only summit if projected level reaches the target
            if (preview.newLevel() < targetLevel) continue;

            // Perform summit — this calls resetProgressForSummit which sets hasRobot=false,
            // so refreshRobots() won't re-create them. We despawn NPCs after to accelerate cleanup.
            // Critical: reset BEFORE despawn — if despawn throws, robots won't be re-created
            // because hasRobot is already false (prevents infinite despawn-recreate loop).
            SummitManager.SummitResult result = summitManager.performSummit(playerId, category);
            if (!result.succeeded()) continue;

            // Now despawn robot NPCs (safe — even if this fails, hasRobot=false prevents re-creation)
            despawnRobotsForPlayer(playerId);

            // Send chat message
            PlayerRef playerRef = resolvePlayerRef(playerId);
            if (playerRef != null) {
                playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                    "[Auto-Summit] " + category.getDisplayName() + " Lv " + result.newLevel())
                    .color(io.hyvexa.common.util.SystemMessageUtils.SUCCESS));
            }

            lastAutoSummitMs.put(playerId, now);

            // Close the player's ascend page so they see fresh state on reopen
            AscendCommand.forceCloseActivePage(playerId);

            return; // One summit per tick for smooth visual
        }
    }

    /**
     * Calculate speed multiplier for a runner (public for passive earnings)
     */
    public double calculateSpeedMultiplier(AscendMap map, int speedLevel, UUID ownerId) {
        if (map == null) {
            return 1.0;
        }
        return speedCalculator != null
            ? speedCalculator.calculateSpeedMultiplier(map, speedLevel, ownerId)
            : 1.0 + (speedLevel * AscendConstants.getMapSpeedMultiplier(map.getDisplayOrder()));
    }

    /**
     * Returns the runner's current lap progress (0.0 to 1.0), synchronized with its actual cycle.
     * Returns -1 if the runner has no active cycle.
     */
    public double getRunnerProgress(UUID ownerId, String mapId) {
        RobotState robot = robots.get(robotKey(ownerId, mapId));
        if (robot == null) {
            return -1;
        }
        AscendMap map = mapStore.getMap(mapId);
        if (map == null) {
            return -1;
        }
        long intervalMs = computeCompletionIntervalMs(map, robot.getSpeedLevel(), ownerId);
        if (intervalMs <= 0L) {
            return -1;
        }
        long lastCompletion = robot.getLastCompletionMs();
        if (lastCompletion <= 0L) {
            return -1;
        }
        long elapsed = System.currentTimeMillis() - lastCompletion;
        if (elapsed < 0) {
            return 0;
        }
        return Math.min(1.0, (double) elapsed / intervalMs);
    }

    private long computeCompletionIntervalMs(AscendMap map, int speedLevel, UUID ownerId) {
        if (map == null) {
            return -1L;
        }
        GhostRecording ghost = ghostStore.getRecording(ownerId, map.getId());
        return computeCompletionIntervalMs(map, ghost, speedLevel, ownerId);
    }

    private long computeCompletionIntervalMs(AscendMap map, GhostRecording ghost, int speedLevel, UUID ownerId) {
        if (map == null) {
            return -1L;
        }

        // Use player's PB time as base (from ghost recording)
        long base;
        if (ghost != null) {
            base = ghost.getCompletionTimeMs();
        } else {
            // Fallback: use map's base run time so runners still earn without a ghost
            base = map.getEffectiveBaseRunTimeMs();
        }
        if (base <= 0L) {
            return -1L;
        }

        // Calculate speed multiplier
        double speedMultiplier = speedCalculator != null
                ? speedCalculator.calculateSpeedMultiplier(map, speedLevel, ownerId)
                : 1.0;

        long interval = (long) (base / speedMultiplier);
        return Math.max(1L, interval);
    }

    String robotKey(UUID ownerId, String mapId) {
        return ownerId.toString() + ":" + mapId;
    }

    /**
     * Check if a player is currently in the Ascend world.
     * Uses the plugin's PlayerRef cache for O(1) lookup instead of scanning all players.
     */
    boolean isPlayerInAscendWorld(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        PlayerRef playerRef = resolvePlayerRef(playerId);
        if (playerRef == null) {
            return false;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }
        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return false;
        }
        if (store.getExternalData() == null) return false;
        World world = store.getExternalData().getWorld();
        return ModeGate.isAscendWorld(world);
    }

    boolean isPlayerNearMapSpawn(UUID playerId, AscendMap map) {
        if (playerId == null || map == null) {
            return false;
        }
        PlayerRef playerRef = resolvePlayerRef(playerId);
        if (playerRef == null) {
            return false;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }
        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return false;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return false;
        }
        Vector3d pos = transform.getPosition();
        double mapX = map.getStartX();
        double mapY = map.getStartY();
        double mapZ = map.getStartZ();
        double px = pos.getX();
        double py = pos.getY();
        double pz = pos.getZ();
        double distSq = (px - mapX) * (px - mapX) + (py - mapY) * (py - mapY) + (pz - mapZ) * (pz - mapZ);
        return distSq <= CHUNK_LOAD_DISTANCE * CHUNK_LOAD_DISTANCE;
    }

    private PlayerRef resolvePlayerRef(UUID playerId) {
        if (playerId == null || playerRefResolver == null) {
            return null;
        }
        return playerRefResolver.apply(playerId);
    }

    private record PendingTeleport(RobotState robot, Ref<EntityStore> entityRef,
                                   double x, double y, double z, float yaw) {}

    private record ViewerContext(Set<String> relevantMapIds, Set<String> highFrequencyMapIds) {
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

    // Runner Cleanup (Server Restart Handling)

    private RunnerCleanupSystem cleanupSystem;

    private void registerCleanupSystem() {
        var registry = EntityStore.REGISTRY;
        if (!registry.hasSystemClass(RunnerCleanupSystem.class)) {
            cleanupSystem = new RunnerCleanupSystem(this);
            registry.registerSystem(cleanupSystem);
        }
    }

    public RunnerCleanupSystem getCleanupSystem() {
        return cleanupSystem;
    }

    public boolean isOrphanedRunner(UUID entityUuid) {
        return orphanCleanup.isOrphaned(entityUuid);
    }

    public boolean isActiveRunnerUuid(UUID entityUuid) {
        if (entityUuid == null) {
            return false;
        }
        return activeEntityUuids.contains(entityUuid) || orphanCleanup.isPendingRemoval(entityUuid);
    }

    public void queueOrphanForRemoval(UUID entityUuid, Ref<EntityStore> entityRef) {
        orphanCleanup.queueForRemoval(entityUuid, entityRef);
    }

    public void markOrphanCleaned(UUID entityUuid) {
        orphanCleanup.markCleaned(entityUuid);
    }

    public boolean isCleanupPending() {
        return orphanCleanup.isCleanupPending();
    }

    /**
     * Apply the "hide other runners" setting for a viewer.
     * If enabled, hides all runners not owned by the viewer.
     * If disabled, shows all runners not owned by the viewer.
     */
    public void applyRunnerVisibility(UUID viewerId) {
        if (viewerId == null) {
            return;
        }
        boolean hide = playerStore.isHideOtherRunners(viewerId);
        EntityVisibilityManager visibilityManager = EntityVisibilityManager.get();
        for (RobotState state : robots.values()) {
            if (state.getOwnerId().equals(viewerId)) {
                continue; // Skip own runners
            }
            UUID entityUuid = state.getEntityUuid();
            if (entityUuid == null) {
                continue;
            }
            if (hide) {
                visibilityManager.hideEntity(viewerId, entityUuid);
            } else {
                visibilityManager.showEntity(viewerId, entityUuid);
            }
        }
    }

    // ── Package-private accessors for extracted subsystems ──

    Set<UUID> getOnlinePlayers() { return onlinePlayers; }
    Set<UUID> getDirtyPlayers() { return dirtyPlayers; }
    AscendPlayerStore getPlayerStore() { return playerStore; }
    AscendMapStore getMapStore() { return mapStore; }
    GhostStore getGhostStore() { return ghostStore; }
    AscendRunTracker getRunTracker() { return runTracker; }
    AscensionManager getAscensionManager() { return ascensionManager; }
    ChallengeManager getChallengeManager() { return challengeManager; }
    SummitManager getSummitManager() { return summitManager; }
    AchievementManager getAchievementManager() { return achievementManager; }
    PlayerRef getPlayerRef(UUID playerId) { return resolvePlayerRef(playerId); }
    Map<String, RobotState> getRobots() { return robots; }
    Set<UUID> getActiveEntityUuids() { return activeEntityUuids; }
    OrphanedEntityCleanup getOrphanCleanup() { return orphanCleanup; }
    RobotSpawner getSpawner() { return spawner; }
}
