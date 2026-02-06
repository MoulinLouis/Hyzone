package io.hyvexa.common.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.duel.DuelConstants;
import io.hyvexa.parkour.ParkourConstants;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.util.PlayerSettingsStore;

import java.util.UUID;

public final class InventoryUtils {

    private InventoryUtils() {
    }

    public static void giveRunItems(Player player) {
        giveRunItems(player, null, false);
    }

    public static void giveRunItems(Player player, Map map) {
        giveRunItems(player, map, false);
    }

    public static void giveRunItems(Player player, Map map, boolean practiceEnabled) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        if (PermissionUtils.isOp(player)) {
            clearContainer(inventory.getHotbar());
            boolean hasSword = map != null && map.isMithrilSwordEnabled();
            boolean hasDaggers = map != null && map.isMithrilDaggersEnabled();
            boolean hasGlider = map != null && map.isGliderEnabled();
            int slotIndex = 0;
            if (hasSword) {
                setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RUN_MITHRIL_SWORD, 1));
            }
            if (hasDaggers) {
                setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RUN_MITHRIL_DAGGERS, 1));
            }
            if (hasGlider) {
                setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RUN_GLIDER, 1));
            }
            if (practiceEnabled) {
                setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_PRACTICE_CHECKPOINT, 1));
                setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_TOGGLE_FLY, 1));
            } else if (PlayerSettingsStore.isResetItemEnabled(resolvePlayerUuid(player))) {
                setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RESET, 1));
            }
            setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RESTART_CHECKPOINT, 1));
            setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_LEAVE, 1));
            if (!practiceEnabled) {
                setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_PRACTICE, 1));
            }
            setHotbarItem(inventory, slotIndex, new ItemStack(ParkourConstants.ITEM_ADMIN_REMOTE, 1));
            applyDropFilter(inventory.getHotbar(), false);
            return;
        }
        applyDropFilters(inventory, false);
        clearAllSections(inventory);
        boolean hasSword = map != null && map.isMithrilSwordEnabled();
        boolean hasDaggers = map != null && map.isMithrilDaggersEnabled();
        boolean hasGlider = map != null && map.isGliderEnabled();
        int slotIndex = 0;
        if (hasSword) {
            setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RUN_MITHRIL_SWORD, 1));
        }
        if (hasDaggers) {
            setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RUN_MITHRIL_DAGGERS, 1));
        }
        if (hasGlider) {
            setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RUN_GLIDER, 1));
        }
        if (practiceEnabled) {
            setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_PRACTICE_CHECKPOINT, 1));
            setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_TOGGLE_FLY, 1));
        } else if (PlayerSettingsStore.isResetItemEnabled(resolvePlayerUuid(player))) {
            setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RESET, 1));
        }
        setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RESTART_CHECKPOINT, 1));
        setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_LEAVE, 1));
        if (!practiceEnabled) {
            setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_PRACTICE, 1));
        }
        setHotbarItem(inventory, slotIndex, new ItemStack(ParkourConstants.ITEM_ADMIN_REMOTE, 1));
        applyDropFilters(inventory, false);
    }

    public static void giveDuelItems(Player player, Map map) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) {
            return;
        }
        hotbar.setItemStackForSlot((short) 0, new ItemStack(ParkourConstants.ITEM_RESET, 1), false);
        hotbar.setItemStackForSlot((short) 1, new ItemStack(ParkourConstants.ITEM_RESTART_CHECKPOINT, 1), false);
        hotbar.setItemStackForSlot((short) 2, new ItemStack(DuelConstants.ITEM_FORFEIT, 1), false);
        for (short i = 3; i < 9; i++) {
            hotbar.setItemStackForSlot(i, ItemStack.EMPTY, false);
        }
    }

    public static void giveMenuItems(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        if (PermissionUtils.isOp(player)) {
            clearContainer(inventory.getHotbar());
            setHotbarItem(inventory, 0, new ItemStack(ParkourConstants.ITEM_MENU, 1));
            setHotbarItem(inventory, 1, new ItemStack(DuelConstants.ITEM_MENU, 1));
            setHotbarItem(inventory, 2, new ItemStack(ParkourConstants.ITEM_LEADERBOARD, 1));
            setHotbarItem(inventory, 3, new ItemStack(ParkourConstants.ITEM_STATS, 1));
            setHotbarItem(inventory, 4, new ItemStack(ParkourConstants.ITEM_ADMIN_REMOTE, 1));
            setHotbarItem(inventory, 8, new ItemStack(ParkourConstants.ITEM_HUB_MENU, 1));
            return;
        }
        applyDropFilters(inventory, false);
        clearAllSections(inventory);
        setHotbarItem(inventory, 0, new ItemStack(ParkourConstants.ITEM_MENU, 1));
        setHotbarItem(inventory, 1, new ItemStack(DuelConstants.ITEM_MENU, 1));
        setHotbarItem(inventory, 2, new ItemStack(ParkourConstants.ITEM_LEADERBOARD, 1));
        setHotbarItem(inventory, 3, new ItemStack(ParkourConstants.ITEM_STATS, 1));
        setHotbarItem(inventory, 4, new ItemStack(ParkourConstants.ITEM_ADMIN_REMOTE, 1));
        setHotbarItem(inventory, 8, new ItemStack(ParkourConstants.ITEM_HUB_MENU, 1));
        applyDropFilters(inventory, false);
    }

    public static void clearAllItems(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        clearAllSections(inventory);
    }

    private static void clearAllSections(Inventory inventory) {
        clearContainer(inventory.getHotbar());
        clearContainer(inventory.getStorage());
        clearContainer(inventory.getBackpack());
        clearContainer(inventory.getTools());
        clearContainer(inventory.getUtility());
        clearContainer(inventory.getArmor());
    }

    private static void clearContainer(ItemContainer container) {
        if (container == null) {
            return;
        }
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            container.setItemStackForSlot(slot, ItemStack.EMPTY, false);
        }
    }

    private static void setHotbarItem(Inventory inventory, int slotIndex, ItemStack itemStack) {
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null || itemStack == null) {
            return;
        }
        if (slotIndex < 0 || slotIndex >= hotbar.getCapacity()) {
            return;
        }
        hotbar.setItemStackForSlot((short) slotIndex, itemStack, false);
    }

    private static void applyDropFilters(Inventory inventory, boolean allowDrop) {
        applyDropFilter(inventory.getHotbar(), allowDrop);
        applyDropFilter(inventory.getStorage(), allowDrop);
        applyDropFilter(inventory.getBackpack(), allowDrop);
        applyDropFilter(inventory.getTools(), allowDrop);
        applyDropFilter(inventory.getUtility(), allowDrop);
        applyDropFilter(inventory.getArmor(), allowDrop);
    }

    private static void applyDropFilter(ItemContainer container, boolean allowDrop) {
        if (container == null) {
            return;
        }
        SlotFilter filter = allowDrop ? SlotFilter.ALLOW : SlotFilter.DENY;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            container.setSlotFilter(FilterActionType.DROP, slot, filter);
            container.setSlotFilter(FilterActionType.REMOVE, slot, filter);
        }
    }

    private static UUID resolvePlayerUuid(Player player) {
        if (player == null) {
            return null;
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }
}
