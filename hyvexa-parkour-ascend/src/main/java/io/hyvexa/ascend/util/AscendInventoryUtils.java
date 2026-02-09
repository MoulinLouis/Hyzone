package io.hyvexa.ascend.util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import io.hyvexa.ascend.AscendConstants;

public final class AscendInventoryUtils {

    private AscendInventoryUtils() {}

    public static void giveMenuItems(Player player) {
        if (player == null) {
            return;
        }
        clearAllContainers(player);
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null || hotbar.getCapacity() <= 0) {
            return;
        }
        hotbar.setItemStackForSlot((short) 0, new ItemStack(AscendConstants.ITEM_DEV_CINDERCLOTH, 1), false);
        hotbar.setItemStackForSlot((short) 1, new ItemStack(AscendConstants.ITEM_DEV_STORMSILK, 1), false);
        hotbar.setItemStackForSlot((short) 2, new ItemStack(AscendConstants.ITEM_DEV_COTTON, 1), false);
        hotbar.setItemStackForSlot((short) 3, new ItemStack(AscendConstants.ITEM_DEV_SHADOWEAVE, 1), false);
        hotbar.setItemStackForSlot((short) 4, new ItemStack(AscendConstants.ITEM_DEV_SILK, 1), false);
        short slot = (short) (hotbar.getCapacity() - 1);
        hotbar.setItemStackForSlot(slot, new ItemStack("Hub_Server_Selector", 1), false);
    }

    public static void ensureMenuItems(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        ItemContainer hotbar = inventory.getHotbar();
        short capacity = hotbar.getCapacity();
        if (capacity <= 0) {
            return;
        }
        setIfMissing(hotbar, (short) 0, AscendConstants.ITEM_DEV_CINDERCLOTH);
        setIfMissing(hotbar, (short) 1, AscendConstants.ITEM_DEV_STORMSILK);
        setIfMissing(hotbar, (short) 2, AscendConstants.ITEM_DEV_COTTON);
        setIfMissing(hotbar, (short) 3, AscendConstants.ITEM_DEV_SHADOWEAVE);
        setIfMissing(hotbar, (short) 4, AscendConstants.ITEM_DEV_SILK);
        short slot = (short) (capacity - 1);
        setIfMissing(hotbar, slot, "Hub_Server_Selector");
    }

    public static void giveRunItems(Player player) {
        if (player == null) {
            return;
        }
        clearAllContainers(player);
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null || hotbar.getCapacity() <= 0) {
            return;
        }
        hotbar.setItemStackForSlot((short) 0, new ItemStack(AscendConstants.ITEM_RESET, 1), false);
        hotbar.setItemStackForSlot((short) 1, new ItemStack(AscendConstants.ITEM_LEAVE, 1), false);
        hotbar.setItemStackForSlot((short) 2, new ItemStack(AscendConstants.ITEM_DEV_CINDERCLOTH, 1), false);
    }

    private static void clearAllContainers(Player player) {
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

    private static void clearContainer(ItemContainer container) {
        if (container == null) {
            return;
        }
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            container.setItemStackForSlot(slot, ItemStack.EMPTY, false);
        }
    }

    private static void setIfMissing(ItemContainer hotbar, short slot, String itemId) {
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
}
