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
                    player.sendMessage(Message.raw("[Achievement] " + achievement.getName() + " unlocked!")
                        .color(SystemMessageUtils.SUCCESS));
                }

                try {
                    io.hyvexa.core.analytics.AnalyticsStore.getInstance().logEvent(playerId, "ascend_achievement",
                            "{\"achievement_id\":\"" + achievement.name() + "\"}");
                } catch (Exception e) { /* silent */ }

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
            case WARMING_UP -> progress.getTotalManualRuns() >= AscendConstants.ACHIEVEMENT_MANUAL_RUNS_10;
            case DEDICATED -> progress.getTotalManualRuns() >= AscendConstants.ACHIEVEMENT_MANUAL_RUNS_100;
            case HALFWAY_THERE -> progress.getTotalManualRuns() >= AscendConstants.ACHIEVEMENT_MANUAL_RUNS_500;
            case MARATHON -> progress.getTotalManualRuns() >= AscendConstants.ACHIEVEMENT_MANUAL_RUNS_1000;
            case UNSTOPPABLE -> progress.getTotalManualRuns() >= AscendConstants.ACHIEVEMENT_MANUAL_RUNS_5000;
            case LIVING_LEGEND -> progress.getTotalManualRuns() >= AscendConstants.ACHIEVEMENT_MANUAL_RUNS_10000;

            // Runners
            case FIRST_ROBOT -> hasAnyRobot(progress);
            case ARMY -> countRobots(progress) >= AscendConstants.ACHIEVEMENT_RUNNER_COUNT;
            case EVOLVED -> hasEvolvedRobot(progress);
            case STAR_COLLECTOR -> hasMaxStarRobot(progress);

            // Prestige
            case FIRST_ELEVATION -> progress.getElevationMultiplier() >= 2;
            case GOING_UP -> getVisibleElevationMultiplier(progress) >= AscendConstants.ACHIEVEMENT_ELEVATION_100;
            case SKY_HIGH -> getVisibleElevationMultiplier(progress) >= AscendConstants.ACHIEVEMENT_ELEVATION_5000;
            case STRATOSPHERE -> getVisibleElevationMultiplier(progress) >= AscendConstants.ACHIEVEMENT_ELEVATION_20000;
            case SUMMIT_SEEKER -> hasAnySummitLevel(progress);
            case PEAK_PERFORMER -> hasAnySummitLevelAbove(progress, AscendConstants.ACHIEVEMENT_SUMMIT_LEVEL_10);
            case MOUNTAINEER -> hasAnySummitLevelAbove(progress, AscendConstants.ACHIEVEMENT_SUMMIT_LEVEL_100);
            case SUMMIT_LEGEND -> hasAnySummitLevelAbove(progress, AscendConstants.ACHIEVEMENT_SUMMIT_LEVEL_1000);
            case ASCENDED -> progress.getAscensionCount() >= 1;
            case VETERAN -> progress.getAscensionCount() >= AscendConstants.ACHIEVEMENT_ASCENSION_5;
            case TRANSCENDENT -> progress.getAscensionCount() >= AscendConstants.ACHIEVEMENT_ASCENSION_10;

            // Skills
            case NEW_POWERS -> !progress.getUnlockedSkillNodes().isEmpty();

            // Challenges
            case CHALLENGER -> !progress.getCompletedChallengeRewards().isEmpty();
            case CHALLENGE_MASTER -> progress.hasAllChallengeRewards();

            // Secret
            case CHAIN_RUNNER -> progress.getConsecutiveManualRuns() >= AscendConstants.ACHIEVEMENT_CONSECUTIVE_RUNS_25;
            case ALL_STARS -> allMapsMaxStars(progress);
            case COMPLETIONIST -> allOtherAchievementsUnlocked(progress);
        };
    }

    private long getVisibleElevationMultiplier(AscendPlayerProgress progress) {
        return Math.round(AscendConstants.getElevationMultiplier(progress.getElevationMultiplier()));
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

    private boolean hasMaxStarRobot(AscendPlayerProgress progress) {
        for (var mapProgress : progress.getMapProgress().values()) {
            if (mapProgress.hasRobot() && mapProgress.getRobotStars() >= AscendConstants.MAX_ROBOT_STARS) {
                return true;
            }
        }
        return false;
    }

    private int getMaxRobotStars(AscendPlayerProgress progress) {
        int max = 0;
        for (var mapProgress : progress.getMapProgress().values()) {
            if (mapProgress.hasRobot()) {
                max = Math.max(max, mapProgress.getRobotStars());
            }
        }
        return max;
    }

    private boolean hasAnySummitLevel(AscendPlayerProgress progress) {
        for (SummitCategory category : SummitCategory.values()) {
            if (progress.getSummitLevel(category) >= 1) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnySummitLevelAbove(AscendPlayerProgress progress, int level) {
        for (SummitCategory category : SummitCategory.values()) {
            if (progress.getSummitLevel(category) >= level) {
                return true;
            }
        }
        return false;
    }

    private boolean allMapsMaxStars(AscendPlayerProgress progress) {
        if (progress.getMapProgress().size() < AscendConstants.MULTIPLIER_SLOTS) {
            return false;
        }
        for (var mapProgress : progress.getMapProgress().values()) {
            if (!mapProgress.hasRobot() || mapProgress.getRobotStars() < AscendConstants.MAX_ROBOT_STARS) {
                return false;
            }
        }
        return true;
    }

    private boolean allOtherAchievementsUnlocked(AscendPlayerProgress progress) {
        for (AchievementType type : AchievementType.values()) {
            if (type == AchievementType.COMPLETIONIST) {
                continue;
            }
            if (!progress.hasAchievement(type)) {
                return false;
            }
        }
        return true;
    }

    private int getMaxSummitLevel(AscendPlayerProgress progress) {
        int max = 0;
        for (SummitCategory category : SummitCategory.values()) {
            max = Math.max(max, progress.getSummitLevel(category));
        }
        return max;
    }

    private int countOtherUnlockedAchievements(AscendPlayerProgress progress) {
        int count = 0;
        for (AchievementType type : AchievementType.values()) {
            if (type == AchievementType.COMPLETIONIST) {
                continue;
            }
            if (progress.hasAchievement(type)) {
                count++;
            }
        }
        return count;
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
            // Milestones
            case FIRST_STEPS -> {
                current = Math.min(1, progress.getTotalManualRuns());
                required = 1;
            }
            case WARMING_UP -> {
                current = Math.min(progress.getTotalManualRuns(), AscendConstants.ACHIEVEMENT_MANUAL_RUNS_10);
                required = AscendConstants.ACHIEVEMENT_MANUAL_RUNS_10;
            }
            case DEDICATED -> {
                current = Math.min(progress.getTotalManualRuns(), AscendConstants.ACHIEVEMENT_MANUAL_RUNS_100);
                required = AscendConstants.ACHIEVEMENT_MANUAL_RUNS_100;
            }
            case HALFWAY_THERE -> {
                current = Math.min(progress.getTotalManualRuns(), AscendConstants.ACHIEVEMENT_MANUAL_RUNS_500);
                required = AscendConstants.ACHIEVEMENT_MANUAL_RUNS_500;
            }
            case MARATHON -> {
                current = Math.min(progress.getTotalManualRuns(), AscendConstants.ACHIEVEMENT_MANUAL_RUNS_1000);
                required = AscendConstants.ACHIEVEMENT_MANUAL_RUNS_1000;
            }
            case UNSTOPPABLE -> {
                current = Math.min(progress.getTotalManualRuns(), AscendConstants.ACHIEVEMENT_MANUAL_RUNS_5000);
                required = AscendConstants.ACHIEVEMENT_MANUAL_RUNS_5000;
            }
            case LIVING_LEGEND -> {
                current = Math.min(progress.getTotalManualRuns(), AscendConstants.ACHIEVEMENT_MANUAL_RUNS_10000);
                required = AscendConstants.ACHIEVEMENT_MANUAL_RUNS_10000;
            }

            // Runners
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
            case STAR_COLLECTOR -> {
                current = Math.min(getMaxRobotStars(progress), AscendConstants.MAX_ROBOT_STARS);
                required = AscendConstants.MAX_ROBOT_STARS;
            }

            // Prestige
            case FIRST_ELEVATION -> {
                current = Math.min(progress.getElevationMultiplier() >= 2 ? 1 : 0, 1);
                required = 1;
            }
            case GOING_UP -> {
                current = (int) Math.min(getVisibleElevationMultiplier(progress), AscendConstants.ACHIEVEMENT_ELEVATION_100);
                required = AscendConstants.ACHIEVEMENT_ELEVATION_100;
            }
            case SKY_HIGH -> {
                current = (int) Math.min(getVisibleElevationMultiplier(progress), AscendConstants.ACHIEVEMENT_ELEVATION_5000);
                required = AscendConstants.ACHIEVEMENT_ELEVATION_5000;
            }
            case STRATOSPHERE -> {
                current = (int) Math.min(getVisibleElevationMultiplier(progress), AscendConstants.ACHIEVEMENT_ELEVATION_20000);
                required = AscendConstants.ACHIEVEMENT_ELEVATION_20000;
            }
            case SUMMIT_SEEKER -> {
                current = hasAnySummitLevel(progress) ? 1 : 0;
                required = 1;
            }
            case PEAK_PERFORMER -> {
                current = Math.min(getMaxSummitLevel(progress), AscendConstants.ACHIEVEMENT_SUMMIT_LEVEL_10);
                required = AscendConstants.ACHIEVEMENT_SUMMIT_LEVEL_10;
            }
            case MOUNTAINEER -> {
                current = Math.min(getMaxSummitLevel(progress), AscendConstants.ACHIEVEMENT_SUMMIT_LEVEL_100);
                required = AscendConstants.ACHIEVEMENT_SUMMIT_LEVEL_100;
            }
            case SUMMIT_LEGEND -> {
                current = Math.min(getMaxSummitLevel(progress), AscendConstants.ACHIEVEMENT_SUMMIT_LEVEL_1000);
                required = AscendConstants.ACHIEVEMENT_SUMMIT_LEVEL_1000;
            }
            case ASCENDED -> {
                current = Math.min(progress.getAscensionCount(), 1);
                required = 1;
            }
            case VETERAN -> {
                current = Math.min(progress.getAscensionCount(), AscendConstants.ACHIEVEMENT_ASCENSION_5);
                required = AscendConstants.ACHIEVEMENT_ASCENSION_5;
            }
            case TRANSCENDENT -> {
                current = Math.min(progress.getAscensionCount(), AscendConstants.ACHIEVEMENT_ASCENSION_10);
                required = AscendConstants.ACHIEVEMENT_ASCENSION_10;
            }

            // Skills
            case NEW_POWERS -> {
                current = Math.min(progress.getUnlockedSkillNodes().size(), 1);
                required = 1;
            }

            // Challenges
            case CHALLENGER -> {
                current = Math.min(progress.getCompletedChallengeRewards().size(), 1);
                required = 1;
            }
            case CHALLENGE_MASTER -> {
                current = progress.getCompletedChallengeRewards().size();
                required = AscendConstants.ChallengeType.values().length;
            }

            // Secret
            case CHAIN_RUNNER -> {
                current = Math.min(progress.getConsecutiveManualRuns(), AscendConstants.ACHIEVEMENT_CONSECUTIVE_RUNS_25);
                required = AscendConstants.ACHIEVEMENT_CONSECUTIVE_RUNS_25;
            }
            case ALL_STARS -> {
                current = allMapsMaxStars(progress) ? 1 : 0;
                required = 1;
            }
            case COMPLETIONIST -> {
                current = countOtherUnlockedAchievements(progress);
                required = AchievementType.values().length - 1;
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
