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

    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> activeTrails = new ConcurrentHashMap<>();

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
        final double[] lastPos = new double[]{Double.NaN, 0d, 0d};
        final double movementThresholdSq = 0.0009d; // ~0.03 blocks

        ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                if (ref == null || !ref.isValid()) {
                    stopTrail(playerId);
                    return;
                }
                world.execute(() -> {
                    try {
                        if (!ref.isValid()) {
                            stopTrail(playerId);
                            return;
                        }
                        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                        if (transform == null || transform.getPosition() == null) return;

                        var pos = transform.getPosition();
                        if (Double.isNaN(lastPos[0])) {
                            lastPos[0] = pos.getX();
                            lastPos[1] = pos.getY();
                            lastPos[2] = pos.getZ();
                            return;
                        }

                        double dx = pos.getX() - lastPos[0];
                        double dy = pos.getY() - lastPos[1];
                        double dz = pos.getZ() - lastPos[2];
                        double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                        lastPos[0] = pos.getX();
                        lastPos[1] = pos.getY();
                        lastPos[2] = pos.getZ();
                        if (distSq <= movementThresholdSq) {
                            return;
                        }

                        SpawnParticleSystem packet = new SpawnParticleSystem(
                                particleId,
                                new Position(pos.getX(), pos.getY() + 0.1, pos.getZ()),
                                new Direction(0f, 0f, 0f),
                                scale,
                                new Color((byte) 255, (byte) 255, (byte) 255)
                        );

                        for (PlayerRef pr : Universe.get().getPlayers()) {
                            if (pr == null || !pr.isValid()) continue;
                            Ref<EntityStore> viewerRef = pr.getReference();
                            if (viewerRef == null || !viewerRef.isValid()) continue;
                            Store<EntityStore> viewerStore = viewerRef.getStore();
                            World viewerWorld = viewerStore.getExternalData().getWorld();
                            if (viewerWorld != world) continue;

                            PacketHandler ph = pr.getPacketHandler();
                            if (ph == null) continue;
                            ph.writeNoCache(packet);
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Trail tick error for " + playerId);
                    }
                });
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Trail schedule error for " + playerId);
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        activeTrails.put(playerId, future);
    }

    /**
     * Stop and remove a player's active trail.
     */
    public void stopTrail(UUID playerId) {
        ScheduledFuture<?> future = activeTrails.remove(playerId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * Check if a player has an active trail.
     */
    public boolean hasTrail(UUID playerId) {
        return activeTrails.containsKey(playerId);
    }
}
