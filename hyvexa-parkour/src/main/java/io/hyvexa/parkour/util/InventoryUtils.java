package io.hyvexa.parkour.util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import io.hyvexa.common.util.PlayerUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.duel.DuelConstants;
import io.hyvexa.parkour.ParkourConstants;
import io.hyvexa.parkour.data.Map;

import java.util.Optional;

public final class InventoryUtils {

    private InventoryUtils() {
    }

    private static Optional<ItemContainer> getValidHotbar(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(inventory.getHotbar());
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
        boolean isOp = PermissionUtils.isOp(player);
        prepareInventory(inventory, isOp);

        boolean hasSword = map != null && map.isMithrilSwordEnabled();
        boolean hasDaggers = map != null && map.isMithrilDaggersEnabled();
        boolean hasGlider = map != null && map.isGliderEnabled();
        if (practiceEnabled) {
            // Practice mode order:
            // Set checkpoint, Restart to checkpoint, Leave map, Leave practice, Toggle fly, Player settings
            setHotbarItem(inventory, 0, new ItemStack(ParkourConstants.ITEM_PRACTICE_CHECKPOINT, 1));
            setHotbarItem(inventory, 1, new ItemStack(ParkourConstants.ITEM_RESTART_CHECKPOINT, 1));
            setHotbarItem(inventory, 2, new ItemStack(ParkourConstants.ITEM_LEAVE, 1));
            setHotbarItem(inventory, 3, new ItemStack(ParkourConstants.ITEM_LEAVE_PRACTICE, 1));
            setHotbarItem(inventory, 4, new ItemStack(ParkourConstants.ITEM_TOGGLE_FLY, 1));
            setHotbarItem(inventory, 5, new ItemStack(ParkourConstants.ITEM_ADMIN_REMOTE, 1));
            int slotIndex = 6;
            if (hasSword) {
                setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RUN_MITHRIL_SWORD, 1));
            }
            if (hasDaggers) {
                setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RUN_MITHRIL_DAGGERS, 1));
            }
            if (hasGlider) {
                setHotbarItem(inventory, slotIndex, new ItemStack(ParkourConstants.ITEM_RUN_GLIDER, 1));
            }
            finalizeInventory(inventory, isOp);
            return;
        }

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
        if (PlayerSettingsStore.isResetItemEnabled(PlayerUtils.resolvePlayerId(player))) {
            setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RESET, 1));
        }
        setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_RESTART_CHECKPOINT, 1));
        setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_LEAVE, 1));
        setHotbarItem(inventory, slotIndex++, new ItemStack(ParkourConstants.ITEM_PRACTICE, 1));
        setHotbarItem(inventory, slotIndex, new ItemStack(ParkourConstants.ITEM_ADMIN_REMOTE, 1));
        finalizeInventory(inventory, isOp);
    }

    public static void giveDuelItems(Player player, Map map) {
        getValidHotbar(player).ifPresent(hotbar -> {
            hotbar.setItemStackForSlot((short) 0, new ItemStack(ParkourConstants.ITEM_RESET, 1), false);
            hotbar.setItemStackForSlot((short) 1, new ItemStack(ParkourConstants.ITEM_RESTART_CHECKPOINT, 1), false);
            hotbar.setItemStackForSlot((short) 2, new ItemStack(DuelConstants.ITEM_FORFEIT, 1), false);
            for (short i = 3; i < 9; i++) {
                hotbar.setItemStackForSlot(i, ItemStack.EMPTY, false);
            }
        });
    }

    public static void giveMenuItems(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        boolean isOp = PermissionUtils.isOp(player);
        prepareInventory(inventory, isOp);
        setHotbarItem(inventory, 0, new ItemStack(ParkourConstants.ITEM_MENU, 1));
        setHotbarItem(inventory, 1, new ItemStack(DuelConstants.ITEM_MENU, 1));
        setHotbarItem(inventory, 2, new ItemStack(ParkourConstants.ITEM_LEADERBOARD, 1));
        setHotbarItem(inventory, 3, new ItemStack(ParkourConstants.ITEM_STATS, 1));
        setHotbarItem(inventory, 4, new ItemStack(ParkourConstants.ITEM_ADMIN_REMOTE, 1));
        setHotbarItem(inventory, 8, new ItemStack(ParkourConstants.ITEM_HUB_MENU, 1));
        finalizeInventory(inventory, isOp);
    }

    private static void prepareInventory(Inventory inventory, boolean isOp) {
        if (isOp) {
            io.hyvexa.common.util.InventoryUtils.clearContainer(inventory.getHotbar());
        } else {
            applyDropFilters(inventory, false);
            io.hyvexa.common.util.InventoryUtils.clearAllContainers(inventory);
        }
    }

    private static void finalizeInventory(Inventory inventory, boolean isOp) {
        if (isOp) {
            applyDropFilter(inventory.getHotbar(), false);
        } else {
            applyDropFilters(inventory, false);
        }
    }

    public static void clearAllItems(Player player) {
        io.hyvexa.common.util.InventoryUtils.clearAllContainers(player);
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
