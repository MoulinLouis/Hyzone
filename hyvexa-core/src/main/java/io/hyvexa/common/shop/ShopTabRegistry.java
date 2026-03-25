package io.hyvexa.common.shop;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ShopTabRegistry {

    private static final CopyOnWriteArrayList<ShopTab> TABS = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<String, ShopTab> INDEX = new ConcurrentHashMap<>();

    private ShopTabRegistry() {}

    public static void register(ShopTab tab) {
        TABS.removeIf(existing -> existing.getId().equals(tab.getId()));
        INDEX.put(tab.getId(), tab);
        TABS.add(tab);
    }

    public static void unregister(String id) {
        TABS.removeIf(existing -> existing.getId().equals(id));
        INDEX.remove(id);
    }

    public static List<ShopTab> getTabs() {
        return TABS.stream()
                .sorted(Comparator.comparingInt(ShopTab::getOrder))
                .toList();
    }

    public static ShopTab getTab(String id) {
        return INDEX.get(id);
    }
}
