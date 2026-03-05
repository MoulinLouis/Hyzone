package io.hyvexa.core.trail;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Shared scheduling, tick loop, viewer collection, and broadcast logic for trail managers.
 * Subclasses provide trail-type-specific state and rendering.
 *
 * @param <TState> the trail state type held per player
 */
abstract class AbstractTrailManager<TState> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long SCHEDULER_INTERVAL_MS = 50L;
    private static final double VIEWER_CULL_DISTANCE_SQ = 96.0d * 96.0d;
    static final double MOVEMENT_THRESHOLD_SQ = 0.0009d; // ~0.03 blocks
    private static final Set<AbstractTrailManager<?>> MANAGERS = ConcurrentHashMap.newKeySet();
    private static final Object SCHEDULER_LOCK = new Object();

    final ConcurrentHashMap<UUID, TState> activeTrails = new ConcurrentHashMap<>();
    private static volatile ScheduledFuture<?> tickTask;

    protected AbstractTrailManager() {
        MANAGERS.add(this);
    }

    protected abstract HytaleLogger logger();

    protected abstract UUID getPlayerId(TState state);

    protected abstract Ref<EntityStore> getRef(TState state);

    protected abstract Store<EntityStore> getStore(TState state);

    protected abstract World getWorld(TState state);

    protected abstract long getIntervalMs(TState state);

    protected abstract long getNextEmissionAtMs(TState state);

    protected abstract void setNextEmissionAtMs(TState state, long ms);

    /**
     * Called on the world thread to emit a trail for the given state.
     * The caller has already verified the trail is still active.
     */
    protected abstract void emitTrailOnWorldThread(TState state, List<ViewerState> viewers);

    public void stopTrail(UUID playerId) {
        activeTrails.remove(playerId);
        cancelTickTaskIfIdle();
    }

    public boolean hasTrail(UUID playerId) {
        return activeTrails.containsKey(playerId);
    }

    public void shutdown() {
        activeTrails.clear();
        cancelTickTaskIfIdle();
    }

    void ensureTickTask() {
        MANAGERS.add(this);
        synchronized (SCHEDULER_LOCK) {
            if (tickTask != null && !tickTask.isCancelled() && !tickTask.isDone()) {
                return;
            }
            tickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                    AbstractTrailManager::tickAllManagers,
                    0L,
                    SCHEDULER_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private static void tickAllManagers() {
        try {
            if (!hasActiveTrails()) {
                cancelTickTaskIfIdle();
                return;
            }
            long now = System.currentTimeMillis();
            for (AbstractTrailManager<?> manager : MANAGERS) {
                try {
                    manager.tickTrails(now);
                } catch (Exception e) {
                    manager.logger().atWarning().withCause(e).log("Trail scheduler tick failed");
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Trail scheduler tick failed");
        } finally {
            cancelTickTaskIfIdle();
        }
    }

    private void tickTrails(long now) {
        if (activeTrails.isEmpty()) {
            return;
        }
        Map<World, List<TState>> dueByWorld = new HashMap<>();
        for (TState state : activeTrails.values()) {
            if (state == null || now < getNextEmissionAtMs(state)) {
                continue;
            }
            Ref<EntityStore> ref = getRef(state);
            if (ref == null || !ref.isValid()) {
                stopTrail(getPlayerId(state));
                continue;
            }
            World world = getWorld(state);
            if (world == null) {
                stopTrail(getPlayerId(state));
                continue;
            }
            setNextEmissionAtMs(state, now + Math.max(1L, getIntervalMs(state)));
            dueByWorld.computeIfAbsent(world, ignored -> new ArrayList<>()).add(state);
        }

        for (Map.Entry<World, List<TState>> entry : dueByWorld.entrySet()) {
            World world = entry.getKey();
            List<TState> states = entry.getValue();
            if (world == null || states.isEmpty()) {
                continue;
            }
            try {
                world.execute(() -> {
                    List<ViewerState> viewers = collectViewersForWorld(world);
                    emitWorldTrails(world, states, viewers);
                });
            } catch (Exception e) {
                logger().atWarning().withCause(e).log("Trail schedule error for world " + world.getName());
            }
        }
    }

    private void emitWorldTrails(World world, List<TState> states, List<ViewerState> viewers) {
        for (TState state : states) {
            UUID playerId = getPlayerId(state);
            try {
                if (activeTrails.get(playerId) != state) {
                    continue;
                }
                Ref<EntityStore> ref = getRef(state);
                if (ref == null || !ref.isValid()) {
                    stopTrail(playerId);
                    continue;
                }
                Store<EntityStore> store = getStore(state);
                if (store == null) {
                    stopTrail(playerId);
                    continue;
                }
                World currentWorld = store.getExternalData().getWorld();
                if (currentWorld != world || currentWorld != getWorld(state)) {
                    stopTrail(playerId);
                    continue;
                }
                emitTrailOnWorldThread(state, viewers);
            } catch (Exception e) {
                logger().atWarning().withCause(e).log("Trail tick error for " + playerId);
            }
        }
    }

    /**
     * Collect viewer positions for a specific world. Must be called on that world's thread.
     */
    static List<ViewerState> collectViewersForWorld(World world) {
        List<ViewerState> viewers = new ArrayList<>();
        for (PlayerRef viewer : Universe.get().getPlayers()) {
            if (viewer == null || !viewer.isValid()) {
                continue;
            }
            Ref<EntityStore> viewerRef = viewer.getReference();
            if (viewerRef == null || !viewerRef.isValid()) {
                continue;
            }
            Store<EntityStore> viewerStore = viewerRef.getStore();
            if (viewerStore.getExternalData().getWorld() != world) {
                continue;
            }
            TransformComponent transform = viewerStore.getComponent(viewerRef, TransformComponent.getComponentType());
            if (transform == null || transform.getPosition() == null) {
                continue;
            }
            var position = transform.getPosition();
            viewers.add(new ViewerState(viewer, position.getX(), position.getY(), position.getZ()));
        }
        return viewers;
    }

    private static boolean hasActiveTrails() {
        for (AbstractTrailManager<?> manager : MANAGERS) {
            if (!manager.activeTrails.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void cancelTickTaskIfIdle() {
        synchronized (SCHEDULER_LOCK) {
            if (hasActiveTrails()) {
                return;
            }
            ScheduledFuture<?> task = tickTask;
            if (task == null) {
                return;
            }
            task.cancel(false);
            tickTask = null;
        }
    }

    void broadcastPacket(World world, List<ViewerState> viewers, ToClientPacket packet) {
        for (ViewerState viewer : viewers) {
            if (viewer == null) {
                continue;
            }
            PlayerRef playerRef = viewer.playerRef();
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }
            Ref<EntityStore> viewerRef = playerRef.getReference();
            if (viewerRef == null || !viewerRef.isValid()) {
                continue;
            }
            Store<EntityStore> viewerStore = viewerRef.getStore();
            if (viewerStore.getExternalData().getWorld() != world) {
                continue;
            }
            PacketHandler packetHandler = playerRef.getPacketHandler();
            if (packetHandler == null) {
                continue;
            }
            packetHandler.writeNoCache(packet);
        }
    }

    void broadcastPacket(World world, List<ViewerState> viewers, ToClientPacket packet,
                         double sourceX, double sourceY, double sourceZ) {
        for (ViewerState viewer : viewers) {
            if (viewer == null) {
                continue;
            }
            double dx = viewer.x() - sourceX;
            double dy = viewer.y() - sourceY;
            double dz = viewer.z() - sourceZ;
            if ((dx * dx) + (dy * dy) + (dz * dz) > VIEWER_CULL_DISTANCE_SQ) {
                continue;
            }
            PlayerRef playerRef = viewer.playerRef();
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }
            Ref<EntityStore> viewerRef = playerRef.getReference();
            if (viewerRef == null || !viewerRef.isValid()) {
                continue;
            }
            Store<EntityStore> viewerStore = viewerRef.getStore();
            if (viewerStore.getExternalData().getWorld() != world) {
                continue;
            }
            PacketHandler packetHandler = playerRef.getPacketHandler();
            if (packetHandler == null) {
                continue;
            }
            packetHandler.writeNoCache(packet);
        }
    }

    /**
     * Read transform position, update lastPos tracking array, and return whether the entity
     * moved beyond the movement threshold. Returns null if the entity has no transform or
     * if this is the first position sample.
     */
    static double[] updatePositionAndCheckMovement(Store<EntityStore> store, Ref<EntityStore> ref,
                                                    double[] lastPos) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return null;
        }
        var pos = transform.getPosition();
        if (Double.isNaN(lastPos[0])) {
            lastPos[0] = pos.getX();
            lastPos[1] = pos.getY();
            lastPos[2] = pos.getZ();
            return null;
        }
        double dx = pos.getX() - lastPos[0];
        double dy = pos.getY() - lastPos[1];
        double dz = pos.getZ() - lastPos[2];
        double distSq = (dx * dx) + (dy * dy) + (dz * dz);
        lastPos[0] = pos.getX();
        lastPos[1] = pos.getY();
        lastPos[2] = pos.getZ();
        if (distSq <= MOVEMENT_THRESHOLD_SQ) {
            return null;
        }
        return new double[]{pos.getX(), pos.getY(), pos.getZ()};
    }

    record ViewerState(PlayerRef playerRef, double x, double y, double z) {}
}
