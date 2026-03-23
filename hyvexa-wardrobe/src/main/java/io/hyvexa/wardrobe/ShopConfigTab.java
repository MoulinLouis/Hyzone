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
import io.hyvexa.common.ui.AccentOverlayUtils;
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

    private static final String ACTION_TOGGLE = "Toggle:";
    private static final String ACTION_PRICE_UP = "PriceUp:";
    private static final String ACTION_PRICE_DOWN = "PriceDown:";
    private static final String ACTION_CURRENCY = "Currency:";

    private final WardrobeBridge wardrobeBridge;
    private final CosmeticShopConfigStore cosmeticShopConfigStore;
    private final ConcurrentHashMap<UUID, String> selectedCategory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> searchText = new ConcurrentHashMap<>();

    public ShopConfigTab(WardrobeBridge wardrobeBridge, CosmeticShopConfigStore cosmeticShopConfigStore) {
        this.wardrobeBridge = wardrobeBridge;
        this.cosmeticShopConfigStore = cosmeticShopConfigStore;
    }

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
        String currentCategory = selectedCategory.get(playerId);
        String currentSearch = searchText.getOrDefault(playerId, "");
        String prefix = getId() + ":";

        // --- Pill bar ---
        cmd.append("#TabContent", "Pages/Shop_WardrobePills.ui");
        List<String> categories = wardrobeBridge.getCategories();
        WardrobeShopUiUtils.buildCategoryPills(cmd, evt, getId(), getAccentColor(), categories, currentCategory);

        // --- Search field ---
        cmd.append("#TabContent", "Pages/Shop_AdminConfigSearch.ui");
        if (!currentSearch.isEmpty()) {
            cmd.set("#ConfigSearchField.Value", currentSearch);
        }
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ConfigSearchField",
                EventData.of(ShopPage.ShopEventData.KEY_SEARCH, "#ConfigSearchField.Value"), false);

        // --- Config rows (filtered) ---
        List<WardrobeCosmeticDef> cosmetics = wardrobeBridge.getCosmeticsByCategory(currentCategory);
        String searchFilter = currentSearch.toLowerCase();

        int rowIndex = 0;
        for (WardrobeCosmeticDef def : cosmetics) {
            if (!searchFilter.isEmpty() && !def.displayName().toLowerCase().contains(searchFilter)) {
                continue;
            }

            CosmeticConfig cfg = cosmeticShopConfigStore.getConfig(def.id());
            boolean available = cfg != null && cfg.available();
            int price = cfg != null ? cfg.price() : 0;
            String currency = cfg != null ? cfg.currency() : "vexa";

            cmd.append("#TabContent", "Pages/Shop_AdminConfigRow.ui");
            String root = "#TabContent[" + (2 + rowIndex) + "] ";

            if (available) {
                AccentOverlayUtils.applyAccent(cmd, root + "#StatusDot",
                        "#22c55e", AccentOverlayUtils.STATUS_DOT);
            }

            WardrobeShopUiUtils.setIcon(cmd, root, "#ConfigIcon", def.iconPath());

            cmd.set(root + "#ConfigName.Text", def.displayName());

            cmd.set(root + "#ConfigPrice.Text", price > 0 ? String.valueOf(price) : "--");

            boolean isFeathers = "feathers".equals(currency);
            cmd.set(root + "#CurrencyVexa.Visible", !isFeathers);
            cmd.set(root + "#CurrencyFeather.Visible", isFeathers);

            if (available) {
                cmd.set(root + "#ToggleLabel.Text", "Disable");
                cmd.set(root + "#ToggleLabel.Style.TextColor", "#ef4444");
            }

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
        if (WardrobeShopUiUtils.handleCategoryFilter(button, playerId, selectedCategory)) {
            return ShopTabResult.REFRESH;
        }
        if (button.startsWith(ACTION_TOGGLE)) {
            String id = button.substring(ACTION_TOGGLE.length());
            cosmeticShopConfigStore.toggleAvailable(id);
            return ShopTabResult.REFRESH;
        }
        if (button.startsWith(ACTION_PRICE_UP)) {
            String id = button.substring(ACTION_PRICE_UP.length());
            cosmeticShopConfigStore.adjustPrice(id, 50);
            return ShopTabResult.REFRESH;
        }
        if (button.startsWith(ACTION_PRICE_DOWN)) {
            String id = button.substring(ACTION_PRICE_DOWN.length());
            cosmeticShopConfigStore.adjustPrice(id, -50);
            return ShopTabResult.REFRESH;
        }
        if (button.startsWith(ACTION_CURRENCY)) {
            String id = button.substring(ACTION_CURRENCY.length());
            cosmeticShopConfigStore.toggleCurrency(id);
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
}
