package io.hyvexa.ascend.mine.robot;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.data.ConveyorConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MinerSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages conveyor belt item entities that transport mined blocks
 * from miner robots to the collection point.
 */
class MineConveyorManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long CONVEYOR_TICK_MS = 20L;

    private final ConveyorConfigStore conveyorConfigStore;
    private final MinePlayerStore playerStore;
    private final MineAchievementTracker achievementTracker;

    // Active conveyor items per player — CopyOnWriteArrayList because spawnConveyorItem (miner tick)
    // and tickConveyorItems (conveyor tick) run on different scheduled executors
    private final Map<UUID, List<ConveyorItemState>> conveyorItems = new ConcurrentHashMap<>();

    private ScheduledFuture<?> conveyorTickTask;

    // Reflection fields for item pickup/merge delay
    private volatile java.lang.reflect.Field pickupDelayField;
    private volatile java.lang.reflect.Field mergeDelayField;

    MineConveyorManager(ConveyorConfigStore conveyorConfigStore,
                        MinePlayerStore playerStore,
                        MineAchievementTracker achievementTracker) {
        this.conveyorConfigStore = conveyorConfigStore;
        this.playerStore = playerStore;
        this.achievementTracker = achievementTracker;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    void start() {
        try {
            pickupDelayField = ItemComponent.class.getDeclaredField("pickupDelay");
            pickupDelayField.setAccessible(true);
            mergeDelayField = ItemComponent.class.getDeclaredField("mergeDelay");
            mergeDelayField.setAccessible(true);
        } catch (Exception e) {
            LOGGER.atWarning().log("Conveyor reflection setup failed: " + e.getMessage());
        }

        conveyorTickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                () -> tickConveyorItems(System.currentTimeMillis()),
                CONVEYOR_TICK_MS,
                CONVEYOR_TICK_MS,
                TimeUnit.MILLISECONDS
        );
    }

    void stop() {
        if (conveyorTickTask != null) {
            conveyorTickTask.cancel(false);
            conveyorTickTask = null;
        }
    }

    // ── Public API ─────────────────────────────────────────────────────

    boolean isConveyorFull(UUID ownerId, MinePlayerProgress progress) {
        return progress.getConveyorBufferCount() + getInFlightCount(ownerId) >= progress.getConveyorCapacity();
    }

    void spawnConveyorItem(MinerRobotState minerState, MinerSlot slot, String blockType) {
        UUID ownerId = minerState.getOwnerId();
        MinePlayerProgress progress = playerStore.getPlayer(ownerId);
        if (progress != null && isConveyorFull(ownerId, progress)) return;

        String worldName = minerState.getWorldName();
        World world = worldName != null ? Universe.get().getWorld(worldName) : null;
        if (world == null) return;

        String mineId = minerState.getMineId();
        int slotIndex = minerState.getSlotIndex();

        // Build full waypoint path: block center -> slot waypoints -> main line waypoints
        List<double[]> path = new ArrayList<>();
        path.add(new double[]{slot.getBlockX() + 0.5, slot.getBlockY() + 0.5, slot.getBlockZ() + 0.5});
        path.addAll(conveyorConfigStore.getSlotWaypoints(mineId, slotIndex));
        path.addAll(conveyorConfigStore.getMainLineWaypoints(mineId));

        if (path.size() < 2) return; // need at least start + 1 waypoint

        double[][] waypoints = path.toArray(new double[0][]);
        double speed = conveyorConfigStore.getConveyorSpeed(mineId);

        ConveyorItemState itemState = new ConveyorItemState(ownerId, mineId, worldName, blockType, speed, waypoints);

        conveyorItems.computeIfAbsent(ownerId, k -> new CopyOnWriteArrayList<>()).add(itemState);

        double startX = waypoints[0][0];
        double startY = waypoints[0][1];
        double startZ = waypoints[0][2];

        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                if (store == null) return;

                ItemStack itemStack = new ItemStack(blockType, 1);
                Holder<EntityStore> holder = ItemComponent.generateItemDrop(store, itemStack,
                        new Vector3d(startX, startY, startZ), Vector3f.ZERO, 0, 0, 0);
                if (holder == null) return;

                Ref<EntityStore> itemRef = store.addEntity(holder, AddReason.SPAWN);
                if (itemRef == null || !itemRef.isValid()) return;

                itemState.setEntityRef(itemRef);

                ItemComponent itemComp = store.getComponent(itemRef, ItemComponent.getComponentType());
                if (itemComp != null) {
                    if (pickupDelayField != null) pickupDelayField.setFloat(itemComp, 999f);
                    if (mergeDelayField != null) mergeDelayField.setFloat(itemComp, 999f);
                }

                Velocity vel = store.getComponent(itemRef, Velocity.getComponentType());
                if (vel != null) vel.setZero();

                // Scale down to half size
                store.addComponent(itemRef, EntityScaleComponent.getComponentType(),
                        new EntityScaleComponent(0.5f));

                // Freeze entity to prevent physics/collision with world blocks (e.g. rails)
                try {
                    store.addComponent(itemRef, Frozen.getComponentType(), Frozen.get());
                } catch (Exception ignored) {}
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to spawn conveyor item: " + e.getMessage());
            }
        });
    }

    void cleanupConveyorItems(UUID ownerId) {
        List<ConveyorItemState> items = conveyorItems.remove(ownerId);
        if (items == null || items.isEmpty()) return;

        for (ConveyorItemState item : items) {
            Ref<EntityStore> ref = item.getEntityRef();
            if (ref == null || !ref.isValid()) continue;
            String worldName = item.getWorldName();
            World world = worldName != null ? Universe.get().getWorld(worldName) : null;
            if (world != null) {
                world.execute(() -> {
                    if (ref.isValid()) {
                        Store<EntityStore> store = ref.getStore();
                        if (store != null) store.removeEntity(ref, RemoveReason.REMOVE);
                    }
                });
            }
        }
    }

    void cleanupAllConveyorItems() {
        for (UUID ownerId : new ArrayList<>(conveyorItems.keySet())) {
            cleanupConveyorItems(ownerId);
        }
    }

    // ── Tick ───────────────────────────────────────────────────────────

    void tickConveyorItems(long now) {
        for (var entry : conveyorItems.entrySet()) {
            UUID ownerId = entry.getKey();
            List<ConveyorItemState> items = entry.getValue();
            if (items.isEmpty()) continue;

            items.removeIf(item -> {
                Ref<EntityStore> ref = item.getEntityRef();
                String worldName = item.getWorldName();
                World world = worldName != null ? Universe.get().getWorld(worldName) : null;

                if (item.isComplete(now)) {
                    String blockTypeItem = item.getBlockType();
                    if (blockTypeItem != null) {
                        MinePlayerProgress progress = playerStore.getPlayer(ownerId);
                        if (progress != null && progress.addToConveyorBuffer(blockTypeItem, 1)) {
                            playerStore.markDirty(ownerId);

                            if (achievementTracker != null) {
                                achievementTracker.incrementBlocksMined(ownerId, 1);
                            }
                        }
                    }

                    if (ref != null && ref.isValid() && world != null) {
                        world.execute(() -> {
                            if (ref.isValid()) {
                                Store<EntityStore> store = ref.getStore();
                                if (store != null) store.removeEntity(ref, RemoveReason.REMOVE);
                            }
                        });
                    }
                    return true;
                }

                if (ref != null && ref.isValid() && world != null) {
                    double x = item.getX(now);
                    double y = item.getY(now);
                    double z = item.getZ(now);

                    world.execute(() -> {
                        if (!ref.isValid()) return;
                        Store<EntityStore> store = world.getEntityStore().getStore();
                        if (store == null) return;

                        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                        if (transform != null) {
                            transform.setPosition(new Vector3d(x, y, z));
                        }

                        Velocity vel = store.getComponent(ref, Velocity.getComponentType());
                        if (vel != null) vel.setZero();

                        ItemComponent itemComp = store.getComponent(ref, ItemComponent.getComponentType());
                        if (itemComp != null) {
                            try {
                                if (pickupDelayField != null) pickupDelayField.setFloat(itemComp, 999f);
                                if (mergeDelayField != null) mergeDelayField.setFloat(itemComp, 999f);
                            } catch (Exception ignored) {}
                        }
                    });
                }
                return false;
            });
        }
    }

    // ── Internal ───────────────────────────────────────────────────────

    private int getInFlightCount(UUID ownerId) {
        List<ConveyorItemState> items = conveyorItems.get(ownerId);
        return items != null ? items.size() : 0;
    }
}
