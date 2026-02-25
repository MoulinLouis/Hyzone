package io.hyvexa.wardrobe;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.shop.ShopTab;
import io.hyvexa.common.shop.ShopTabResult;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.core.cosmetic.CosmeticDefinition;
import io.hyvexa.core.cosmetic.CosmeticManager;
import io.hyvexa.core.cosmetic.CosmeticStore;
import io.hyvexa.core.economy.VexaStore;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CosmeticsShopTab implements ShopTab {

    private static final String ACTION_PREVIEW = "Preview:";
    private static final String ACTION_BUY = "Buy:";
    private static final String ACTION_EQUIP = "Equip:";
    private static final String ACTION_UNEQUIP = "Unequip:";

    @Override
    public String getId() {
        return "cosmetics";
    }

    @Override
    public String getLabel() {
        return "Cosmetics";
    }

    @Override
    public String getAccentColor() {
        return "#6366f1";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public void buildContent(UICommandBuilder cmd, UIEventBuilder evt, UUID playerId, long vexa) {
        String equippedId = playerId != null ? CosmeticStore.getInstance().getEquippedCosmeticId(playerId) : null;

        CosmeticDefinition[] defs = CosmeticDefinition.values();
        for (int i = 0; i < defs.length; i++) {
            CosmeticDefinition def = defs[i];
            String id = def.getId();
            boolean owned = playerId != null && CosmeticStore.getInstance().ownsCosmetic(playerId, id);
            boolean equipped = id.equals(equippedId);

            cmd.append("#TabContent", "Pages/Parkour_CosmeticShopEntry.ui");
            String root = "#TabContent[" + i + "] ";

            cmd.set(root + "#EntryAccent.Background", def.getHexColor());
            cmd.set(root + "#EntryName.Text", def.getDisplayName());
            cmd.set(root + "#EntryPrice.Text", def.getPrice() + " Vexa");

            cmd.set(root + "#BuyGroup.Visible", !owned);
            cmd.set(root + "#EquipGroup.Visible", owned && !equipped);
            cmd.set(root + "#UnequipGroup.Visible", owned && equipped);

            evt.addEventBinding(CustomUIEventBindingType.Activating,
                    root + "#PreviewButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, getId() + ":" + ACTION_PREVIEW + id), false);

            if (!owned) {
                evt.addEventBinding(CustomUIEventBindingType.Activating,
                        root + "#BuyButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, getId() + ":" + ACTION_BUY + id), false);
            } else if (!equipped) {
                evt.addEventBinding(CustomUIEventBindingType.Activating,
                        root + "#EquipButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, getId() + ":" + ACTION_EQUIP + id), false);
            } else {
                evt.addEventBinding(CustomUIEventBindingType.Activating,
                        root + "#UnequipButton",
                        EventData.of(ButtonEventData.KEY_BUTTON, getId() + ":" + ACTION_UNEQUIP + id), false);
            }
        }
    }

    @Override
    public ShopTabResult handleEvent(String button, Ref<EntityStore> ref, Store<EntityStore> store,
                                     Player player, UUID playerId) {
        if (button.startsWith(ACTION_PREVIEW)) {
            String cosmeticId = button.substring(ACTION_PREVIEW.length());
            CosmeticDefinition def = CosmeticDefinition.fromId(cosmeticId);
            String name = def != null ? def.getDisplayName() : cosmeticId;
            player.sendMessage(Message.raw("[Shop] Previewing " + name + " for 5 seconds...")
                    .color(SystemMessageUtils.SECONDARY));
            executeOnWorldThread(player, (wRef, wStore) ->
                    CosmeticManager.getInstance().previewCosmetic(wRef, wStore, cosmeticId));
            return ShopTabResult.NONE;
        }

        if (button.startsWith(ACTION_BUY)) {
            String cosmeticId = button.substring(ACTION_BUY.length());
            return handleBuy(player, playerId, cosmeticId);
        }

        if (button.startsWith(ACTION_EQUIP)) {
            String cosmeticId = button.substring(ACTION_EQUIP.length());
            return handleEquip(player, playerId, cosmeticId);
        }

        if (button.startsWith(ACTION_UNEQUIP)) {
            return handleUnequip(player, playerId);
        }

        return ShopTabResult.NONE;
    }

    private ShopTabResult handleBuy(Player player, UUID playerId, String cosmeticId) {
        CosmeticDefinition def = CosmeticDefinition.fromId(cosmeticId);
        if (def == null) return ShopTabResult.NONE;

        long vexa = VexaStore.getInstance().getVexa(playerId);
        if (vexa < def.getPrice()) {
            player.sendMessage(Message.raw("[Shop] Not enough vexa! You need " + def.getPrice()
                    + " vexa but have " + vexa + ".").color(SystemMessageUtils.ERROR));
            return ShopTabResult.NONE;
        }

        VexaStore.getInstance().removeVexa(playerId, def.getPrice());
        CosmeticStore.getInstance().purchaseCosmetic(playerId, cosmeticId);
        player.sendMessage(Message.raw("[Shop] Purchased " + def.getDisplayName() + "!")
                .color(SystemMessageUtils.SUCCESS));
        try {
            io.hyvexa.core.analytics.AnalyticsStore.getInstance().logEvent(playerId, "gem_spend",
                    "{\"amount\":" + def.getPrice() + ",\"item\":\"" + cosmeticId + "\"}");
        } catch (Exception e) { /* silent */ }

        return ShopTabResult.REFRESH;
    }

    private ShopTabResult handleEquip(Player player, UUID playerId, String cosmeticId) {
        CosmeticStore.getInstance().equipCosmetic(playerId, cosmeticId);
        CosmeticDefinition def = CosmeticDefinition.fromId(cosmeticId);
        String name = def != null ? def.getDisplayName() : cosmeticId;
        player.sendMessage(Message.raw("[Shop] Equipped " + name + "!")
                .color(SystemMessageUtils.SUCCESS));

        executeOnWorldThread(player, (wRef, wStore) ->
                CosmeticManager.getInstance().applyCosmetic(wRef, wStore, cosmeticId));

        return ShopTabResult.REFRESH;
    }

    private ShopTabResult handleUnequip(Player player, UUID playerId) {
        CosmeticStore.getInstance().unequipCosmetic(playerId);
        player.sendMessage(Message.raw("[Shop] Cosmetic unequipped.")
                .color(SystemMessageUtils.SECONDARY));

        executeOnWorldThread(player, (wRef, wStore) ->
                CosmeticManager.getInstance().removeCosmetic(wRef, wStore));

        return ShopTabResult.REFRESH;
    }

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
}
