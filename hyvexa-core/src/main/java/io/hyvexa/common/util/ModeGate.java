package io.hyvexa.common.util;

import com.hypixel.hytale.server.core.universe.world.World;
import io.hyvexa.common.WorldConstants;

public final class ModeGate {

    private ModeGate() {
    }

    /**
     * Returns false if world or world name is null (safe to call without null checks).
     */
    public static boolean isWorld(World world, String expectedName) {
        if (world == null || world.getName() == null) {
            return false;
        }
        return expectedName.equalsIgnoreCase(world.getName());
    }

    public static boolean isHubWorld(World world) {
        return isWorld(world, WorldConstants.WORLD_HUB);
    }

    public static boolean isParkourWorld(World world) {
        return isWorld(world, WorldConstants.WORLD_PARKOUR);
    }

    public static boolean isAscendWorld(World world) {
        return isWorld(world, WorldConstants.WORLD_ASCEND);
    }

    public static boolean isPurgeWorld(World world) {
        return isWorld(world, WorldConstants.WORLD_PURGE);
    }

    public static boolean isRunOrFallWorld(World world) {
        return isWorld(world, WorldConstants.WORLD_RUN_OR_FALL);
    }
}
