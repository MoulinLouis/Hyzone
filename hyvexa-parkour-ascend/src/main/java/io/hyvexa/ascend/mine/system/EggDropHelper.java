package io.hyvexa.ascend.mine.system;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.mine.achievement.MineAchievement;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.data.MineZone;
import io.hyvexa.ascend.mine.data.MineZoneLayer;
import io.hyvexa.ascend.mine.hud.MineHudManager;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class EggDropHelper {

    private static final short CHEST_SLOT_MIN = 3;
    private static final short CHEST_SLOT_MAX = 6;

    private EggDropHelper() {}

    public static void tryDropEgg(UUID playerId, Player player, MineZone zone, int blockY,
                                  MinePlayerProgress progress, MinePlayerStore store,
                                  MineHudManager hudManager, MineAchievementTracker achievementTracker) {
        MineZoneLayer layer = zone.getLayerForY(blockY);
        if (layer == null) return;

        if (ThreadLocalRandom.current().nextDouble() >= layer.getEggDropChance()) return;

        progress.addEgg(layer.getId());
        store.markDirty(playerId);

        // Place physical chest in hotbar
        placeEggChest(player, progress, layer.getId());

        // Toast notification + achievement
        if (hudManager != null) {
            hudManager.showMineToast(playerId, "Egg", 1);
        }

        if (achievementTracker != null) {
            achievementTracker.checkAchievement(playerId, MineAchievement.FIRST_EGG);
        }
    }

    private static void placeEggChest(Player player, MinePlayerProgress progress, String layerId) {
        Inventory inventory = player.getInventory();
        if (inventory == null) return;
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) return;

        // Check if player already has a chest for this layer
        Short existingSlot = progress.findSlotForLayer(layerId);
        if (existingSlot != null) {
            ItemStack stack = hotbar.getItemStack(existingSlot);
            if (stack != null && stack.getQuantity() < 64) {
                hotbar.setItemStackForSlot(existingSlot,
                    new ItemStack(AscendConstants.ITEM_MINE_EGG_CHEST, stack.getQuantity() + 1), false);
            }
            // If already 64, egg stays in backing store only
            return;
        }

        // Find first empty slot in range 3-6
        for (short slot = CHEST_SLOT_MIN; slot <= CHEST_SLOT_MAX; slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                hotbar.setItemStackForSlot(slot, new ItemStack(AscendConstants.ITEM_MINE_EGG_CHEST, 1), false);
                progress.assignChestSlot(slot, layerId);
                return;
            }
        }

        // All slots occupied
        player.sendMessage(Message.raw("Chest slots full!"));
    }
}
