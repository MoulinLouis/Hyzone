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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.mine.MineBonusCalculator;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.common.ghost.GhostRecording;
import io.hyvexa.common.ghost.GhostSample;
import io.hyvexa.ascend.summit.SummitManager;
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

public class RobotManager {

    /*
     * Threading model:
     * - robots, onlinePlayers, dirtyPlayers, teleportWarningByRobot:
     *   ConcurrentHashMap/newKeySet collections (thread-safe by construction).
     * - orphanCleanup: owns orphan UUID + pending-removal concurrency state.
     * - spawner: owns volatile npcPlugin (set once in start(), thereafter read-only).
     * - refreshSystem: owns volatile refresh timestamps and viewerContext.
     * - autoRunnerUpgradeEngine: owns per-player cooldown maps.
     * - lastAutoUpgradeMs: volatile, read/written from scheduled executor tick.
     * - tickTask: only accessed in start()/stop() (single-threaded lifecycle).
     *
     * Entity operations (spawn/despawn) must run on the World thread via world.execute().
     */

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RUNNER_UUIDS_FILE = "runner_uuids.txt";
    private static final long AUTO_UPGRADE_INTERVAL_MS = 50L;
    private static final long HEADLESS_MOVEMENT_INTERVAL_MS = 50L;
    private static final long VISIBLE_MOVEMENT_INTERVAL_MS = AscendConstants.RUNNER_TICK_INTERVAL_MS;
    private static final long TELEPORT_WARNING_THROTTLE_MS = 10_000L;
    private static final long TELEPORT_WARNING_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(30L);
    private static final long TELEPORT_WARNING_CLEANUP_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5L);

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final GhostStore ghostStore;
    private final Map<String, RobotState> robots = new ConcurrentHashMap<>();
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final OrphanedEntityCleanup orphanCleanup;
    private final AutoRunnerUpgradeEngine autoRunnerUpgradeEngine;
    private final RobotSpawner spawner;
    private final RobotRefreshSystem refreshSystem;
    private final Map<String, Long> teleportWarningByRobot = new ConcurrentHashMap<>();
    private ScheduledFuture<?> tickTask;
    private volatile long lastAutoUpgradeMs = 0;
    private volatile long lastTeleportWarningCleanupMs = 0L;

    public RobotManager(AscendMapStore mapStore, AscendPlayerStore playerStore, GhostStore ghostStore) {
        this.mapStore = mapStore;
        this.playerStore = playerStore;
        this.ghostStore = ghostStore;
        this.orphanCleanup = new OrphanedEntityCleanup(LOGGER,
                Path.of("mods", "Parkour", RUNNER_UUIDS_FILE));
        this.autoRunnerUpgradeEngine = new AutoRunnerUpgradeEngine(this);
        this.spawner = new RobotSpawner(this);
        this.refreshSystem = new RobotRefreshSystem(this);
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
            autoRunnerUpgradeEngine.onPlayerLeave(playerId);
            // Despawn all robots for this player
            removeTrackedRobotsForPlayer(playerId);
        }
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
            if (now - lastTeleportWarningCleanupMs >= TELEPORT_WARNING_CLEANUP_INTERVAL_MS) {
                cleanupTeleportWarningCache(now);
                lastTeleportWarningCleanupMs = now;
            }
            orphanCleanup.processPendingRemovals();
            refreshSystem.refreshRobots(now);

            boolean doAutoOps = now - lastAutoUpgradeMs >= AUTO_UPGRADE_INTERVAL_MS;
            if (doAutoOps) {
                try {
                    autoRunnerUpgradeEngine.performAutoElevation(now);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error in auto-elevation");
                }
                try {
                    autoRunnerUpgradeEngine.performAutoSummit(now);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error in auto-summit");
                }
            }

            RobotRefreshSystem.ViewerContext currentViewerContext = refreshSystem.getViewerContext(now);
            refreshSystem.syncRobotNpcState(currentViewerContext, now);

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
                    autoRunnerUpgradeEngine.performAutoRunnerUpgrades();
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error in auto-upgrades");
                }
            }
        } catch (Throwable t) {
            LOGGER.atSevere().withCause(t).log("Critical error in robot tick: " + t.getClass().getSimpleName());
        }
    }

    private void tickRobotMovement(RobotState robot, long now, RobotRefreshSystem.ViewerContext currentViewerContext,
                                   Map<World, List<PendingTeleport>> teleportsByWorld) {
        if (!robot.isEntityDesired()) {
            return;
        }
        Ref<EntityStore> entityRef = robot.getEntityRef();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        AscendMap map = refreshSystem.getCachedMap(robot, now);
        GhostRecording ghost = refreshSystem.getCachedGhost(robot, now);
        World world = refreshSystem.getCachedWorld(robot, now);
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
        updatePreviousPosition(robot, sample.x(), sample.y(), sample.z());
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

    private void updatePreviousPosition(RobotState robot, double x, double y, double z) {
        robot.setPreviousPosition(x, y, z);
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
        AscendMap map = refreshSystem.getCachedMap(robot, now);
        if (map == null) {
            return;
        }
        UUID ownerId = robot.getOwnerId();
        GhostRecording ghost = refreshSystem.getCachedGhost(robot, now);
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
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        double multiplierGainBonus = 1.0;
        double evolutionPowerBonus = 3.0;
        double baseMultiplierBonus = 0.0;
        if (plugin != null && plugin.getSummitManager() != null) {
            multiplierGainBonus = plugin.getSummitManager().getMultiplierGainBonus(ownerId);
            evolutionPowerBonus = plugin.getSummitManager().getEvolutionPowerBonus(ownerId);
            baseMultiplierBonus = plugin.getSummitManager().getBaseMultiplierBonus(ownerId);
        }
        BigNumber multiplierIncrement = AscendConstants.getRunnerMultiplierIncrement(stars, multiplierGainBonus, evolutionPowerBonus, baseMultiplierBonus);

        BigNumber totalMultiplierBonus = multiplierIncrement.multiply(BigNumber.fromLong(completions));

        // Calculate payout BEFORE adding multiplier (use current multiplier, not the new one)
        BigNumber payoutPerRun = playerStore.getCompletionPayout(ownerId, maps, AscendConstants.MULTIPLIER_SLOTS, mapId, BigNumber.ZERO);

        BigNumber totalPayout = payoutPerRun.multiply(BigNumber.fromLong(completions));

        // Use atomic operations to prevent race conditions
        if (!playerStore.atomicAddVolt(ownerId, totalPayout)) {
            LOGGER.atWarning().log("Failed to add runner volt for " + ownerId + " on map " + mapId);
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
        World world = refreshSystem.getCachedWorld(robot, now);
        if (entityRef != null && entityRef.isValid() && world != null) {
            queueTeleport(teleportsByWorld, world, robot, entityRef,
                    map.getStartX(), map.getStartY(), map.getStartZ(), map.getStartRotY());
            robot.clearPreviousPosition();
        }
    }

    /**
     * Calculate speed multiplier for a runner (public for passive earnings)
     */
    public static double calculateSpeedMultiplier(AscendMap map, int speedLevel, UUID ownerId) {
        // Base speed multiplier from upgrades + additive skill tree bonuses
        double speedMultiplier = 1.0 + (speedLevel * AscendConstants.getMapSpeedMultiplier(map.getDisplayOrder()));

        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            // Summit runner speed is a multiplier (×1.0 at level 0, ×1.45 at level 1, etc.)
            SummitManager summitManager = plugin.getSummitManager();
            if (summitManager != null) {
                speedMultiplier *= summitManager.getRunnerSpeedBonus(ownerId);
            }

            // Skill tree: Runner Speed Boost (×1.1 global runner speed)
            AscensionManager ascensionManager = plugin.getAscensionManager();
            if (ascensionManager != null) {
                if (ascensionManager.hasRunnerSpeedBoost(ownerId)) {
                    speedMultiplier *= 1.1;
                }
                // Skill tree: Runner Speed II (×1.2 global runner speed)
                if (ascensionManager.hasRunnerSpeedBoost2(ownerId)) {
                    speedMultiplier *= 1.2;
                }
                // Skill tree: Runner Speed III (×1.3 global runner speed)
                if (ascensionManager.hasRunnerSpeedBoost3(ownerId)) {
                    speedMultiplier *= 1.3;
                }
                // Skill tree: Runner Speed IV (×1.5 global runner speed)
                if (ascensionManager.hasRunnerSpeedBoost4(ownerId)) {
                    speedMultiplier *= 1.5;
                }
                // Skill tree: Runner Speed V (×2.0 global runner speed)
                if (ascensionManager.hasRunnerSpeedBoost5(ownerId)) {
                    speedMultiplier *= 2.0;
                }
            }

            // Momentum: temporary speed boost from manual run (per-map)
            // Base: x2.0, with Momentum Surge skill: x2.5
            AscendPlayerStore ps = plugin.getPlayerStore();
            if (ps != null) {
                AscendPlayerProgress progress = ps.getPlayer(ownerId);
                if (progress != null) {
                    AscendPlayerProgress.MapProgress mapProgress = progress.getMapProgress().get(map.getId());
                    if (mapProgress != null && mapProgress.isMomentumActive()) {
                        double momentumMultiplier;
                        if (ascensionManager != null && ascensionManager.hasMomentumMastery(ownerId)) {
                            momentumMultiplier = AscendConstants.MOMENTUM_MASTERY_MULTIPLIER;
                        } else if (ascensionManager != null && ascensionManager.hasMomentumSurge(ownerId)) {
                            momentumMultiplier = AscendConstants.MOMENTUM_SURGE_MULTIPLIER;
                        } else {
                            momentumMultiplier = AscendConstants.MOMENTUM_SPEED_MULTIPLIER;
                        }
                        speedMultiplier *= momentumMultiplier;
                    }

                }
            }

            // Mine cross-progression bonus
            MineBonusCalculator mineBonusCalc = plugin.getMineBonusCalculator();
            if (mineBonusCalc != null) {
                MinePlayerStore mps = plugin.getMinePlayerStore();
                if (mps != null) {
                    MinePlayerProgress mineProgress = mps.getPlayer(ownerId);
                    if (mineProgress != null) {
                        speedMultiplier *= mineBonusCalc.getRunnerSpeedMultiplier(mineProgress);
                    }
                }
            }
        }

        return speedMultiplier;
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
        double speedMultiplier = calculateSpeedMultiplier(map, speedLevel, ownerId);

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
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return false;
        }
        PlayerRef playerRef = plugin.getPlayerRef(playerId);
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
        World world = store.getExternalData().getWorld();
        return ModeGate.isAscendWorld(world);
    }

    /**
     * Check if a player is close enough to a map's spawn point that the chunk should be loaded.
     * This is used to determine if we can safely respawn a robot entity.
     * If the player is far away, the chunk might still be unloaded and the entity might
     * reappear when it reloads - so we don't want to spawn a duplicate.
     */
    private static final double CHUNK_LOAD_DISTANCE = 128.0; // Chunks typically load within render distance

    boolean isPlayerNearMapSpawn(UUID playerId, AscendMap map) {
        if (playerId == null || map == null) {
            return false;
        }
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return false;
        }
        PlayerRef playerRef = plugin.getPlayerRef(playerId);
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

    private record PendingTeleport(RobotState robot, Ref<EntityStore> entityRef,
                                   double x, double y, double z, float yaw) {}

    // Package-private getters for extracted classes

    AscendMapStore getMapStore() { return mapStore; }
    AscendPlayerStore getPlayerStore() { return playerStore; }
    GhostStore getGhostStore() { return ghostStore; }
    Map<String, RobotState> getRobots() { return robots; }
    Set<UUID> getOnlinePlayers() { return onlinePlayers; }
    Set<UUID> getDirtyPlayers() { return dirtyPlayers; }
    OrphanedEntityCleanup getOrphanCleanup() { return orphanCleanup; }
    RobotSpawner getSpawner() { return spawner; }

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
        if (orphanCleanup.isPendingRemoval(entityUuid)) {
            return true;
        }
        for (RobotState state : robots.values()) {
            if (entityUuid.equals(state.getEntityUuid())) {
                return true;
            }
        }
        return false;
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
}
