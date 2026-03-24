package io.hyvexa.ascend.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.npc.NPCHelper;
import io.hyvexa.common.visibility.EntityVisibilityManager;

import java.util.UUID;

class RobotSpawner {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final RobotManager manager;
    private volatile NPCPlugin npcPlugin;

    RobotSpawner(RobotManager manager) {
        this.manager = manager;
    }

    void initNpcPlugin() {
        npcPlugin = NPCHelper.initNpcPlugin(LOGGER, "robots");
    }

    boolean isNpcAvailable() {
        return npcPlugin != null;
    }

    NPCPlugin getNpcPlugin() {
        return npcPlugin;
    }

    void spawnNpcForRobot(RobotState state, AscendMap map) {
        if (npcPlugin == null || map == null) {
            return;
        }
        // Prevent duplicate spawns
        if (state.isSpawning()) {
            return;
        }
        // Check if we already have a valid entity
        Ref<EntityStore> existingRef = state.getEntityRef();
        if (existingRef != null && existingRef.isValid()) {
            return;
        }
        // HARD CAP: If entityUuid is set, an entity may still exist in world
        // (e.g., in unloaded chunk). Don't spawn another one.
        UUID existingUuid = state.getEntityUuid();
        if (existingUuid != null) {
            return;
        }
        state.setSpawning(true);
        String worldName = map.getWorld();
        if (worldName == null || worldName.isEmpty()) {
            state.setSpawning(false);
            return;
        }
        World world = Universe.get().getWorld(worldName);
        if (world == null) {
            state.setSpawning(false);
            return;
        }

        // Must run on World thread for entity operations
        world.execute(() -> spawnNpcOnWorldThread(state, map, world));
    }

    private void spawnNpcOnWorldThread(RobotState state, AscendMap map, World world) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store == null) {
                return;
            }
            Vector3d position = new Vector3d(map.getStartX(), map.getStartY(), map.getStartZ());
            Vector3f rotation = new Vector3f(map.getStartRotX(), map.getStartRotY(), map.getStartRotZ());
            String displayName = "Runner";

            String npcRoleName = AscendConstants.getRunnerEntityType(state.getStars());
            Object result = npcPlugin.spawnNPC(store, npcRoleName, displayName, position, rotation);
            if (result != null) {
                Ref<EntityStore> entityRef = NPCHelper.extractEntityRef(result, LOGGER);
                if (entityRef != null) {
                    state.setEntityRef(entityRef);
                    // Extract and store entity UUID for visibility filtering
                    try {
                        UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
                        if (uuidComponent != null) {
                            UUID entityUuid = uuidComponent.getUuid();
                            state.setEntityUuid(entityUuid);
                            if (entityUuid != null) {
                                manager.getActiveEntityUuids().add(entityUuid);
                            }

                            // Hide from players currently running on this map
                            hideFromActiveRunners(state.getMapId(), entityUuid);
                            // Hide from players with "hide other runners" setting
                            hideFromViewersWithSetting(state.getOwnerId(), entityUuid);
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().log("Failed to get NPC UUID: " + e.getMessage());
                    }
                    NPCHelper.setupNpcDefaults(store, entityRef, LOGGER);
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to spawn NPC: " + e.getMessage());
        } finally {
            state.setSpawning(false);
        }
    }

    void despawnNpcForRobot(RobotState state) {
        if (state == null) {
            return;
        }
        Ref<EntityStore> entityRef = state.getEntityRef();
        if (entityRef == null || !entityRef.isValid()) {
            state.setEntityRef(null);
            // Note: We intentionally do NOT clear entityUuid here.
            // If isValid() is false due to chunk unload, the entity still exists
            // and will reappear when chunks reload. Only clear UUID on successful despawn.
            return;
        }
        // Get world from map to run on world thread
        AscendMap map = manager.getMapStore() != null ? manager.getMapStore().getMap(state.getMapId()) : null;
        if (map != null && map.getWorld() != null) {
            World world = Universe.get().getWorld(map.getWorld());
            if (world != null) {
                world.execute(() -> despawnNpcOnWorldThread(state, entityRef));
                return;
            }
        }
        // Fallback: try without world thread (may fail)
        despawnNpcOnWorldThread(state, entityRef);
    }

    private void despawnNpcOnWorldThread(RobotState state, Ref<EntityStore> entityRef) {
        boolean despawnSuccess = NPCHelper.despawnEntity(entityRef, LOGGER);
        state.setEntityRef(null);
        // Only clear entityUuid if despawn was successful.
        // If despawn failed, the entity may still exist (e.g., in unloaded chunk)
        // and we don't want to spawn a duplicate when it reloads.
        if (despawnSuccess) {
            UUID clearedUuid = state.getEntityUuid();
            state.setEntityUuid(null);
            if (clearedUuid != null) {
                manager.getActiveEntityUuids().remove(clearedUuid);
            }
        }
    }

    /**
     * Hide a newly spawned runner from all players currently running on the same map.
     * Skips the runner's owner so they can still see their own runner while playing.
     */
    void hideFromActiveRunners(String mapId, UUID runnerUuid) {
        AscendRunTracker runTracker = manager.getRunTracker();
        if (runTracker == null) {
            return;
        }
        // Find the owner of this runner to skip them
        UUID runnerOwnerId = null;
        for (RobotState state : manager.getRobots().values()) {
            if (runnerUuid.equals(state.getEntityUuid())) {
                runnerOwnerId = state.getOwnerId();
                break;
            }
        }
        EntityVisibilityManager visibilityManager = EntityVisibilityManager.get();
        // Iterate over all online players in the Ascend world
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) {
                continue;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId == null || !manager.isPlayerInAscendWorld(playerId)) {
                continue;
            }
            // Don't hide a runner from its owner - they should see their own runner
            if (playerId.equals(runnerOwnerId)) {
                continue;
            }
            String activeMapId = runTracker.getActiveMapId(playerId);
            if (mapId.equals(activeMapId)) {
                visibilityManager.hideEntity(playerId, runnerUuid);
            }
        }
    }

    /**
     * After spawning a new runner, hide it from all online viewers who have the "hide other runners" setting ON.
     * Skips the owner of the runner (they should always see their own).
     */
    void hideFromViewersWithSetting(UUID runnerOwnerId, UUID entityUuid) {
        if (entityUuid == null) {
            return;
        }
        EntityVisibilityManager visibilityManager = EntityVisibilityManager.get();
        for (UUID viewerId : manager.getOnlinePlayers()) {
            if (viewerId.equals(runnerOwnerId)) {
                continue; // Owner always sees their own runners
            }
            if (manager.getPlayerStore().isHideOtherRunners(viewerId)) {
                visibilityManager.hideEntity(viewerId, entityUuid);
            }
        }
    }

}
