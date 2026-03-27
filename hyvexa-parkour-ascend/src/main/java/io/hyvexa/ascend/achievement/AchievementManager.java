package io.hyvexa.ascend.achievement;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import io.hyvexa.ascend.AscensionConstants;
import io.hyvexa.ascend.AscensionConstants.AchievementType;
import io.hyvexa.ascend.ElevationConstants;
import io.hyvexa.ascend.RunnerEconomyConstants;
import io.hyvexa.ascend.SummitConstants.SummitCategory;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.GameplayState;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.core.analytics.PlayerAnalytics;

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
    private final PlayerAnalytics analytics;

    public AchievementManager(AscendPlayerStore playerStore, PlayerAnalytics analytics) {
        this.playerStore = playerStore;
        this.analytics = analytics;
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
            if (progress.gameplay().hasAchievement(achievement)) {
                continue;
            }

            if (isAchievementEarned(playerId, progress, achievement)) {
                progress.gameplay().unlockAchievement(achievement);
                newlyUnlocked.add(achievement);

                if (player != null) {
                    player.sendMessage(Message.raw("[Achievement] " + achievement.getName() + " unlocked!")
                        .color(SystemMessageUtils.SUCCESS));
                }

                try {
                    analytics.logEvent(playerId, "ascend_achievement",
                            "{\"achievement_id\":\"" + achievement.name() + "\"}");
                } catch (Exception e) { /* silent */ }

                LOGGER.atInfo().log("[Achievement] Player " + playerId + " unlocked: " + achievement.name());
            }
        }

        if (!newlyUnlocked.isEmpty()) {
            playerStore.markDirty(playerId);
        }

        return newlyUnlocked;
    }

    private boolean isAchievementEarned(UUID playerId, AscendPlayerProgress progress, AchievementType achievement) {
        AchievementProgress ap = getProgress(playerId, achievement);
        return ap.current() >= ap.required();
    }

    private long getVisibleElevationMultiplier(AscendPlayerProgress progress) {
        return Math.round(ElevationConstants.getElevationMultiplier(progress.economy().getElevationMultiplier()));
    }

    private boolean hasAnyRobot(AscendPlayerProgress progress) {
        for (var mapProgress : progress.gameplay().getMapProgress().values()) {
            if (mapProgress.hasRobot()) {
                return true;
            }
        }
        return false;
    }

    private int countRobots(AscendPlayerProgress progress) {
        int count = 0;
        for (var mapProgress : progress.gameplay().getMapProgress().values()) {
            if (mapProgress.hasRobot()) {
                count++;
            }
        }
        return count;
    }

    private boolean hasEvolvedRobot(AscendPlayerProgress progress) {
        for (var mapProgress : progress.gameplay().getMapProgress().values()) {
            if (mapProgress.hasRobot() && mapProgress.getRobotStars() >= 1) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMaxStarRobot(AscendPlayerProgress progress) {
        for (var mapProgress : progress.gameplay().getMapProgress().values()) {
            if (mapProgress.hasRobot() && mapProgress.getRobotStars() >= RunnerEconomyConstants.MAX_ROBOT_STARS) {
                return true;
            }
        }
        return false;
    }

    private int getMaxRobotStars(AscendPlayerProgress progress) {
        int max = 0;
        for (var mapProgress : progress.gameplay().getMapProgress().values()) {
            if (mapProgress.hasRobot()) {
                max = Math.max(max, mapProgress.getRobotStars());
            }
        }
        return max;
    }

    private boolean hasAnySummitLevel(AscendPlayerProgress progress) {
        for (SummitCategory category : SummitCategory.values()) {
            if (progress.economy().getSummitLevel(category) >= 1) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnySummitLevelAbove(AscendPlayerProgress progress, int level) {
        for (SummitCategory category : SummitCategory.values()) {
            if (progress.economy().getSummitLevel(category) >= level) {
                return true;
            }
        }
        return false;
    }

    private boolean allMapsMaxStars(AscendPlayerProgress progress) {
        if (progress.gameplay().getMapProgress().size() < RunnerEconomyConstants.MULTIPLIER_SLOTS) {
            return false;
        }
        for (var mapProgress : progress.gameplay().getMapProgress().values()) {
            if (!mapProgress.hasRobot() || mapProgress.getRobotStars() < RunnerEconomyConstants.MAX_ROBOT_STARS) {
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
            if (!progress.gameplay().hasAchievement(type)) {
                return false;
            }
        }
        return true;
    }

    private int getMaxSummitLevel(AscendPlayerProgress progress) {
        int max = 0;
        for (SummitCategory category : SummitCategory.values()) {
            max = Math.max(max, progress.economy().getSummitLevel(category));
        }
        return max;
    }

    private int countOtherUnlockedAchievements(AscendPlayerProgress progress) {
        int count = 0;
        for (AchievementType type : AchievementType.values()) {
            if (type == AchievementType.COMPLETIONIST) {
                continue;
            }
            if (progress.gameplay().hasAchievement(type)) {
                count++;
            }
        }
        return count;
    }

    public AchievementProgress getProgress(UUID playerId, AchievementType achievement) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return new AchievementProgress(achievement, 0, 1, false);
        }

        boolean unlocked = progress.gameplay().hasAchievement(achievement);
        int current = 0;
        int required = 1;

        switch (achievement) {
            // Milestones
            case FIRST_STEPS -> {
                current = Math.min(1, progress.gameplay().getTotalManualRuns());
                required = 1;
            }
            case WARMING_UP -> {
                current = Math.min(progress.gameplay().getTotalManualRuns(), AscensionConstants.ACHIEVEMENT_MANUAL_RUNS_10);
                required = AscensionConstants.ACHIEVEMENT_MANUAL_RUNS_10;
            }
            case DEDICATED -> {
                current = Math.min(progress.gameplay().getTotalManualRuns(), AscensionConstants.ACHIEVEMENT_MANUAL_RUNS_100);
                required = AscensionConstants.ACHIEVEMENT_MANUAL_RUNS_100;
            }
            case HALFWAY_THERE -> {
                current = Math.min(progress.gameplay().getTotalManualRuns(), AscensionConstants.ACHIEVEMENT_MANUAL_RUNS_500);
                required = AscensionConstants.ACHIEVEMENT_MANUAL_RUNS_500;
            }
            case MARATHON -> {
                current = Math.min(progress.gameplay().getTotalManualRuns(), AscensionConstants.ACHIEVEMENT_MANUAL_RUNS_1000);
                required = AscensionConstants.ACHIEVEMENT_MANUAL_RUNS_1000;
            }
            case UNSTOPPABLE -> {
                current = Math.min(progress.gameplay().getTotalManualRuns(), AscensionConstants.ACHIEVEMENT_MANUAL_RUNS_5000);
                required = AscensionConstants.ACHIEVEMENT_MANUAL_RUNS_5000;
            }
            case LIVING_LEGEND -> {
                current = Math.min(progress.gameplay().getTotalManualRuns(), AscensionConstants.ACHIEVEMENT_MANUAL_RUNS_10000);
                required = AscensionConstants.ACHIEVEMENT_MANUAL_RUNS_10000;
            }

            // Runners
            case FIRST_ROBOT -> {
                current = hasAnyRobot(progress) ? 1 : 0;
                required = 1;
            }
            case ARMY -> {
                current = Math.min(countRobots(progress), AscensionConstants.ACHIEVEMENT_RUNNER_COUNT);
                required = AscensionConstants.ACHIEVEMENT_RUNNER_COUNT;
            }
            case EVOLVED -> {
                current = hasEvolvedRobot(progress) ? 1 : 0;
                required = 1;
            }
            case STAR_COLLECTOR -> {
                current = Math.min(getMaxRobotStars(progress), RunnerEconomyConstants.MAX_ROBOT_STARS);
                required = RunnerEconomyConstants.MAX_ROBOT_STARS;
            }

            // Prestige
            case FIRST_ELEVATION -> {
                current = Math.min(progress.economy().getElevationMultiplier() >= 2 ? 1 : 0, 1);
                required = 1;
            }
            case GOING_UP -> {
                current = (int) Math.min(getVisibleElevationMultiplier(progress), AscensionConstants.ACHIEVEMENT_ELEVATION_100);
                required = AscensionConstants.ACHIEVEMENT_ELEVATION_100;
            }
            case SKY_HIGH -> {
                current = (int) Math.min(getVisibleElevationMultiplier(progress), AscensionConstants.ACHIEVEMENT_ELEVATION_5000);
                required = AscensionConstants.ACHIEVEMENT_ELEVATION_5000;
            }
            case STRATOSPHERE -> {
                current = (int) Math.min(getVisibleElevationMultiplier(progress), AscensionConstants.ACHIEVEMENT_ELEVATION_20000);
                required = AscensionConstants.ACHIEVEMENT_ELEVATION_20000;
            }
            case SUMMIT_SEEKER -> {
                current = hasAnySummitLevel(progress) ? 1 : 0;
                required = 1;
            }
            case PEAK_PERFORMER -> {
                current = Math.min(getMaxSummitLevel(progress), AscensionConstants.ACHIEVEMENT_SUMMIT_LEVEL_10);
                required = AscensionConstants.ACHIEVEMENT_SUMMIT_LEVEL_10;
            }
            case MOUNTAINEER -> {
                current = Math.min(getMaxSummitLevel(progress), AscensionConstants.ACHIEVEMENT_SUMMIT_LEVEL_100);
                required = AscensionConstants.ACHIEVEMENT_SUMMIT_LEVEL_100;
            }
            case SUMMIT_LEGEND -> {
                current = Math.min(getMaxSummitLevel(progress), AscensionConstants.ACHIEVEMENT_SUMMIT_LEVEL_1000);
                required = AscensionConstants.ACHIEVEMENT_SUMMIT_LEVEL_1000;
            }
            case ASCENDED -> {
                current = Math.min(progress.gameplay().getAscensionCount(), 1);
                required = 1;
            }
            case VETERAN -> {
                current = Math.min(progress.gameplay().getAscensionCount(), AscensionConstants.ACHIEVEMENT_ASCENSION_5);
                required = AscensionConstants.ACHIEVEMENT_ASCENSION_5;
            }
            case TRANSCENDENT -> {
                current = Math.min(progress.gameplay().getAscensionCount(), AscensionConstants.ACHIEVEMENT_ASCENSION_10);
                required = AscensionConstants.ACHIEVEMENT_ASCENSION_10;
            }

            // Skills
            case NEW_POWERS -> {
                current = Math.min(progress.gameplay().getUnlockedSkillNodes().size(), 1);
                required = 1;
            }

            // Challenges
            case CHALLENGER -> {
                current = Math.min(progress.gameplay().getCompletedChallengeRewards().size(), 1);
                required = 1;
            }
            case CHALLENGE_MASTER -> {
                current = progress.gameplay().getCompletedChallengeRewards().size();
                required = AscensionConstants.ChallengeType.values().length;
            }

            // Easter Eggs
            case CAT_COLLECTOR -> {
                current = Math.min(progress.gameplay().getFoundCatCount(), AscensionConstants.ACHIEVEMENT_CATS_REQUIRED);
                required = AscensionConstants.ACHIEVEMENT_CATS_REQUIRED;
            }

            // Secret
            case CHAIN_RUNNER -> {
                current = Math.min(progress.gameplay().getConsecutiveManualRuns(), AscensionConstants.ACHIEVEMENT_CONSECUTIVE_RUNS_25);
                required = AscensionConstants.ACHIEVEMENT_CONSECUTIVE_RUNS_25;
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
