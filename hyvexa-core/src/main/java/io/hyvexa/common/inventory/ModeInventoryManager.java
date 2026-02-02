package io.hyvexa.common.inventory;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;

/**
 * Centralized inventory management utilities shared across all game modes.
 * Consolidates duplicate inventory code from:
 * - InventoryUtils (hyvexa-parkour)
 * - HubRouter (hyvexa-hub)
 * - ParkourAscendPlugin (hyvexa-parkour-ascend)
 */
public final class ModeInventoryManager {

    private ModeInventoryManager() {
    }

    /**
     * Clear all inventory sections for a player.
     * @param player The player whose inventory to clear
     */
    public static void clearAllInventory(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
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

    /**
     * Clear only the hotbar for a player.
     * @param player The player whose hotbar to clear
     */
    public static void clearHotbar(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        clearContainer(inventory.getHotbar());
    }

    /**
     * Clear a single item container.
     * @param container The container to clear
     */
    public static void clearContainer(ItemContainer container) {
        if (container == null) {
            return;
        }
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            container.setItemStackForSlot(slot, ItemStack.EMPTY, false);
        }
    }

    /**
     * Set an item in the player's hotbar at the specified slot.
     * @param player The player
     * @param slotIndex The hotbar slot (0-8)
     * @param itemId The item ID to set
     * @return true if the item was set successfully
     */
    public static boolean setHotbarItem(Player player, int slotIndex, String itemId) {
        return setHotbarItem(player, slotIndex, new ItemStack(itemId, 1));
    }

    /**
     * Set an item in the player's hotbar at the specified slot.
     * @param player The player
     * @param slotIndex The hotbar slot (0-8)
     * @param itemStack The item stack to set
     * @return true if the item was set successfully
     */
    public static boolean setHotbarItem(Player player, int slotIndex, ItemStack itemStack) {
        if (player == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return false;
        }
        return setHotbarItem(inventory, slotIndex, itemStack);
    }

    /**
     * Set an item in an inventory's hotbar at the specified slot.
     * @param inventory The inventory
     * @param slotIndex The hotbar slot (0-8)
     * @param itemStack The item stack to set
     * @return true if the item was set successfully
     */
    public static boolean setHotbarItem(Inventory inventory, int slotIndex, ItemStack itemStack) {
        if (inventory == null || itemStack == null) {
            return false;
        }
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) {
            return false;
        }
        if (slotIndex < 0 || slotIndex >= hotbar.getCapacity()) {
            return false;
        }
        hotbar.setItemStackForSlot((short) slotIndex, itemStack, false);
        return true;
    }

    /**
     * Set an item in the last slot of the player's hotbar.
     * Commonly used for hub/menu selector items.
     * @param player The player
     * @param itemId The item ID to set
     * @return true if the item was set successfully
     */
    public static boolean setLastHotbarItem(Player player, String itemId) {
        if (player == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return false;
        }
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null || hotbar.getCapacity() <= 0) {
            return false;
        }
        short lastSlot = (short) (hotbar.getCapacity() - 1);
        hotbar.setItemStackForSlot(lastSlot, new ItemStack(itemId, 1), false);
        return true;
    }

    /**
     * Set an item in a slot only if it's missing or different.
     * @param player The player
     * @param slotIndex The hotbar slot
     * @param itemId The expected item ID
     * @return true if an item was placed (it was missing or different)
     */
    public static boolean setIfMissing(Player player, int slotIndex, String itemId) {
        if (player == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return false;
        }
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) {
            return false;
        }
        if (slotIndex < 0 || slotIndex >= hotbar.getCapacity()) {
            return false;
        }
        ItemStack existing = hotbar.getItemStack((short) slotIndex);
        if (existing == null || ItemStack.isEmpty(existing) || !itemId.equals(existing.getItemId())) {
            hotbar.setItemStackForSlot((short) slotIndex, new ItemStack(itemId, 1), false);
            return true;
        }
        return false;
    }

    /**
     * Apply drop filters to all inventory containers.
     * @param player The player
     * @param allowDrop true to allow dropping, false to block
     */
    public static void applyDropFilters(Player player, boolean allowDrop) {
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        applyDropFilter(inventory.getHotbar(), allowDrop);
        applyDropFilter(inventory.getStorage(), allowDrop);
        applyDropFilter(inventory.getBackpack(), allowDrop);
        applyDropFilter(inventory.getTools(), allowDrop);
        applyDropFilter(inventory.getUtility(), allowDrop);
        applyDropFilter(inventory.getArmor(), allowDrop);
    }

    /**
     * Apply drop filter to a single container.
     * @param container The container
     * @param allowDrop true to allow dropping, false to block
     */
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

    /**
     * Get the hotbar capacity for a player.
     * @param player The player
     * @return The hotbar capacity, or 0 if unavailable
     */
    public static int getHotbarCapacity(Player player) {
        if (player == null) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return 0;
        }
        ItemContainer hotbar = inventory.getHotbar();
        return hotbar != null ? hotbar.getCapacity() : 0;
    }
}
