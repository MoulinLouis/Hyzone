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
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.protocol.AnimationSlot;

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
    private static final double FOLLOW_TOLERANCE = 0.35;
    private static final double TELEPORT_SNAP_DISTANCE = 15.0;
    private static final double FOLLOW_MOVE_FACTOR = 0.35;
    private static final double MIN_MOVE_STEP = 0.08;
    private static final double MAX_MOVE_STEP = 0.45;
    private static final double MAX_VERTICAL_STEP = 0.45;
    private static final double TELEPORT_MOVE_EPSILON = 0.02;
    private static final float IDLE_ROTATION_EPSILON_DEGREES = 0.2f;
    private static final long ROTATION_LOG_INTERVAL_MS = 800L;
    private static final float ROTATION_SPIKE_DELTA_DEGREES = 120.0f;
    private static final boolean ROTATION_DEBUG_LOGS = false;
    private static final long IDLE_MOVEMENT_STOP_REFRESH_MS = 200L;

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
                double toPlayerDx = playerPos.getX() - petPos.getX();
                double toPlayerDz = playerPos.getZ() - petPos.getZ();
                double horizontalDistance = Math.sqrt(toPlayerDx * toPlayerDx + toPlayerDz * toPlayerDz);
                double yDelta = playerPos.getY() - petPos.getY();

                double targetX = petPos.getX();
                double targetY = petPos.getY();
                double targetZ = petPos.getZ();

                if (horizontalDistance > TELEPORT_SNAP_DISTANCE) {
                    double dirX = horizontalDistance > 0.001 ? toPlayerDx / horizontalDistance : 0.0;
                    double dirZ = horizontalDistance > 0.001 ? toPlayerDz / horizontalDistance : 1.0;
                    targetX = playerPos.getX() - dirX * FOLLOW_DISTANCE;
                    targetY = playerPos.getY();
                    targetZ = playerPos.getZ() - dirZ * FOLLOW_DISTANCE;
                } else {
                    if (horizontalDistance > FOLLOW_DISTANCE + FOLLOW_TOLERANCE) {
                        double dirX = horizontalDistance > 0.001 ? toPlayerDx / horizontalDistance : 0.0;
                        double dirZ = horizontalDistance > 0.001 ? toPlayerDz / horizontalDistance : 1.0;
                        double moveStep = Math.min(MAX_MOVE_STEP,
                            Math.max(MIN_MOVE_STEP, (horizontalDistance - FOLLOW_DISTANCE) * FOLLOW_MOVE_FACTOR));
                        targetX = petPos.getX() + dirX * moveStep;
                        targetZ = petPos.getZ() + dirZ * moveStep;
                    }
                    if (Math.abs(yDelta) > 0.2) {
                        targetY = petPos.getY() + clamp(yDelta, -MAX_VERTICAL_STEP, MAX_VERTICAL_STEP);
                    }
                }

                Vector3d targetPos = new Vector3d(targetX, targetY, targetZ);
                double moveDx = targetX - petPos.getX();
                double moveDy = targetY - petPos.getY();
                double moveDz = targetZ - petPos.getZ();
                double moveDistance = Math.sqrt(moveDx * moveDx + moveDy * moveDy + moveDz * moveDz);
                // Always face the owner player position.
                float previousYaw = normalizeYawSigned(state.currentYaw);
                float targetYawWrapped = previousYaw;
                if (horizontalDistance > 0.001) {
                    targetYawWrapped = normalizeYawSigned((float) Math.toDegrees(Math.atan2(toPlayerDx, toPlayerDz)));
                }
                float yawDelta = angleDeltaDegrees(targetYawWrapped, previousYaw);
                state.currentYaw = targetYawWrapped;

                boolean rotatedInPlace = Math.abs(yawDelta) > IDLE_ROTATION_EPSILON_DEGREES;
                if (moveDistance <= TELEPORT_MOVE_EPSILON) {
                    // Keep pet fully idle when no positional motion is needed.
                    forceIdleMovementStop(state, entityRef, store, rotatedInPlace);
                    if (!rotatedInPlace) {
                        return;
                    }
                    maybeLogRotation(state, previousYaw, targetYawWrapped, yawDelta, horizontalDistance);
                    store.addComponent(entityRef, Teleport.getComponentType(),
                        new Teleport(world, petPos, new Vector3f(0, state.currentYaw, 0)));
                    return;
                }
                clearIdleMovementStop(state, entityRef, store);
                maybeLogRotation(state, previousYaw, targetYawWrapped, yawDelta, horizontalDistance);

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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float angleDeltaDegrees(float targetYaw, float currentYaw) {
        return normalizeYawSigned(targetYaw - currentYaw);
    }

    private static float normalizeYawSigned(float yaw) {
        float normalized = yaw % 360f;
        if (normalized > 180f) normalized -= 360f;
        if (normalized < -180f) normalized += 360f;
        return normalized;
    }

    private void forceIdleMovementStop(PetState state, Ref<EntityStore> entityRef, Store<EntityStore> store,
                                       boolean bypassThrottle) {
        if (entityRef == null || !entityRef.isValid() || store == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!bypassThrottle
            && state.idleMovementStopForced
            && now - state.lastIdleMovementStopApplyMs < IDLE_MOVEMENT_STOP_REFRESH_MS) {
            return;
        }
        try {
            AnimationUtils.stopAnimation(entityRef, AnimationSlot.Movement, store);
            state.idleMovementStopForced = true;
            state.lastIdleMovementStopApplyMs = now;
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to force pet movement-stop animation: " + e.getMessage());
        }
    }

    private void clearIdleMovementStop(PetState state, Ref<EntityStore> entityRef, Store<EntityStore> store) {
        if (!state.idleMovementStopForced || entityRef == null || !entityRef.isValid() || store == null) {
            return;
        }
        try {
            AnimationUtils.stopAnimation(entityRef, AnimationSlot.Movement, store);
            state.idleMovementStopForced = false;
            state.lastIdleMovementStopApplyMs = 0L;
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to clear pet movement-stop animation: " + e.getMessage());
        }
    }

    private void maybeLogRotation(PetState state, float previousYaw, float targetYaw, float yawDelta,
                                  double horizontalDistance) {
        if (!ROTATION_DEBUG_LOGS) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean spike = Math.abs(yawDelta) >= ROTATION_SPIKE_DELTA_DEGREES;
        if (!spike && now - state.lastRotationLogMs < ROTATION_LOG_INTERVAL_MS) {
            return;
        }
        state.lastRotationLogMs = now;
        LOGGER.atInfo().log(String.format(
            "PetRot owner=%s yawPrev=%.1f yawNow=%.1f yawTarget=%.1f delta=%.1f dist=%.2f%s",
            shortOwner(state.ownerId),
            previousYaw,
            state.currentYaw,
            targetYaw,
            yawDelta,
            horizontalDistance,
            spike ? " spike=true" : ""
        ));
    }

    private static String shortOwner(UUID ownerId) {
        if (ownerId == null) return "null";
        String value = ownerId.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
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
        public volatile long lastRotationLogMs;
        public volatile long lastIdleMovementStopApplyMs;
        public volatile boolean idleMovementStopForced;

        PetState(UUID ownerId, String worldName, String npcType, float scale) {
            this.ownerId = ownerId;
            this.worldName = worldName;
            this.npcType = npcType;
            this.scale = scale;
        }
    }
}
