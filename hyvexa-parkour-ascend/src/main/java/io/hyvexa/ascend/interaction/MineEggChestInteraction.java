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
import org.bson.BsonDocument;

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

        // Use the exact slot the player right-clicked
        short activeSlot = interactionContext.getHeldItemSlot();
        if (activeSlot < CHEST_SLOT_MIN || activeSlot > CHEST_SLOT_MAX) return;

        Inventory inventory = player.getInventory();
        ItemContainer hotbar = inventory != null ? inventory.getHotbar() : null;
        if (hotbar == null) return;

        ItemStack heldStack = hotbar.getItemStack(activeSlot);
        if (heldStack == null || ItemStack.isEmpty(heldStack)) return;

        // Resolve layerId: primary source is item metadata, fallback to chestSlotMap for legacy items
        String layerId = null;
        BsonDocument meta = heldStack.getMetadata();
        if (meta != null && meta.containsKey("EggLayerId")) {
            layerId = meta.getString("EggLayerId").getValue();
        }
        if (layerId == null) {
            layerId = progress.getChestLayerId(activeSlot);
        }
        if (layerId == null) return;

        // Open the egg (rolls rarity, removes from backing store)
        CollectedMiner miner = eggService.openEgg(playerId, layerId, progress);
        if (miner == null) {
            player.sendMessage(Message.raw("No eggs of this type!"));
            return;
        }

        // Decrement or remove the physical item, preserving metadata and itemId
        if (heldStack.getQuantity() > 1) {
            hotbar.setItemStackForSlot(activeSlot,
                new ItemStack(heldStack.getItemId(), heldStack.getQuantity() - 1, heldStack.getMetadata()), false);
        } else {
            hotbar.setItemStackForSlot(activeSlot, null, false);
            progress.removeChestSlot(activeSlot);
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
}
