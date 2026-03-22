package io.hyvexa.ascend.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.mine.data.CollectedMiner;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.egg.EggOpenService;
import io.hyvexa.ascend.mine.egg.EggRouletteAnimation;

import javax.annotation.Nonnull;
import java.util.UUID;

public class MineEggChestInteraction extends SimpleInteraction {

    public static final BuilderCodec<MineEggChestInteraction> CODEC =
            BuilderCodec.builder(MineEggChestInteraction.class, MineEggChestInteraction::new).build();

    private static final short CHEST_SLOT_MIN = 3;
    private static final short CHEST_SLOT_MAX = 6;

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;

        UUID playerId = playerRef.getUuid();
        AscendInteractionBridge.Services services = AscendInteractionBridge.get();
        if (services == null) return;

        MinePlayerStore minePlayerStore = services.minePlayerStore();
        EggOpenService eggService = services.eggOpenService();
        if (minePlayerStore == null || eggService == null) return;

        MinePlayerProgress progress = minePlayerStore.getOrCreatePlayer(playerId);

        // Find the active chest slot
        Short activeSlot = findActiveChestSlot(player, progress);
        if (activeSlot == null) return;

        String layerId = progress.getChestLayerId(activeSlot);
        if (layerId == null) return;

        // Open the egg (rolls rarity, removes from backing store)
        CollectedMiner miner = eggService.openEgg(playerId, layerId, progress);
        if (miner == null) {
            player.sendMessage(Message.raw("No eggs of this type!"));
            return;
        }

        // Decrement or remove the physical item
        Inventory inventory = player.getInventory();
        ItemContainer hotbar = inventory != null ? inventory.getHotbar() : null;
        if (hotbar != null) {
            ItemStack stack = hotbar.getItemStack(activeSlot);
            if (stack != null && stack.getQuantity() > 1) {
                hotbar.setItemStackForSlot(activeSlot, new ItemStack(AscendConstants.ITEM_MINE_EGG_CHEST, stack.getQuantity() - 1), false);
            } else {
                hotbar.setItemStackForSlot(activeSlot, null, false);
                progress.removeChestSlot(activeSlot);
            }
        }

        // Play the 3D roulette animation
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        PacketHandler ph = playerRef.getPacketHandler();
        if (world == null || ph == null) {
            player.sendMessage(Message.raw("Hatched a " + miner.getRarity().getDisplayName() + " miner!"));
            return;
        }
        EggRouletteAnimation.play(player, ph, playerRef, store, ref, world, miner.getRarity(), () -> {});
    }

    private Short findActiveChestSlot(Player player, MinePlayerProgress progress) {
        Inventory inventory = player.getInventory();
        if (inventory == null) return null;
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) return null;

        for (short slot = CHEST_SLOT_MIN; slot <= CHEST_SLOT_MAX; slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            boolean hasItem = stack != null && AscendConstants.ITEM_MINE_EGG_CHEST.equals(stack.getItemId());
            String mappedLayer = progress.getChestLayerId(slot);

            if (hasItem && mappedLayer != null) {
                return slot;
            }
            // Rebuild on mismatch: item exists but no map entry
            if (hasItem && mappedLayer == null) {
                rebuildChestSlotMap(hotbar, progress);
                mappedLayer = progress.getChestLayerId(slot);
                if (mappedLayer != null) return slot;
            }
            // Stale map entry: no item but map entry exists
            if (!hasItem && mappedLayer != null) {
                progress.removeChestSlot(slot);
            }
        }
        return null;
    }

    private void rebuildChestSlotMap(ItemContainer hotbar, MinePlayerProgress progress) {
        progress.clearChestSlots();
        var eggs = progress.getEggInventory();
        var usedLayers = new java.util.HashSet<String>();

        for (short slot = CHEST_SLOT_MIN; slot <= CHEST_SLOT_MAX; slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null || !AscendConstants.ITEM_MINE_EGG_CHEST.equals(stack.getItemId())) continue;

            // Try to match this slot to an egg layer based on available eggs
            for (var entry : eggs.entrySet()) {
                if (entry.getValue() > 0 && !usedLayers.contains(entry.getKey())) {
                    progress.assignChestSlot(slot, entry.getKey());
                    usedLayers.add(entry.getKey());
                    break;
                }
            }
        }
    }
}
