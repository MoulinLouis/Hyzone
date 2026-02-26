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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

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

    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> activeTrails = new ConcurrentHashMap<>();

    private ModelParticleTrailManager() {}

    public static ModelParticleTrailManager getInstance() {
        return INSTANCE;
    }

    public void startTrail(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, World world,
                           String particleId, float scale, long intervalMs,
                           float xOffset, float yOffset, float zOffset) {
        stopTrail(playerId);

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

                        Player source = store.getComponent(ref, Player.getComponentType());
                        if (source == null) {
                            stopTrail(playerId);
                            return;
                        }

                        ModelParticle particle = new ModelParticle(
                                particleId,
                                scale,
                                null,
                                EntityPart.Entity,
                                null,
                                new Vector3f(xOffset, yOffset, zOffset),
                                null,
                                false
                        );
                        SpawnModelParticles packet = new SpawnModelParticles(
                                source.getNetworkId(),
                                new ModelParticle[]{particle}
                        );

                        for (PlayerRef viewer : Universe.get().getPlayers()) {
                            if (viewer == null || !viewer.isValid()) continue;
                            Ref<EntityStore> viewerRef = viewer.getReference();
                            if (viewerRef == null || !viewerRef.isValid()) continue;
                            Store<EntityStore> viewerStore = viewerRef.getStore();
                            World viewerWorld = viewerStore.getExternalData().getWorld();
                            if (viewerWorld != world) continue;

                            PacketHandler ph = viewer.getPacketHandler();
                            if (ph == null) continue;
                            ph.writeNoCache(packet);
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Model trail tick error for " + playerId);
                    }
                });
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Model trail schedule error for " + playerId);
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        activeTrails.put(playerId, future);
    }

    public void stopTrail(UUID playerId) {
        ScheduledFuture<?> future = activeTrails.remove(playerId);
        if (future != null) {
            future.cancel(false);
        }
        sendClearPacket(playerId);
    }

    public boolean hasTrail(UUID playerId) {
        return activeTrails.containsKey(playerId);
    }

    private void sendClearPacket(UUID playerId) {
        if (playerId == null) return;

        for (PlayerRef sourceRef : Universe.get().getPlayers()) {
            if (sourceRef == null || !sourceRef.isValid()) continue;
            if (!playerId.equals(sourceRef.getUuid())) continue;

            Ref<EntityStore> entityRef = sourceRef.getReference();
            if (entityRef == null || !entityRef.isValid()) return;
            Store<EntityStore> entityStore = entityRef.getStore();
            World world = entityStore.getExternalData().getWorld();
            Player source = entityStore.getComponent(entityRef, Player.getComponentType());
            if (world == null || source == null) return;

            SpawnModelParticles clearPacket = new SpawnModelParticles(
                    source.getNetworkId(),
                    new ModelParticle[0]
            );

            for (PlayerRef viewer : Universe.get().getPlayers()) {
                if (viewer == null || !viewer.isValid()) continue;
                Ref<EntityStore> viewerRef = viewer.getReference();
                if (viewerRef == null || !viewerRef.isValid()) continue;
                Store<EntityStore> viewerStore = viewerRef.getStore();
                World viewerWorld = viewerStore.getExternalData().getWorld();
                if (viewerWorld != world) continue;

                PacketHandler ph = viewer.getPacketHandler();
                if (ph == null) continue;
                ph.writeNoCache(clearPacket);
            }
            return;
        }
    }
}
