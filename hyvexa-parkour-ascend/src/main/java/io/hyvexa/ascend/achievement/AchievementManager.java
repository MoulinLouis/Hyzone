package io.hyvexa.ascend.achievement;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.AchievementType;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.util.SystemMessageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages the achievement system for Ascend mode.
 * Achievements grant titles that can be displayed next to the player's name.
 */
public class AchievementManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AscendPlayerStore playerStore;

    public AchievementManager(AscendPlayerStore playerStore) {
        this.playerStore = playerStore;
    }

    /**
     * Checks all achievements for a player and unlocks any newly earned ones.
     *
     * @return list of newly unlocked achievements
     */
    public List<AchievementType> checkAndUnlockAchievements(UUID playerId, Player player) {
        List<AchievementType> newlyUnlocked = new ArrayList<>();
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return newlyUnlocked;
        }

        for (AchievementType achievement : AchievementType.values()) {
            if (progress.hasAchievement(achievement)) {
                continue;
            }

            if (isAchievementEarned(playerId, progress, achievement)) {
                progress.unlockAchievement(achievement);
                newlyUnlocked.add(achievement);
                playerStore.markDirty(playerId);

                // Notify player
                if (player != null) {
                    player.sendMessage(Message.raw("[Achievement] " + achievement.getName() + " - Title: \"" + achievement.getTitle() + "\"")
                        .color(SystemMessageUtils.SUCCESS));
                }

                LOGGER.atInfo().log("[Achievement] Player " + playerId + " unlocked: " + achievement.name());
            }
        }

        return newlyUnlocked;
    }

    /**
     * Checks if a specific achievement's requirements are met.
     */
    private boolean isAchievementEarned(UUID playerId, AscendPlayerProgress progress, AchievementType achievement) {
        return switch (achievement) {
            // Milestones
            case FIRST_STEPS -> progress.getTotalManualRuns() >= 1;
            case DEDICATED -> progress.getTotalManualRuns() >= AscendConstants.ACHIEVEMENT_MANUAL_RUNS_100;
            case MARATHON -> progress.getTotalManualRuns() >= AscendConstants.ACHIEVEMENT_MANUAL_RUNS_1000;

            // Runners
            case FIRST_ROBOT -> hasAnyRobot(progress);
            case ARMY -> countRobots(progress) >= AscendConstants.ACHIEVEMENT_RUNNER_COUNT;
            case EVOLVED -> hasEvolvedRobot(progress);

            // Prestige
            case FIRST_ELEVATION -> progress.getElevationMultiplier() >= 1;
            case SUMMIT_SEEKER -> hasAnySummitLevel(progress);
            case ASCENDED -> progress.getAscensionCount() >= 1;
        };
    }

    private boolean hasAnyRobot(AscendPlayerProgress progress) {
        for (var mapProgress : progress.getMapProgress().values()) {
            if (mapProgress.hasRobot()) {
                return true;
            }
        }
        return false;
    }

    private int countRobots(AscendPlayerProgress progress) {
        int count = 0;
        for (var mapProgress : progress.getMapProgress().values()) {
            if (mapProgress.hasRobot()) {
                count++;
            }
        }
        return count;
    }

    private boolean hasEvolvedRobot(AscendPlayerProgress progress) {
        for (var mapProgress : progress.getMapProgress().values()) {
            if (mapProgress.hasRobot() && mapProgress.getRobotStars() >= 1) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnySummitLevel(AscendPlayerProgress progress) {
        for (SummitCategory category : SummitCategory.values()) {
            if (progress.getSummitLevel(category) >= 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the list of available titles for a player (from unlocked achievements).
     */
    public List<String> getAvailableTitles(UUID playerId) {
        List<String> titles = new ArrayList<>();
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return titles;
        }

        for (AchievementType achievement : progress.getUnlockedAchievements()) {
            titles.add(achievement.getTitle());
        }
        return titles;
    }

    /**
     * Gets the player's active title.
     */
    public String getActiveTitle(UUID playerId) {
        return playerStore.getActiveTitle(playerId);
    }

    /**
     * Sets the player's active title.
     *
     * @return true if the title was set, false if the player doesn't have that title
     */
    public boolean setActiveTitle(UUID playerId, String title) {
        if (title == null || title.isEmpty()) {
            playerStore.setActiveTitle(playerId, null);
            return true;
        }

        // Verify the player has earned this title
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return false;
        }

        for (AchievementType achievement : progress.getUnlockedAchievements()) {
            if (achievement.getTitle().equalsIgnoreCase(title)) {
                playerStore.setActiveTitle(playerId, title);
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the formatted display name with title for a player.
     */
    public String getDisplayNameWithTitle(UUID playerId, String playerName) {
        String title = getActiveTitle(playerId);
        if (title == null || title.isEmpty()) {
            return playerName;
        }
        return "[" + title + "] " + playerName;
    }

    /**
     * Gets achievement progress for display.
     */
    public AchievementProgress getProgress(UUID playerId, AchievementType achievement) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return new AchievementProgress(achievement, 0, 1, false);
        }

        boolean unlocked = progress.hasAchievement(achievement);
        int current = 0;
        int required = 1;

        switch (achievement) {
            case FIRST_STEPS -> {
                current = Math.min(1, progress.getTotalManualRuns());
                required = 1;
            }
            case DEDICATED -> {
                current = Math.min(progress.getTotalManualRuns(), AscendConstants.ACHIEVEMENT_MANUAL_RUNS_100);
                required = AscendConstants.ACHIEVEMENT_MANUAL_RUNS_100;
            }
            case MARATHON -> {
                current = Math.min(progress.getTotalManualRuns(), AscendConstants.ACHIEVEMENT_MANUAL_RUNS_1000);
                required = AscendConstants.ACHIEVEMENT_MANUAL_RUNS_1000;
            }
            case FIRST_ROBOT -> {
                current = hasAnyRobot(progress) ? 1 : 0;
                required = 1;
            }
            case ARMY -> {
                current = Math.min(countRobots(progress), AscendConstants.ACHIEVEMENT_RUNNER_COUNT);
                required = AscendConstants.ACHIEVEMENT_RUNNER_COUNT;
            }
            case EVOLVED -> {
                current = hasEvolvedRobot(progress) ? 1 : 0;
                required = 1;
            }
            case FIRST_ELEVATION -> {
                current = Math.min(progress.getElevationMultiplier(), 1);
                required = 1;
            }
            case SUMMIT_SEEKER -> {
                current = hasAnySummitLevel(progress) ? 1 : 0;
                required = 1;
            }
            case ASCENDED -> {
                current = Math.min(progress.getAscensionCount(), 1);
                required = 1;
            }
        }

        return new AchievementProgress(achievement, current, required, unlocked);
    }

    public record AchievementProgress(
        AchievementType achievement,
        int current,
        int required,
        boolean unlocked
    ) {
        public double percentage() {
            if (required <= 0) return 100.0;
            return Math.min(100.0, (current * 100.0) / required);
        }
    }
}
