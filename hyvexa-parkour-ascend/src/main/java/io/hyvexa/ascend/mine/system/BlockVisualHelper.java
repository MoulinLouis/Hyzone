package io.hyvexa.ascend.mine.system;

import com.hypixel.hytale.server.core.universe.world.World;

import java.util.UUID;

/**
 * Per-player block crack visuals via WorldNotificationHandler.
 * Uses remaining-health convention: 1.0 = full, 0.0 = destroyed.
 */
public final class BlockVisualHelper {

    private BlockVisualHelper() {}

    /**
     * Send block crack visuals to a single player.
     *
     * @param remainingFraction remaining health 0.0–1.0 (1.0 = no damage)
     * @param delta             negative damage increment (e.g. -0.3 for 30% damage dealt)
     */
    public static void sendBlockCracks(World world, UUID playerId, int x, int y, int z,
                                        float remainingFraction, float delta) {
        world.getNotificationHandler().updateBlockDamage(
            x, y, z,
            remainingFraction,
            delta,
            playerRef -> playerId.equals(playerRef.getUuid())
        );
    }

    /**
     * Clear crack visuals for a player at a position (reset to full health).
     */
    public static void clearBlockCracks(World world, UUID playerId, int x, int y, int z) {
        world.getNotificationHandler().updateBlockDamage(
            x, y, z,
            1f, 0f,
            playerRef -> playerId.equals(playerRef.getUuid())
        );
    }
}
