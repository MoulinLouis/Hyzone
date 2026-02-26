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

    private static final String ACTION_FILTER = "Filter:";
    private static final String ACTION_BUY = "Buy:";
    private static final String ACTION_FREE_TOGGLE = "FreeToggle";
    private static final String FILTER_ALL = "All";

    private final ConcurrentHashMap<UUID, String> selectedCategory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> freeOnly = new ConcurrentHashMap<>();

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
        WardrobeBridge bridge = WardrobeBridge.getInstance();
        String currentCategory = selectedCategory.get(playerId);

        // Pill bar
        cmd.append("#TabContent", "Pages/Shop_WardrobePills.ui");
        List<String> categories = bridge.getCategories();

        // "All" pill
        int pillIndex = 0;
        cmd.append("#PillBar", "Pages/Shop_WardrobePill.ui");
        String allRoot = "#PillBar[" + pillIndex + "] ";
        cmd.set(allRoot + "#PillLabel.Text", FILTER_ALL);
        if (currentCategory == null) {
            cmd.set(allRoot + "#PillActive.Visible", true);
            cmd.set(allRoot + "#PillLabel.Style.TextColor", "#e879f9");
        }
        evt.addEventBinding(CustomUIEventBindingType.Activating, allRoot + "#PillButton",
                EventData.of(ButtonEventData.KEY_BUTTON, getId() + ":" + ACTION_FILTER + FILTER_ALL), false);
        pillIndex++;

        // Category pills
        for (String cat : categories) {
            cmd.append("#PillBar", "Pages/Shop_WardrobePill.ui");
            String pillRoot = "#PillBar[" + pillIndex + "] ";
            cmd.set(pillRoot + "#PillLabel.Text", cat);
            if (cat.equals(currentCategory)) {
                cmd.set(pillRoot + "#PillActive.Visible", true);
                cmd.set(pillRoot + "#PillLabel.Style.TextColor", "#e879f9");
            }
            evt.addEventBinding(CustomUIEventBindingType.Activating, pillRoot + "#PillButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, getId() + ":" + ACTION_FILTER + cat), false);
            pillIndex++;
        }

        // "Free" toggle checkbox
        boolean isFreeOnly = freeOnly.getOrDefault(playerId, false);
        cmd.append("#PillBar", "Pages/Shop_WardrobeFreeToggle.ui");
        String freeRoot = "#PillBar[" + pillIndex + "] ";
        if (isFreeOnly) {
            cmd.set(freeRoot + "#FreeBox.Background", "#22c55e");
            cmd.set(freeRoot + "#FreeLabel.Style.TextColor", "#22c55e");
        }
        evt.addEventBinding(CustomUIEventBindingType.Activating, freeRoot + "#FreeToggleBtn",
                EventData.of(ButtonEventData.KEY_BUTTON, getId() + ":" + ACTION_FREE_TOGGLE), false);

        // Grid
        cmd.append("#TabContent", "Pages/Shop_WardrobeGrid.ui");
        List<WardrobeCosmeticDef> cosmetics = bridge.getCosmeticsByCategory(currentCategory);
        CosmeticShopConfigStore configStore = CosmeticShopConfigStore.getInstance();

        int cardIndex = 0;
        for (WardrobeCosmeticDef def : cosmetics) {
            if (!configStore.isAvailable(def.id())) continue;

            String currency = configStore.getCurrency(def.id());
            boolean isFeathers = "feathers".equals(currency);
            if (isFreeOnly && !isFeathers) continue;

            boolean owned = playerId != null && CosmeticStore.getInstance().ownsCosmetic(playerId, def.id());
            int price = configStore.getPrice(def.id());
            long balance = CurrencyBridge.getBalance(currency, playerId);

            cmd.append("#WardrobeGrid", "Pages/Shop_WardrobeCard.ui");
            String root = "#WardrobeGrid[" + cardIndex + "] ";

            // Icon
            String iconAssetPath = toAssetPath(def.iconPath());
            if (iconAssetPath != null) {
                cmd.set(root + "#CardIcon.AssetPath", iconAssetPath);
            } else {
                cmd.set(root + "#CardIcon.Visible", false);
            }

            // Name
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
        if (button.startsWith(ACTION_FILTER)) {
            String filter = button.substring(ACTION_FILTER.length());
            if (FILTER_ALL.equals(filter)) {
                selectedCategory.remove(playerId);
            } else {
                selectedCategory.put(playerId, filter);
            }
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
        WardrobeCosmeticDef def = WardrobeBridge.getInstance().findById(confirmKey);
        if (def == null) return;
        CosmeticShopConfigStore configStore = CosmeticShopConfigStore.getInstance();
        cmd.set("#ConfirmSkinName.Text", def.displayName());
        cmd.set("#ConfirmPrice.Text", String.valueOf(configStore.getPrice(def.id())));
    }

    @Override
    public boolean handleConfirm(String confirmKey, Ref<EntityStore> ref, Store<EntityStore> store,
                                 Player player, UUID playerId) {
        String result = WardrobeBridge.getInstance().purchase(playerId, confirmKey);

        boolean isError = result.startsWith("Not enough") || result.startsWith("Unknown")
                || result.startsWith("You already") || result.startsWith("This cosmetic");
        String color = isError ? SystemMessageUtils.ERROR : SystemMessageUtils.SUCCESS;
        player.sendMessage(Message.raw("[Wardrobe] " + result).color(color));
        return true;
    }

    @Override
    public void evictPlayer(UUID playerId) {
        selectedCategory.remove(playerId);
        freeOnly.remove(playerId);
    }

    /**
     * Normalize icon asset paths for AssetImage.AssetPath.
     */
    private static String toAssetPath(String iconPath) {
        if (iconPath == null || iconPath.isBlank()) {
            return null;
        }

        String normalized = iconPath.replace('\\', '/');
        while (normalized.startsWith("../")) {
            normalized = normalized.substring(3);
        }

        if (normalized.startsWith("Common/")) {
            normalized = normalized.substring("Common/".length());
        }

        if (normalized.startsWith("Textures/")) {
            return "UI/Custom/" + normalized;
        }

        return normalized;
    }
}
