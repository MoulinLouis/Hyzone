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

    public static int[] unpackPosition(long packed) {
        // 26-bit x (signed), 12-bit y (unsigned), 26-bit z (signed)
        int x = (int) (packed >> 38);
        if ((x & 0x2000000) != 0) x |= ~0x3FFFFFF; // sign-extend from 26 bits
        int y = (int) ((packed >> 26) & 0xFFF);
        int z = (int) (packed & 0x3FFFFFF);
        if ((z & 0x2000000) != 0) z |= ~0x3FFFFFF; // sign-extend from 26 bits
        return new int[]{x, y, z};
    }
}
