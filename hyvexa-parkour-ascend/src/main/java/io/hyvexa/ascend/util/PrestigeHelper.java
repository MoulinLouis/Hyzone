package io.hyvexa.ascend.util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.robot.RobotManager;

import java.util.UUID;

/**
 * Shared helper for the prestige flow steps that are duplicated across
 * Elevation, Summit, Ascension, Transcendence, Challenge, and Admin pages.
 */
public final class PrestigeHelper {

    private PrestigeHelper() {}

    /**
     * Despawn all robots for a player. Safe to call when the plugin or
     * robot manager is null.
     */
    public static void despawnRobots(UUID playerId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        RobotManager robotManager = plugin.getRobotManager();
        if (robotManager != null) {
            robotManager.despawnRobotsForPlayer(playerId);
        }
    }

    /**
     * Check and unlock achievements for a player. Safe to call when the
     * plugin or achievement manager is null.
     */
    public static void checkAchievements(UUID playerId, Player player) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        if (plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
        }
    }
}
