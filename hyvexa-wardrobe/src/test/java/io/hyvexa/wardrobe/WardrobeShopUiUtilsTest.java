package io.hyvexa.wardrobe;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardrobeShopUiUtilsTest {

    @Test
    void handleCategoryFilterReturnsFalseForNonFilterButtons() {
        UUID playerId = UUID.randomUUID();
        Map<UUID, String> selectedCategory = new HashMap<>();
        selectedCategory.put(playerId, "Weapons");

        boolean handled = WardrobeShopUiUtils.handleCategoryFilter("Buy:Skin", playerId, selectedCategory);

        assertFalse(handled);
        assertEquals("Weapons", selectedCategory.get(playerId));
    }

    @Test
    void handleCategoryFilterStoresSpecificCategory() {
        UUID playerId = UUID.randomUUID();
        Map<UUID, String> selectedCategory = new HashMap<>();

        boolean handled = WardrobeShopUiUtils.handleCategoryFilter("Filter:Effects", playerId, selectedCategory);

        assertTrue(handled);
        assertEquals("Effects", selectedCategory.get(playerId));
    }

    @Test
    void handleCategoryFilterAllRemovesExistingSelection() {
        UUID playerId = UUID.randomUUID();
        Map<UUID, String> selectedCategory = new HashMap<>();
        selectedCategory.put(playerId, "Effects");

        boolean handled = WardrobeShopUiUtils.handleCategoryFilter("Filter:All", playerId, selectedCategory);

        assertTrue(handled);
        assertNull(selectedCategory.get(playerId));
    }
}
