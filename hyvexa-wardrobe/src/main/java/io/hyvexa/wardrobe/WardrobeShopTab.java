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
import io.hyvexa.core.cosmetic.CosmeticStore;
import io.hyvexa.core.economy.CurrencyBridge;
import io.hyvexa.core.wardrobe.CosmeticShopConfigStore;
import io.hyvexa.core.wardrobe.WardrobeBridge;
import io.hyvexa.core.wardrobe.WardrobeBridge.WardrobeCosmeticDef;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WardrobeShopTab implements ShopTab {

    private static final String ACTION_BUY = "Buy:";
    private static final String ACTION_FREE_TOGGLE = "FreeToggle";

    private final WardrobeBridge wardrobeBridge;
    private final CosmeticStore cosmeticStore;
    private final CosmeticShopConfigStore cosmeticShopConfigStore;
    private final ConcurrentHashMap<UUID, String> selectedCategory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> freeOnly = new ConcurrentHashMap<>();

    public WardrobeShopTab(WardrobeBridge wardrobeBridge, CosmeticStore cosmeticStore,
                           CosmeticShopConfigStore cosmeticShopConfigStore) {
        this.wardrobeBridge = wardrobeBridge;
        this.cosmeticStore = cosmeticStore;
        this.cosmeticShopConfigStore = cosmeticShopConfigStore;
    }

    @Override
    public String getId() {
        return "wardrobe";
    }

    @Override
    public String getLabel() {
        return "Cosmetics";
    }

    @Override
    public String getAccentColor() {
        return "#e879f9";
    }

    @Override
    public int getOrder() {
        return 5;
    }

    @Override
    public void buildContent(UICommandBuilder cmd, UIEventBuilder evt, UUID playerId, long vexa) {
        String currentCategory = selectedCategory.get(playerId);

        // Pill bar
        cmd.append("#TabContent", "Pages/Shop_WardrobePills.ui");
        List<String> categories = wardrobeBridge.getCategories();
        int pillIndex = WardrobeShopUiUtils.buildCategoryPills(cmd, evt, getId(), getAccentColor(),
                categories, currentCategory);

        // "Free" toggle checkbox
        boolean isFreeOnly = freeOnly.getOrDefault(playerId, false);
        cmd.append("#PillBar", "Pages/Shop_WardrobeFreeToggle.ui");
        String freeRoot = "#PillBar[" + pillIndex + "] ";
        if (isFreeOnly) {
            cmd.set(freeRoot + "#FreeBox #FreeBoxFill.Visible", true);
            cmd.set(freeRoot + "#FreeLabel.Style.TextColor", "#22c55e");
        }
        evt.addEventBinding(CustomUIEventBindingType.Activating, freeRoot + "#FreeToggleBtn",
                EventData.of(ButtonEventData.KEY_BUTTON, getId() + ":" + ACTION_FREE_TOGGLE), false);

        // Grid
        cmd.append("#TabContent", "Pages/Shop_WardrobeGrid.ui");
        List<WardrobeCosmeticDef> cosmetics = wardrobeBridge.getCosmeticsByCategory(currentCategory);
        int cardIndex = 0;
        for (WardrobeCosmeticDef def : cosmetics) {
            if (!cosmeticShopConfigStore.isAvailable(def.id())) continue;

            String currency = cosmeticShopConfigStore.getCurrency(def.id());
            boolean isFeathers = "feathers".equals(currency);
            if (isFreeOnly && !isFeathers) continue;

            boolean owned = cosmeticStore.ownsCosmetic(playerId, def.id());
            int price = cosmeticShopConfigStore.getPrice(def.id());
            long balance = CurrencyBridge.getBalance(currency, playerId);

            cmd.append("#WardrobeGrid", "Pages/Shop_WardrobeCard.ui");
            String root = "#WardrobeGrid[" + cardIndex + "] ";

            WardrobeShopUiUtils.setIcon(cmd, root, "#CardIcon", def.iconPath());

            cmd.set(root + "#CardName.Text", def.displayName());

            if (owned) {
                cmd.set(root + "#OwnedBadge.Visible", true);
            } else if (isFeathers) {
                if (balance >= price) {
                    cmd.set(root + "#PriceFeatherBuyBadge.Visible", true);
                    cmd.set(root + "#BuyFeatherPriceLabel.Text", String.valueOf(price));
                    evt.addEventBinding(CustomUIEventBindingType.Activating, root + "#CardButton",
                            EventData.of(ButtonEventData.KEY_BUTTON, getId() + ":" + ACTION_BUY + def.id()), false);
                } else {
                    cmd.set(root + "#PriceFeatherCantAffordBadge.Visible", true);
                    cmd.set(root + "#CantAffordFeatherPrice.Text", String.valueOf(price));
                }
            } else {
                if (balance >= price) {
                    cmd.set(root + "#PriceBuyBadge.Visible", true);
                    cmd.set(root + "#BuyPriceLabel.Text", String.valueOf(price));
                    evt.addEventBinding(CustomUIEventBindingType.Activating, root + "#CardButton",
                            EventData.of(ButtonEventData.KEY_BUTTON, getId() + ":" + ACTION_BUY + def.id()), false);
                } else {
                    cmd.set(root + "#PriceCantAffordBadge.Visible", true);
                    cmd.set(root + "#CantAffordPrice.Text", String.valueOf(price));
                }
            }
            cardIndex++;
        }
    }

    @Override
    public ShopTabResult handleEvent(String button, Ref<EntityStore> ref, Store<EntityStore> store,
                                     Player player, UUID playerId) {
        if (WardrobeShopUiUtils.handleCategoryFilter(button, playerId, selectedCategory)) {
            return ShopTabResult.REFRESH;
        }

        if (ACTION_FREE_TOGGLE.equals(button)) {
            boolean current = freeOnly.getOrDefault(playerId, false);
            if (current) {
                freeOnly.remove(playerId);
            } else {
                freeOnly.put(playerId, true);
            }
            return ShopTabResult.REFRESH;
        }

        if (button.startsWith(ACTION_BUY)) {
            String cosmeticId = button.substring(ACTION_BUY.length());
            return ShopTabResult.showConfirm(cosmeticId);
        }

        return ShopTabResult.NONE;
    }

    @Override
    public void populateConfirmOverlay(UICommandBuilder cmd, String confirmKey) {
        WardrobeCosmeticDef def = wardrobeBridge.findById(confirmKey);
        if (def == null) return;
        String currency = cosmeticShopConfigStore.getCurrency(def.id());
        boolean isFeathers = "feathers".equals(currency);
        cmd.set("#ConfirmSkinName.Text", def.displayName());
        cmd.set("#ConfirmPrice.Text", String.valueOf(cosmeticShopConfigStore.getPrice(def.id())));
        cmd.set("#ConfirmVexaIcon.Visible", !isFeathers);
        cmd.set("#ConfirmFeatherIcon.Visible", isFeathers);
    }

    @Override
    public boolean handleConfirm(String confirmKey, Ref<EntityStore> ref, Store<EntityStore> store,
                                 Player player, UUID playerId) {
        WardrobeBridge.PurchaseResult result = wardrobeBridge.purchase(playerId, confirmKey);
        String color = result.success() ? SystemMessageUtils.SUCCESS : SystemMessageUtils.ERROR;
        player.sendMessage(Message.raw("[Wardrobe] " + result.message()).color(color));
        return true;
    }

    @Override
    public void evictPlayer(UUID playerId) {
        selectedCategory.remove(playerId);
        freeOnly.remove(playerId);
    }
}
