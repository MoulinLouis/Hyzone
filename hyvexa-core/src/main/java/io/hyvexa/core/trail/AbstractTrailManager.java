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

    private static final long SCHEDULER_INTERVAL_MS = 50L;
    static final double MOVEMENT_THRESHOLD_SQ = 0.0009d; // ~0.03 blocks

    final ConcurrentHashMap<UUID, TState> activeTrails = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> tickTask;

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
    protected abstract void emitTrailOnWorldThread(TState state, List<PlayerRef> viewers);

    public void stopTrail(UUID playerId) {
        activeTrails.remove(playerId);
    }

    public boolean hasTrail(UUID playerId) {
        return activeTrails.containsKey(playerId);
    }

    public void shutdown() {
        ScheduledFuture<?> task = tickTask;
        if (task != null) {
            task.cancel(false);
            tickTask = null;
        }
        activeTrails.clear();
    }

    synchronized void ensureTickTask() {
        if (tickTask != null && !tickTask.isCancelled() && !tickTask.isDone()) {
            return;
        }
        tickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                this::tickTrails,
                0L,
                SCHEDULER_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private void tickTrails() {
        try {
            if (activeTrails.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            Map<World, List<PlayerRef>> viewersByWorld = collectWorldViewers();
            for (TState state : activeTrails.values()) {
                if (state == null || now < getNextEmissionAtMs(state)) {
                    continue;
                }
                setNextEmissionAtMs(state, now + Math.max(1L, getIntervalMs(state)));
                tickTrail(state, viewersByWorld.getOrDefault(getWorld(state), List.of()));
            }
        } catch (Exception e) {
            logger().atWarning().withCause(e).log("Trail scheduler tick failed");
        }
    }

    Map<World, List<PlayerRef>> collectWorldViewers() {
        Map<World, List<PlayerRef>> viewersByWorld = new HashMap<>();
        for (PlayerRef viewer : Universe.get().getPlayers()) {
            if (viewer == null || !viewer.isValid()) {
                continue;
            }
            Ref<EntityStore> viewerRef = viewer.getReference();
            if (viewerRef == null || !viewerRef.isValid()) {
                continue;
            }
            Store<EntityStore> viewerStore = viewerRef.getStore();
            World viewerWorld = viewerStore.getExternalData().getWorld();
            if (viewerWorld == null) {
                continue;
            }
            viewersByWorld.computeIfAbsent(viewerWorld, ignored -> new ArrayList<>()).add(viewer);
        }
        return viewersByWorld;
    }

    private void tickTrail(TState state, List<PlayerRef> viewers) {
        try {
            Ref<EntityStore> ref = getRef(state);
            if (ref == null || !ref.isValid()) {
                stopTrail(getPlayerId(state));
                return;
            }
            getWorld(state).execute(() -> {
                try {
                    if (activeTrails.get(getPlayerId(state)) != state) {
                        return;
                    }
                    if (!getRef(state).isValid()) {
                        stopTrail(getPlayerId(state));
                        return;
                    }
                    World currentWorld = getStore(state).getExternalData().getWorld();
                    if (currentWorld != getWorld(state)) {
                        stopTrail(getPlayerId(state));
                        return;
                    }
                    emitTrailOnWorldThread(state, viewers);
                } catch (Exception e) {
                    logger().atWarning().withCause(e).log("Trail tick error for " + getPlayerId(state));
                }
            });
        } catch (Exception e) {
            logger().atWarning().withCause(e).log("Trail schedule error for " + getPlayerId(state));
        }
    }

    void broadcastPacket(World world, List<PlayerRef> viewers, ToClientPacket packet) {
        for (PlayerRef viewer : viewers) {
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
            PacketHandler packetHandler = viewer.getPacketHandler();
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
}
