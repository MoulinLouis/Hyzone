package io.hyvexa.common.shop;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ShopTabRegistry {

    private static final CopyOnWriteArrayList<ShopTab> TABS = new CopyOnWriteArrayList<>();

    private ShopTabRegistry() {}

    public static void register(ShopTab tab) {
        TABS.removeIf(existing -> existing.getId().equals(tab.getId()));
        TABS.add(tab);
    }

    public static List<ShopTab> getTabs() {
        return TABS.stream()
                .sorted(Comparator.comparingInt(ShopTab::getOrder))
                .toList();
    }

    public static ShopTab getTab(String id) {
        for (ShopTab tab : TABS) {
            if (tab.getId().equals(id)) {
                return tab;
            }
        }
        return null;
    }
}
