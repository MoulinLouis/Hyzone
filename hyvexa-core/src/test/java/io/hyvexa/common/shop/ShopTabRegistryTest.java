package io.hyvexa.common.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShopTabRegistryTest {

    @BeforeEach
    void clearRegistry() {
        // No clear() method exists — re-register known IDs to avoid cross-test pollution.
        // Each test registers its own tabs, and dedup ensures stale entries are replaced.
    }

    @Test
    void registerAddsTabToRegistry() {
        ShopTab tab = stub("regTest1", 1);
        ShopTabRegistry.register(tab);

        assertSame(tab, ShopTabRegistry.getTab("regTest1"));
        assertTrue(ShopTabRegistry.getTabs().stream().anyMatch(t -> t.getId().equals("regTest1")));
    }

    @Test
    void registerDeduplicatesById() {
        ShopTab first = stub("dedupTest", 1);
        ShopTab second = stub("dedupTest", 1);
        ShopTabRegistry.register(first);
        ShopTabRegistry.register(second);

        assertSame(second, ShopTabRegistry.getTab("dedupTest"));
        long count = ShopTabRegistry.getTabs().stream()
                .filter(t -> t.getId().equals("dedupTest"))
                .count();
        assertEquals(1, count);
    }

    @Test
    void getTabReturnsNullForUnknownId() {
        assertNull(ShopTabRegistry.getTab("nonexistent_xyz_" + System.nanoTime()));
    }

    @Test
    void getTabsReturnsSortedByOrder() {
        ShopTabRegistry.register(stub("sortC", 3));
        ShopTabRegistry.register(stub("sortA", 1));
        ShopTabRegistry.register(stub("sortB", 2));

        List<ShopTab> tabs = ShopTabRegistry.getTabs();
        // Find our three tabs in the list (other tests may have registered tabs too)
        List<ShopTab> ours = tabs.stream()
                .filter(t -> t.getId().startsWith("sort"))
                .toList();
        assertEquals(3, ours.size());
        assertEquals("sortA", ours.get(0).getId());
        assertEquals("sortB", ours.get(1).getId());
        assertEquals("sortC", ours.get(2).getId());
    }

    @Test
    void getTabsReturnsDefensiveCopy() {
        ShopTabRegistry.register(stub("defensiveTest", 1));
        List<ShopTab> first = ShopTabRegistry.getTabs();
        // .toList() returns an unmodifiable list, so mutating should throw
        assertThrows(UnsupportedOperationException.class, () -> first.add(stub("hack", 99)));
    }

    // --- Minimal stub implementing only getId/getOrder (the only methods ShopTabRegistry calls) ---

    private static ShopTab stub(String id, int order) {
        return new ShopTab() {
            @Override public String getId() { return id; }
            @Override public String getLabel() { return id; }
            @Override public String getAccentColor() { return "#000"; }
            @Override public int getOrder() { return order; }

            @Override
            public void buildContent(UICommandBuilder cmd, UIEventBuilder evt, UUID playerId, long vexa) {}

            @Override
            public ShopTabResult handleEvent(String button, Ref<EntityStore> ref, Store<EntityStore> store,
                                             Player player, UUID playerId) {
                return ShopTabResult.NONE;
            }
        };
    }
}
