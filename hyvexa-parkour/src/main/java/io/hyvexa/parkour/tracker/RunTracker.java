package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import io.hyvexa.parkour.util.InventoryUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.parkour.ParkourConstants;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;
import io.hyvexa.parkour.data.TransformData;
import io.hyvexa.parkour.ghost.GhostNpcManager;
import io.hyvexa.parkour.ghost.GhostRecorder;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Tracks active parkour runs, checkpoint detection, and finish logic. */
public class RunTracker {

    private static final double TOUCH_RADIUS_SQ = ParkourConstants.TOUCH_RADIUS * ParkourConstants.TOUCH_RADIUS;
    private static final double START_MOVE_THRESHOLD_SQ = 0.0025;
    private static final long OFFLINE_RUN_EXPIRY_MS = TimeUnit.MINUTES.toMillis(30L);

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final SettingsStore settingsStore;
    private final ConcurrentHashMap<UUID, ActiveRun> activeRuns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TrackerUtils.FallState> idleFalls = new ConcurrentHashMap<>();
    private final Set<UUID> readyPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Long> lastSeenAt = new ConcurrentHashMap<>();
    private final PingTracker pingTracker = new PingTracker();
    private final JumpTracker jumpTracker;
    private final RunValidator validator;
    private final RunSessionTracker sessionTracker;
    private final RunTeleporter teleporter;
    private GhostRecorder ghostRecorder;
    private GhostNpcManager ghostNpcManager;

    public RunTracker(MapStore mapStore, ProgressStore progressStore,
                             SettingsStore settingsStore) {
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.settingsStore = settingsStore;
        this.jumpTracker = new JumpTracker(progressStore);
        this.validator = new RunValidator(mapStore, progressStore);
        this.sessionTracker = new RunSessionTracker(mapStore, progressStore);
        this.teleporter = new RunTeleporter(mapStore, activeRuns);
    }

    public void setGhostRecorder(GhostRecorder ghostRecorder) {
        this.ghostRecorder = ghostRecorder;
        this.validator.setGhostRecorder(ghostRecorder);
    }

    public void setGhostNpcManager(GhostNpcManager ghostNpcManager) {
        this.ghostNpcManager = ghostNpcManager;
        this.validator.setGhostNpcManager(ghostNpcManager);
    }

    public ActiveRun setActiveMap(UUID playerId, String mapId) {
        return setActiveMap(playerId, mapId, null);
    }

    public ActiveRun setActiveMap(UUID playerId, String mapId, TransformData start) {
        if (ghostRecorder != null) {
            ghostRecorder.cancelRecording(playerId);
        }
        if (ghostNpcManager != null) {
            ghostNpcManager.despawnGhost(playerId);
        }
        ActiveRun run = new ActiveRun(mapId, System.currentTimeMillis());
        activeRuns.put(playerId, run);
        idleFalls.remove(playerId);
        armStartOnMovement(run, start);
        if (ghostNpcManager != null) {
            ghostNpcManager.spawnGhost(playerId, mapId);
        }
        return run;
    }

    public void clearActiveMap(UUID playerId) {
        ActiveRun run = activeRuns.remove(playerId);
        if (run != null && run.practiceEnabled) {
            setFly(playerId, false);
        }
        if (ghostRecorder != null) {
            ghostRecorder.cancelRecording(playerId);
        }
        if (ghostNpcManager != null) {
            ghostNpcManager.despawnGhost(playerId);
        }
    }

    public void clearPlayer(UUID playerId) {
        clearActiveMap(playerId);
        idleFalls.remove(playerId);
        teleporter.clearPlayer(playerId);
        readyPlayers.remove(playerId);
        lastSeenAt.remove(playerId);
        sessionTracker.clearPlayer(playerId);
        jumpTracker.clearPlayer(playerId);
    }

    public void handleDisconnect(UUID playerId) {
        if (playerId == null) {
            return;
        }
        if (ghostRecorder != null) {
            ghostRecorder.cancelRecording(playerId);
        }
        if (ghostNpcManager != null) {
            ghostNpcManager.despawnGhost(playerId);
        }
        ActiveRun run = activeRuns.get(playerId);
        if (run != null) {
            run.lastPosition = null;
            run.fallState.fallStartTime = null;
            run.fallState.lastY = null;
            run.skipNextTimeIncrement = true;
        }
        idleFalls.remove(playerId);
        teleporter.clearPlayer(playerId);
        readyPlayers.remove(playerId);
        lastSeenAt.put(playerId, System.currentTimeMillis());
        sessionTracker.clearPlayer(playerId);
        jumpTracker.clearPlayer(playerId);
    }

    public java.util.Map<UUID, TeleportStatsSnapshot> drainTeleportStats() {
        return teleporter.drainTeleportStats();
    }

    public static final class TeleportStatsSnapshot {
        public final int startTrigger;
        public final int leaveTrigger;
        public final int runRespawn;
        public final int idleRespawn;
        public final int finish;
        public final int checkpoint;

        TeleportStatsSnapshot(int startTrigger, int leaveTrigger, int runRespawn, int idleRespawn, int finish,
                                      int checkpoint) {
            this.startTrigger = startTrigger;
            this.leaveTrigger = leaveTrigger;
            this.runRespawn = runRespawn;
            this.idleRespawn = idleRespawn;
            this.finish = finish;
            this.checkpoint = checkpoint;
        }

        public boolean isEmpty() {
            return startTrigger == 0 && leaveTrigger == 0 && runRespawn == 0
                    && idleRespawn == 0 && finish == 0 && checkpoint == 0;
        }
    }

    public CheckpointProgress getCheckpointProgress(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        if (run == null) {
            return null;
        }
        Map map = mapStore.getMap(run.mapId);
        if (map == null) {
            return null;
        }
        int total = map.getCheckpoints().size();
        int touched = Math.min(run.touchedCheckpoints.size(), total);
        return new CheckpointProgress(touched, total);
    }

    public CheckpointSplit getLastCheckpointSplit(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        if (run == null) {
            return null;
        }
        int index = run.lastCheckpointIndex;
        if (index < 0) {
            return null;
        }
        Long timeMs = run.checkpointTouchTimes.get(index);
        if (timeMs == null) {
            return null;
        }
        return new CheckpointSplit(index, timeMs);
    }

    public void sweepStalePlayers(Set<UUID> onlinePlayers) {
        if (onlinePlayers == null || onlinePlayers.isEmpty()) {
            idleFalls.clear();
            return;
        }
        long now = System.currentTimeMillis();
        idleFalls.keySet().removeIf(id -> !onlinePlayers.contains(id));
        readyPlayers.removeIf(id -> !onlinePlayers.contains(id));
        lastSeenAt.keySet().removeIf(id -> onlinePlayers.contains(id));
        activeRuns.keySet().removeIf(id -> !onlinePlayers.contains(id) && isExpiredOfflineRun(id, now));
    }

    public String getActiveMapId(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        return run != null ? run.mapId : null;
    }

    public boolean isPracticeEnabled(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        return run != null && run.practiceEnabled;
    }

    public boolean isFlyActive(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        return run != null && run.flyActive;
    }

    public boolean toggleFly(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        if (run == null || !run.practiceEnabled) {
            return false;
        }
        run.flyActive = !run.flyActive;
        setFly(playerId, run.flyActive);
        return run.flyActive;
    }

    public boolean enablePractice(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        if (run == null) {
            return false;
        }
        run.practiceEnabled = true;
        run.practiceCheckpoint = null;
        run.practiceHeadRotation = null;
        run.touchedCheckpoints.clear();
        run.checkpointTouchTimes.clear();
        run.lastCheckpointIndex = -1;
        run.finishTouched = false;
        if (run.lastPosition != null) {
            run.lastValidFlyPosition = new double[]{run.lastPosition.getX(), run.lastPosition.getY(), run.lastPosition.getZ()};
        } else {
            Map map = mapStore.getMap(run.mapId);
            if (map != null && map.getStart() != null) {
                run.lastValidFlyPosition = new double[]{map.getStart().getX(), map.getStart().getY(), map.getStart().getZ()};
            }
        }
        run.flyActive = false;
        return true;
    }

    private void setFly(UUID playerId, boolean enabled) {
        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null) {
            return;
        }
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }
        Store<EntityStore> store = entityRef.getStore();
        MovementManager movementManager = store.getComponent(entityRef, MovementManager.getComponentType());
        if (movementManager == null) {
            return;
        }
        if (enabled) {
            MovementSettings settings = movementManager.getSettings();
            if (settings == null) {
                return;
            }
            settings.canFly = true;
            MovementStatesComponent movementStates = store.getComponent(entityRef,
                    MovementStatesComponent.getComponentType());
            if (movementStates != null && movementStates.getMovementStates() != null) {
                movementStates.getMovementStates().flying = true;
            }
            var packetHandler = playerRef.getPacketHandler();
            if (packetHandler != null) {
                movementManager.update(packetHandler);
                packetHandler.writeNoCache(
                        new SetMovementStates(new SavedMovementStates(true)));
            }
        } else {
            MovementSettings settings = movementManager.getSettings();
            if (settings != null) {
                settings.canFly = false;
            }
            MovementStatesComponent movementStates = store.getComponent(entityRef,
                    MovementStatesComponent.getComponentType());
            if (movementStates != null && movementStates.getMovementStates() != null) {
                movementStates.getMovementStates().flying = false;
            }
            var packetHandler = playerRef.getPacketHandler();
            if (packetHandler != null) {
                movementManager.update(packetHandler);
                packetHandler.writeNoCache(
                        new SetMovementStates(new SavedMovementStates(false)));
            }
        }
    }

    public boolean setPracticeCheckpoint(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (playerRef == null) {
            return false;
        }
        ActiveRun run = activeRuns.get(playerRef.getUuid());
        if (run == null || !run.practiceEnabled) {
            return false;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return false;
        }
        Vector3d position = transform.getPosition();
        Vector3f rotation = transform.getRotation();
        if (position == null || rotation == null) {
            return false;
        }
        Vector3f headRotation = playerRef.getHeadRotation();
        TransformData checkpoint = new TransformData();
        checkpoint.setX(position.getX());
        checkpoint.setY(position.getY());
        checkpoint.setZ(position.getZ());
        Vector3f useRotation = headRotation != null ? headRotation : rotation;
        checkpoint.setRotX(useRotation.getX());
        checkpoint.setRotY(useRotation.getY());
        checkpoint.setRotZ(useRotation.getZ());
        run.practiceCheckpoint = checkpoint;
        run.practiceHeadRotation = headRotation != null ? headRotation.clone() : null;
        return true;
    }

    public Long getElapsedTimeMs(UUID playerId) {
        ActiveRun run = activeRuns.get(playerId);
        if (run == null) {
            return null;
        }
        if (run.waitingForStart) {
            return 0L;
        }
        return Math.max(0L, run.elapsedMs);
    }

    public void checkPlayer(Ref<EntityStore> ref, Store<EntityStore> store) {
        checkPlayer(ref, store, null, Float.NaN);
    }

    public void checkPlayer(Ref<EntityStore> ref, Store<EntityStore> store, float deltaSeconds) {
        checkPlayer(ref, store, null, deltaSeconds);
    }

    /**
     * Main per-tick player update: processes triggers, checkpoints, ghost recording, and run state.
     * Must be called from the world thread. Uses store directly (no command buffer required).
     *
     * @param buffer optional command buffer for ECS writes (may be null for non-tick callers)
     * @param deltaSeconds tick delta in seconds (NaN if unavailable)
     */
    public void checkPlayer(Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                            float deltaSeconds) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        MovementStatesComponent movementStatesComponent = store.getComponent(ref, MovementStatesComponent.getComponentType());
        MovementStates movementStates = movementStatesComponent != null
                ? movementStatesComponent.getMovementStates()
                : null;
        if (playerRef == null || player == null || transform == null) {
            return;
        }
        if (!isPlayerReady(playerRef.getUuid())) {
            return;
        }
        jumpTracker.trackJump(playerRef, movementStates);
        lastSeenAt.put(playerRef.getUuid(), System.currentTimeMillis());
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin != null && plugin.getDuelTracker() != null
                && plugin.getDuelTracker().isInMatch(playerRef.getUuid())) {
            return;
        }
        ActiveRun run = activeRuns.get(playerRef.getUuid());
        Vector3d position = transform.getPosition();
        if (shouldTeleportFromVoid(position.getY())) {
            if (run == null) {
                teleporter.teleportToSpawn(ref, store, transform, buffer);
                teleporter.recordTeleport(playerRef.getUuid(), RunTeleporter.TeleportCause.IDLE_RESPAWN);
                return;
            }
            Map map = mapStore.getMap(run.mapId);
            if (map != null) {
                teleporter.teleportToRespawn(ref, store, run, map, buffer);
                run.fallState.fallStartTime = null;
                run.fallState.lastY = null;
                teleporter.recordTeleport(playerRef.getUuid(), RunTeleporter.TeleportCause.RUN_RESPAWN);
            } else {
                teleporter.teleportToSpawn(ref, store, transform, buffer);
                teleporter.recordTeleport(playerRef.getUuid(), RunTeleporter.TeleportCause.IDLE_RESPAWN);
            }
            return;
        }
        if (run == null) {
            long fallTimeoutMs = getFallRespawnTimeoutMs();
            boolean allowOpIdleFall = settingsStore != null && settingsStore.isIdleFallRespawnForOp();
            if (fallTimeoutMs > 0 && (allowOpIdleFall || !PermissionUtils.isOp(player))
                    && shouldRespawnFromFall(getIdleFallState(playerRef.getUuid()), position.getY(),
                    movementStates,
                    fallTimeoutMs)) {
                teleporter.teleportToSpawn(ref, store, transform, buffer);
                teleporter.recordTeleport(playerRef.getUuid(), RunTeleporter.TeleportCause.IDLE_RESPAWN);
                return;
            }
            Map triggerMap = findStartTriggerMap(position);
            if (triggerMap != null) {
                startRunFromTrigger(ref, store, playerRef, player, triggerMap, buffer);
            }
            return;
        }
        Map map = mapStore.getMap(run.mapId);
        if (map == null) {
            return;
        }
        updateStartOnMovement(run, position, playerRef);
        long previousElapsedMs = getRunElapsedMs(run);
        Vector3d previousPosition = run.lastPosition;
        advanceRunTime(run, deltaSeconds);
        long currentElapsedMs = getRunElapsedMs(run);
        double deltaMs = Math.max(0.0, currentElapsedMs - previousElapsedMs);
        if (checkLeaveTrigger(ref, store, player, playerRef, position, map, buffer)) {
            return;
        }
        if (run.practiceEnabled && map.hasFlyZone()) {
            if (!isInsideFlyZone(position, map)) {
                long now = System.currentTimeMillis();
                if (now - run.lastFlyZoneRollbackMs >= ParkourConstants.FLY_ZONE_ROLLBACK_THROTTLE_MS) {
                    run.lastFlyZoneRollbackMs = now;
                    player.sendMessage(SystemMessageUtils.parkourWarn("You don't have the right to go there."));
                    double[] rollback = run.lastValidFlyPosition;
                    if (rollback != null) {
                        Vector3d rollbackPos = new Vector3d(rollback[0], rollback[1], rollback[2]);
                        Vector3f headRot = playerRef.getHeadRotation();
                        Vector3f bodyRot = headRot != null ? headRot : transform.getRotation();
                        Teleport tp = Teleport.createForPlayer(
                                store.getExternalData().getWorld(),
                                new com.hypixel.hytale.math.vector.Transform(rollbackPos, bodyRot));
                        if (headRot != null) {
                            tp.setHeadRotation(headRot.clone());
                        }
                        teleporter.addTeleport(ref, store, buffer, tp);
                    }
                }
                return;
            }
            run.lastValidFlyPosition = new double[]{position.getX(), position.getY(), position.getZ()};
        }
        validator.checkCheckpoints(run, playerRef, player, position, map, previousPosition, previousElapsedMs, deltaMs);
        long fallTimeoutMs = getFallRespawnTimeoutMs();
        if (map.isFreeFallEnabled()) {
            run.fallState.fallStartTime = null;
            run.fallState.lastY = position.getY();
            fallTimeoutMs = 0L;
        }
        if (fallTimeoutMs > 0 && shouldRespawnFromFall(run, position.getY(), movementStates, fallTimeoutMs)) {
            run.fallState.fallStartTime = null;
            run.fallState.lastY = null;
            if (run.lastCheckpointIndex < 0) {
                armStartOnMovement(run, map.getStart());
            }
            teleporter.teleportToRespawn(ref, store, run, map, buffer);
            teleporter.recordTeleport(playerRef.getUuid(), RunTeleporter.TeleportCause.RUN_RESPAWN);

            sessionTracker.recordFailure(playerRef.getUuid(), run.mapId);
            sessionTracker.checkRecommendations(playerRef.getUuid(), run, map, ref, store, playerRef, this);

            return;
        }
        validator.checkFinish(run, playerRef, player, position, map, transform, ref, store, buffer, previousPosition,
                previousElapsedMs, deltaMs, sessionTracker, teleporter, this);
        if (activeRuns.get(playerRef.getUuid()) == run) {
            run.lastPosition = copyPosition(position);
        }
    }

    private Map findStartTriggerMap(Vector3d position) {
        if (position == null) {
            return null;
        }
        return mapStore.findMapByStartTrigger(
                position.getX(),
                position.getY(),
                position.getZ(),
                TOUCH_RADIUS_SQ
        );
    }

    private void startRunFromTrigger(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
                                     Player player, Map map, CommandBuffer<EntityStore> buffer) {
        if (map.getStart() == null) {
            player.sendMessage(SystemMessageUtils.parkourError("Map '" + map.getId() + "' has no start set."));
            return;
        }
        setActiveMap(playerRef.getUuid(), map.getId(), map.getStart());
        Vector3d position = new Vector3d(map.getStart().getX(), map.getStart().getY(), map.getStart().getZ());
        Vector3f rotation = new Vector3f(map.getStart().getRotX(), map.getStart().getRotY(),
                map.getStart().getRotZ());
        teleporter.addTeleport(ref, store, buffer, new Teleport(store.getExternalData().getWorld(), position, rotation));
        teleporter.recordTeleport(playerRef.getUuid(), RunTeleporter.TeleportCause.START_TRIGGER);
        player.sendMessage(buildRunStartMessage(map));
        InventoryUtils.giveRunItems(player, map, false);
    }

    private boolean checkLeaveTrigger(Ref<EntityStore> ref, Store<EntityStore> store, Player player,
                                      PlayerRef playerRef, Vector3d position, Map map,
                                      CommandBuffer<EntityStore> buffer) {
        TransformData trigger = map.getLeaveTrigger();
        if (trigger == null) {
            return false;
        }
        if (distanceSq(position, trigger) > TOUCH_RADIUS_SQ) {
            return false;
        }
        TransformData leaveTeleport = map.getLeaveTeleport();
        if (leaveTeleport != null) {
            Vector3d targetPosition = new Vector3d(leaveTeleport.getX(), leaveTeleport.getY(), leaveTeleport.getZ());
            Vector3f targetRotation = new Vector3f(leaveTeleport.getRotX(), leaveTeleport.getRotY(),
                    leaveTeleport.getRotZ());
            teleporter.addTeleport(ref, store, buffer, new Teleport(store.getExternalData().getWorld(), targetPosition, targetRotation));
            teleporter.recordTeleport(playerRef.getUuid(), RunTeleporter.TeleportCause.LEAVE_TRIGGER);
        }
        clearActiveMap(playerRef.getUuid());
        InventoryUtils.giveMenuItems(player);
        player.sendMessage(buildRunEndMessage(map));
        return true;
    }

    private Message buildRunStartMessage(Map map) {
        String mapName = validator.getMapDisplayName(map);
        return SystemMessageUtils.withParkourPrefix(
                Message.raw("Run started: ").color(SystemMessageUtils.SECONDARY),
                Message.raw(mapName).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(".").color(SystemMessageUtils.SECONDARY)
        );
    }

    private Message buildRunEndMessage(Map map) {
        String mapName = validator.getMapDisplayName(map);
        return SystemMessageUtils.withParkourPrefix(
                Message.raw("Run ended: ").color(SystemMessageUtils.SECONDARY),
                Message.raw(mapName).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(".").color(SystemMessageUtils.SECONDARY)
        );
    }

    private long getFallRespawnTimeoutMs() {
        double seconds = settingsStore != null
                ? settingsStore.getFallRespawnSeconds()
                : ParkourConstants.DEFAULT_FALL_RESPAWN_SECONDS;
        if (seconds <= 0) {
            return 0L;
        }
        return (long) Math.max(1L, seconds * 1000.0);
    }

    private boolean shouldRespawnFromFall(ActiveRun run, double currentY, MovementStates movementStates,
                                          long fallTimeoutMs) {
        return TrackerUtils.shouldRespawnFromFall(run.fallState, currentY,
                TrackerUtils.isFallTrackingBlocked(movementStates), fallTimeoutMs);
    }

    private boolean shouldRespawnFromFall(TrackerUtils.FallState fallState, double currentY,
                                          MovementStates movementStates, long fallTimeoutMs) {
        return TrackerUtils.shouldRespawnFromFall(fallState, currentY,
                TrackerUtils.isFallTrackingBlocked(movementStates), fallTimeoutMs);
    }

    // --- Delegation methods for teleport operations (preserve public API) ---

    public void teleportToSpawn(Ref<EntityStore> ref, Store<EntityStore> store, TransformComponent transform) {
        teleporter.teleportToSpawn(ref, store, transform);
    }

    // Called from interaction handlers (outside ECS tick) — store.addComponent() is safe here
    public boolean teleportToLastCheckpoint(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        return teleporter.teleportToLastCheckpoint(ref, store, playerRef);
    }

    // Called from interaction handlers (outside ECS tick) — store.addComponent() is safe here
    public boolean resetRunToStart(Ref<EntityStore> ref, Store<EntityStore> store, Player player, PlayerRef playerRef) {
        return teleporter.resetRunToStart(ref, store, player, playerRef, this);
    }

    // --- ActiveRun (package-private for collaborator access) ---

    static class ActiveRun {
        final String mapId;
        final TrackerUtils.FallState fallState = new TrackerUtils.FallState();
        long startTimeMs;
        boolean waitingForStart;
        Vector3d startPosition;
        Vector3d lastPosition;
        final Set<Integer> touchedCheckpoints = new HashSet<>();
        final java.util.Map<Integer, Long> checkpointTouchTimes = new java.util.HashMap<>();
        boolean finishTouched;
        int lastCheckpointIndex = -1;
        boolean practiceEnabled;
        boolean flyActive;
        TransformData practiceCheckpoint;
        Vector3f practiceHeadRotation;
        long lastFinishWarningMs;
        long elapsedMs;
        double elapsedRemainderMs;
        boolean skipNextTimeIncrement;
        Long startPingMs;
        Long finishPingMs;
        double[] lastValidFlyPosition;
        long lastFlyZoneRollbackMs;

        ActiveRun(String mapId, long startTimeMs) {
            this.mapId = mapId;
            this.startTimeMs = startTimeMs;
            this.elapsedMs = 0L;
        }
    }

    public static class CheckpointProgress {
        public final int touched;
        public final int total;

        public CheckpointProgress(int touched, int total) {
            this.touched = touched;
            this.total = total;
        }
    }

    public static class CheckpointSplit {
        public final int index;
        public final long timeMs;

        public CheckpointSplit(int index, long timeMs) {
            this.index = index;
            this.timeMs = timeMs;
        }
    }

    // --- Internal helpers ---

    private static double distanceSq(Vector3d position, TransformData target) {
        double dx = position.getX() - target.getX();
        double dy = position.getY() - target.getY();
        double dz = position.getZ() - target.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static double distanceSq(Vector3d position, Vector3d target) {
        double dx = position.getX() - target.getX();
        double dy = position.getY() - target.getY();
        double dz = position.getZ() - target.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean isInsideFlyZone(Vector3d pos, Map map) {
        return pos.getX() >= map.getFlyZoneMinX() && pos.getX() <= map.getFlyZoneMaxX()
            && pos.getY() >= map.getFlyZoneMinY() && pos.getY() <= map.getFlyZoneMaxY()
            && pos.getZ() >= map.getFlyZoneMinZ() && pos.getZ() <= map.getFlyZoneMaxZ();
    }

    private void armStartOnMovement(ActiveRun run, TransformData start) {
        if (run == null) {
            return;
        }
        if (start == null) {
            run.waitingForStart = false;
            run.startPosition = null;
            return;
        }
        run.startPosition = new Vector3d(start.getX(), start.getY(), start.getZ());
        run.waitingForStart = true;
        run.startTimeMs = System.currentTimeMillis();
        run.elapsedMs = 0L;
        run.elapsedRemainderMs = 0.0;
        run.skipNextTimeIncrement = false;
        pingTracker.resetPingSnapshots(run);
    }

    private void updateStartOnMovement(ActiveRun run, Vector3d position, PlayerRef playerRef) {
        if (run == null || !run.waitingForStart || run.startPosition == null || position == null) {
            return;
        }
        if (distanceSq(position, run.startPosition) > START_MOVE_THRESHOLD_SQ) {
            run.waitingForStart = false;
            run.startTimeMs = System.currentTimeMillis();
            run.elapsedMs = 0L;
            run.elapsedRemainderMs = 0.0;
            run.skipNextTimeIncrement = true;
            pingTracker.resetPingSnapshots(run);
            pingTracker.recordStartPing(run, playerRef);
            if (ghostRecorder != null && !run.practiceEnabled) {
                ghostRecorder.startRecording(playerRef.getUuid(), run.mapId);
            }
            if (ghostNpcManager != null) {
                ghostNpcManager.startPlayback(playerRef.getUuid());
            }
        }
    }

    private TrackerUtils.FallState getIdleFallState(UUID playerId) {
        return idleFalls.computeIfAbsent(playerId, ignored -> new TrackerUtils.FallState());
    }

    private boolean shouldTeleportFromVoid(double currentY) {
        double voidY = settingsStore != null
                ? settingsStore.getFallFailsafeVoidY()
                : ParkourConstants.FALL_FAILSAFE_VOID_Y;
        return Double.isFinite(voidY) && currentY <= voidY;
    }

    public void markPlayerReady(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId != null) {
            readyPlayers.add(playerId);
        }
    }

    public void markPlayerReady(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                return;
            }
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId != null) {
                readyPlayers.add(playerId);
            }
        }, world).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> null);
    }

    private boolean isPlayerReady(UUID playerId) {
        return playerId != null && readyPlayers.contains(playerId);
    }

    private boolean isExpiredOfflineRun(UUID playerId, long nowMs) {
        Long lastSeen = lastSeenAt.get(playerId);
        if (lastSeen == null) {
            return false;
        }
        return nowMs - lastSeen >= OFFLINE_RUN_EXPIRY_MS;
    }

    private static Vector3d copyPosition(Vector3d position) {
        if (position == null) {
            return null;
        }
        return new Vector3d(position.getX(), position.getY(), position.getZ());
    }

    // --- Ping tracking (delegated to PingTracker) ---

    void recordFinishPing(ActiveRun run, PlayerRef playerRef) {
        pingTracker.recordFinishPing(run, playerRef);
    }

    void sendLatencyWarning(ActiveRun run, Player player) {
        pingTracker.sendLatencyWarning(run, player);
    }

    // --- Jump tracking (delegated to JumpTracker) ---

    public void flushPendingJumps() {
        jumpTracker.flushPendingJumps();
    }

    // --- Run time ---

    private void advanceRunTime(ActiveRun run, float deltaSeconds) {
        if (run == null) {
            return;
        }
        if (run.waitingForStart) {
            run.elapsedMs = 0L;
            run.elapsedRemainderMs = 0.0;
            run.skipNextTimeIncrement = false;
            return;
        }
        if (run.skipNextTimeIncrement) {
            run.skipNextTimeIncrement = false;
            return;
        }
        if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
            run.elapsedMs = Math.max(0L, System.currentTimeMillis() - run.startTimeMs);
            run.elapsedRemainderMs = 0.0;
            return;
        }
        double deltaMs = Math.max(0.0, deltaSeconds * 1000.0);
        long wholeMs = (long) deltaMs;
        double fractionMs = deltaMs - wholeMs + run.elapsedRemainderMs;
        long carryMs = (long) fractionMs;
        run.elapsedRemainderMs = fractionMs - carryMs;
        run.elapsedMs += wholeMs + carryMs;
    }

    private long getRunElapsedMs(ActiveRun run) {
        if (run == null || run.waitingForStart) {
            return 0L;
        }
        return Math.max(0L, run.elapsedMs);
    }
}
