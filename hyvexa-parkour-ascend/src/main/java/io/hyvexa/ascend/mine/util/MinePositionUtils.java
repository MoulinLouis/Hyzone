package io.hyvexa.ascend.mine.util;

/**
 * Shared utility for packing block positions into a single long.
 * Used by BlockDamageTracker, MineManager, and MineAoEBreaker.
 */
public final class MinePositionUtils {

    private MinePositionUtils() {}

    public static long packPosition(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }
}
