package io.hyvexa.ascend.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.ghost.GhostRecording;
import io.hyvexa.ascend.ghost.GhostSample;
import io.hyvexa.ascend.ghost.GhostStore;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.visibility.EntityVisibilityManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class RobotManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RUNNER_UUIDS_FILE = "runner_uuids.txt";
    private static final long AUTO_UPGRADE_INTERVAL_MS = 400L;
    private static final long TELEPORT_WARNING_THROTTLE_MS = 10_000L;

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final GhostStore ghostStore;
    private final Map<String, RobotState> robots = new ConcurrentHashMap<>();
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> orphanedRunnerUuids = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> teleportWarningByRobot = new ConcurrentHashMap<>();
    // Pending removals: entityUuid -> entityRef (queued during ECS tick, processed outside)
    private final Map<UUID, Ref<EntityStore>> pendingRemovals = new ConcurrentHashMap<>();
    private ScheduledFuture<?> tickTask;
    private long lastRefreshMs;
    private volatile NPCPlugin npcPlugin;
    private volatile boolean cleanupPending = false;
    private long lastAutoUpgradeMs = 0;

    public RobotManager(AscendMapStore mapStore, AscendPlayerStore playerStore, GhostStore ghostStore) {
        this.mapStore = mapStore;
        this.playerStore = playerStore;
        this.ghostStore = ghostStore;
    }

    public void start() {
        // Load orphaned runner UUIDs from previous shutdown for cleanup
        loadOrphanedRunnerUuids();

        try {
            npcPlugin = NPCPlugin.get();
        } catch (Exception e) {
            LOGGER.atWarning().log("NPCPlugin not available, robots will be invisible: " + e.getMessage());
            npcPlugin = null;
        }

        // Always register cleanup system - it detects orphans by checking
        // for Frozen+Invulnerable entities not in active robots list
        cleanupPending = true;
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
        saveRunnerUuidsForCleanup();
        despawnAllRobots();
        onlinePlayers.clear();
    }

    public void onPlayerJoin(UUID playerId) {
        if (playerId != null) {
            onlinePlayers.add(playerId);
            applyRunnerVisibility(playerId);
        }
    }

    public void onPlayerLeave(UUID playerId) {
        if (playerId != null) {
            onlinePlayers.remove(playerId);
            // Despawn all robots for this player
            despawnRobotsForPlayer(playerId);
        }
    }

    private void despawnRobotsForPlayer(UUID playerId) {
        for (String key : List.copyOf(robots.keySet())) {
            if (key.startsWith(playerId.toString() + ":")) {
                RobotState removed = robots.remove(key);
                if (removed != null) {
                    despawnNpcForRobot(removed);
                }
            }
        }
    }

    public void spawnRobot(UUID ownerId, String mapId) {
        String key = robotKey(ownerId, mapId);
        RobotState state = new RobotState(ownerId, mapId);
        if (robots.putIfAbsent(key, state) != null) {
            return; // Already existed
        }

        // Spawn NPC entity if NPCPlugin is available
        if (npcPlugin != null && mapStore != null) {
            AscendMap map = mapStore.getMap(mapId);
            if (map != null) {
                spawnNpcForRobot(state, map);
            }
        }
    }

    private void spawnNpcForRobot(RobotState state, AscendMap map) {
        if (npcPlugin == null || map == null) {
            return;
        }
        // Prevent duplicate spawns
        if (state.isSpawning()) {
            return;
        }
        // Check if we already have a valid entity
        Ref<EntityStore> existingRef = state.getEntityRef();
        if (existingRef != null && existingRef.isValid()) {
            return;
        }
        // HARD CAP: If entityUuid is set, an entity may still exist in world
        // (e.g., in unloaded chunk). Don't spawn another one.
        UUID existingUuid = state.getEntityUuid();
        if (existingUuid != null) {
            return;
        }
        state.setSpawning(true);
        String worldName = map.getWorld();
        if (worldName == null || worldName.isEmpty()) {
            state.setSpawning(false);
            return;
        }
        World world = Universe.get().getWorld(worldName);
        if (world == null) {
            state.setSpawning(false);
            return;
        }

        // Must run on World thread for entity operations
        world.execute(() -> spawnNpcOnWorldThread(state, map, world));
    }

    private void spawnNpcOnWorldThread(RobotState state, AscendMap map, World world) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store == null) {
                return;
            }
            Vector3d position = new Vector3d(map.getStartX(), map.getStartY(), map.getStartZ());
            Vector3f rotation = new Vector3f(map.getStartRotX(), map.getStartRotY(), map.getStartRotZ());
            String displayName = "Runner";

            String npcRoleName = AscendConstants.getRunnerEntityType(state.getStars());
            Object result = npcPlugin.spawnNPC(store, npcRoleName, displayName, position, rotation);
            if (result != null) {
                Ref<EntityStore> entityRef = extractEntityRef(result);
                if (entityRef != null) {
                    state.setEntityRef(entityRef);
                    // Extract and store entity UUID for visibility filtering
                    try {
                        UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
                        if (uuidComponent != null) {
                            UUID entityUuid = uuidComponent.getUuid();
                            state.setEntityUuid(entityUuid);

                            // Hide from players currently running on this map
                            hideFromActiveRunners(state.getMapId(), entityUuid);
                            // Hide from players with "hide other runners" setting
                            hideFromViewersWithSetting(state.getOwnerId(), entityUuid);
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().log("Failed to get NPC UUID: " + e.getMessage());
                    }
                    // Make NPC invulnerable so players can't kill it
                    try {
                        store.addComponent(entityRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
                    } catch (Exception e) {
                        LOGGER.atWarning().log("Failed to make NPC invulnerable: " + e.getMessage());
                    }
                    // Freeze NPC to disable AI movement (we control it via teleport)
                    try {
                        store.addComponent(entityRef, Frozen.getComponentType(), Frozen.get());
                    } catch (Exception e) {
                        LOGGER.atWarning().log("Failed to freeze NPC: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to spawn NPC: " + e.getMessage());
        } finally {
            state.setSpawning(false);
        }
    }

    @SuppressWarnings("unchecked")
    private Ref<EntityStore> extractEntityRef(Object pairResult) {
        if (pairResult == null) {
            return null;
        }
        try {
            // Try common Pair accessor methods
            for (String methodName : List.of("getFirst", "getLeft", "getKey", "first", "left")) {
                try {
                    java.lang.reflect.Method method = pairResult.getClass().getMethod(methodName);
                    Object value = method.invoke(pairResult);
                    if (value instanceof Ref<?> ref) {
                        return (Ref<EntityStore>) ref;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Try next method
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to extract entity ref from NPC result: " + e.getMessage());
        }
        return null;
    }

    public void despawnRobot(UUID ownerId, String mapId) {
        String key = robotKey(ownerId, mapId);
        RobotState state = robots.remove(key);
        if (state != null) {
            despawnNpcForRobot(state);
        }
    }

    private void despawnNpcForRobot(RobotState state) {
        if (state == null) {
            return;
        }
        Ref<EntityStore> entityRef = state.getEntityRef();
        if (entityRef == null || !entityRef.isValid()) {
            state.setEntityRef(null);
            // Note: We intentionally do NOT clear entityUuid here.
            // If isValid() is false due to chunk unload, the entity still exists
            // and will reappear when chunks reload. Only clear UUID on successful despawn.
            return;
        }
        // Get world from map to run on world thread
        AscendMap map = mapStore != null ? mapStore.getMap(state.getMapId()) : null;
        if (map != null && map.getWorld() != null) {
            World world = Universe.get().getWorld(map.getWorld());
            if (world != null) {
                world.execute(() -> despawnNpcOnWorldThread(state, entityRef));
                return;
            }
        }
        // Fallback: try without world thread (may fail)
        despawnNpcOnWorldThread(state, entityRef);
    }

    private void despawnNpcOnWorldThread(RobotState state, Ref<EntityStore> entityRef) {
        boolean despawnSuccess = false;
        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store != null) {
                store.removeEntity(entityRef, RemoveReason.REMOVE);
                despawnSuccess = true;
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to despawn NPC: " + e.getMessage());
        }
        state.setEntityRef(null);
        // Only clear entityUuid if despawn was successful.
        // If despawn failed, the entity may still exist (e.g., in unloaded chunk)
        // and we don't want to spawn a duplicate when it reloads.
        if (despawnSuccess) {
            state.setEntityUuid(null);
        }
    }

    public void despawnAllRobots() {
        for (RobotState state : robots.values()) {
            despawnNpcForRobot(state);
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

    public void respawnRobot(UUID ownerId, String mapId, int newStars) {
        String key = robotKey(ownerId, mapId);
        RobotState state = robots.get(key);
        if (state == null) {
            return;
        }
        // Update stars in the state
        state.setStars(newStars);
        state.setSpeedLevel(0);
        // Despawn old NPC and spawn new one with updated entity type
        despawnNpcForRobot(state);
        if (npcPlugin != null && mapStore != null) {
            AscendMap map = mapStore.getMap(mapId);
            if (map != null) {
                spawnNpcForRobot(state, map);
            }
        }
    }

    private void tick() {
        try {
            long now = System.currentTimeMillis();
            // Process any pending orphan removals first (queued from ECS tick)
            processPendingRemovals();
            refreshRobots(now);
            for (RobotState robot : robots.values()) {
                tickRobot(robot, now);
                tickRobotMovement(robot, now);
            }
            // Auto-upgrade runners for players with the skill (throttled)
            if (now - lastAutoUpgradeMs >= AUTO_UPGRADE_INTERVAL_MS) {
                lastAutoUpgradeMs = now;
                performAutoRunnerUpgrades();
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Error in robot tick: " + e.getMessage());
        }
    }

    private void tickRobotMovement(RobotState robot, long now) {
        Ref<EntityStore> entityRef = robot.getEntityRef();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        // Get ghost recording
        GhostRecording ghost = ghostStore.getRecording(robot.getOwnerId(), robot.getMapId());
        if (ghost == null) {
            return; // No ghost = no movement (player hasn't completed manually)
        }

        AscendMap map = mapStore.getMap(robot.getMapId());
        if (map == null) {
            return;
        }

        String worldName = map.getWorld();
        if (worldName == null || worldName.isEmpty()) {
            return;
        }

        World world = Universe.get().getWorld(worldName);
        if (world == null) {
            return;
        }

        // Calculate progress through the run with speed multiplier
        long intervalMs = computeCompletionIntervalMs(map, robot.getSpeedLevel(), robot.getOwnerId());
        if (intervalMs <= 0L) {
            return;
        }

        long lastCompletionMs = robot.getLastCompletionMs();
        if (lastCompletionMs <= 0L) {
            return;
        }

        long elapsed = now - lastCompletionMs;
        double progress = Math.min(1.0, (double) elapsed / (double) intervalMs);

        // Calculate speed multiplier for time compression
        double baseTime = ghost.getCompletionTimeMs();
        double speedMultiplier = (double) baseTime / intervalMs;

        // Interpolate ghost position at current progress
        GhostSample sample = ghost.interpolateAt(progress, speedMultiplier);
        double[] targetPos = sample.toPositionArray();
        float yaw = sample.yaw();

        // Teleport NPC to interpolated position with recorded rotation
        world.execute(() -> teleportNpcWithRecordedRotation(robot, entityRef, world, targetPos, yaw));

        // Update previous position for next tick
        robot.setPreviousPosition(targetPos);
    }

    private void teleportNpcWithRecordedRotation(RobotState robot, Ref<EntityStore> entityRef, World world,
                                                  double[] targetPos, float yaw) {
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store == null) {
                return;
            }

            Vector3d targetVec = new Vector3d(targetPos[0], targetPos[1], targetPos[2]);
            Vector3f rotation = new Vector3f(0, yaw, 0);
            store.addComponent(entityRef, Teleport.getComponentType(),
                new Teleport(world, targetVec, rotation));
        } catch (Exception e) {
            logTeleportWarning(robot, world, targetPos, yaw, e);
        }
    }

    private void logTeleportWarning(RobotState robot, World world, double[] targetPos, float yaw, Exception error) {
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
        LOGGER.at(Level.WARNING).withCause(error).log(
                "Runner teleport failed owner=" + ownerId
                        + " map=" + mapId
                        + " world=" + worldName
                        + " target=" + formatPosition(targetPos)
                        + " yaw=" + yaw
        );
    }

    private String formatPosition(double[] position) {
        if (position == null || position.length < 3) {
            return "n/a";
        }
        return String.format("(%.2f, %.2f, %.2f)", position[0], position[1], position[2]);
    }

    private void refreshRobots(long now) {
        if (playerStore == null || mapStore == null) {
            return;
        }
        if (now - lastRefreshMs < AscendConstants.RUNNER_REFRESH_INTERVAL_MS) {
            return;
        }
        lastRefreshMs = now;
        Map<UUID, AscendPlayerProgress> players = playerStore.getPlayersSnapshot();
        Set<String> activeKeys = new HashSet<>();
        for (Map.Entry<UUID, AscendPlayerProgress> entry : players.entrySet()) {
            UUID playerId = entry.getKey();
            AscendPlayerProgress progress = entry.getValue();
            if (playerId == null || progress == null) {
                continue;
            }
            // Only spawn robots for players currently in the Ascend world.
            // This handles world transitions correctly (e.g., player went to Hub).
            if (!isPlayerInAscendWorld(playerId)) {
                continue;
            }
            for (Map.Entry<String, AscendPlayerProgress.MapProgress> mapEntry : progress.getMapProgress().entrySet()) {
                String mapId = mapEntry.getKey();
                AscendPlayerProgress.MapProgress mapProgress = mapEntry.getValue();
                if (mapId == null || mapProgress == null) {
                    continue;
                }
                if (!mapProgress.hasRobot()) {
                    continue;
                }
                AscendMap map = mapStore.getMap(mapId);
                if (map == null) {
                    continue;
                }
                String key = robotKey(playerId, mapId);
                RobotState existing = robots.get(key);
                if (existing == null) {
                    RobotState state = new RobotState(playerId, mapId);
                    state.setSpeedLevel(mapProgress.getRobotSpeedLevel());
                    state.setStars(mapProgress.getRobotStars());
                    state.setLastCompletionMs(now);
                    robots.put(key, state);
                    // Spawn NPC for newly created robot
                    if (npcPlugin != null) {
                        spawnNpcForRobot(state, map);
                    }
                } else {
                    existing.setSpeedLevel(mapProgress.getRobotSpeedLevel());
                    existing.setStars(mapProgress.getRobotStars());
                    if (existing.getLastCompletionMs() <= 0L) {
                        existing.setLastCompletionMs(now);
                    }
                    // Skip if already spawning to prevent duplicates
                    if (existing.isSpawning()) {
                        continue;
                    }
                    Ref<EntityStore> existingRef = existing.getEntityRef();
                    boolean refIsValid = existingRef != null && existingRef.isValid();
                    boolean hasExistingEntity = existing.getEntityUuid() != null;

                    if (refIsValid) {
                        // Entity is healthy, clear any invalid timestamp
                        existing.clearInvalid();
                    } else if (hasExistingEntity) {
                        // Entity was spawned but ref is now invalid (chunk unloaded?)
                        existing.markInvalid(now);
                        // Only force respawn if:
                        // 1. Invalid for long enough (chunk had time to reload)
                        // 2. Player is close to spawn point (chunk should be loaded)
                        // This prevents respawning while the entity is just in an unloaded chunk
                        if (existing.isInvalidForTooLong(now, AscendConstants.RUNNER_INVALID_RECOVERY_MS)) {
                            if (isPlayerNearMapSpawn(playerId, map)) {
                                // Mark old entity for orphan cleanup before spawning new one
                                UUID oldUuid = existing.getEntityUuid();
                                if (oldUuid != null) {
                                    orphanedRunnerUuids.add(oldUuid);
                                    cleanupPending = true;
                                }
                                existing.setEntityRef(null);
                                existing.setEntityUuid(null);
                                existing.clearInvalid();
                                if (npcPlugin != null) {
                                    spawnNpcForRobot(existing, map);
                                }
                            }
                            // If player is far, don't respawn - entity might still be in unloaded chunk
                        }
                    } else if (npcPlugin != null && existingRef == null) {
                        // Never spawned, spawn now
                        spawnNpcForRobot(existing, map);
                    }
                }
                activeKeys.add(key);
            }
        }
        if (activeKeys.isEmpty()) {
            despawnAllRobots();
            return;
        }
        for (String key : List.copyOf(robots.keySet())) {
            if (!activeKeys.contains(key)) {
                RobotState removed = robots.remove(key);
                if (removed != null) {
                    UUID entityUuid = removed.getEntityUuid();
                    despawnNpcForRobot(removed);
                    // If entity UUID still exists after despawn attempt, it may have
                    // failed (chunk unloaded, etc). Mark for orphan cleanup.
                    if (entityUuid != null && removed.getEntityUuid() != null) {
                        orphanedRunnerUuids.add(entityUuid);
                        cleanupPending = true;
                    }
                }
            }
        }
    }

    private void tickRobot(RobotState robot, long now) {
        if (robot.isWaiting()) {
            return;
        }
        AscendMap map = mapStore.getMap(robot.getMapId());
        if (map == null) {
            return;
        }
        UUID ownerId = robot.getOwnerId();
        int speedLevel = robot.getSpeedLevel();
        long intervalMs = computeCompletionIntervalMs(map, speedLevel, ownerId);
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
        if (plugin != null && plugin.getSummitManager() != null) {
            multiplierGainBonus = plugin.getSummitManager().getMultiplierGainBonus(ownerId);
            evolutionPowerBonus = plugin.getSummitManager().getEvolutionPowerBonus(ownerId);
        }
        BigNumber multiplierIncrement = AscendConstants.getRunnerMultiplierIncrement(stars, multiplierGainBonus, evolutionPowerBonus);

        BigNumber totalMultiplierBonus = multiplierIncrement.multiply(BigNumber.fromLong(completions));

        // Calculate payout BEFORE adding multiplier (use current multiplier, not the new one)
        List<AscendMap> maps = mapStore.listMapsSorted();
        BigNumber payoutPerRun = playerStore.getCompletionPayout(ownerId, maps, AscendConstants.MULTIPLIER_SLOTS, mapId, BigNumber.ZERO);

        BigNumber totalPayout = payoutPerRun.multiply(BigNumber.fromLong(completions));

        // Use atomic operations to prevent race conditions
        if (!playerStore.atomicAddVexa(ownerId, totalPayout)) {
            LOGGER.atWarning().log("Failed to add runner vexa for " + ownerId + " on map " + mapId);
        }
        if (!playerStore.atomicAddTotalVexaEarned(ownerId, totalPayout)) {
            LOGGER.atWarning().log("Failed to add total vexa earned for " + ownerId);
        }
        if (!playerStore.atomicAddMapMultiplier(ownerId, mapId, totalMultiplierBonus)) {
            LOGGER.atWarning().log("Failed to add map multiplier for " + ownerId + " on map " + mapId);
        }

        robot.setLastCompletionMs(lastCompletionMs + (intervalMs * completions));
        robot.addRunsCompleted(completions);

        // Teleport NPC back to start after completion and reset previous position
        Ref<EntityStore> entityRef = robot.getEntityRef();
        if (entityRef != null && entityRef.isValid()) {
            String worldName = map.getWorld();
            if (worldName != null && !worldName.isEmpty()) {
                World world = Universe.get().getWorld(worldName);
                if (world != null) {
                    double[] startPosArr = {map.getStartX(), map.getStartY(), map.getStartZ()};
                    world.execute(() -> teleportNpcWithRecordedRotation(robot, entityRef, world, startPosArr, map.getStartRotY()));
                    robot.setPreviousPosition(null);  // Reset for new run
                }
            }
        }
    }

    // Auto Runner Upgrades (Skill Tree)

    private void performAutoRunnerUpgrades() {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) return;
        AscensionManager ascensionManager = plugin.getAscensionManager();
        if (ascensionManager == null) return;

        for (UUID playerId : onlinePlayers) {
            if (!ascensionManager.hasAutoRunners(playerId)) continue;
            autoUpgradeRunners(playerId);
        }
    }

    private void autoUpgradeRunners(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) return;
        if (!progress.isAutoUpgradeEnabled()) return;

        List<AscendMap> maps = mapStore.listMapsSorted();

        // First priority: buy runners on unlocked maps that have a ghost recording (free)
        for (AscendMap map : maps) {
            AscendPlayerProgress.MapProgress mp = progress.getMapProgress().get(map.getId());
            if (mp == null || !mp.isUnlocked() || mp.hasRobot()) continue;

            GhostRecording ghost = ghostStore.getRecording(playerId, map.getId());
            if (ghost == null) continue;

            playerStore.setHasRobot(playerId, map.getId(), true);
            return; // One action per call for smooth visual
        }

        // Auto-evolve eligible maps (free, all at once — each map independent of others)
        // Runs before speed upgrades so a map at max level evolves immediately,
        // even while other maps still have affordable speed upgrades.
        if (progress.isAutoEvolutionEnabled()) {
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            AscensionManager am = plugin != null ? plugin.getAscensionManager() : null;
            if (am != null && am.hasAutoEvolution(playerId)) {
                for (AscendMap map : maps) {
                    AscendPlayerProgress.MapProgress mp = progress.getMapProgress().get(map.getId());
                    if (mp == null || !mp.hasRobot()) continue;
                    if (mp.getRobotSpeedLevel() >= AscendConstants.MAX_SPEED_LEVEL
                            && mp.getRobotStars() < AscendConstants.MAX_ROBOT_STARS) {
                        int newStars = playerStore.evolveRobot(playerId, map.getId());
                        respawnRobot(playerId, map.getId(), newStars);
                    }
                }
            }
        }

        // Speed upgrade: find cheapest across all maps (one per call for smooth visual)
        BigNumber vexa = progress.getVexa();
        String cheapestMapId = null;
        BigNumber cheapestCost = null;

        for (AscendMap map : maps) {
            AscendPlayerProgress.MapProgress mp = progress.getMapProgress().get(map.getId());
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

        if (cheapestMapId != null && cheapestCost != null && vexa.gte(cheapestCost)) {
            if (!playerStore.atomicSpendVexa(playerId, cheapestCost)) return;
            playerStore.incrementRobotSpeedLevel(playerId, cheapestMapId);
            playerStore.checkAndUnlockEligibleMaps(playerId, mapStore);
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

            // Skill tree: Runner Speed Boost (×1.5 global runner speed)
            AscensionManager ascensionManager = plugin.getAscensionManager();
            if (ascensionManager != null && ascensionManager.hasRunnerSpeedBoost(ownerId)) {
                speedMultiplier *= 1.5;
            }

            // Momentum: temporary ×2.0 speed boost from manual run (per-map)
            AscendPlayerStore ps = plugin.getPlayerStore();
            if (ps != null) {
                AscendPlayerProgress progress = ps.getPlayer(ownerId);
                if (progress != null) {
                    AscendPlayerProgress.MapProgress mapProgress = progress.getMapProgress().get(map.getId());
                    if (mapProgress != null && mapProgress.isMomentumActive()) {
                        speedMultiplier *= AscendConstants.MOMENTUM_SPEED_MULTIPLIER;
                    }
                }
            }
        }

        return speedMultiplier;
    }

    private long computeCompletionIntervalMs(AscendMap map, int speedLevel, UUID ownerId) {
        if (map == null) {
            return -1L;
        }

        // Use player's PB time as base (from ghost recording)
        GhostRecording ghost = ghostStore.getRecording(ownerId, map.getId());
        if (ghost == null) {
            return -1L;
        }
        long base = ghost.getCompletionTimeMs();
        if (base <= 0L) {
            return -1L;
        }

        // Calculate speed multiplier
        double speedMultiplier = calculateSpeedMultiplier(map, speedLevel, ownerId);

        long interval = (long) (base / speedMultiplier);
        return Math.max(1L, interval);
    }

    private String robotKey(UUID ownerId, String mapId) {
        return ownerId.toString() + ":" + mapId;
    }

    /**
     * Hide a newly spawned runner from all players currently running on the same map.
     */
    private void hideFromActiveRunners(String mapId, UUID runnerUuid) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        AscendRunTracker runTracker = plugin.getRunTracker();
        if (runTracker == null) {
            return;
        }
        EntityVisibilityManager visibilityManager = EntityVisibilityManager.get();
        // Iterate over all online players in the Ascend world
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) {
                continue;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId == null || !isPlayerInAscendWorld(playerId)) {
                continue;
            }
            String activeMapId = runTracker.getActiveMapId(playerId);
            if (mapId.equals(activeMapId)) {
                visibilityManager.hideEntity(playerId, runnerUuid);
            }
        }
    }

    private static final String ASCEND_WORLD_NAME = "Ascend";

    /**
     * Check if a player is currently in the Ascend world.
     * Uses the plugin's PlayerRef cache for O(1) lookup instead of scanning all players.
     */
    private boolean isPlayerInAscendWorld(UUID playerId) {
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
        return world != null && ASCEND_WORLD_NAME.equalsIgnoreCase(world.getName());
    }

    /**
     * Check if a player is close enough to a map's spawn point that the chunk should be loaded.
     * This is used to determine if we can safely respawn a robot entity.
     * If the player is far away, the chunk might still be unloaded and the entity might
     * reappear when it reloads - so we don't want to spawn a duplicate.
     */
    private static final double CHUNK_LOAD_DISTANCE = 128.0; // Chunks typically load within render distance

    private boolean isPlayerNearMapSpawn(UUID playerId, AscendMap map) {
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

    // Runner Cleanup (Server Restart Handling)

    private Path getRunnerUuidsPath() {
        return Path.of("mods", "Parkour", RUNNER_UUIDS_FILE);
    }

    /**
     * Save current runner entity UUIDs to a file.
     * Called at shutdown so we can clean them up on next startup.
     */
    private void saveRunnerUuidsForCleanup() {
        Set<UUID> uuids = new HashSet<>();
        for (RobotState state : robots.values()) {
            UUID entityUuid = state.getEntityUuid();
            if (entityUuid != null) {
                uuids.add(entityUuid);
            }
        }
        if (uuids.isEmpty()) {
            // No runners to save, delete file if exists
            try {
                Files.deleteIfExists(getRunnerUuidsPath());
            } catch (IOException e) {
                // Ignore
            }
            return;
        }
        try {
            Path path = getRunnerUuidsPath();
            Files.createDirectories(path.getParent());
            List<String> lines = uuids.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
            Files.write(path, lines);
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to save runner UUIDs: " + e.getMessage());
        }
    }

    /**
     * Load orphaned runner UUIDs from previous shutdown.
     */
    private void loadOrphanedRunnerUuids() {
        Path path = getRunnerUuidsPath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    UUID uuid = UUID.fromString(trimmed);
                    orphanedRunnerUuids.add(uuid);
                } catch (IllegalArgumentException e) {
                    // Invalid UUID, skip
                }
            }
            // Delete file after loading
            Files.delete(path);
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to load orphaned runner UUIDs: " + e.getMessage());
        }
    }

    /**
     * Register the cleanup system to remove orphaned runners.
     * This is called once at startup if there are UUIDs to clean.
     */
    private void registerCleanupSystem() {
        var registry = EntityStore.REGISTRY;
        if (!registry.hasSystemClass(RunnerCleanupSystem.class)) {
            registry.registerSystem(new RunnerCleanupSystem(this));
        }
    }

    /**
     * Check if a UUID is an orphaned runner that should be removed.
     * Called by RunnerCleanupSystem.
     */
    public boolean isOrphanedRunner(UUID entityUuid) {
        return orphanedRunnerUuids.contains(entityUuid);
    }

    /**
     * Check if a UUID belongs to an active runner managed by this RobotManager.
     * Used by cleanup system to avoid removing valid runners.
     */
    public boolean isActiveRunnerUuid(UUID entityUuid) {
        if (entityUuid == null) {
            return false;
        }
        // Also skip if already pending removal (avoid re-queueing)
        if (pendingRemovals.containsKey(entityUuid)) {
            return true;
        }
        for (RobotState state : robots.values()) {
            if (entityUuid.equals(state.getEntityUuid())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Queue an orphaned runner for deferred removal.
     * Called from RunnerCleanupSystem during ECS tick - actual removal
     * happens in processPendingRemovals() which runs outside the tick.
     */
    public void queueOrphanForRemoval(UUID entityUuid, Ref<EntityStore> entityRef) {
        if (entityUuid == null || entityRef == null) {
            return;
        }
        // Only queue if not already pending
        pendingRemovals.putIfAbsent(entityUuid, entityRef);
    }

    /**
     * Process pending orphan removals. Called from tick() which runs
     * outside ECS processing, so store mutations are safe.
     */
    private void processPendingRemovals() {
        if (pendingRemovals.isEmpty()) {
            return;
        }

        // Copy keys to avoid concurrent modification
        for (UUID entityUuid : List.copyOf(pendingRemovals.keySet())) {
            Ref<EntityStore> ref = pendingRemovals.remove(entityUuid);
            if (ref == null) {
                continue;
            }

            // Get world from ref's store for world-thread execution
            if (!ref.isValid()) {
                // Ref became invalid - entity might already be gone
                markOrphanCleaned(entityUuid);
                continue;
            }

            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                markOrphanCleaned(entityUuid);
                continue;
            }

            World world = store.getExternalData().getWorld();
            if (world == null) {
                // Try direct removal as fallback
                removeOrphanDirect(entityUuid, ref, store);
                continue;
            }

            // Execute removal on world thread
            world.execute(() -> removeOrphanOnWorldThread(entityUuid, ref));
        }
    }

    private void removeOrphanOnWorldThread(UUID entityUuid, Ref<EntityStore> ref) {
        try {
            if (ref == null || !ref.isValid()) {
                markOrphanCleaned(entityUuid);
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                markOrphanCleaned(entityUuid);
                return;
            }
            store.removeEntity(ref, RemoveReason.REMOVE);
            markOrphanCleaned(entityUuid);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to remove orphaned runner " + entityUuid + ": " + e.getMessage());
            // Re-queue for retry on next tick
            if (ref != null && ref.isValid()) {
                pendingRemovals.put(entityUuid, ref);
            } else {
                // Ref is invalid, consider it cleaned
                markOrphanCleaned(entityUuid);
            }
        }
    }

    private void removeOrphanDirect(UUID entityUuid, Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            store.removeEntity(ref, RemoveReason.REMOVE);
            markOrphanCleaned(entityUuid);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to remove orphaned runner " + entityUuid + ": " + e.getMessage());
            markOrphanCleaned(entityUuid); // Don't retry direct removals
        }
    }

    /**
     * Mark an orphaned runner as cleaned up.
     */
    public void markOrphanCleaned(UUID entityUuid) {
        orphanedRunnerUuids.remove(entityUuid);
        pendingRemovals.remove(entityUuid);
        if (orphanedRunnerUuids.isEmpty() && pendingRemovals.isEmpty()) {
            cleanupPending = false;
        }
    }

    /**
     * Check if cleanup is still pending.
     */
    public boolean isCleanupPending() {
        return cleanupPending || !pendingRemovals.isEmpty();
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

    /**
     * After spawning a new runner, hide it from all online viewers who have the "hide other runners" setting ON.
     * Skips the owner of the runner (they should always see their own).
     */
    private void hideFromViewersWithSetting(UUID runnerOwnerId, UUID entityUuid) {
        if (entityUuid == null) {
            return;
        }
        EntityVisibilityManager visibilityManager = EntityVisibilityManager.get();
        for (UUID viewerId : onlinePlayers) {
            if (viewerId.equals(runnerOwnerId)) {
                continue; // Owner always sees their own runners
            }
            if (playerStore.isHideOtherRunners(viewerId)) {
                visibilityManager.hideEntity(viewerId, entityUuid);
            }
        }
    }
}
