package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.core.cosmetic.CosmeticDefinition;
import io.hyvexa.core.cosmetic.CosmeticStore;
import io.hyvexa.core.economy.VexaStore;
import io.hyvexa.parkour.cosmetic.CosmeticManager;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CosmeticShopPage extends BaseParkourPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String PREFIX_PREVIEW = "Preview:";
    private static final String PREFIX_BUY = "Buy:";
    private static final String PREFIX_EQUIP = "Equip:";
    private static final String PREFIX_UNEQUIP = "Unequip:";

    private final CosmeticManager cosmeticManager;

    public CosmeticShopPage(@Nonnull PlayerRef playerRef, CosmeticManager cosmeticManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.cosmeticManager = cosmeticManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Parkour_CosmeticShop.ui");

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;

        // Set vexa balance
        long vexa = playerId != null ? VexaStore.getInstance().getVexa(playerId) : 0;
        commandBuilder.set("#VexaBalance.Text", String.valueOf(vexa));

        String equippedId = playerId != null ? CosmeticStore.getInstance().getEquippedCosmeticId(playerId) : null;

        // Append entries for each cosmetic
        CosmeticDefinition[] defs = CosmeticDefinition.values();
        for (int i = 0; i < defs.length; i++) {
            CosmeticDefinition def = defs[i];
            String id = def.getId();
            boolean owned = playerId != null && CosmeticStore.getInstance().ownsCosmetic(playerId, id);
            boolean equipped = id.equals(equippedId);

            commandBuilder.append("#CosmeticItems", "Pages/Parkour_CosmeticShopEntry.ui");
            String prefix = "#CosmeticItems[" + i + "] ";

            // Set display name and accent color
            commandBuilder.set(prefix + "#EntryAccent.Background", def.getHexColor());
            commandBuilder.set(prefix + "#EntryName.Text", def.getDisplayName());
            commandBuilder.set(prefix + "#EntryPrice.Text", def.getPrice() + " Vexa");

            // Show/hide buttons based on ownership state
            commandBuilder.set(prefix + "#BuyGroup.Visible", !owned);
            commandBuilder.set(prefix + "#EquipGroup.Visible", owned && !equipped);
            commandBuilder.set(prefix + "#UnequipGroup.Visible", owned && equipped);

            // Bind button events with cosmetic ID encoded in the button string
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    prefix + "#PreviewButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, PREFIX_PREVIEW + id), false);

            if (!owned) {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        prefix + "#BuyButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, PREFIX_BUY + id), false);
            } else if (!equipped) {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        prefix + "#EquipButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, PREFIX_EQUIP + id), false);
            } else {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                        prefix + "#UnequipButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, PREFIX_UNEQUIP + id), false);
            }
        }

        // Close button
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        String button = data.getButton();
        if (button == null) return;

        if (BUTTON_CLOSE.equals(button)) {
            this.close();
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;
        UUID playerId = playerRef.getUuid();

        if (button.startsWith(PREFIX_PREVIEW)) {
            String cosmeticId = button.substring(PREFIX_PREVIEW.length());
            CosmeticDefinition def = CosmeticDefinition.fromId(cosmeticId);
            String name = def != null ? def.getDisplayName() : cosmeticId;
            player.sendMessage(Message.raw("[Shop] Previewing " + name + " for 5 seconds...")
                    .color(SystemMessageUtils.SECONDARY));
            executeOnWorldThread(player, (wRef, wStore) ->
                    cosmeticManager.previewCosmetic(wRef, wStore, cosmeticId));
            return;
        }

        if (button.startsWith(PREFIX_BUY)) {
            String cosmeticId = button.substring(PREFIX_BUY.length());
            handleBuy(player, playerRef, playerId, cosmeticId);
            return;
        }

        if (button.startsWith(PREFIX_EQUIP)) {
            String cosmeticId = button.substring(PREFIX_EQUIP.length());
            handleEquip(player, playerId, cosmeticId);
            return;
        }

        if (button.startsWith(PREFIX_UNEQUIP)) {
            handleUnequip(player, playerId);
        }
    }

    // ── World thread helper ──────────────────────────────────────────────

    private void executeOnWorldThread(Player player, WorldThreadAction action) {
        Ref<EntityStore> freshRef = player.getReference();
        if (freshRef == null || !freshRef.isValid()) return;
        Store<EntityStore> freshStore = freshRef.getStore();
        World world = freshStore.getExternalData().getWorld();
        if (world == null) return;
        CompletableFuture.runAsync(() -> {
            if (!freshRef.isValid()) return;
            action.run(freshRef, freshStore);
        }, world);
    }

    @FunctionalInterface
    private interface WorldThreadAction {
        void run(Ref<EntityStore> ref, Store<EntityStore> store);
    }

    // ── Handlers ─────────────────────────────────────────────────────────

    private void handleBuy(Player player, PlayerRef playerRef, UUID playerId, String cosmeticId) {
        CosmeticDefinition def = CosmeticDefinition.fromId(cosmeticId);
        if (def == null) return;

        long vexa = VexaStore.getInstance().getVexa(playerId);
        if (vexa < def.getPrice()) {
            player.sendMessage(Message.raw("[Shop] Not enough vexa! You need " + def.getPrice()
                    + " vexa but have " + vexa + ".").color(SystemMessageUtils.ERROR));
            return;
        }

        VexaStore.getInstance().removeVexa(playerId, def.getPrice());
        CosmeticStore.getInstance().purchaseCosmetic(playerId, cosmeticId);
        player.sendMessage(Message.raw("[Shop] Purchased " + def.getDisplayName() + "!")
                .color(SystemMessageUtils.SUCCESS));
        try {
            io.hyvexa.core.analytics.AnalyticsStore.getInstance().logEvent(playerId, "gem_spend",
                    "{\"amount\":" + def.getPrice() + ",\"item\":\"" + cosmeticId + "\"}");
        } catch (Exception e) { /* silent */ }

        // Re-open page on world thread to refresh state
        executeOnWorldThread(player, (wRef, wStore) -> {
            Player p = wStore.getComponent(wRef, Player.getComponentType());
            PlayerRef pr = wStore.getComponent(wRef, PlayerRef.getComponentType());
            if (p != null && pr != null) {
                p.getPageManager().openCustomPage(wRef, wStore,
                        new CosmeticShopPage(pr, cosmeticManager));
            }
        });
    }

    private void handleEquip(Player player, UUID playerId, String cosmeticId) {
        CosmeticStore.getInstance().equipCosmetic(playerId, cosmeticId);
        CosmeticDefinition def = CosmeticDefinition.fromId(cosmeticId);
        String name = def != null ? def.getDisplayName() : cosmeticId;
        player.sendMessage(Message.raw("[Shop] Equipped " + name + "!")
                .color(SystemMessageUtils.SUCCESS));

        executeOnWorldThread(player, (wRef, wStore) -> {
            cosmeticManager.applyCosmetic(wRef, wStore, cosmeticId);
            Player p = wStore.getComponent(wRef, Player.getComponentType());
            PlayerRef pr = wStore.getComponent(wRef, PlayerRef.getComponentType());
            if (p != null && pr != null) {
                p.getPageManager().openCustomPage(wRef, wStore,
                        new CosmeticShopPage(pr, cosmeticManager));
            }
        });
    }

    private void handleUnequip(Player player, UUID playerId) {
        CosmeticStore.getInstance().unequipCosmetic(playerId);
        player.sendMessage(Message.raw("[Shop] Cosmetic unequipped.")
                .color(SystemMessageUtils.SECONDARY));

        executeOnWorldThread(player, (wRef, wStore) -> {
            cosmeticManager.removeCosmetic(wRef, wStore);
            Player p = wStore.getComponent(wRef, Player.getComponentType());
            PlayerRef pr = wStore.getComponent(wRef, PlayerRef.getComponentType());
            if (p != null && pr != null) {
                p.getPageManager().openCustomPage(wRef, wStore,
                        new CosmeticShopPage(pr, cosmeticManager));
            }
        });
    }
}
