package io.hyvexa.core.trail;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;

/**
 * Manages active particle trails per player.
 * A trail is a repeating scheduled task that reads the player's position
 * and broadcasts SpawnParticleSystem packets to all connected players.
 */
public class TrailManager extends AbstractTrailManager<TrailManager.TrailState> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final TrailManager INSTANCE = new TrailManager();
    private static final Color WHITE = new Color((byte) 255, (byte) 255, (byte) 255);

    private TrailManager() {}

    public static TrailManager getInstance() {
        return INSTANCE;
    }

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
        putTrailState(playerId, new TrailState(playerId, ref, store, world, particleId, scale, intervalMs));
        ensureTickTask();
    }

    @Override
    protected void emitTrailOnWorldThread(TrailState state, List<AbstractTrailManager.ViewerState> viewers) {
        double[] pos = updatePositionAndCheckMovement(state.store, state.ref, state.lastPos);
        if (pos == null) {
            return;
        }

        SpawnParticleSystem packet = new SpawnParticleSystem(
                state.particleId,
                new Position(pos[0], pos[1] + 0.1, pos[2]),
                new Direction(0f, 0f, 0f),
                state.scale,
                WHITE
        );
        broadcastPacket(state.world, viewers, packet, pos[0], pos[1], pos[2]);
    }

    static final class TrailState {
        final UUID playerId;
        final Ref<EntityStore> ref;
        final Store<EntityStore> store;
        final World world;
        final String particleId;
        final float scale;
        final long intervalMs;
        final double[] lastPos = new double[]{Double.NaN, 0d, 0d};
        volatile long nextEmissionAtMs;

        TrailState(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store,
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
