package io.hyvexa.common.util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import io.hyvexa.common.WorldConstants;

public final class InventoryUtils {

    public static final short SLOT_SHOP = 7;
    public static final short SLOT_SERVER_SELECTOR = 8;

    private InventoryUtils() {
    }

    public static void clearAllContainers(Player player) {
        if (player == null) {
            return;
        }
        clearAllContainers(player.getInventory());
    }

    public static void clearAllContainers(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        clearContainer(inventory.getHotbar());
        clearContainer(inventory.getStorage());
        clearContainer(inventory.getBackpack());
        clearContainer(inventory.getTools());
        clearContainer(inventory.getUtility());
        clearContainer(inventory.getArmor());
    }

    public static void clearContainer(ItemContainer container) {
        if (container == null) {
            return;
        }
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            container.setItemStackForSlot(slot, ItemStack.EMPTY, false);
        }
    }

    public static void safeSetSlot(ItemContainer hotbar, short slot, ItemStack item) {
        if (hotbar == null || item == null) {
            return;
        }
        if (slot < 0 || slot >= hotbar.getCapacity()) {
            return;
        }
        hotbar.setItemStackForSlot(slot, item, false);
    }

    public static void giveGlobalItems(ItemContainer hotbar) {
        safeSetSlot(hotbar, SLOT_SHOP, new ItemStack(WorldConstants.ITEM_SHOP, 1));
        safeSetSlot(hotbar, SLOT_SERVER_SELECTOR, new ItemStack(WorldConstants.ITEM_SERVER_SELECTOR, 1));
    }

    public static void ensureGlobalItems(ItemContainer hotbar) {
        setIfMissing(hotbar, SLOT_SHOP, WorldConstants.ITEM_SHOP);
        setIfMissing(hotbar, SLOT_SERVER_SELECTOR, WorldConstants.ITEM_SERVER_SELECTOR);
    }

    public static void setIfMissing(ItemContainer hotbar, short slot, String itemId) {
        if (hotbar == null || itemId == null || itemId.isBlank()) {
            return;
        }
        if (slot < 0 || slot >= hotbar.getCapacity()) {
            return;
        }
        ItemStack existing = hotbar.getItemStack(slot);
        if (existing == null || ItemStack.isEmpty(existing) || !itemId.equals(existing.getItemId())) {
            hotbar.setItemStackForSlot(slot, new ItemStack(itemId, 1), false);
        }
    }

    public static void applyDropFilters(Inventory inventory, boolean allowDrop) {
        ItemContainer[] containers = {
            inventory.getHotbar(), inventory.getStorage(), inventory.getBackpack(),
            inventory.getTools(), inventory.getUtility(), inventory.getArmor()
        };
        for (ItemContainer container : containers) {
            applyDropFilter(container, allowDrop);
        }
    }

    public static void applyDropFilter(ItemContainer container, boolean allowDrop) {
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
