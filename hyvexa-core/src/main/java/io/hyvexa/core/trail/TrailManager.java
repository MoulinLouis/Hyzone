package io.hyvexa.core.trail;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
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
 * Manages active particle trails per player.
 * A trail is a repeating scheduled task that reads the player's position
 * and broadcasts SpawnParticleSystem packets to all connected players.
 */
public class TrailManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final TrailManager INSTANCE = new TrailManager();
    private static final long SCHEDULER_INTERVAL_MS = 50L;
    private static final double MOVEMENT_THRESHOLD_SQ = 0.0009d; // ~0.03 blocks

    private final ConcurrentHashMap<UUID, TrailState> activeTrails = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> tickTask;

    private TrailManager() {}

    public static TrailManager getInstance() {
        return INSTANCE;
    }

    /**
     * Start a particle trail for a player.
     * Stops any existing trail first.
     */
    public void startTrail(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store,
                           World world, String particleId, float scale, long intervalMs) {
        stopTrail(playerId);
        if (playerId == null || ref == null || store == null || world == null || particleId == null || particleId.isBlank()) {
            return;
        }
        activeTrails.put(playerId, new TrailState(playerId, ref, store, world, particleId, scale, intervalMs));
        ensureTickTask();
    }

    /**
     * Stop and remove a player's active trail.
     */
    public void stopTrail(UUID playerId) {
        activeTrails.remove(playerId);
    }

    /**
     * Check if a player has an active trail.
     */
    public boolean hasTrail(UUID playerId) {
        return activeTrails.containsKey(playerId);
    }

    private synchronized void ensureTickTask() {
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
            for (TrailState state : activeTrails.values()) {
                if (state == null || now < state.nextEmissionAtMs) {
                    continue;
                }
                state.nextEmissionAtMs = now + Math.max(1L, state.intervalMs);
                tickTrail(state, viewersByWorld.getOrDefault(state.world, List.of()));
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Trail scheduler tick failed");
        }
    }

    private Map<World, List<PlayerRef>> collectWorldViewers() {
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

    private void tickTrail(TrailState state, List<PlayerRef> viewers) {
        try {
            if (state.ref == null || !state.ref.isValid()) {
                stopTrail(state.playerId);
                return;
            }
            state.world.execute(() -> emitTrailOnWorldThread(state, viewers));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Trail schedule error for " + state.playerId);
        }
    }

    private void emitTrailOnWorldThread(TrailState state, List<PlayerRef> viewers) {
        try {
            if (activeTrails.get(state.playerId) != state) {
                return;
            }
            if (!state.ref.isValid()) {
                stopTrail(state.playerId);
                return;
            }
            World currentWorld = state.store.getExternalData().getWorld();
            if (currentWorld != state.world) {
                stopTrail(state.playerId);
                return;
            }
            TransformComponent transform = state.store.getComponent(state.ref, TransformComponent.getComponentType());
            if (transform == null || transform.getPosition() == null) {
                return;
            }

            var pos = transform.getPosition();
            if (Double.isNaN(state.lastPos[0])) {
                state.lastPos[0] = pos.getX();
                state.lastPos[1] = pos.getY();
                state.lastPos[2] = pos.getZ();
                return;
            }

            double dx = pos.getX() - state.lastPos[0];
            double dy = pos.getY() - state.lastPos[1];
            double dz = pos.getZ() - state.lastPos[2];
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            state.lastPos[0] = pos.getX();
            state.lastPos[1] = pos.getY();
            state.lastPos[2] = pos.getZ();
            if (distSq <= MOVEMENT_THRESHOLD_SQ) {
                return;
            }

            SpawnParticleSystem packet = new SpawnParticleSystem(
                    state.particleId,
                    new Position(pos.getX(), pos.getY() + 0.1, pos.getZ()),
                    new Direction(0f, 0f, 0f),
                    state.scale,
                    new Color((byte) 255, (byte) 255, (byte) 255)
            );
            broadcastPacket(state.world, viewers, packet);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Trail tick error for " + state.playerId);
        }
    }

    private void broadcastPacket(World world, List<PlayerRef> viewers, SpawnParticleSystem packet) {
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

    private static final class TrailState {
        private final UUID playerId;
        private final Ref<EntityStore> ref;
        private final Store<EntityStore> store;
        private final World world;
        private final String particleId;
        private final float scale;
        private final long intervalMs;
        private final double[] lastPos = new double[]{Double.NaN, 0d, 0d};
        private volatile long nextEmissionAtMs;

        private TrailState(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store,
                           World world, String particleId, float scale, long intervalMs) {
            this.playerId = playerId;
            this.ref = ref;
            this.store = store;
            this.world = world;
            this.particleId = particleId;
            this.scale = scale;
            this.intervalMs = intervalMs;
            this.nextEmissionAtMs = 0L;
        }
    }
}
