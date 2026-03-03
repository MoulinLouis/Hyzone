package io.hyvexa.wardrobe;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.shop.ShopTab;
import io.hyvexa.common.shop.ShopTabResult;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.core.cosmetic.CosmeticDefinition;
import io.hyvexa.core.cosmetic.CosmeticManager;
import io.hyvexa.core.cosmetic.CosmeticStore;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class EffectsShopTab implements ShopTab {

    private static final String ACTION_PREVIEW = "Preview:";
    private static final String ACTION_BUY = "Buy:";
    private static final String ACTION_EQUIP = "Equip:";
    private static final String ACTION_UNEQUIP = "Unequip:";
    private static final String FILTER_GLOW = "Glow";
    private static final String FILTER_TRAIL = "Trail";

    private final ConcurrentHashMap<UUID, String> selectedFilter = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "effects";
    }

    @Override
    public String getLabel() {
        return "Effects";
    }

    @Override
    public String getAccentColor() {
        return "#6366f1";
    }

    @Override
    public int getOrder() {
        return 6;
    }

    @Override
    public void buildContent(UICommandBuilder cmd, UIEventBuilder evt, UUID playerId, long vexa) {
        String equippedId = CosmeticStore.getInstance().getEquippedCosmeticId(playerId);
        String filter = selectedFilter.getOrDefault(playerId, FILTER_GLOW);

        // Pill bar
        cmd.append("#TabContent", "Pages/Shop_WardrobePills.ui");
        int pillIndex = 0;

        // "Glow" pill
        cmd.append("#PillBar", "Pages/Shop_WardrobePill.ui");
        String glowRoot = "#PillBar[" + pillIndex + "] ";
        cmd.set(glowRoot + "#PillLabel.Text", FILTER_GLOW);
        if (FILTER_GLOW.equals(filter)) {
            cmd.set(glowRoot + "#PillActive.Visible", true);
            cmd.set(glowRoot + "#PillLabel.Style.TextColor", "#6366f1");
        }
        evt.addEventBinding(CustomUIEventBindingType.Activating, glowRoot + "#PillButton",
                EventData.of(ButtonEventData.KEY_BUTTON, getId() + ":" + WardrobeShopUiUtils.ACTION_FILTER + FILTER_GLOW), false);
        pillIndex++;

        // "Trail" pill
        cmd.append("#PillBar", "Pages/Shop_WardrobePill.ui");
        String trailRoot = "#PillBar[" + pillIndex + "] ";
        cmd.set(trailRoot + "#PillLabel.Text", FILTER_TRAIL);
        if (FILTER_TRAIL.equals(filter)) {
            cmd.set(trailRoot + "#PillActive.Visible", true);
            cmd.set(trailRoot + "#PillLabel.Style.TextColor", "#6366f1");
        }
        evt.addEventBinding(CustomUIEventBindingType.Activating, trailRoot + "#PillButton",
                EventData.of(ButtonEventData.KEY_BUTTON, getId() + ":" + WardrobeShopUiUtils.ACTION_FILTER + FILTER_TRAIL), false);

        // Entries
        CosmeticDefinition.Kind kind = FILTER_TRAIL.equals(filter)
                ? CosmeticDefinition.Kind.TRAIL : CosmeticDefinition.Kind.GLOW;
        int contentIndex = 1; // pill bar is index 0
        for (CosmeticDefinition def : CosmeticDefinition.values()) {
            if (def.getKind() != kind) continue;
            contentIndex = appendEntry(cmd, evt, playerId, equippedId, def, contentIndex);
        }
    }

    @Override
    public ShopTabResult handleEvent(String button, Ref<EntityStore> ref, Store<EntityStore> store,
                                     Player player, UUID playerId) {
        if (button.startsWith(WardrobeShopUiUtils.ACTION_FILTER)) {
            String filter = button.substring(WardrobeShopUiUtils.ACTION_FILTER.length());
            selectedFilter.put(playerId, filter);
            return ShopTabResult.REFRESH;
        }

        if (button.startsWith(ACTION_PREVIEW)) {
            String cosmeticId = button.substring(ACTION_PREVIEW.length());
            CosmeticDefinition def = CosmeticDefinition.fromId(cosmeticId);
            String name = def != null ? def.getDisplayName() : cosmeticId;
            if (!executeOnWorldThread(player, (wRef, wStore) ->
                    CosmeticManager.getInstance().previewCosmetic(wRef, wStore, cosmeticId))) {
                player.sendMessage(Message.raw("[Shop] Could not preview right now. Try again.")
                        .color(SystemMessageUtils.ERROR));
                return ShopTabResult.NONE;
            }
            player.sendMessage(Message.raw("[Shop] Previewing " + name + " for 5 seconds...")
                    .color(SystemMessageUtils.SECONDARY));
            return ShopTabResult.NONE;
        }

        if (button.startsWith(ACTION_BUY)) {
            String cosmeticId = button.substring(ACTION_BUY.length());
            CosmeticDefinition def = CosmeticDefinition.fromId(cosmeticId);
            if (def == null) return ShopTabResult.NONE;
            CosmeticStore.PurchaseResult result = CosmeticStore.getInstance().purchaseShopCosmetic(playerId, def);
            String color = result.success() ? SystemMessageUtils.SUCCESS : SystemMessageUtils.ERROR;
            player.sendMessage(Message.raw("[Shop] " + result.message()).color(color));
            return result.success() ? ShopTabResult.REFRESH : ShopTabResult.NONE;
        }

        if (button.startsWith(ACTION_EQUIP)) {
            String cosmeticId = button.substring(ACTION_EQUIP.length());
            CosmeticStore.getInstance().equipCosmetic(playerId, cosmeticId);
            CosmeticDefinition def = CosmeticDefinition.fromId(cosmeticId);
            String name = def != null ? def.getDisplayName() : cosmeticId;
            if (!executeOnWorldThread(player, (wRef, wStore) ->
                    CosmeticManager.getInstance().applyCosmetic(wRef, wStore, cosmeticId))) {
                player.sendMessage(Message.raw("[Shop] Could not apply that cosmetic right now. Try again.")
                        .color(SystemMessageUtils.ERROR));
                return ShopTabResult.REFRESH;
            }
            player.sendMessage(Message.raw("[Shop] Equipped " + name + "!")
                    .color(SystemMessageUtils.SUCCESS));
            return ShopTabResult.REFRESH;
        }

        if (button.startsWith(ACTION_UNEQUIP)) {
            CosmeticStore.getInstance().unequipCosmetic(playerId);
            if (!executeOnWorldThread(player, (wRef, wStore) ->
                    CosmeticManager.getInstance().removeCosmetic(wRef, wStore))) {
                player.sendMessage(Message.raw("[Shop] Could not remove cosmetic visual right now. Try again.")
                        .color(SystemMessageUtils.ERROR));
                return ShopTabResult.REFRESH;
            }
            player.sendMessage(Message.raw("[Shop] Cosmetic unequipped.")
                    .color(SystemMessageUtils.SECONDARY));
            return ShopTabResult.REFRESH;
        }

        return ShopTabResult.NONE;
    }

    @Override
    public void evictPlayer(UUID playerId) {
        selectedFilter.remove(playerId);
    }

    private int appendEntry(UICommandBuilder cmd, UIEventBuilder evt, UUID playerId, String equippedId,
                            CosmeticDefinition def, int index) {
        String id = def.getId();
        boolean owned = CosmeticStore.getInstance().ownsCosmetic(playerId, id);
        boolean equipped = id.equals(equippedId);

        cmd.append("#TabContent", "Pages/Parkour_CosmeticShopEntry.ui");
        String root = "#TabContent[" + index + "] ";

        cmd.set(root + "#EntryAccent.Background", def.getHexColor());
        cmd.set(root + "#EntryName.Text", def.getDisplayName());
        String currency = def.getKind() == CosmeticDefinition.Kind.TRAIL ? "Feathers" : "Vexa";
        cmd.set(root + "#EntryPrice.Text", def.getPrice() + " " + currency);

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
        return index + 1;
    }

    private boolean executeOnWorldThread(Player player, WorldThreadAction action) {
        Ref<EntityStore> freshRef = player.getReference();
        if (freshRef == null || !freshRef.isValid()) return false;
        Store<EntityStore> freshStore = freshRef.getStore();
        if (freshStore == null || freshStore.getExternalData() == null) return false;
        var world = freshStore.getExternalData().getWorld();
        if (world == null) return false;
        CompletableFuture.runAsync(() -> {
            if (!freshRef.isValid()) return;
            action.run(freshRef, freshStore);
        }, world);
        return true;
    }

    @FunctionalInterface
    private interface WorldThreadAction {
        void run(Ref<EntityStore> ref, Store<EntityStore> store);
    }
}
