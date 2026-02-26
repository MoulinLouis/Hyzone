package io.hyvexa.wardrobe;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.shop.ShopTab;
import io.hyvexa.common.shop.ShopTabResult;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.core.wardrobe.CosmeticShopConfigStore;
import io.hyvexa.core.wardrobe.CosmeticShopConfigStore.CosmeticConfig;
import io.hyvexa.core.wardrobe.WardrobeBridge;
import io.hyvexa.core.wardrobe.WardrobeBridge.WardrobeCosmeticDef;

import java.util.List;
import java.util.UUID;

/**
 * OP-only admin tab for configuring wardrobe cosmetic availability and pricing.
 */
public class ShopConfigTab implements ShopTab {

    private static final String ACTION_TOGGLE = "Toggle:";
    private static final String ACTION_PRICE_UP = "PriceUp:";
    private static final String ACTION_PRICE_DOWN = "PriceDown:";
    private static final String ACTION_CURRENCY = "Currency:";

    @Override
    public String getId() {
        return "shopconfig";
    }

    @Override
    public String getLabel() {
        return "Shop Config";
    }

    @Override
    public String getAccentColor() {
        return "#f59e0b";
    }

    @Override
    public int getOrder() {
        return 15;
    }

    @Override
    public boolean isVisibleTo(UUID playerId) {
        return PermissionUtils.isOp(playerId);
    }

    @Override
    public void buildContent(UICommandBuilder cmd, UIEventBuilder evt, UUID playerId, long vexa) {
        CosmeticShopConfigStore configStore = CosmeticShopConfigStore.getInstance();
        List<WardrobeCosmeticDef> allCosmetics = WardrobeBridge.getInstance().getAllCosmetics();

        for (int i = 0; i < allCosmetics.size(); i++) {
            WardrobeCosmeticDef def = allCosmetics.get(i);
            CosmeticConfig cfg = configStore.getConfig(def.id());
            boolean available = cfg != null && cfg.available();
            int price = cfg != null ? cfg.price() : 0;
            String currency = cfg != null ? cfg.currency() : "vexa";

            cmd.append("#TabContent", "Pages/Shop_AdminConfigRow.ui");
            String root = "#TabContent[" + i + "] ";

            // Status dot color
            if (available) {
                cmd.set(root + "#StatusDot.Background", "#22c55e");
            }

            // Icon
            String iconAssetPath = toAssetPath(def.iconPath());
            if (iconAssetPath != null) {
                cmd.set(root + "#ConfigIcon.AssetPath", iconAssetPath);
            } else {
                cmd.set(root + "#ConfigIcon.Visible", false);
            }

            // Name
            cmd.set(root + "#ConfigName.Text", def.displayName());

            // Price
            cmd.set(root + "#ConfigPrice.Text", price > 0 ? String.valueOf(price) : "--");

            // Currency icon
            boolean isFeathers = "feathers".equals(currency);
            cmd.set(root + "#CurrencyVexa.Visible", !isFeathers);
            cmd.set(root + "#CurrencyFeather.Visible", isFeathers);

            // Toggle button text/color
            if (available) {
                cmd.set(root + "#ToggleLabel.Text", "Disable");
                cmd.set(root + "#ToggleLabel.Style.TextColor", "#ef4444");
            }

            // Event bindings
            String prefix = getId() + ":";
            evt.addEventBinding(CustomUIEventBindingType.Activating, root + "#ToggleBtn",
                    EventData.of(ButtonEventData.KEY_BUTTON, prefix + ACTION_TOGGLE + def.id()), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, root + "#PriceUpBtn",
                    EventData.of(ButtonEventData.KEY_BUTTON, prefix + ACTION_PRICE_UP + def.id()), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, root + "#PriceDownBtn",
                    EventData.of(ButtonEventData.KEY_BUTTON, prefix + ACTION_PRICE_DOWN + def.id()), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, root + "#CurrencyToggleBtn",
                    EventData.of(ButtonEventData.KEY_BUTTON, prefix + ACTION_CURRENCY + def.id()), false);
        }
    }

    @Override
    public ShopTabResult handleEvent(String button, Ref<EntityStore> ref, Store<EntityStore> store,
                                     Player player, UUID playerId) {
        CosmeticShopConfigStore configStore = CosmeticShopConfigStore.getInstance();

        if (button.startsWith(ACTION_TOGGLE)) {
            String id = button.substring(ACTION_TOGGLE.length());
            configStore.toggleAvailable(id);
            return ShopTabResult.REFRESH;
        }
        if (button.startsWith(ACTION_PRICE_UP)) {
            String id = button.substring(ACTION_PRICE_UP.length());
            configStore.adjustPrice(id, 50);
            return ShopTabResult.REFRESH;
        }
        if (button.startsWith(ACTION_PRICE_DOWN)) {
            String id = button.substring(ACTION_PRICE_DOWN.length());
            configStore.adjustPrice(id, -50);
            return ShopTabResult.REFRESH;
        }
        if (button.startsWith(ACTION_CURRENCY)) {
            String id = button.substring(ACTION_CURRENCY.length());
            configStore.toggleCurrency(id);
            return ShopTabResult.REFRESH;
        }

        return ShopTabResult.NONE;
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
