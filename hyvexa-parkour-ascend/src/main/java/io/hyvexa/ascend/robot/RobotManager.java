package io.hyvexa.ascend.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerEventHandler;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.ghost.GhostRecording;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.OrphanedEntityCleanup;

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
     * Orchestrator — delegates to:
     *   autoUpgrader:        auto-upgrade, auto-elevation, auto-summit
     *   refreshSystem:       robot cache refresh, viewer context, NPC state sync, visibility
     *   movementController:  position interpolation, teleport batching, speed calculations
     *
     * Threading model:
     * - robots, onlinePlayers, dirtyPlayers:
     *   ConcurrentHashMap/newKeySet collections (thread-safe by construction).
     * - orphanCleanup: owns orphan UUID + pending-removal concurrency state.
     * - spawner: owns npcPlugin lifecycle; set once in start(), thereafter read-only.
     * - tickTask: only accessed in start()/stop() (single-threaded lifecycle).
     *
     * Entity operations (spawn/despawn) must run on the World thread via world.execute().
     */

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RUNNER_UUIDS_FILE = "runner_uuids.txt";
    private static final long AUTO_UPGRADE_INTERVAL_MS = 50L;
    private static final double CHUNK_LOAD_DISTANCE = 128.0;

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
    private final RobotSpawner spawner = new RobotSpawner(this);
    private final AutoRunnerUpgradeEngine autoUpgrader = new AutoRunnerUpgradeEngine(this);
    private final RobotRefreshSystem refreshSystem = new RobotRefreshSystem(this);
    private final RobotMovementController movementController;
    private ScheduledFuture<?> tickTask;
    private volatile long lastAutoUpgradeMs = 0;

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
        this.movementController = new RobotMovementController(this, refreshSystem, speedCalculator);
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
        refreshSystem.resetViewerContext();
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
            autoUpgrader.onPlayerLeave(playerId);
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
            movementController.clearTeleportWarning(state);
            spawner.despawnNpcForRobot(state);
            queueOrphanIfDespawnFailed(entityUuid, state);
        }
    }

    public void despawnAllRobots() {
        for (RobotState state : robots.values()) {
            movementController.clearTeleportWarning(state);
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
        state.setStars(newStars);
        state.setSpeedLevel(0);
        spawner.despawnNpcForRobot(state);
        if (spawner.isNpcAvailable() && mapStore != null) {
            AscendMap map = mapStore.getMap(mapId);
            if (map != null) {
                long now = System.currentTimeMillis();
                refreshSystem.refreshRobotCache(state, map, now);
                if (refreshSystem.getViewerContext(now).isRelevantMap(mapId)) {
                    spawner.spawnNpcForRobot(state, map);
                }
            }
        }
    }

    private void tick() {
        try {
            long now = System.currentTimeMillis();
            movementController.cleanupTeleportWarnings(now);
            orphanCleanup.processPendingRemovals();
            refreshSystem.refreshRobots(now);

            boolean doAutoOps = now - lastAutoUpgradeMs >= AUTO_UPGRADE_INTERVAL_MS;
            if (doAutoOps) {
                try {
                    autoUpgrader.performAutoElevation(now);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error in auto-elevation");
                }
                try {
                    autoUpgrader.performAutoSummit(now);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error in auto-summit");
                }
            }

            RobotRefreshSystem.ViewerContext currentViewerContext = refreshSystem.getViewerContext(now);
            refreshSystem.syncRobotNpcState(currentViewerContext, now);

            List<AscendMap> sortedMaps = mapStore != null ? mapStore.listMapsSorted() : List.of();
            Map<World, List<RobotMovementController.PendingTeleport>> teleportsByWorld = new HashMap<>();
            for (RobotState robot : robots.values()) {
                tickRobot(robot, now, sortedMaps, teleportsByWorld);
                movementController.tickMovement(robot, now, currentViewerContext, teleportsByWorld);
            }
            movementController.flushTeleportBatches(teleportsByWorld);

            if (doAutoOps) {
                lastAutoUpgradeMs = now;
                try {
                    autoUpgrader.performAutoRunnerUpgrades();
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error in auto-upgrades");
                }
            }
        } catch (Throwable t) {
            LOGGER.atSevere().withCause(t).log("Critical error in robot tick: " + t.getClass().getSimpleName());
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
        movementController.clearTeleportWarning(removed);
        spawner.despawnNpcForRobot(removed);
        queueOrphanIfDespawnFailed(entityUuid, removed);
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
                           Map<World, List<RobotMovementController.PendingTeleport>> teleportsByWorld) {
        if (robot.isWaiting()) {
            return;
        }
        AscendMap map = refreshSystem.getCachedMap(robot, now);
        if (map == null) {
            return;
        }
        UUID ownerId = robot.getOwnerId();
        GhostRecording ghost = refreshSystem.getCachedGhost(robot, now);
        long intervalMs = movementController.computeCompletionIntervalMs(map, ghost, robot.getSpeedLevel(), ownerId);
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
        BigNumber payoutPerRun = playerStore.progression().getCompletionPayout(ownerId, maps, AscendConstants.MULTIPLIER_SLOTS, mapId, BigNumber.ZERO);

        BigNumber totalPayout = payoutPerRun.multiply(BigNumber.fromLong(completions));

        // Use event handler for volt + side-effects (tutorial thresholds, ascension triggers)
        eventHandler.addVoltWithEffects(ownerId, totalPayout);
        playerStore.volt().addTotalVoltEarned(ownerId, totalPayout);
        playerStore.runners().addMapMultiplier(ownerId, mapId, totalMultiplierBonus);

        robot.setLastCompletionMs(lastCompletionMs + (intervalMs * completions));
        robot.addRunsCompleted(completions);

        Ref<EntityStore> entityRef = robot.getEntityRef();
        World world = refreshSystem.getCachedWorld(robot, now);
        if (entityRef != null && entityRef.isValid() && world != null) {
            movementController.queueTeleport(teleportsByWorld, world, robot, entityRef,
                    map.getStartX(), map.getStartY(), map.getStartZ(), map.getStartRotY());
            robot.clearPreviousPosition();
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

    public double getRunnerProgress(UUID ownerId, String mapId) {
        return movementController.getRunnerProgress(ownerId, mapId);
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

    public void applyRunnerVisibility(UUID viewerId) {
        refreshSystem.applyRunnerVisibility(viewerId);
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
    RobotMovementController getMovementController() { return movementController; }
}
