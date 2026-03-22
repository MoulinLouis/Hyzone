package io.hyvexa.ascend.util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.robot.RobotManager;

import java.util.UUID;

/**
 * Shared helper for the prestige flow steps that are duplicated across
 * Elevation, Summit, Ascension, Transcendence, Challenge, and Admin pages.
 */
public final class PrestigeHelper {

    private PrestigeHelper() {}

    /**
     * Despawn all robots for a player. Safe to call when the robot manager is null.
     */
    public static void despawnRobots(UUID playerId, RobotManager robotManager) {
        if (robotManager != null) {
            robotManager.despawnRobotsForPlayer(playerId);
        }
    }

    /**
     * Check and unlock achievements for a player. Safe to call when the
     * achievement manager is null.
     */
    public static void checkAchievements(UUID playerId, Player player,
                                         AchievementManager achievementManager) {
        if (achievementManager != null) {
            achievementManager.checkAndUnlockAchievements(playerId, player);
        }
    }
}
