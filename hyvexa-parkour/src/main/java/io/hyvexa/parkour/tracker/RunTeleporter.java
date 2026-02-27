package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.TransformData;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Teleport operations extracted from RunTracker. */
class RunTeleporter {

    private final MapStore mapStore;
    private final ConcurrentHashMap<UUID, RunTracker.ActiveRun> activeRuns;
    private final ConcurrentHashMap<UUID, TeleportStats> teleportStats = new ConcurrentHashMap<>();

    RunTeleporter(MapStore mapStore, ConcurrentHashMap<UUID, RunTracker.ActiveRun> activeRuns) {
        this.mapStore = mapStore;
        this.activeRuns = activeRuns;
    }

    void teleportToSpawn(Ref<EntityStore> ref, Store<EntityStore> store, TransformComponent transform) {
        TrackerUtils.teleportToSpawn(ref, store, transform, null);
    }

    void teleportToSpawn(Ref<EntityStore> ref, Store<EntityStore> store, TransformComponent transform,
                         CommandBuffer<EntityStore> buffer) {
        TrackerUtils.teleportToSpawn(ref, store, transform, buffer);
    }

    void teleportToRespawn(Ref<EntityStore> ref, Store<EntityStore> store, RunTracker.ActiveRun run, Map map,
                           CommandBuffer<EntityStore> buffer) {
        if (run != null) {
            run.lastPosition = null;
        }
        TransformData spawn = null;
        if (run != null && run.practiceEnabled && run.practiceCheckpoint != null) {
            spawn = run.practiceCheckpoint;
        }
        int checkpointIndex = resolveCheckpointIndex(run, map);
        if (spawn == null && checkpointIndex >= 0 && checkpointIndex < map.getCheckpoints().size()) {
            spawn = map.getCheckpoints().get(checkpointIndex);
        }
        if (spawn == null) {
            spawn = map.getStart();
        }
        if (spawn == null) {
            return;
        }
        Vector3d position = new Vector3d(spawn.getX(), spawn.getY(), spawn.getZ());
        Vector3f rotation = new Vector3f(spawn.getRotX(), spawn.getRotY(), spawn.getRotZ());
        addTeleport(ref, store, buffer, new Teleport(store.getExternalData().getWorld(), position, rotation));
    }

    boolean teleportToLastCheckpoint(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (playerRef == null) {
            return false;
        }
        RunTracker.ActiveRun run = activeRuns.get(playerRef.getUuid());
        if (run == null) {
            return false;
        }
        if (run.practiceEnabled) {
            return teleportToPracticeCheckpoint(ref, store, playerRef, run);
        }
        Map map = mapStore.getMapReadonly(run.mapId);
        if (map == null) {
            return false;
        }
        int checkpointIndex = resolveCheckpointIndex(run, map);
        if (checkpointIndex < 0 || checkpointIndex >= map.getCheckpoints().size()) {
            return false;
        }
        TransformData checkpoint = map.getCheckpoints().get(checkpointIndex);
        if (checkpoint == null) {
            return false;
        }
        Vector3d position = new Vector3d(checkpoint.getX(), checkpoint.getY(), checkpoint.getZ());
        Vector3f rotation = new Vector3f(checkpoint.getRotX(), checkpoint.getRotY(), checkpoint.getRotZ());
        store.addComponent(ref, Teleport.getComponentType(),
                new Teleport(store.getExternalData().getWorld(), position, rotation));
        recordTeleport(playerRef.getUuid(), TeleportCause.CHECKPOINT);
        run.fallState.fallStartTime = null;
        run.fallState.lastY = null;
        run.lastPosition = null;
        return true;
    }

    private boolean teleportToPracticeCheckpoint(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
                                                 RunTracker.ActiveRun run) {
        if (run == null || run.practiceCheckpoint == null) {
            return false;
        }
        Vector3d position = new Vector3d(run.practiceCheckpoint.getX(),
                run.practiceCheckpoint.getY(),
                run.practiceCheckpoint.getZ());
        Vector3f rotation = new Vector3f(run.practiceCheckpoint.getRotX(),
                run.practiceCheckpoint.getRotY(),
                run.practiceCheckpoint.getRotZ());
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return false;
        }
        Teleport teleport = Teleport.createForPlayer(world, new Transform(position, rotation));
        if (run.practiceHeadRotation != null) {
            teleport.setHeadRotation(run.practiceHeadRotation.clone());
        }
        store.addComponent(ref, Teleport.getComponentType(), teleport);
        recordTeleport(playerRef.getUuid(), TeleportCause.CHECKPOINT);
        run.fallState.fallStartTime = null;
        run.fallState.lastY = null;
        run.lastPosition = null;
        return true;
    }

    boolean resetRunToStart(Ref<EntityStore> ref, Store<EntityStore> store, Player player, PlayerRef playerRef,
                            RunTracker runTracker) {
        if (playerRef == null || player == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            player.sendMessage(SystemMessageUtils.parkourError("World not available."));
            return false;
        }
        String mapId = runTracker.getActiveMapId(playerRef.getUuid());
        if (mapId == null) {
            player.sendMessage(SystemMessageUtils.parkourWarn("No active map to reset."));
            return false;
        }
        Map map = mapStore.getMapReadonly(mapId);
        if (map == null || map.getStart() == null) {
            player.sendMessage(SystemMessageUtils.parkourError("Map start not available."));
            return false;
        }
        RunTracker.ActiveRun previous = activeRuns.get(playerRef.getUuid());
        boolean practiceEnabled = previous != null && previous.practiceEnabled;
        boolean flyActive = previous != null && previous.flyActive;
        TransformData practiceCheckpoint = previous != null ? previous.practiceCheckpoint : null;
        Vector3f practiceHeadRotation = previous != null ? previous.practiceHeadRotation : null;
        TransformData practiceStartPosition = previous != null ? previous.practiceStartPosition : null;
        int practiceStartCheckpointIndex = previous != null ? previous.practiceStartCheckpointIndex : -1;
        boolean practiceStartFinishTouched = previous != null && previous.practiceStartFinishTouched;
        RunTracker.ActiveRun run = runTracker.setActiveMap(playerRef.getUuid(), mapId, map.getStart());
        if (run != null) {
            run.practiceEnabled = practiceEnabled;
            run.flyActive = flyActive;
            run.practiceCheckpoint = practiceCheckpoint;
            run.practiceHeadRotation = practiceHeadRotation;
            run.practiceStartPosition = practiceStartPosition;
            run.practiceStartCheckpointIndex = practiceStartCheckpointIndex;
            run.practiceStartFinishTouched = practiceStartFinishTouched;
            if (previous != null) {
                run.practiceStartTouchedCheckpoints.addAll(previous.practiceStartTouchedCheckpoints);
                run.practiceStartCheckpointTouchTimes.putAll(previous.practiceStartCheckpointTouchTimes);
            }
        }
        Vector3d position = new Vector3d(map.getStart().getX(), map.getStart().getY(), map.getStart().getZ());
        Vector3f rotation = new Vector3f(map.getStart().getRotX(), map.getStart().getRotY(),
                map.getStart().getRotZ());
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, position, rotation));
        return true;
    }

    void addTeleport(Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> buffer,
                     Teleport teleport) {
        if (ref == null || teleport == null) {
            return;
        }
        if (buffer != null) {
            buffer.addComponent(ref, Teleport.getComponentType(), teleport);
        } else if (store != null) {
            store.addComponent(ref, Teleport.getComponentType(), teleport);
        }
    }

    void recordTeleport(UUID playerId, TeleportCause cause) {
        if (playerId == null || cause == null) {
            return;
        }
        teleportStats.computeIfAbsent(playerId, ignored -> new TeleportStats()).increment(cause);
    }

    java.util.Map<UUID, RunTracker.TeleportStatsSnapshot> drainTeleportStats() {
        if (teleportStats.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<UUID, RunTracker.TeleportStatsSnapshot> snapshots = new HashMap<>();
        for (java.util.Map.Entry<UUID, TeleportStats> entry : teleportStats.entrySet()) {
            RunTracker.TeleportStatsSnapshot snapshot = entry.getValue().snapshotAndReset();
            if (snapshot.isEmpty()) {
                teleportStats.remove(entry.getKey(), entry.getValue());
                continue;
            }
            snapshots.put(entry.getKey(), snapshot);
        }
        return snapshots;
    }

    void clearPlayer(UUID playerId) {
        teleportStats.remove(playerId);
    }

    private int resolveCheckpointIndex(RunTracker.ActiveRun run, Map map) {
        if (run == null || map == null) {
            return -1;
        }
        return TrackerUtils.resolveCheckpointIndex(run.lastCheckpointIndex, run.touchedCheckpoints,
                map.getCheckpoints());
    }

    enum TeleportCause {
        START_TRIGGER,
        LEAVE_TRIGGER,
        RUN_RESPAWN,
        IDLE_RESPAWN,
        FINISH,
        CHECKPOINT
    }

    static final class TeleportStats {
        private final AtomicInteger startTrigger = new AtomicInteger();
        private final AtomicInteger leaveTrigger = new AtomicInteger();
        private final AtomicInteger runRespawn = new AtomicInteger();
        private final AtomicInteger idleRespawn = new AtomicInteger();
        private final AtomicInteger finish = new AtomicInteger();
        private final AtomicInteger checkpoint = new AtomicInteger();

        void increment(TeleportCause cause) {
            if (cause == null) {
                return;
            }
            switch (cause) {
                case START_TRIGGER -> startTrigger.incrementAndGet();
                case LEAVE_TRIGGER -> leaveTrigger.incrementAndGet();
                case RUN_RESPAWN -> runRespawn.incrementAndGet();
                case IDLE_RESPAWN -> idleRespawn.incrementAndGet();
                case FINISH -> finish.incrementAndGet();
                case CHECKPOINT -> checkpoint.incrementAndGet();
            }
        }

        RunTracker.TeleportStatsSnapshot snapshotAndReset() {
            return new RunTracker.TeleportStatsSnapshot(
                    startTrigger.getAndSet(0),
                    leaveTrigger.getAndSet(0),
                    runRespawn.getAndSet(0),
                    idleRespawn.getAndSet(0),
                    finish.getAndSet(0),
                    checkpoint.getAndSet(0)
            );
        }
    }
}
