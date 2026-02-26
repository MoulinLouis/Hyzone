package io.hyvexa.parkour.pet;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages per-player pet NPCs using Frozen + periodic Teleport.
 * Player ref is resolved fresh each tick from the world's EntityStore (not stored at spawn).
 */
public class PetManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long TICK_INTERVAL_MS = 50;
    private static final double FOLLOW_DISTANCE = 2.0;
    private static final double TELEPORT_SNAP_DISTANCE = 15.0;
    private static final float ROTATION_LERP_FACTOR = 0.15f;

    private static PetManager instance;

    private final ConcurrentHashMap<UUID, PetState> activePets = new ConcurrentHashMap<>();
    private ScheduledFuture<?> tickTask;
    private volatile NPCPlugin npcPlugin;

    public static PetManager getInstance() {
        return instance;
    }

    public PetManager() {
        instance = this;
    }

    public void start() {
        try {
            npcPlugin = NPCPlugin.get();
        } catch (Exception e) {
            LOGGER.atWarning().log("NPCPlugin not available, pets will not spawn: " + e.getMessage());
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
        for (UUID playerId : List.copyOf(activePets.keySet())) {
            despawnPet(playerId);
        }
        activePets.clear();
    }

    public boolean hasPet(UUID playerId) {
        return activePets.containsKey(playerId);
    }

    public PetState getPetState(UUID playerId) {
        return activePets.get(playerId);
    }

    public void spawnPet(UUID playerId, Ref<EntityStore> playerRef, String npcType, float scale) {
        if (npcPlugin == null || playerId == null || playerRef == null) return;

        despawnPet(playerId);

        Store<EntityStore> store = playerRef.getStore();
        if (store == null) return;

        World world = store.getExternalData().getWorld();
        if (world == null) return;

        PetState state = new PetState(playerId, world.getName(), npcType, scale);
        activePets.put(playerId, state);

        world.execute(() -> spawnOnWorldThread(state, world, store));
    }

    public void despawnPet(UUID playerId) {
        if (playerId == null) return;
        PetState state = activePets.remove(playerId);
        if (state == null) return;
        despawnEntity(state);
    }

    public void setScale(UUID playerId, float scale) {
        PetState state = activePets.get(playerId);
        if (state == null) return;
        state.scale = scale;

        Ref<EntityStore> entityRef = state.entityRef;
        if (entityRef == null || !entityRef.isValid()) return;
        Store<EntityStore> store = entityRef.getStore();
        if (store == null) return;
        World world = store.getExternalData().getWorld();
        if (world == null) return;

        world.execute(() -> {
            try {
                if (!entityRef.isValid()) return;
                store.addComponent(entityRef, EntityScaleComponent.getComponentType(),
                    new EntityScaleComponent(scale));
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to set pet scale: " + e.getMessage());
            }
        });
    }

    public void respawn(UUID playerId, Ref<EntityStore> playerRef, String npcType, float scale) {
        spawnPet(playerId, playerRef, npcType, scale);
    }

    // --- Internal ---

    private void spawnOnWorldThread(PetState state, World world, Store<EntityStore> store) {
        try {
            // Resolve player ref from world (same pattern as Pets+ mod)
            Ref<EntityStore> playerRef = world.getEntityStore().getRefFromUUID(state.ownerId);
            if (playerRef == null || !playerRef.isValid()) return;

            TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d playerPos = transform.getPosition();
            float playerYaw = transform.getRotation().getYaw();

            // Spawn slightly behind the player
            double offsetX = -Math.sin(Math.toRadians(playerYaw)) * FOLLOW_DISTANCE;
            double offsetZ = -Math.cos(Math.toRadians(playerYaw)) * FOLLOW_DISTANCE;
            Vector3d spawnPos = new Vector3d(playerPos.getX() + offsetX, playerPos.getY(), playerPos.getZ() + offsetZ);
            Vector3f rotation = new Vector3f(0, playerYaw, 0);

            Object result = npcPlugin.spawnNPC(store, state.npcType, "Pet", spawnPos, rotation);
            if (result == null) return;

            Ref<EntityStore> entityRef = extractEntityRef(result);
            if (entityRef == null) return;

            state.entityRef = entityRef;
            state.currentYaw = playerYaw;

            // Get entity UUID
            try {
                UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
                if (uuidComponent != null) {
                    state.entityUuid = uuidComponent.getUuid();
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to get pet UUID: " + e.getMessage());
            }

            // Invulnerable + Frozen (movement via Teleport only)
            store.addComponent(entityRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
            store.addComponent(entityRef, Frozen.getComponentType(), Frozen.get());

            // Scale
            if (state.scale != 1.0f) {
                store.addComponent(entityRef, EntityScaleComponent.getComponentType(),
                    new EntityScaleComponent(state.scale));
                try {
                    NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
                    if (npcEntity != null) {
                        npcEntity.setInitialModelScale(state.scale);
                    }
                } catch (Exception ignored) {}
            }

            // Remove Interactable so players can't interact with pet
            try {
                com.hypixel.hytale.component.Archetype<EntityStore> archetype = store.getArchetype(entityRef);
                if (archetype.contains(com.hypixel.hytale.server.core.modules.entity.component.Interactable.getComponentType())) {
                    store.removeComponent(entityRef, com.hypixel.hytale.server.core.modules.entity.component.Interactable.getComponentType());
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to spawn pet: " + e.getMessage());
        }
    }

    private void tick() {
        try {
            for (PetState state : activePets.values()) {
                tickFollow(state);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Error in pet tick: " + e.getMessage());
        }
    }

    private void tickFollow(PetState state) {
        Ref<EntityStore> entityRef = state.entityRef;
        if (entityRef == null || !entityRef.isValid()) return;

        // Resolve world by name each tick (not from a potentially stale ref)
        World world = Universe.get().getWorld(state.worldName);
        if (world == null) return;

        // ALL logic on the world thread — same pattern as Pets+ mod EntityTickingSystem
        world.execute(() -> {
            try {
                if (!entityRef.isValid()) return;

                Store<EntityStore> store = world.getEntityStore().getStore();
                if (store == null) return;

                // Resolve player ref fresh from the world (not stored)
                Ref<EntityStore> playerRef = world.getEntityStore().getRefFromUUID(state.ownerId);
                if (playerRef == null || !playerRef.isValid()) return;

                TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
                TransformComponent petTransform = store.getComponent(entityRef, TransformComponent.getComponentType());
                if (playerTransform == null || petTransform == null) return;

                Vector3d playerPos = playerTransform.getPosition();
                Vector3d petPos = petTransform.getPosition();
                double distance = playerPos.distanceTo(petPos);

                // Don't move if already within follow range
                if (distance <= FOLLOW_DISTANCE) return;

                // Lerp directly toward the player — the lag naturally creates trailing
                Vector3d targetPos;
                if (distance > TELEPORT_SNAP_DISTANCE) {
                    // Snap: teleport to FOLLOW_DISTANCE from player
                    double dx = playerPos.getX() - petPos.getX();
                    double dz = playerPos.getZ() - petPos.getZ();
                    double hDist = Math.sqrt(dx * dx + dz * dz);
                    double dirX = hDist > 0.01 ? dx / hDist : 0;
                    double dirZ = hDist > 0.01 ? dz / hDist : 1;
                    targetPos = new Vector3d(
                        playerPos.getX() - dirX * FOLLOW_DISTANCE,
                        playerPos.getY(),
                        playerPos.getZ() - dirZ * FOLLOW_DISTANCE
                    );
                } else {
                    double lerpFactor = Math.min(0.25, distance / 10.0);
                    targetPos = new Vector3d(
                        petPos.getX() + (playerPos.getX() - petPos.getX()) * lerpFactor,
                        petPos.getY() + (playerPos.getY() - petPos.getY()) * lerpFactor,
                        petPos.getZ() + (playerPos.getZ() - petPos.getZ()) * lerpFactor
                    );
                }

                // Face direction of movement (not toward player)
                double moveDx = targetPos.getX() - petPos.getX();
                double moveDz = targetPos.getZ() - petPos.getZ();
                double moveDist = Math.sqrt(moveDx * moveDx + moveDz * moveDz);

                if (moveDist > 0.01) {
                    float targetYaw = (float) Math.toDegrees(Math.atan2(moveDx, moveDz));
                    float yawDelta = targetYaw - state.currentYaw;
                    while (yawDelta > 180f) yawDelta -= 360f;
                    while (yawDelta < -180f) yawDelta += 360f;
                    state.currentYaw += yawDelta * ROTATION_LERP_FACTOR;
                }

                store.addComponent(entityRef, Teleport.getComponentType(),
                    new Teleport(world, targetPos, new Vector3f(0, state.currentYaw, 0)));
            } catch (Exception e) {
                LOGGER.atWarning().log("Pet follow tick error: " + e.getMessage());
            }
        });
    }

    private void despawnEntity(PetState state) {
        Ref<EntityStore> entityRef = state.entityRef;
        if (entityRef == null || !entityRef.isValid()) {
            state.entityRef = null;
            return;
        }

        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store == null) return;
            World world = store.getExternalData().getWorld();
            if (world != null) {
                world.execute(() -> {
                    try {
                        if (entityRef.isValid()) {
                            entityRef.getStore().removeEntity(entityRef, RemoveReason.REMOVE);
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().log("Failed to despawn pet: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to despawn pet: " + e.getMessage());
        }
        state.entityRef = null;
        state.entityUuid = null;
    }

    @SuppressWarnings("unchecked")
    private Ref<EntityStore> extractEntityRef(Object pairResult) {
        if (pairResult == null) return null;
        try {
            for (String methodName : List.of("getFirst", "getLeft", "getKey", "first", "left")) {
                try {
                    java.lang.reflect.Method method = pairResult.getClass().getMethod(methodName);
                    Object value = method.invoke(pairResult);
                    if (value instanceof Ref<?> ref) {
                        return (Ref<EntityStore>) ref;
                    }
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to extract entity ref from NPC result: " + e.getMessage());
        }
        return null;
    }

    // --- State ---

    public static class PetState {
        public final UUID ownerId;
        public final String worldName;
        public volatile Ref<EntityStore> entityRef;
        public volatile UUID entityUuid;
        public volatile String npcType;
        public volatile float scale;
        public volatile float currentYaw;

        PetState(UUID ownerId, String worldName, String npcType, float scale) {
            this.ownerId = ownerId;
            this.worldName = worldName;
            this.npcType = npcType;
            this.scale = scale;
        }
    }
}
