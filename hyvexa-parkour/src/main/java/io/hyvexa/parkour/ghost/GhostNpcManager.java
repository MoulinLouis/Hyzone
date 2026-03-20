package io.hyvexa.parkour.ghost;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import io.hyvexa.common.ghost.GhostRecording;
import io.hyvexa.common.ghost.GhostSample;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.common.util.EntityUtils;
import io.hyvexa.common.util.OrphanedEntityCleanup;
import io.hyvexa.common.visibility.EntityVisibilityManager;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.util.PlayerSettingsStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GhostNpcManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long TICK_INTERVAL_MS = 50;
    private static final String GHOST_UUIDS_FILE = "ghost_uuids.txt";
    private static final String NPC_ENTITY_TYPE = "Kweebec_Seedling";
    private static final String NPC_DISPLAY_NAME = "Ghost";

    private final GhostStore ghostStore;
    private final MapStore mapStore;
    private final ConcurrentHashMap<UUID, GhostNpcState> activeGhosts = new ConcurrentHashMap<>();
    private final OrphanedEntityCleanup orphanCleanup;
    private ScheduledFuture<?> tickTask;
    private volatile NPCPlugin npcPlugin;

    public GhostNpcManager(GhostStore ghostStore, MapStore mapStore) {
        this.ghostStore = ghostStore;
        this.mapStore = mapStore;
        this.orphanCleanup = new OrphanedEntityCleanup(LOGGER,
                Path.of("mods", "Parkour", GHOST_UUIDS_FILE));
    }

    public void start() {
        orphanCleanup.loadOrphanedUuids();
        registerCleanupSystem();

        try {
            npcPlugin = NPCPlugin.get();
        } catch (Exception e) {
            LOGGER.atWarning().log("NPCPlugin not available, ghosts will not spawn: " + e.getMessage());
            npcPlugin = null;
        }

        tickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
            this::tick,
            TICK_INTERVAL_MS,
            TICK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
        Set<UUID> activeUuids = new HashSet<>();
        for (GhostNpcState state : activeGhosts.values()) {
            UUID entityUuid = state.entityUuid;
            if (entityUuid != null) {
                activeUuids.add(entityUuid);
            }
        }
        orphanCleanup.saveUuidsForCleanup(activeUuids);
        for (UUID playerId : List.copyOf(activeGhosts.keySet())) {
            despawnGhost(playerId);
        }
        activeGhosts.clear();
    }

    public void spawnGhost(UUID playerId, String mapId) {
        if (npcPlugin == null || playerId == null || mapId == null) {
            return;
        }

        if (!PlayerSettingsStore.isGhostVisible(playerId)) {
            return;
        }

        GhostRecording recording = ghostStore.getRecording(playerId, mapId);
        if (recording == null) {
            return; // No recording, no ghost
        }

        Map map = mapStore.getMapReadonly(mapId);
        if (map == null || map.getStart() == null) {
            return;
        }

        // Despawn existing ghost for this player first
        despawnGhost(playerId);

        String worldName = map.getWorld();
        if (worldName == null || worldName.isEmpty()) {
            return;
        }

        World world = Universe.get().getWorld(worldName);
        if (world == null) {
            return;
        }

        GhostNpcState state = new GhostNpcState(playerId, mapId, recording, worldName);
        activeGhosts.put(playerId, state);

        state.spawning = true;
        world.execute(() -> spawnNpcOnWorldThread(state, map, world));
    }

    public void startPlayback(UUID playerId) {
        GhostNpcState state = activeGhosts.get(playerId);
        if (state == null) {
            return;
        }
        state.playbackStartMs = System.currentTimeMillis();
    }

    public void despawnGhost(UUID playerId) {
        if (playerId == null) {
            return;
        }
        GhostNpcState state = activeGhosts.remove(playerId);
        if (state == null) {
            return;
        }
        despawnNpcEntity(state);
    }

    public void hideGhostsFromPlayer(UUID viewerId) {
        if (viewerId == null) {
            return;
        }
        EntityVisibilityManager visibilityManager = EntityVisibilityManager.get();
        for (GhostNpcState state : activeGhosts.values()) {
            UUID entityUuid = state.entityUuid;
            if (entityUuid != null) {
                visibilityManager.hideEntity(viewerId, entityUuid);
            }
        }
    }

    private void spawnNpcOnWorldThread(GhostNpcState state, Map map, World world) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store == null) {
                return;
            }

            Vector3d position = new Vector3d(
                map.getStart().getX(),
                map.getStart().getY(),
                map.getStart().getZ()
            );
            Vector3f rotation = new Vector3f(
                map.getStart().getRotX(),
                map.getStart().getRotY(),
                map.getStart().getRotZ()
            );

            Object result = npcPlugin.spawnNPC(store, NPC_ENTITY_TYPE, NPC_DISPLAY_NAME, position, rotation);
            if (result != null) {
                Ref<EntityStore> entityRef = EntityUtils.extractEntityRef(result);
                if (entityRef != null) {
                    state.entityRef = entityRef;

                    UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
                    if (uuidComponent != null) {
                        UUID entityUuid = uuidComponent.getUuid();
                        state.entityUuid = entityUuid;
                        hideFromAllExceptOwner(state.ownerId, entityUuid);
                    }

                    store.addComponent(entityRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
                    store.addComponent(entityRef, Frozen.getComponentType(), Frozen.get());
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to spawn ghost NPC: " + e.getMessage());
        } finally {
            state.spawning = false;
        }
    }

    private void hideFromAllExceptOwner(UUID ownerId, UUID entityUuid) {
        EntityVisibilityManager visibilityManager = EntityVisibilityManager.get();
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) {
                continue;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId == null || playerId.equals(ownerId)) {
                continue; // Don't hide from the owner
            }
            visibilityManager.hideEntity(playerId, entityUuid);
        }
    }


    private void despawnNpcEntity(GhostNpcState state) {
        if (state == null) {
            return;
        }
        Ref<EntityStore> entityRef = state.entityRef;
        if (entityRef == null || !entityRef.isValid()) {
            queueOrphanIfDespawnFailed(state.entityUuid);
            state.entityRef = null;
            return;
        }

        Map map = mapStore.getMapReadonly(state.mapId);
        if (map != null && map.getWorld() != null) {
            World world = Universe.get().getWorld(map.getWorld());
            if (world != null) {
                world.execute(() -> removeEntityOnWorldThread(state, entityRef));
                return;
            }
        }

        // Fallback: try without world thread
        removeEntityOnWorldThread(state, entityRef);
    }

    private void removeEntityOnWorldThread(GhostNpcState state, Ref<EntityStore> entityRef) {
        boolean despawnSuccess = false;
        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store != null) {
                store.removeEntity(entityRef, RemoveReason.REMOVE);
                despawnSuccess = true;
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to despawn ghost NPC: " + e.getMessage());
        }
        state.entityRef = null;
        if (despawnSuccess) {
            state.entityUuid = null;
        } else {
            queueOrphanIfDespawnFailed(state.entityUuid);
        }
    }

    private void tick() {
        try {
            orphanCleanup.processPendingRemovals();
            long now = System.currentTimeMillis();
            List<UUID> toDespawn = null;
            for (GhostNpcState state : activeGhosts.values()) {
                if (tickGhost(state, now)) {
                    if (toDespawn == null) toDespawn = new ArrayList<>();
                    toDespawn.add(state.ownerId);
                }
            }
            if (toDespawn != null) {
                for (UUID id : toDespawn) {
                    despawnGhost(id);
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Error in ghost tick: " + e.getMessage());
        }
    }

    /** @return true if the ghost should be despawned */
    private boolean tickGhost(GhostNpcState state, long now) {
        // Not started playback yet
        if (state.playbackStartMs <= 0) {
            return false;
        }

        Ref<EntityStore> entityRef = state.entityRef;
        if (entityRef == null || !entityRef.isValid()) {
            return false;
        }

        GhostRecording recording = state.recording;
        String worldName = state.worldName;

        World world = Universe.get().getWorld(worldName);
        if (world == null) {
            return false;
        }

        long elapsed = now - state.playbackStartMs;
        long completionTimeMs = recording.getCompletionTimeMs();
        if (completionTimeMs <= 0) {
            return false;
        }

        double progress = (double) elapsed / (double) completionTimeMs;

        if (progress >= 1.0) {
            return true;
        }

        GhostSample sample = recording.interpolateAt(progress);
        double[] targetPos = sample.toPositionArray();
        float yaw = sample.yaw();

        world.execute(() -> teleportNpc(entityRef, world, targetPos, yaw));
        return false;
    }

    private void teleportNpc(Ref<EntityStore> entityRef, World world,
                             double[] targetPos, float yaw) {
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }
        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store == null) {
                return;
            }
            Vector3d targetVec = new Vector3d(targetPos[0], targetPos[1], targetPos[2]);
            Vector3f rotation = new Vector3f(0, yaw, 0);
            store.addComponent(entityRef, Teleport.getComponentType(),
                new Teleport(world, targetVec, rotation));
        } catch (Exception e) {
            // Silently ignore teleport errors
        }
    }

    private static class GhostNpcState {
        final UUID ownerId;
        final String mapId;
        final GhostRecording recording;
        final String worldName;
        volatile Ref<EntityStore> entityRef;
        volatile UUID entityUuid;
        volatile long playbackStartMs;
        volatile boolean spawning;

        GhostNpcState(UUID ownerId, String mapId, GhostRecording recording, String worldName) {
            this.ownerId = ownerId;
            this.mapId = mapId;
            this.recording = recording;
            this.worldName = worldName;
        }
    }

    private void registerCleanupSystem() {
        var registry = EntityStore.REGISTRY;
        if (!registry.hasSystemClass(GhostCleanupSystem.class)) {
            registry.registerSystem(new GhostCleanupSystem(this));
        }
    }

    private void queueOrphanIfDespawnFailed(UUID entityUuid) {
        orphanCleanup.addOrphan(entityUuid);
    }

    public boolean isOrphanedGhost(UUID entityUuid) {
        return orphanCleanup.isOrphaned(entityUuid);
    }

    public boolean isActiveGhostUuid(UUID entityUuid) {
        if (entityUuid == null) {
            return false;
        }
        if (orphanCleanup.isPendingRemoval(entityUuid)) {
            return true;
        }
        for (GhostNpcState state : activeGhosts.values()) {
            if (entityUuid.equals(state.entityUuid)) {
                return true;
            }
        }
        return false;
    }

    public void queueOrphanForRemoval(UUID entityUuid, Ref<EntityStore> entityRef) {
        orphanCleanup.queueForRemoval(entityUuid, entityRef);
    }

    public boolean hasPendingSpawn() {
        for (GhostNpcState state : activeGhosts.values()) {
            if (state.spawning) {
                return true;
            }
        }
        return false;
    }

}
