package io.hyvexa.purge.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeUpgradeState;
import io.hyvexa.purge.data.PurgeUpgradeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PurgeUpgradeManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PURGE_HP_UPGRADE_MODIFIER = "purge_upgrade_hp";
    private static final String ITEM_BULLET = "Bullet";
    private static final short SLOT_AMMO = 1;
    private static final int AMMO_PER_STACK = 60;

    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> regenTasks = new ConcurrentHashMap<>();

    public List<PurgeUpgradeType> selectRandomUpgrades(int count) {
        List<PurgeUpgradeType> pool = new ArrayList<>(List.of(PurgeUpgradeType.values()));
        Collections.shuffle(pool);
        return pool.subList(0, Math.min(count, pool.size()));
    }

    public void applyUpgrade(PurgeSession session, UUID playerId, PurgeUpgradeType type,
                             Ref<EntityStore> ref, Store<EntityStore> store) {
        PurgeUpgradeState state = session.getUpgradeState(playerId);
        if (state == null) return;
        state.addStack(type);
        int stacks = state.getStacks(type);

        switch (type) {
            case SWIFT_FEET -> applySwiftFeet(ref, store, stacks);
            case IRON_SKIN -> applyIronSkin(ref, store, stacks);
            case AMMO_CACHE -> applyAmmoCache(ref, store);
            case SECOND_WIND -> applySecondWind(playerId, ref, store, stacks);
            case THICK_HIDE, SCAVENGER -> {} // Read at use-time, no immediate apply
        }
    }

    public double getScrapMultiplier(PurgeSession session, UUID playerId) {
        PurgeUpgradeState state = session.getUpgradeState(playerId);
        if (state == null) return 1.0;
        int stacks = state.getStacks(PurgeUpgradeType.SCAVENGER);
        return 1.0 + stacks * 0.25;
    }

    public void revertPlayerUpgrades(PurgeSession session, UUID playerId,
                                      Ref<EntityStore> ref, Store<EntityStore> store) {
        PurgeUpgradeState state = session.getUpgradeState(playerId);
        if (state == null) return;

        if (state.getStacks(PurgeUpgradeType.SWIFT_FEET) > 0) {
            revertSwiftFeet(ref, store);
        }
        if (state.getStacks(PurgeUpgradeType.IRON_SKIN) > 0) {
            revertIronSkin(ref, store);
        }
        cancelRegenTask(playerId);
    }

    public void cleanupPlayer(UUID playerId) {
        cancelRegenTask(playerId);
    }

    // --- SWIFT_FEET ---

    private void applySwiftFeet(Ref<EntityStore> ref, Store<EntityStore> store, int stacks) {
        if (ref == null || !ref.isValid()) return;
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) return;

        movementManager.refreshDefaultSettings(ref, store);
        movementManager.applyDefaultSettings();
        MovementSettings settings = movementManager.getSettings();
        if (settings == null) return;

        float multiplier = 1.0f + stacks * 0.10f;
        settings.maxSpeedMultiplier *= multiplier;
        settings.forwardRunSpeedMultiplier *= multiplier;
        settings.backwardRunSpeedMultiplier *= multiplier;
        settings.strafeRunSpeedMultiplier *= multiplier;
        settings.forwardSprintSpeedMultiplier *= multiplier;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            var packetHandler = playerRef.getPacketHandler();
            if (packetHandler != null) {
                movementManager.update(packetHandler);
            }
        }
    }

    private void revertSwiftFeet(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) return;

        movementManager.refreshDefaultSettings(ref, store);
        movementManager.applyDefaultSettings();

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            var packetHandler = playerRef.getPacketHandler();
            if (packetHandler != null) {
                movementManager.update(packetHandler);
            }
        }
    }

    // --- IRON_SKIN ---

    private void applyIronSkin(Ref<EntityStore> ref, Store<EntityStore> store, int stacks) {
        if (ref == null || !ref.isValid()) return;
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) return;

        int healthIndex = DefaultEntityStatTypes.getHealth();
        float multiplier = 1.0f + stacks * 0.20f;
        statMap.putModifier(healthIndex, PURGE_HP_UPGRADE_MODIFIER,
                new StaticModifier(Modifier.ModifierTarget.MAX,
                        StaticModifier.CalculationType.MULTIPLICATIVE, multiplier));
        statMap.update();
        statMap.maximizeStatValue(healthIndex);
        statMap.update();
    }

    private void revertIronSkin(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) return;

        int healthIndex = DefaultEntityStatTypes.getHealth();
        statMap.removeModifier(healthIndex, PURGE_HP_UPGRADE_MODIFIER);
        statMap.update();
    }

    // --- AMMO_CACHE ---

    private void applyAmmoCache(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) return;

        // Read current ammo count and add 60
        ItemStack current = inventory.getHotbar().getItemStack(SLOT_AMMO);
        int currentCount = (current != null && !current.isEmpty()) ? current.getQuantity() : 0;
        inventory.getHotbar().setItemStackForSlot(SLOT_AMMO,
                new ItemStack(ITEM_BULLET, currentCount + AMMO_PER_STACK), false);
    }

    // --- SECOND_WIND ---

    private void applySecondWind(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, int stacks) {
        cancelRegenTask(playerId);

        ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                if (ref == null || !ref.isValid()) {
                    cancelRegenTask(playerId);
                    return;
                }
                Store<EntityStore> currentStore = ref.getStore();
                if (currentStore == null) return;

                EntityStatMap statMap = currentStore.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap == null) return;

                int healthIndex = DefaultEntityStatTypes.getHealth();
                var health = statMap.get(healthIndex);
                if (health != null && health.get() < health.getMax()) {
                    statMap.addStatValue(healthIndex, stacks);
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Second Wind regen error");
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);

        regenTasks.put(playerId, task);
    }

    private void cancelRegenTask(UUID playerId) {
        ScheduledFuture<?> task = regenTasks.remove(playerId);
        if (task != null) {
            task.cancel(false);
        }
    }
}
