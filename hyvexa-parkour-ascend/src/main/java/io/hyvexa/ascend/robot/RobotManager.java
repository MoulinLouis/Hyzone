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
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
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

public class RobotManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double ROBOT_BASE_SPEED = 5.0;  // Blocks per second base speed

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final GhostStore ghostStore;
    private final Map<String, RobotState> robots = new ConcurrentHashMap<>();
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
    private ScheduledFuture<?> tickTask;
    private long lastRefreshMs;
    private volatile NPCPlugin npcPlugin;

    public RobotManager(AscendMapStore mapStore, AscendPlayerStore playerStore, GhostStore ghostStore) {
        this.mapStore = mapStore;
        this.playerStore = playerStore;
        this.ghostStore = ghostStore;
    }

    public void start() {
        try {
            npcPlugin = NPCPlugin.get();
            LOGGER.atInfo().log("NPCPlugin initialized for robot spawning");
            // Log available NPC roles for debugging
            try {
                java.util.List<String> roles = npcPlugin.getRoleTemplateNames(true);
                LOGGER.atInfo().log("[RobotNPC] Available NPC roles: " + roles);
            } catch (Exception e) {
                LOGGER.atWarning().log("[RobotNPC] Could not list NPC roles: " + e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("NPCPlugin not available, robots will be invisible: " + e.getMessage());
            npcPlugin = null;
        }
        tickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
            this::tick,
            AscendConstants.RUNNER_TICK_INTERVAL_MS,
            AscendConstants.RUNNER_TICK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        LOGGER.atInfo().log("RobotManager started");
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
        despawnAllRobots();
        onlinePlayers.clear();
        LOGGER.atInfo().log("RobotManager stopped");
    }

    public void onPlayerJoin(UUID playerId) {
        if (playerId != null) {
            onlinePlayers.add(playerId);
            LOGGER.atInfo().log("[RobotNPC] Player joined: " + playerId);
        }
    }

    public void onPlayerLeave(UUID playerId) {
        if (playerId != null) {
            onlinePlayers.remove(playerId);
            LOGGER.atInfo().log("[RobotNPC] Player left: " + playerId);
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
        if (robots.containsKey(key)) {
            LOGGER.atWarning().log("Robot already exists for " + key);
            return;
        }

        RobotState state = new RobotState(ownerId, mapId);
        robots.put(key, state);

        // Spawn NPC entity if NPCPlugin is available
        if (npcPlugin != null && mapStore != null) {
            AscendMap map = mapStore.getMap(mapId);
            if (map != null) {
                spawnNpcForRobot(state, map);
            }
        }

        LOGGER.atInfo().log("Robot spawned for " + ownerId + " on map " + mapId);
    }

    private void spawnNpcForRobot(RobotState state, AscendMap map) {
        LOGGER.atInfo().log("[RobotNPC] Attempting to spawn NPC for map: " + (map != null ? map.getId() : "null"));
        if (npcPlugin == null) {
            LOGGER.atWarning().log("[RobotNPC] NPCPlugin is null, cannot spawn");
            return;
        }
        if (map == null) {
            LOGGER.atWarning().log("[RobotNPC] Map is null, cannot spawn");
            return;
        }
        // Prevent duplicate spawns
        if (state.isSpawning()) {
            LOGGER.atInfo().log("[RobotNPC] Already spawning for this robot, skipping");
            return;
        }
        // Check if we already have a valid entity
        Ref<EntityStore> existingRef = state.getEntityRef();
        if (existingRef != null && existingRef.isValid()) {
            LOGGER.atInfo().log("[RobotNPC] Robot already has valid NPC entity, skipping spawn");
            return;
        }
        state.setSpawning(true);
        String worldName = map.getWorld();
        if (worldName == null || worldName.isEmpty()) {
            LOGGER.atWarning().log("[RobotNPC] World name is null/empty for map: " + map.getId());
            state.setSpawning(false);
            return;
        }
        LOGGER.atInfo().log("[RobotNPC] Looking for world: " + worldName);
        World world = Universe.get().getWorld(worldName);
        if (world == null) {
            LOGGER.atWarning().log("[RobotNPC] World not found: " + worldName);
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
                LOGGER.atWarning().log("[RobotNPC] Store is null for world: " + map.getWorld());
                return;
            }
            Vector3d position = new Vector3d(map.getStartX(), map.getStartY(), map.getStartZ());
            Vector3f rotation = new Vector3f(map.getStartRotX(), map.getStartRotY(), map.getStartRotZ());
            String displayName = "Runner";

            String npcRoleName = AscendConstants.getRunnerEntityType(state.getStars());
            LOGGER.atInfo().log("[RobotNPC] Calling NPCPlugin.spawnNPC at " + position + " with role " + npcRoleName);
            Object result = npcPlugin.spawnNPC(store, npcRoleName, displayName, position, rotation);
            LOGGER.atInfo().log("[RobotNPC] spawnNPC returned: " + (result != null ? result.getClass().getName() : "null"));
            if (result != null) {
                Ref<EntityStore> entityRef = extractEntityRef(result);
                LOGGER.atInfo().log("[RobotNPC] Extracted entityRef: " + (entityRef != null ? "valid" : "null"));
                if (entityRef != null) {
                    state.setEntityRef(entityRef);
                    // Extract and store entity UUID for visibility filtering
                    try {
                        UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
                        if (uuidComponent != null) {
                            UUID entityUuid = uuidComponent.getUuid();
                            state.setEntityUuid(entityUuid);
                            LOGGER.atInfo().log("[RobotNPC] NPC UUID: " + entityUuid);

                            // Hide from players currently running on this map
                            hideFromActiveRunners(state.getMapId(), entityUuid);
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().log("[RobotNPC] Failed to get NPC UUID: " + e.getMessage());
                    }
                    // Make NPC invulnerable so players can't kill it
                    try {
                        store.addComponent(entityRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
                        LOGGER.atInfo().log("[RobotNPC] NPC made invulnerable");
                    } catch (Exception e) {
                        LOGGER.atWarning().log("[RobotNPC] Failed to make NPC invulnerable: " + e.getMessage());
                    }
                    // Freeze NPC to disable AI movement (we control it via teleport)
                    try {
                        store.addComponent(entityRef, Frozen.getComponentType(), Frozen.get());
                        LOGGER.atInfo().log("[RobotNPC] NPC frozen (AI disabled)");
                    } catch (Exception e) {
                        LOGGER.atWarning().log("[RobotNPC] Failed to freeze NPC: " + e.getMessage());
                    }
                    LOGGER.atInfo().log("[RobotNPC] NPC spawned successfully at " + position);
                }
            } else {
                LOGGER.atWarning().log("[RobotNPC] spawnNPC returned null");
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[RobotNPC] Failed to spawn NPC: " + e.getMessage());
            e.printStackTrace();
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
        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store != null) {
                store.removeEntity(entityRef, RemoveReason.REMOVE);
                LOGGER.atInfo().log("[RobotNPC] NPC despawned for robot");
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[RobotNPC] Failed to despawn NPC: " + e.getMessage());
        }
        state.setEntityRef(null);
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
        LOGGER.atInfo().log("[RobotNPC] Respawned robot for " + ownerId + " on map " + mapId + " with " + newStars + " stars");
    }

    private void tick() {
        try {
            long now = System.currentTimeMillis();
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

    private void teleportNpc(Ref<EntityStore> entityRef, World world, Vector3d targetPos, double[] previousPos) {
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }
        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store == null) {
                return;
            }

            // Calculate yaw based on movement direction
            // We need to extract target coordinates for comparison
            // Since Vector3d doesn't have x(), y(), z() accessors, we pass the raw coordinates
            Vector3f rotation = new Vector3f(0, 0, 0);
            store.addComponent(entityRef, Teleport.getComponentType(), new Teleport(world, targetPos, rotation));
        } catch (Exception e) {
            // Silently ignore teleport errors
        }
    }

    private void teleportNpcWithRotation(Ref<EntityStore> entityRef, World world, double[] targetPos, double[] previousPos) {
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }
        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store == null) {
                return;
            }

            // Calculate yaw based on movement direction
            float yaw = 0;
            if (previousPos != null) {
                double dx = targetPos[0] - previousPos[0];
                double dz = targetPos[2] - previousPos[2];
                // Only update rotation if there's meaningful horizontal movement
                if (dx != 0 || dz != 0) {
                    // atan2(dx, dz) gives angle from Z+ axis, add 180 to face forward
                    yaw = (float) (Math.toDegrees(Math.atan2(dx, dz)) + 180.0);
                }
            }

            Vector3d targetVec = new Vector3d(targetPos[0], targetPos[1], targetPos[2]);
            Vector3f rotation = new Vector3f(0, yaw, 0);
            store.addComponent(entityRef, Teleport.getComponentType(), new Teleport(world, targetVec, rotation));
        } catch (Exception e) {
            // Silently ignore teleport errors
        }
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
            // Only spawn robots for online players
            if (!onlinePlayers.contains(playerId)) {
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
                    LOGGER.atInfo().log("[RobotNPC] Creating new robot for key: " + key);
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
                    // Respawn NPC if entity was destroyed - but despawn old one first
                    // Skip if already spawning to prevent duplicates
                    if (existing.isSpawning()) {
                        continue;
                    }
                    Ref<EntityStore> existingRef = existing.getEntityRef();
                    if (npcPlugin != null && (existingRef == null || !existingRef.isValid())) {
                        // Despawn old NPC first to avoid duplicates
                        if (existingRef != null) {
                            despawnNpcForRobot(existing);
                        }
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
                    despawnNpcForRobot(removed);
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
        double multiplierIncrement = AscendConstants.getRunnerMultiplierIncrement(stars);

        // Apply double lap skill if available
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getAscensionManager() != null) {
            if (plugin.getAscensionManager().hasDoubleLap(ownerId)) {
                completions *= 2; // Double completions per cycle
            }
        }

        double totalMultiplierBonus = completions * multiplierIncrement;

        // Calculate payout BEFORE adding multiplier (use current multiplier, not the new one)
        List<AscendMap> maps = mapStore.listMapsSorted();
        double payoutPerRun = playerStore.getCompletionPayout(ownerId, maps, AscendConstants.MULTIPLIER_SLOTS, mapId, 0.0);

        // Apply Summit coin flow bonus
        double coinFlowBonus = 0.0;
        if (plugin != null && plugin.getSummitManager() != null) {
            coinFlowBonus = plugin.getSummitManager().getCoinFlowBonus(ownerId);
        }
        double totalPayout = payoutPerRun * completions * (1.0 + coinFlowBonus);

        // Add coins first, then increase multiplier
        playerStore.addCoins(ownerId, totalPayout);
        playerStore.addTotalCoinsEarned(ownerId, totalPayout);
        playerStore.addMapMultiplier(ownerId, mapId, totalMultiplierBonus);

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
                    world.execute(() -> teleportNpc(entityRef, world, startPos, null));
                    robot.setPreviousPosition(null);  // Reset for new run
                }
            }
        }
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

        // Base speed multiplier from upgrades
        double speedMultiplier = 1.0 + (speedLevel * AscendConstants.SPEED_UPGRADE_MULTIPLIER);

        // Add Summit runner speed bonus
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            SummitManager summitManager = plugin.getSummitManager();
            AscensionManager ascensionManager = plugin.getAscensionManager();

            if (summitManager != null) {
                speedMultiplier += summitManager.getRunnerSpeedBonus(ownerId);
            }

            if (ascensionManager != null) {
                speedMultiplier += ascensionManager.getRunnerSpeedBonus(ownerId);
            }
        }

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
        for (UUID playerId : onlinePlayers) {
            String activeMapId = runTracker.getActiveMapId(playerId);
            if (mapId.equals(activeMapId)) {
                visibilityManager.hideEntity(playerId, runnerUuid);
            }
        }
    }
}
