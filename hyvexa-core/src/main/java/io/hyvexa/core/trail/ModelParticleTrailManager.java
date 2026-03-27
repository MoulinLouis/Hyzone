package io.hyvexa.core.trail;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.protocol.ModelParticle;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;

/**
 * Manages active model-particle trails per player.
 */
public class ModelParticleTrailManager extends AbstractTrailManager<ModelParticleTrailManager.TrailState> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public ModelParticleTrailManager() {}

    @Override
    protected HytaleLogger logger() {
        return LOGGER;
    }

    @Override
    protected UUID getPlayerId(TrailState state) {
        return state.playerId;
    }

    @Override
    protected Ref<EntityStore> getRef(TrailState state) {
        return state.ref;
    }

    @Override
    protected Store<EntityStore> getStore(TrailState state) {
        return state.store;
    }

    @Override
    protected World getWorld(TrailState state) {
        return state.world;
    }

    @Override
    protected long getIntervalMs(TrailState state) {
        return state.intervalMs;
    }

    @Override
    protected long getNextEmissionAtMs(TrailState state) {
        return state.nextEmissionAtMs;
    }

    @Override
    protected void setNextEmissionAtMs(TrailState state, long ms) {
        state.nextEmissionAtMs = ms;
    }

    public void startTrail(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, World world,
                           String particleId, float scale, long intervalMs,
                           float xOffset, float yOffset, float zOffset) {
        stopTrail(playerId);
        if (playerId == null || ref == null || store == null || world == null || particleId == null || particleId.isBlank()) {
            return;
        }
        putTrailState(playerId, new TrailState(
                playerId, ref, store, world, particleId, scale, intervalMs, xOffset, yOffset, zOffset
        ));
        ensureTickTask();
    }

    @Override
    public void stopTrail(UUID playerId) {
        TrailState removed = removeTrailState(playerId);
        if (removed != null && removed.world != null) {
            try {
                removed.world.execute(() -> sendClearPacket(removed));
            } catch (Exception ignored) {}
        }
        cancelTickTaskIfIdle();
    }

    @Override
    protected void emitTrailOnWorldThread(TrailState state, List<AbstractTrailManager.ViewerState> viewers) {
        Player source = state.store.getComponent(state.ref, Player.getComponentType());
        if (source == null) {
            stopTrail(state.playerId);
            return;
        }

        double[] pos = updatePositionAndCheckMovement(state.store, state.ref, state.lastPos);
        if (pos == null) {
            return;
        }

        NetworkId nid = state.store.getComponent(state.ref, NetworkId.getComponentType());
        if (nid == null) {
            stopTrail(state.playerId);
            return;
        }

        SpawnModelParticles packet = new SpawnModelParticles(
                nid.getId(),
                state.particlePayload
        );
        broadcastPacket(state.world, viewers, packet, pos[0], pos[1], pos[2]);
    }

    private void sendClearPacket(TrailState state) {
        if (state == null || state.ref == null || !state.ref.isValid()) {
            return;
        }
        if (state.store.getExternalData() == null) return;
        World world = state.store.getExternalData().getWorld();
        NetworkId nid = state.store.getComponent(state.ref, NetworkId.getComponentType());
        if (world == null || nid == null) {
            return;
        }

        SpawnModelParticles clearPacket = new SpawnModelParticles(
                nid.getId(),
                new ModelParticle[0]
        );
        broadcastPacket(world, collectViewersForWorld(world), clearPacket);
    }

    static final class TrailState {
        final UUID playerId;
        final Ref<EntityStore> ref;
        final Store<EntityStore> store;
        final World world;
        final String particleId;
        final float scale;
        final long intervalMs;
        final float xOffset;
        final float yOffset;
        final float zOffset;
        final ModelParticle[] particlePayload;
        final double[] lastPos = new double[]{Double.NaN, 0d, 0d};
        volatile long nextEmissionAtMs;

        TrailState(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, World world,
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
            this.particlePayload = new ModelParticle[]{
                    new ModelParticle(
                            particleId,
                            scale,
                            null,
                            EntityPart.Entity,
                            null,
                            new Vector3f(xOffset, yOffset, zOffset),
                            null,
                            false
                    )
            };
            this.nextEmissionAtMs = 0L;
        }
    }
}
