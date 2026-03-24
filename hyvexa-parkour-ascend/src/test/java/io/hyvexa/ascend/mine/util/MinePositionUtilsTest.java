package io.hyvexa.ascend.mine.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MinePositionUtilsTest {

    @Test
    void packPositionEncodesOriginAsZero() {
        assertEquals(0L, MinePositionUtils.packPosition(0, 0, 0));
    }

    @Test
    void packPositionEncodesXInUpperBits() {
        assertEquals(1L << 38, MinePositionUtils.packPosition(1, 0, 0));
    }

    @Test
    void packPositionEncodesYInMiddleBits() {
        assertEquals(1L << 26, MinePositionUtils.packPosition(0, 1, 0));
    }

    @Test
    void packPositionEncodesZInLowerBits() {
        assertEquals(1L, MinePositionUtils.packPosition(0, 0, 1));
    }

    @Test
    void packPositionIsDeterministic() {
        long a = MinePositionUtils.packPosition(100, 64, 200);
        long b = MinePositionUtils.packPosition(100, 64, 200);
        assertEquals(a, b);
    }

    @Test
    void packPositionDistinguishesAdjacentY() {
        long a = MinePositionUtils.packPosition(100, 64, 200);
        long b = MinePositionUtils.packPosition(100, 65, 200);
        assertNotEquals(a, b);
    }

    @Test
    void packPositionDistinguishesXFromZ() {
        assertNotEquals(
                MinePositionUtils.packPosition(1, 2, 3),
                MinePositionUtils.packPosition(3, 2, 1));
    }

    @Test
    void packPositionDistinguishesYFromZ() {
        assertNotEquals(
                MinePositionUtils.packPosition(1, 2, 3),
                MinePositionUtils.packPosition(1, 3, 2));
    }

    @Test
    void packPositionHandlesNegativeCoordinates() {
        // Should not throw
        MinePositionUtils.packPosition(-1, -1, -1);

        assertNotEquals(
                MinePositionUtils.packPosition(-1, 0, 0),
                MinePositionUtils.packPosition(1, 0, 0));
    }

    @Test
    void packPositionHandlesMaxBitRanges() {
        // X uses 26 bits (0x3FFFFFF = 67108863)
        long maxX = MinePositionUtils.packPosition(67108863, 0, 0);
        assertEquals(0x3FFFFFFL << 38, maxX);

        // Y uses 12 bits (0xFFF = 4095)
        long maxY = MinePositionUtils.packPosition(0, 4095, 0);
        assertEquals(0xFFFL << 26, maxY);

        // Z uses 26 bits
        long maxZ = MinePositionUtils.packPosition(0, 0, 67108863);
        assertEquals(0x3FFFFFFL, maxZ);
    }
}
