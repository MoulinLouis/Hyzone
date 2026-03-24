package io.hyvexa.ascend.mine.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MinerRarityTest {

    @Test
    void fromNameReturnsCorrectRarityForAllValues() {
        for (MinerRarity rarity : MinerRarity.values()) {
            assertEquals(rarity, MinerRarity.fromName(rarity.name()));
            assertEquals(rarity, MinerRarity.fromName(rarity.name().toLowerCase()));
            assertEquals(rarity, MinerRarity.fromName(rarity.getDisplayName()));
        }
    }

    @Test
    void fromNameReturnsNullForUnknownName() {
        assertNull(MinerRarity.fromName("mythical"));
        assertNull(MinerRarity.fromName(""));
        assertNull(MinerRarity.fromName(null));
    }

    @Test
    void allRaritiesHaveNonBlankDisplayNameAndColor() {
        for (MinerRarity rarity : MinerRarity.values()) {
            assertNotNull(rarity.getDisplayName());
            assertFalse(rarity.getDisplayName().isEmpty());
            assertNotNull(rarity.getColor());
            assertEquals('#', rarity.getColor().charAt(0));
        }
    }

    @Test
    void allRaritiesHaveNonBlankEntityType() {
        for (MinerRarity rarity : MinerRarity.values()) {
            assertNotNull(rarity.getEntityType());
            assertFalse(rarity.getEntityType().isEmpty());
        }
    }
}
