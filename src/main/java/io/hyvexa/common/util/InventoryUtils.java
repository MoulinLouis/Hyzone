package io.hyvexa.common.util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import io.hyvexa.parkour.ParkourConstants;


public final class InventoryUtils {

    private InventoryUtils() {
    }

    public static void giveRunItems(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        if (PermissionUtils.isOp(player)) {
            clearContainer(inventory.getHotbar());
            setHotbarItem(inventory, 0, new ItemStack(ParkourConstants.ITEM_RESET, 1));
            setHotbarItem(inventory, 1, new ItemStack(ParkourConstants.ITEM_RESTART_CHECKPOINT, 1));
            setHotbarItem(inventory, 2, new ItemStack(ParkourConstants.ITEM_LEAVE, 1));
            return;
        }
        boolean allowDrop = false;
        applyDropFilters(inventory, true);
        clearAllSections(inventory);
        setHotbarItem(inventory, 0, new ItemStack(ParkourConstants.ITEM_RESET, 1));
        setHotbarItem(inventory, 1, new ItemStack(ParkourConstants.ITEM_RESTART_CHECKPOINT, 1));
        setHotbarItem(inventory, 2, new ItemStack(ParkourConstants.ITEM_LEAVE, 1));
        applyDropFilters(inventory, allowDrop);
    }

    public static void giveMenuItems(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        if (PermissionUtils.isOp(player)) {
            clearContainer(inventory.getHotbar());
            setHotbarItem(inventory, 0, new ItemStack(ParkourConstants.ITEM_MENU, 1));
            setHotbarItem(inventory, 1, new ItemStack(ParkourConstants.ITEM_LEADERBOARD, 1));
            setHotbarItem(inventory, 2, new ItemStack(ParkourConstants.ITEM_STATS, 1));
            return;
        }
        boolean allowDrop = false;
        applyDropFilters(inventory, true);
        clearAllSections(inventory);
        setHotbarItem(inventory, 0, new ItemStack(ParkourConstants.ITEM_MENU, 1));
        setHotbarItem(inventory, 1, new ItemStack(ParkourConstants.ITEM_LEADERBOARD, 1));
        setHotbarItem(inventory, 2, new ItemStack(ParkourConstants.ITEM_STATS, 1));
        applyDropFilters(inventory, allowDrop);
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
        container.clear();
    }

    private static void setHotbarItem(Inventory inventory, int slotIndex, ItemStack itemStack) {
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null || itemStack == null) {
            return;
        }
        if (slotIndex < 0 || slotIndex >= hotbar.getCapacity()) {
            return;
        }
        hotbar.setItemStackForSlot((short) slotIndex, itemStack);
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
}
