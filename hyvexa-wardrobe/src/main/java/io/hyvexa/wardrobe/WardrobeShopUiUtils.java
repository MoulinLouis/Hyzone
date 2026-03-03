package io.hyvexa.wardrobe;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.AssetPathUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WardrobeShopUiUtils {

    static final String ACTION_FILTER = "Filter:";
    static final String FILTER_ALL = "All";

    private WardrobeShopUiUtils() {
    }

    static void setIcon(UICommandBuilder cmd, String root, String elementId, String iconPath) {
        String assetPath = AssetPathUtils.normalizeIconAssetPath(iconPath);
        if (assetPath != null) {
            cmd.set(root + elementId + ".AssetPath", assetPath);
        } else {
            cmd.set(root + elementId + ".Visible", false);
        }
    }

    static boolean handleCategoryFilter(String button, UUID playerId, Map<UUID, String> selectedCategory) {
        if (!button.startsWith(ACTION_FILTER)) return false;
        String filter = button.substring(ACTION_FILTER.length());
        if (FILTER_ALL.equals(filter)) {
            selectedCategory.remove(playerId);
        } else {
            selectedCategory.put(playerId, filter);
        }
        return true;
    }

    static int buildCategoryPills(UICommandBuilder cmd, UIEventBuilder evt, String tabId,
                                  String accentColor, List<String> categories, String currentCategory) {
        int pillIndex = 0;

        cmd.append("#PillBar", "Pages/Shop_WardrobePill.ui");
        String allRoot = "#PillBar[" + pillIndex + "] ";
        cmd.set(allRoot + "#PillLabel.Text", FILTER_ALL);
        if (currentCategory == null) {
            cmd.set(allRoot + "#PillActive.Visible", true);
            cmd.set(allRoot + "#PillLabel.Style.TextColor", accentColor);
        }
        evt.addEventBinding(CustomUIEventBindingType.Activating, allRoot + "#PillButton",
                EventData.of(ButtonEventData.KEY_BUTTON, tabId + ":" + ACTION_FILTER + FILTER_ALL), false);
        pillIndex++;

        for (String category : categories) {
            cmd.append("#PillBar", "Pages/Shop_WardrobePill.ui");
            String pillRoot = "#PillBar[" + pillIndex + "] ";
            cmd.set(pillRoot + "#PillLabel.Text", category);
            if (category.equals(currentCategory)) {
                cmd.set(pillRoot + "#PillActive.Visible", true);
                cmd.set(pillRoot + "#PillLabel.Style.TextColor", accentColor);
            }
            evt.addEventBinding(CustomUIEventBindingType.Activating, pillRoot + "#PillButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, tabId + ":" + ACTION_FILTER + category), false);
            pillIndex++;
        }

        return pillIndex;
    }
}
