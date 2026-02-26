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
import io.hyvexa.wardrobe.ui.ShopPage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OP-only admin tab for configuring wardrobe cosmetic availability and pricing.
 * Supports category filtering (pills) and text search.
 */
public class ShopConfigTab implements ShopTab {

    private static final String ACTION_FILTER = "Filter:";
    private static final String ACTION_TOGGLE = "Toggle:";
    private static final String ACTION_PRICE_UP = "PriceUp:";
    private static final String ACTION_PRICE_DOWN = "PriceDown:";
    private static final String ACTION_CURRENCY = "Currency:";
    private static final String FILTER_ALL = "All";

    private final ConcurrentHashMap<UUID, String> selectedCategory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> searchText = new ConcurrentHashMap<>();

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
        WardrobeBridge bridge = WardrobeBridge.getInstance();
        CosmeticShopConfigStore configStore = CosmeticShopConfigStore.getInstance();
        String currentCategory = selectedCategory.get(playerId);
        String currentSearch = searchText.getOrDefault(playerId, "");
        String prefix = getId() + ":";

        // --- Pill bar ---
        cmd.append("#TabContent", "Pages/Shop_WardrobePills.ui");
        List<String> categories = bridge.getCategories();

        int pillIndex = 0;
        // "All" pill
        cmd.append("#PillBar", "Pages/Shop_WardrobePill.ui");
        String allRoot = "#PillBar[" + pillIndex + "] ";
        cmd.set(allRoot + "#PillLabel.Text", FILTER_ALL);
        if (currentCategory == null) {
            cmd.set(allRoot + "#PillActive.Visible", true);
            cmd.set(allRoot + "#PillLabel.Style.TextColor", "#f59e0b");
        }
        evt.addEventBinding(CustomUIEventBindingType.Activating, allRoot + "#PillButton",
                EventData.of(ButtonEventData.KEY_BUTTON, prefix + ACTION_FILTER + FILTER_ALL), false);
        pillIndex++;

        // Category pills
        for (String cat : categories) {
            cmd.append("#PillBar", "Pages/Shop_WardrobePill.ui");
            String pillRoot = "#PillBar[" + pillIndex + "] ";
            cmd.set(pillRoot + "#PillLabel.Text", cat);
            if (cat.equals(currentCategory)) {
                cmd.set(pillRoot + "#PillActive.Visible", true);
                cmd.set(pillRoot + "#PillLabel.Style.TextColor", "#f59e0b");
            }
            evt.addEventBinding(CustomUIEventBindingType.Activating, pillRoot + "#PillButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, prefix + ACTION_FILTER + cat), false);
            pillIndex++;
        }

        // --- Search field ---
        cmd.append("#TabContent", "Pages/Shop_AdminConfigSearch.ui");
        if (!currentSearch.isEmpty()) {
            cmd.set("#ConfigSearchField.Value", currentSearch);
        }
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ConfigSearchField",
                EventData.of(ShopPage.ShopEventData.KEY_SEARCH, "#ConfigSearchField.Value"), false);

        // --- Config rows (filtered) ---
        List<WardrobeCosmeticDef> cosmetics = bridge.getCosmeticsByCategory(currentCategory);
        String searchFilter = currentSearch.toLowerCase();

        int rowIndex = 0;
        for (WardrobeCosmeticDef def : cosmetics) {
            if (!searchFilter.isEmpty() && !def.displayName().toLowerCase().contains(searchFilter)) {
                continue;
            }

            CosmeticConfig cfg = configStore.getConfig(def.id());
            boolean available = cfg != null && cfg.available();
            int price = cfg != null ? cfg.price() : 0;
            String currency = cfg != null ? cfg.currency() : "vexa";

            cmd.append("#TabContent", "Pages/Shop_AdminConfigRow.ui");
            String root = "#TabContent[" + (2 + rowIndex) + "] ";

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
            evt.addEventBinding(CustomUIEventBindingType.Activating, root + "#ToggleBtn",
                    EventData.of(ButtonEventData.KEY_BUTTON, prefix + ACTION_TOGGLE + def.id()), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, root + "#PriceUpBtn",
                    EventData.of(ButtonEventData.KEY_BUTTON, prefix + ACTION_PRICE_UP + def.id()), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, root + "#PriceDownBtn",
                    EventData.of(ButtonEventData.KEY_BUTTON, prefix + ACTION_PRICE_DOWN + def.id()), false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, root + "#CurrencyToggleBtn",
                    EventData.of(ButtonEventData.KEY_BUTTON, prefix + ACTION_CURRENCY + def.id()), false);
            rowIndex++;
        }
    }

    @Override
    public ShopTabResult handleEvent(String button, Ref<EntityStore> ref, Store<EntityStore> store,
                                     Player player, UUID playerId) {
        CosmeticShopConfigStore configStore = CosmeticShopConfigStore.getInstance();

        if (button.startsWith(ACTION_FILTER)) {
            String filter = button.substring(ACTION_FILTER.length());
            if (FILTER_ALL.equals(filter)) {
                selectedCategory.remove(playerId);
            } else {
                selectedCategory.put(playerId, filter);
            }
            return ShopTabResult.REFRESH;
        }
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

    @Override
    public ShopTabResult handleSearch(String text, UUID playerId) {
        String normalized = text != null ? text.trim().toLowerCase() : "";
        String current = searchText.getOrDefault(playerId, "");
        if (normalized.equals(current)) return ShopTabResult.NONE;

        if (normalized.isEmpty()) {
            searchText.remove(playerId);
        } else {
            searchText.put(playerId, normalized);
        }
        return ShopTabResult.REFRESH;
    }

    @Override
    public void evictPlayer(UUID playerId) {
        selectedCategory.remove(playerId);
        searchText.remove(playerId);
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
