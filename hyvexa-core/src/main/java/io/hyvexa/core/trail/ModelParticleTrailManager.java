package io.hyvexa.core.trail;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.protocol.ModelParticle;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
 * Manages active model-particle trails per player.
 */
public class ModelParticleTrailManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ModelParticleTrailManager INSTANCE = new ModelParticleTrailManager();
    private static final long SCHEDULER_INTERVAL_MS = 50L;
    private static final double MOVEMENT_THRESHOLD_SQ = 0.0009d; // ~0.03 blocks

    private final ConcurrentHashMap<UUID, TrailState> activeTrails = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> tickTask;

    private ModelParticleTrailManager() {}

    public static ModelParticleTrailManager getInstance() {
        return INSTANCE;
    }

    public void startTrail(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, World world,
                           String particleId, float scale, long intervalMs,
                           float xOffset, float yOffset, float zOffset) {
        stopTrail(playerId);
        if (playerId == null || ref == null || store == null || world == null || particleId == null || particleId.isBlank()) {
            return;
        }
        activeTrails.put(playerId, new TrailState(
                playerId, ref, store, world, particleId, scale, intervalMs, xOffset, yOffset, zOffset
        ));
        ensureTickTask();
    }

    public void stopTrail(UUID playerId) {
        TrailState removed = activeTrails.remove(playerId);
        sendClearPacket(removed);
    }

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
            LOGGER.atWarning().withCause(e).log("Model trail scheduler tick failed");
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
            LOGGER.atWarning().withCause(e).log("Model trail schedule error for " + state.playerId);
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

            Player source = state.store.getComponent(state.ref, Player.getComponentType());
            if (source == null) {
                stopTrail(state.playerId);
                return;
            }
            TransformComponent transform = state.store.getComponent(state.ref, TransformComponent.getComponentType());
            if (transform == null || transform.getPosition() == null) {
                return;
            }
            var position = transform.getPosition();
            if (Double.isNaN(state.lastPos[0])) {
                state.lastPos[0] = position.getX();
                state.lastPos[1] = position.getY();
                state.lastPos[2] = position.getZ();
                return;
            }

            double dx = position.getX() - state.lastPos[0];
            double dy = position.getY() - state.lastPos[1];
            double dz = position.getZ() - state.lastPos[2];
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            state.lastPos[0] = position.getX();
            state.lastPos[1] = position.getY();
            state.lastPos[2] = position.getZ();
            if (distSq <= MOVEMENT_THRESHOLD_SQ) {
                return;
            }

            ModelParticle particle = new ModelParticle(
                    state.particleId,
                    state.scale,
                    null,
                    EntityPart.Entity,
                    null,
                    new Vector3f(state.xOffset, state.yOffset, state.zOffset),
                    null,
                    false
            );
            SpawnModelParticles packet = new SpawnModelParticles(
                    source.getNetworkId(),
                    new ModelParticle[]{particle}
            );
            broadcastPacket(state.world, viewers, packet);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Model trail tick error for " + state.playerId);
        }
    }

    private void broadcastPacket(World world, List<PlayerRef> viewers, SpawnModelParticles packet) {
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

    private void sendClearPacket(TrailState state) {
        if (state == null || state.ref == null || !state.ref.isValid()) {
            return;
        }
        World world = state.store.getExternalData().getWorld();
        Player source = state.store.getComponent(state.ref, Player.getComponentType());
        if (world == null || source == null) {
            return;
        }

        SpawnModelParticles clearPacket = new SpawnModelParticles(
                source.getNetworkId(),
                new ModelParticle[0]
        );
        broadcastPacket(world, collectWorldViewers().getOrDefault(world, List.of()), clearPacket);
    }

    private static final class TrailState {
        private final UUID playerId;
        private final Ref<EntityStore> ref;
        private final Store<EntityStore> store;
        private final World world;
        private final String particleId;
        private final float scale;
        private final long intervalMs;
        private final float xOffset;
        private final float yOffset;
        private final float zOffset;
        private final double[] lastPos = new double[]{Double.NaN, 0d, 0d};
        private volatile long nextEmissionAtMs;

        private TrailState(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, World world,
                           String particleId, float scale, long intervalMs,
                           float xOffset, float yOffset, float zOffset) {
            this.playerId = playerId;
            this.ref = ref;
            this.store = store;
            this.world = world;
            this.particleId = particleId;
            this.scale = scale;
            this.intervalMs = intervalMs;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.zOffset = zOffset;
            this.nextEmissionAtMs = 0L;
        }
    }
}
