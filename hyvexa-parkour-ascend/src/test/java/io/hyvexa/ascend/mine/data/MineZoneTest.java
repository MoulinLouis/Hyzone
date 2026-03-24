package io.hyvexa.ascend.mine.data;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineZoneTest {

    @Test
    void constructorNormalizesCoordinateOrder() {
        MineZone zone = new MineZone("z1", "m1", 10, 20, 30, 0, 5, 15);
        assertEquals(0, zone.getMinX());
        assertEquals(10, zone.getMaxX());
        assertEquals(5, zone.getMinY());
        assertEquals(20, zone.getMaxY());
        assertEquals(15, zone.getMinZ());
        assertEquals(30, zone.getMaxZ());
    }

    @Test
    void containsReturnsTrueForPointsInsideBounds() {
        MineZone zone = new MineZone("z1", "m1", 0, 0, 0, 10, 10, 10);
        assertTrue(zone.contains(5, 5, 5));
        assertTrue(zone.contains(0, 0, 0));
        assertTrue(zone.contains(10, 10, 10));
    }

    @Test
    void containsReturnsFalseForPointsOutsideBounds() {
        MineZone zone = new MineZone("z1", "m1", 0, 0, 0, 10, 10, 10);
        assertFalse(zone.contains(-1, 5, 5));
        assertFalse(zone.contains(11, 5, 5));
        assertFalse(zone.contains(5, -1, 5));
        assertFalse(zone.contains(5, 11, 5));
        assertFalse(zone.contains(5, 5, -1));
        assertFalse(zone.contains(5, 5, 11));
    }

    @Test
    void getTotalBlocksCalculatesVolume() {
        assertEquals(1000, new MineZone("z1", "m1", 0, 0, 0, 9, 9, 9).getTotalBlocks());
        assertEquals(1, new MineZone("z2", "m1", 5, 5, 5, 5, 5, 5).getTotalBlocks());
        assertEquals(24, new MineZone("z3", "m1", 0, 0, 0, 3, 1, 2).getTotalBlocks());
    }

    @Test
    void getLayerForYReturnsFirstMatchingLayer() {
        MineZone zone = new MineZone("z1", "m1", 0, 0, 0, 10, 10, 10);
        MineZoneLayer layer1 = new MineZoneLayer("l1", "z1", 0, 5);
        MineZoneLayer layer2 = new MineZoneLayer("l2", "z1", 6, 10);
        zone.getLayers().add(layer1);
        zone.getLayers().add(layer2);

        assertSame(layer1, zone.getLayerForY(3));
        assertSame(layer2, zone.getLayerForY(8));
    }

    @Test
    void getLayerForYReturnsNullWhenNoLayerCoversY() {
        MineZone zone = new MineZone("z1", "m1", 0, 0, 0, 10, 10, 10);
        zone.getLayers().add(new MineZoneLayer("l1", "z1", 5, 10));

        assertNull(zone.getLayerForY(2));
    }

    @Test
    void getBlockTableForYFallsBackToZoneTable() {
        MineZone zone = new MineZone("z1", "m1", 0, 0, 0, 10, 10, 10);
        zone.getBlockTable().put("stone", 0.5);
        zone.getBlockTable().put("gold", 0.5);

        MineZoneLayer layer = new MineZoneLayer("l1", "z1", 5, 10);
        layer.getBlockTable().put("diamond", 1.0);
        zone.getLayers().add(layer);

        Map<String, Double> layerTable = zone.getBlockTableForY(7);
        assertEquals(1.0, layerTable.get("diamond"), 1e-9);
        assertNull(layerTable.get("stone"));

        Map<String, Double> fallback = zone.getBlockTableForY(2);
        assertEquals(0.5, fallback.get("stone"), 1e-9);
        assertEquals(0.5, fallback.get("gold"), 1e-9);
    }
}
