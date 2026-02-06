package io.hyvexa.ascend.robot;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

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

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final GhostStore ghostStore;
    private final Map<String, RobotState> robots = new ConcurrentHashMap<>();
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> orphanedRunnerUuids = ConcurrentHashMap.newKeySet();
    // Pending removals: entityUuid -> entityRef (queued during ECS tick, processed outside)
    private final Map<UUID, Ref<EntityStore>> pendingRemovals = new ConcurrentHashMap<>();
    private ScheduledFuture<?> tickTask;
    private long lastRefreshMs;
    private volatile NPCPlugin npcPlugin;
    private volatile boolean cleanupPending = false;

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
        float yaw = sample.getYaw();

        // Teleport NPC to interpolated position with recorded rotation
        world.execute(() -> teleportNpcWithRecordedRotation(entityRef, world, targetPos, yaw));

        // Update previous position for next tick
        robot.setPreviousPosition(targetPos);
    }

    private void teleportNpcWithRecordedRotation(Ref<EntityStore> entityRef, World world,
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
            // Silently ignore teleport errors
        }
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
            multiplierGainBonus = plugin.getSummitManager().getMultiplierGainBonus(ownerId).doubleValue();
            evolutionPowerBonus = plugin.getSummitManager().getEvolutionPowerBonus(ownerId).doubleValue();
        }
        BigDecimal multiplierIncrement = AscendConstants.getRunnerMultiplierIncrement(stars, multiplierGainBonus, evolutionPowerBonus);

        // Apply double lap skill if available
        if (plugin != null && plugin.getAscensionManager() != null) {
            if (plugin.getAscensionManager().hasDoubleLap(ownerId)) {
                completions *= 2; // Double completions per cycle
            }
        }

        MathContext ctx = new MathContext(30, RoundingMode.HALF_UP);
        BigDecimal totalMultiplierBonus = multiplierIncrement.multiply(BigDecimal.valueOf(completions), ctx);

        // Calculate payout BEFORE adding multiplier (use current multiplier, not the new one)
        List<AscendMap> maps = mapStore.listMapsSorted();
        BigDecimal payoutPerRun = playerStore.getCompletionPayout(ownerId, maps, AscendConstants.MULTIPLIER_SLOTS, mapId, BigDecimal.ZERO);

        BigDecimal totalPayout = payoutPerRun.multiply(BigDecimal.valueOf(completions), ctx)
                                             .setScale(2, RoundingMode.HALF_UP);

        // Use atomic operations to prevent race conditions
        if (!playerStore.atomicAddCoins(ownerId, totalPayout)) {
            LOGGER.atWarning().log("Failed to add runner coins for " + ownerId + " on map " + mapId);
        }
        if (!playerStore.atomicAddTotalCoinsEarned(ownerId, totalPayout)) {
            LOGGER.atWarning().log("Failed to add total coins earned for " + ownerId);
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
                    Vector3d startPos = new Vector3d(map.getStartX(), map.getStartY(), map.getStartZ());
                    double[] startPosArr = {map.getStartX(), map.getStartY(), map.getStartZ()};
                    world.execute(() -> teleportNpcWithRecordedRotation(entityRef, world, startPosArr, map.getStartRotY()));
                    robot.setPreviousPosition(null);  // Reset for new run
                }
            }
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
            SummitManager summitManager = plugin.getSummitManager();
            AscensionManager ascensionManager = plugin.getAscensionManager();

            // Ascension skill tree bonuses are additive (+10%, +100%)
            if (ascensionManager != null) {
                speedMultiplier += ascensionManager.getRunnerSpeedBonus(ownerId);
            }

            // Summit runner speed is a multiplier (×1.0 at level 0, ×1.45 at level 1, etc.)
            if (summitManager != null) {
                speedMultiplier *= summitManager.getRunnerSpeedBonus(ownerId).doubleValue();
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
     * This is more reliable than tracking join/leave events because it checks
     * the actual world the player is in, handling world transitions correctly.
     */
    private boolean isPlayerInAscendWorld(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null || !playerId.equals(playerRef.getUuid())) {
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
            if (world == null || world.getName() == null) {
                continue;
            }
            return ASCEND_WORLD_NAME.equalsIgnoreCase(world.getName());
        }
        return false;
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
        double mapX = map.getStartX();
        double mapY = map.getStartY();
        double mapZ = map.getStartZ();

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null || !playerId.equals(playerRef.getUuid())) {
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
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }
            Vector3d pos = transform.getPosition();
            double px = pos.getX();
            double py = pos.getY();
            double pz = pos.getZ();
            double distSq = (px - mapX) * (px - mapX) + (py - mapY) * (py - mapY) + (pz - mapZ) * (pz - mapZ);
            return distSq <= CHUNK_LOAD_DISTANCE * CHUNK_LOAD_DISTANCE;
        }
        return false;
    }

    // ========================================
    // Runner Cleanup (Server Restart Handling)
    // ========================================

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
}
