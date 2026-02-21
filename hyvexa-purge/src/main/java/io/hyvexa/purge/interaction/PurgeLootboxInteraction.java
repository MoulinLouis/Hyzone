package io.hyvexa.purge.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.HyvexaPurgePlugin;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSessionPlayerState;
import io.hyvexa.purge.data.PurgeWeaponUpgradeStore;
import io.hyvexa.purge.manager.PurgeSessionManager;
import io.hyvexa.purge.ui.PurgeLootboxRollPage;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PurgeLootboxInteraction extends SimpleInteraction {

    public static final BuilderCodec<PurgeLootboxInteraction> CODEC =
            BuilderCodec.builder(PurgeLootboxInteraction.class, PurgeLootboxInteraction::new).build();

    private static final short SLOT_LOOTBOX = 2;

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        if (!ref.isValid()) {
            return;
        }
        var store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
        if (plugin == null) {
            return;
        }
        PurgeSessionManager sessionManager = plugin.getSessionManager();
        PurgeSession session = sessionManager.getSessionByPlayer(playerId);
        if (session == null) {
            player.sendMessage(Message.raw("No active Purge session."));
            return;
        }
        PurgeSessionPlayerState playerState = session.getPlayerState(playerId);
        if (playerState == null || !playerState.isAlive()) {
            return;
        }

        // Get owned weapons, exclude current
        Set<String> owned = PurgeWeaponUpgradeStore.getInstance().getOwnedWeaponIds(playerId);
        String currentWeapon = playerState.getCurrentWeaponId();
        List<String> candidates = new ArrayList<>();
        for (String weaponId : owned) {
            if (!weaponId.equals(currentWeapon)) {
                candidates.add(weaponId);
            }
        }

        if (candidates.isEmpty()) {
            player.sendMessage(Message.raw("You have no other weapons to roll."));
            return;
        }

        // Consume 1 lootbox
        Inventory inventory = player.getInventory();
        if (inventory != null && inventory.getHotbar() != null) {
            ItemStack existing = inventory.getHotbar().getItemStack(SLOT_LOOTBOX);
            if (existing != null && !existing.isEmpty()) {
                int remaining = existing.getQuantity() - 1;
                if (remaining <= 0) {
                    inventory.getHotbar().setItemStackForSlot(SLOT_LOOTBOX, ItemStack.EMPTY, false);
                } else {
                    inventory.getHotbar().setItemStackForSlot(SLOT_LOOTBOX,
                            new ItemStack(existing.getItemId(), remaining), false);
                }
            }
        }

        // Roll random weapon
        String rolledWeapon = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

        // Open lootbox UI
        player.getPageManager().openCustomPage(ref, store,
                new PurgeLootboxRollPage(playerRef, playerId, playerState, rolledWeapon, candidates));
    }
}
