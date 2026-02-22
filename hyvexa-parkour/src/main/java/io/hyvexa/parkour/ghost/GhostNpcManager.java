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
import io.hyvexa.common.visibility.EntityVisibilityManager;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.util.PlayerSettingsStore;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GhostNpcManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long TICK_INTERVAL_MS = 50;
    private static final String NPC_ENTITY_TYPE = "Kweebec_Seedling";
    private static final String NPC_DISPLAY_NAME = "Ghost";

    private final GhostStore ghostStore;
    private final MapStore mapStore;
    private final ConcurrentHashMap<UUID, GhostNpcState> activeGhosts = new ConcurrentHashMap<>();
    private ScheduledFuture<?> tickTask;
    private volatile NPCPlugin npcPlugin;

    public GhostNpcManager(GhostStore ghostStore, MapStore mapStore) {
        this.ghostStore = ghostStore;
        this.mapStore = mapStore;
    }

    public void start() {
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

        Map map = mapStore.getMap(mapId);
        if (map == null || map.getStart() == null) {
            return;
        }

        // Despawn existing ghost for this player first
        despawnGhost(playerId);

        GhostNpcState state = new GhostNpcState(playerId, mapId);
        activeGhosts.put(playerId, state);

        String worldName = map.getWorld();
        if (worldName == null || worldName.isEmpty()) {
            return;
        }

        World world = Universe.get().getWorld(worldName);
        if (world == null) {
            return;
        }

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
                Ref<EntityStore> entityRef = extractEntityRef(result);
                if (entityRef != null) {
                    state.entityRef = entityRef;

                    // Extract entity UUID for visibility
                    try {
                        UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
                        if (uuidComponent != null) {
                            UUID entityUuid = uuidComponent.getUuid();
                            state.entityUuid = entityUuid;

                            // Hide from ALL players except the owner
                            hideFromAllExceptOwner(state.ownerId, entityUuid);
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().log("Failed to get ghost NPC UUID: " + e.getMessage());
                    }

                    // Make NPC invulnerable
                    try {
                        store.addComponent(entityRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
                    } catch (Exception e) {
                        LOGGER.atWarning().log("Failed to make ghost NPC invulnerable: " + e.getMessage());
                    }

                    // Freeze NPC to disable AI movement
                    try {
                        store.addComponent(entityRef, Frozen.getComponentType(), Frozen.get());
                    } catch (Exception e) {
                        LOGGER.atWarning().log("Failed to freeze ghost NPC: " + e.getMessage());
                    }
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

    @SuppressWarnings("unchecked")
    private Ref<EntityStore> extractEntityRef(Object pairResult) {
        if (pairResult == null) {
            return null;
        }
        try {
            for (String methodName : List.of("getFirst", "getLeft", "getKey", "first", "left")) {
                try {
                    java.lang.reflect.Method method = pairResult.getClass().getMethod(methodName);
                    Object value = method.invoke(pairResult);
                    if (value instanceof Ref<?> ref) {
                        return (Ref<EntityStore>) ref;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Try next method
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to extract entity ref from NPC result: " + e.getMessage());
        }
        return null;
    }

    private void despawnNpcEntity(GhostNpcState state) {
        if (state == null) {
            return;
        }
        Ref<EntityStore> entityRef = state.entityRef;
        if (entityRef == null || !entityRef.isValid()) {
            state.entityRef = null;
            return;
        }

        Map map = mapStore.getMap(state.mapId);
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
        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store != null) {
                store.removeEntity(entityRef, RemoveReason.REMOVE);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to despawn ghost NPC: " + e.getMessage());
        }
        state.entityRef = null;
        state.entityUuid = null;
    }

    private void tick() {
        try {
            long now = System.currentTimeMillis();
            for (GhostNpcState state : activeGhosts.values()) {
                tickGhost(state, now);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Error in ghost tick: " + e.getMessage());
        }
    }

    private void tickGhost(GhostNpcState state, long now) {
        // Not started playback yet
        if (state.playbackStartMs <= 0) {
            return;
        }

        Ref<EntityStore> entityRef = state.entityRef;
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        GhostRecording recording = ghostStore.getRecording(state.ownerId, state.mapId);
        if (recording == null) {
            return;
        }

        Map map = mapStore.getMap(state.mapId);
        if (map == null) {
            return;
        }

        String worldName = map.getWorld();
        if (worldName == null || worldName.isEmpty()) {
            return;
        }

        World world = Universe.get().getWorld(worldName);
        if (world == null) {
            return;
        }

        long elapsed = now - state.playbackStartMs;
        long completionTimeMs = recording.getCompletionTimeMs();
        if (completionTimeMs <= 0) {
            return;
        }

        double progress = (double) elapsed / (double) completionTimeMs;

        if (progress >= 1.0) {
            // Ghost finished the run, despawn it
            despawnGhost(state.ownerId);
            return;
        }

        GhostSample sample = recording.interpolateAt(progress);
        double[] targetPos = sample.toPositionArray();
        float yaw = sample.yaw();

        world.execute(() -> teleportNpc(entityRef, world, targetPos, yaw));
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
        volatile Ref<EntityStore> entityRef;
        volatile UUID entityUuid;
        volatile long playbackStartMs;
        volatile boolean spawning;

        GhostNpcState(UUID ownerId, String mapId) {
            this.ownerId = ownerId;
            this.mapId = mapId;
        }
    }
}
