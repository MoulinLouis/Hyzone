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
import org.bson.BsonDocument;
import org.bson.BsonString;

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
        placeEggChest(player, progress, layer);

        // Toast notification + achievement
        if (hudManager != null) {
            hudManager.showMineToast(playerId, "Egg", 1);
        }

        if (achievementTracker != null) {
            achievementTracker.checkAchievement(playerId, MineAchievement.FIRST_EGG);
        }
    }

    private static void placeEggChest(Player player, MinePlayerProgress progress, MineZoneLayer layer) {
        Inventory inventory = player.getInventory();
        if (inventory == null) return;
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) return;

        String layerId = layer.getId();
        String eggItemId = resolveEggItemId(layer);

        // Check if player already has a chest for this layer (via metadata scan)
        Short existingSlot = findSlotWithLayerMeta(hotbar, layerId);
        if (existingSlot != null) {
            ItemStack stack = hotbar.getItemStack(existingSlot);
            if (stack != null && stack.getQuantity() < 64) {
                hotbar.setItemStackForSlot(existingSlot,
                    new ItemStack(stack.getItemId(), stack.getQuantity() + 1, stack.getMetadata()), false);
            }
            // If already 64, egg stays in backing store only
            return;
        }

        // Fallback: check chestSlotMap for legacy items without metadata
        Short legacySlot = progress.findSlotForLayer(layerId);
        if (legacySlot != null) {
            ItemStack stack = hotbar.getItemStack(legacySlot);
            if (stack != null && stack.getQuantity() < 64) {
                // Upgrade legacy item with metadata
                BsonDocument meta = new BsonDocument("EggLayerId", new BsonString(layerId));
                hotbar.setItemStackForSlot(legacySlot,
                    new ItemStack(eggItemId, stack.getQuantity() + 1, meta), false);
            }
            return;
        }

        // Find first empty slot in range 3-6
        for (short slot = CHEST_SLOT_MIN; slot <= CHEST_SLOT_MAX; slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                BsonDocument meta = new BsonDocument("EggLayerId", new BsonString(layerId));
                hotbar.setItemStackForSlot(slot, new ItemStack(eggItemId, 1, meta), false);
                progress.assignChestSlot(slot, layerId);
                return;
            }
        }

        // All slots occupied
        player.sendMessage(Message.raw("Chest slots full!"));
    }

    static Short findSlotWithLayerMeta(ItemContainer hotbar, String layerId) {
        for (short slot = CHEST_SLOT_MIN; slot <= CHEST_SLOT_MAX; slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) continue;
            BsonDocument meta = stack.getMetadata();
            if (meta != null && meta.containsKey("EggLayerId")
                    && layerId.equals(meta.getString("EggLayerId").getValue())) {
                return slot;
            }
        }
        return null;
    }

    public static String resolveEggItemId(MineZoneLayer layer) {
        return (layer != null && layer.getEggItemId() != null)
            ? layer.getEggItemId() : AscendConstants.ITEM_MINE_EGG_CHEST;
    }
}
