package io.hyvexa.ascend.util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.util.InventoryUtils;

public final class AscendInventoryUtils {

    private AscendInventoryUtils() {}

    public static void giveMenuItems(Player player) {
        if (!isInventoryWriteSafe(player)) {
            return;
        }
        InventoryUtils.clearAllContainers(player);
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
        hotbar.setItemStackForSlot((short) 5, new ItemStack(WorldConstants.ITEM_SHOP, 1), false);
        short slot = (short) (hotbar.getCapacity() - 1);
        hotbar.setItemStackForSlot(slot, new ItemStack(WorldConstants.ITEM_SERVER_SELECTOR, 1), false);
    }

    public static void ensureMenuItems(Player player) {
        if (!isInventoryWriteSafe(player)) {
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
        InventoryUtils.setIfMissing(hotbar, (short) 0, AscendConstants.ITEM_DEV_CINDERCLOTH);
        InventoryUtils.setIfMissing(hotbar, (short) 1, AscendConstants.ITEM_DEV_STORMSILK);
        InventoryUtils.setIfMissing(hotbar, (short) 2, AscendConstants.ITEM_DEV_COTTON);
        InventoryUtils.setIfMissing(hotbar, (short) 3, AscendConstants.ITEM_DEV_SHADOWEAVE);
        InventoryUtils.setIfMissing(hotbar, (short) 4, AscendConstants.ITEM_DEV_SILK);
        InventoryUtils.setIfMissing(hotbar, (short) 5, WorldConstants.ITEM_SHOP);
        short slot = (short) (capacity - 1);
        InventoryUtils.setIfMissing(hotbar, slot, WorldConstants.ITEM_SERVER_SELECTOR);
    }

    public static void giveRunItems(Player player) {
        if (!isInventoryWriteSafe(player)) {
            return;
        }
        InventoryUtils.clearAllContainers(player);
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

    private static boolean isInventoryWriteSafe(Player player) {
        return player != null && player.getWorld() != null;
    }
}
