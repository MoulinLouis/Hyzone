package io.hyvexa.ascend.mine.data;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineZoneLayerTest {

    @Test
    void constructorNormalizesYRange() {
        MineZoneLayer layer = new MineZoneLayer("l1", "z1", 20, 5);
        assertEquals(5, layer.getMinY());
        assertEquals(20, layer.getMaxY());
    }

    @Test
    void containsYReturnsTrueForYInRange() {
        MineZoneLayer layer = new MineZoneLayer("l1", "z1", 5, 10);
        assertTrue(layer.containsY(5));
        assertTrue(layer.containsY(7));
        assertTrue(layer.containsY(10));
    }

    @Test
    void containsYReturnsFalseForYOutOfRange() {
        MineZoneLayer layer = new MineZoneLayer("l1", "z1", 5, 10);
        assertFalse(layer.containsY(4));
        assertFalse(layer.containsY(11));
    }

    @Test
    void getBlockTableForRarityReturnsRaritySpecificTable() {
        MineZoneLayer layer = new MineZoneLayer("l1", "z1", 0, 10);
        layer.getBlockTable().put("stone", 1.0);

        Map<String, Double> epicTable = Map.of("diamond", 1.0);
        layer.getRarityBlockTables().put(MinerRarity.EPIC, new java.util.HashMap<>(epicTable));

        Map<String, Double> result = layer.getBlockTableForRarity(MinerRarity.EPIC);
        assertEquals(1.0, result.get("diamond"), 1e-9);
    }

    @Test
    void getBlockTableForRarityFallsBackToBaseWhenNoRarityTable() {
        MineZoneLayer layer = new MineZoneLayer("l1", "z1", 0, 10);
        layer.getBlockTable().put("stone", 1.0);

        Map<String, Double> result = layer.getBlockTableForRarity(MinerRarity.COMMON);
        assertSame(layer.getBlockTable(), result);
    }

    @Test
    void getBlockTableForRarityFallsBackToBaseWhenRarityTableEmpty() {
        MineZoneLayer layer = new MineZoneLayer("l1", "z1", 0, 10);
        layer.getBlockTable().put("stone", 1.0);
        layer.getRarityBlockTables().put(MinerRarity.RARE, new java.util.HashMap<>());

        Map<String, Double> result = layer.getBlockTableForRarity(MinerRarity.RARE);
        assertSame(layer.getBlockTable(), result);
    }

    @Test
    void setDisplayNameDefaultsToEmptyStringOnNull() {
        MineZoneLayer layer = new MineZoneLayer("l1", "z1", 0, 10);
        layer.setDisplayName(null);
        assertEquals("", layer.getDisplayName());
    }
}
