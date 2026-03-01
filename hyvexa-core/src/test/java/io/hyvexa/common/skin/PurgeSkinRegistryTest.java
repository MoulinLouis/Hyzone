package io.hyvexa.common.skin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PurgeSkinRegistryTest {

    @Test
    void getSkinsForKnownWeapon() {
        List<PurgeSkinDefinition> skins = PurgeSkinRegistry.getSkinsForWeapon("AK47");
        assertEquals(4, skins.size());
    }

    @Test
    void getSkinsForUnknownWeapon() {
        List<PurgeSkinDefinition> skins = PurgeSkinRegistry.getSkinsForWeapon("UNKNOWN");
        assertTrue(skins.isEmpty());
    }

    @Test
    void getSkinFound() {
        PurgeSkinDefinition skin = PurgeSkinRegistry.getSkin("AK47", "Asimov");
        assertNotNull(skin);
        assertEquals("Asimov", skin.getSkinId());
        assertEquals("AK47", skin.getWeaponId());
    }

    @Test
    void getSkinNotFound() {
        assertNull(PurgeSkinRegistry.getSkin("AK47", "FAKE"));
    }

    @Test
    void getSkinnedItemIdExistingSkin() {
        String itemId = PurgeSkinRegistry.getSkinnedItemId("AK47", "Asimov");
        assertEquals("AK47_Asimov", itemId);
    }

    @Test
    void getSkinnedItemIdFallsBackToWeaponId() {
        String itemId = PurgeSkinRegistry.getSkinnedItemId("AK47", "NONEXISTENT");
        assertEquals("AK47", itemId);
    }

    @Test
    void getAllSkins() {
        List<PurgeSkinDefinition> all = PurgeSkinRegistry.getAllSkins();
        assertEquals(4, all.size());
    }

    @Test
    void hasAnySkins() {
        assertTrue(PurgeSkinRegistry.hasAnySkins("AK47"));
    }

    @Test
    void hasNoSkins() {
        assertFalse(PurgeSkinRegistry.hasAnySkins("UNKNOWN"));
    }

    @Test
    void setSkinPriceUpdatesExisting() {
        // Save original price to restore after test
        PurgeSkinDefinition skin = PurgeSkinRegistry.getSkin("AK47", "Blossom");
        int originalPrice = skin.getPrice();
        try {
            assertTrue(PurgeSkinRegistry.setSkinPrice("AK47", "Blossom", 999));
            assertEquals(999, skin.getPrice());
        } finally {
            skin.setPrice(originalPrice);
        }
    }

    @Test
    void setSkinPriceReturnsFalseForUnknown() {
        assertFalse(PurgeSkinRegistry.setSkinPrice("AK47", "NONEXISTENT", 50));
    }
}
