package io.hyvexa.ascend.data;

import io.hyvexa.ascend.AscensionConstants;
import io.hyvexa.ascend.SummitConstants;
import io.hyvexa.common.math.BigNumber;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AscendPlayerProgressTest {

    @Test
    void casVoltRequiresTheCurrentReferenceAndAddVoltClampsAtZero() {
        AscendPlayerProgress progress = new AscendPlayerProgress();
        BigNumber initial = BigNumber.fromLong(10);
        BigNumber updated = BigNumber.fromLong(25);

        progress.economy().setVolt(initial);

        assertFalse(progress.economy().casVolt(BigNumber.fromLong(10), updated));
        assertTrue(progress.economy().casVolt(initial, updated));
        assertEquals(updated, progress.economy().getVolt());

        progress.economy().addVolt(BigNumber.fromLong(-100));
        assertEquals(BigNumber.ZERO, progress.economy().getVolt());
    }

    @Test
    void totalVoltTrackingOnlyAddsPositiveValues() {
        AscendPlayerProgress progress = new AscendPlayerProgress();

        progress.economy().addTotalVoltEarned(BigNumber.fromLong(5));
        progress.economy().addTotalVoltEarned(BigNumber.fromLong(-10));
        progress.economy().addSummitAccumulatedVolt(BigNumber.fromLong(3));
        progress.economy().addElevationAccumulatedVolt(BigNumber.fromLong(7));

        assertEquals(5.0, progress.economy().getTotalVoltEarned().toDouble(), 1e-9);
        assertEquals(3.0, progress.economy().getSummitAccumulatedVolt().toDouble(), 1e-9);
        assertEquals(7.0, progress.economy().getElevationAccumulatedVolt().toDouble(), 1e-9);
    }

    @Test
    void summitXpClampsAtZeroAndLevelUsesCumulativeXpThresholds() {
        AscendPlayerProgress progress = new AscendPlayerProgress();
        double levelFiveXp = SummitConstants.getCumulativeXpForLevel(5);

        assertEquals(0.0, progress.economy().addSummitXp(SummitConstants.SummitCategory.MULTIPLIER_GAIN, -25), 1e-9);

        progress.economy().setSummitXp(SummitConstants.SummitCategory.MULTIPLIER_GAIN, levelFiveXp);

        assertEquals(levelFiveXp, progress.economy().getSummitXp(SummitConstants.SummitCategory.MULTIPLIER_GAIN), 1e-9);
        assertEquals(5, progress.economy().getSummitLevel(SummitConstants.SummitCategory.MULTIPLIER_GAIN));
        assertEquals(5, progress.economy().getSummitLevels().get(SummitConstants.SummitCategory.MULTIPLIER_GAIN));
    }

    @Test
    void ascensionAndSkillNodesTrackSpentAndAvailablePoints() {
        AscendPlayerProgress progress = new AscendPlayerProgress();

        assertEquals(1, progress.gameplay().incrementAscensionCount());
        progress.gameplay().setSkillTreePoints(10);

        assertTrue(progress.gameplay().unlockSkillNode(AscensionConstants.SkillTreeNode.AUTO_RUNNERS));
        assertFalse(progress.gameplay().unlockSkillNode(AscensionConstants.SkillTreeNode.AUTO_RUNNERS));
        assertTrue(progress.gameplay().hasSkillNode(AscensionConstants.SkillTreeNode.AUTO_RUNNERS));
        assertEquals(10, progress.gameplay().getSkillTreePoints());
        assertEquals(1, progress.gameplay().getSpentSkillPoints());
        assertEquals(9, progress.gameplay().getAvailableSkillPoints());
    }

    @Test
    void achievementsAndChallengeRewardsTrackCompletion() {
        AscendPlayerProgress progress = new AscendPlayerProgress();

        assertTrue(progress.gameplay().unlockAchievement(AscensionConstants.AchievementType.FIRST_STEPS));
        assertFalse(progress.gameplay().unlockAchievement(AscensionConstants.AchievementType.FIRST_STEPS));
        progress.gameplay().addChallengeReward(AscensionConstants.ChallengeType.CHALLENGE_1);
        progress.gameplay().addChallengeReward(AscensionConstants.ChallengeType.CHALLENGE_3);

        assertTrue(progress.gameplay().hasAchievement(AscensionConstants.AchievementType.FIRST_STEPS));
        assertTrue(progress.gameplay().hasChallengeReward(AscensionConstants.ChallengeType.CHALLENGE_1));
        assertEquals(2, progress.gameplay().getCompletedChallengeCount());
    }

    @Test
    void automationAndAutoSettingsRoundTrip() {
        AscendPlayerProgress progress = new AscendPlayerProgress();

        progress.automation().setAutoUpgradeEnabled(true);
        progress.automation().setAutoEvolutionEnabled(true);
        progress.automation().setHideOtherRunners(true);
        progress.automation().setBreakAscensionEnabled(true);
        progress.automation().setAutoAscendEnabled(true);
        progress.automation().setAutoElevationEnabled(true);
        progress.automation().setAutoElevationTimerSeconds(15);
        progress.automation().setAutoElevationTargets(List.of(100L, 250L));
        progress.automation().setAutoElevationTargetIndex(1);
        progress.automation().setAutoSummitEnabled(true);
        progress.automation().setAutoSummitTimerSeconds(20);
        progress.automation().setAutoSummitConfig(List.of(
            new AutomationConfig.AutoSummitCategoryConfig(true, 10),
            new AutomationConfig.AutoSummitCategoryConfig(false, 20),
            new AutomationConfig.AutoSummitCategoryConfig(true, 30)
        ));
        progress.automation().setAutoSummitRotationIndex(2);

        assertTrue(progress.automation().isAutoUpgradeEnabled());
        assertTrue(progress.automation().isAutoEvolutionEnabled());
        assertTrue(progress.automation().isHideOtherRunners());
        assertTrue(progress.automation().isBreakAscensionEnabled());
        assertTrue(progress.automation().isAutoAscendEnabled());
        assertTrue(progress.automation().isAutoElevationEnabled());
        assertEquals(15, progress.automation().getAutoElevationTimerSeconds());
        assertEquals(List.of(100L, 250L), progress.automation().getAutoElevationTargets());
        assertEquals(1, progress.automation().getAutoElevationTargetIndex());
        assertTrue(progress.automation().isAutoSummitEnabled());
        assertEquals(20, progress.automation().getAutoSummitTimerSeconds());
        assertEquals(3, progress.automation().getAutoSummitConfig().size());
        assertEquals(2, progress.automation().getAutoSummitRotationIndex());
    }

    @Test
    void resetMapProgressPreservingPbsKeepsOnlyBestTimes() {
        AscendPlayerProgress progress = new AscendPlayerProgress();
        GameplayState.MapProgress mapOne = progress.gameplay().getOrCreateMapProgress("map-1");
        mapOne.setUnlocked(true);
        mapOne.setHasRobot(true);
        mapOne.setRobotSpeedLevel(7);
        mapOne.setBestTimeMs(1234L);

        GameplayState.MapProgress mapTwo = progress.gameplay().getOrCreateMapProgress("map-2");
        mapTwo.setUnlocked(true);
        mapTwo.setRobotStars(3);

        progress.gameplay().resetMapProgressPreservingPBs();

        assertEquals(1, progress.gameplay().getMapProgress().size());
        GameplayState.MapProgress preserved = progress.gameplay().getMapProgress().get("map-1");
        assertNotNull(preserved);
        assertEquals(1234L, preserved.getBestTimeMs());
        assertFalse(preserved.isUnlocked());
        assertFalse(preserved.hasRobot());
        assertEquals(0, preserved.getRobotSpeedLevel());
    }

    @Test
    void mapProgressTracksMomentumRobotStateAndMultiplierClamping() {
        GameplayState.MapProgress progress = new GameplayState.MapProgress();

        progress.activateMomentum(250);
        progress.setRobotSpeedLevel(-5);
        progress.setRobotStars(-2);
        progress.setMultiplier(BigNumber.ZERO);

        assertTrue(progress.isMomentumActive());
        assertTrue(progress.getMomentumProgress() > 0.0);
        assertEquals(0, progress.getRobotSpeedLevel());
        assertEquals(0, progress.getRobotStars());
        assertEquals(1.0, progress.getMultiplier().toDouble(), 1e-9);

        assertEquals(1, progress.incrementRobotSpeedLevel());
        assertEquals(1, progress.incrementRobotStars());
        assertEquals(1.5, progress.addMultiplier(BigNumber.fromDouble(0.5)).toDouble(), 1e-9);
    }

    @Test
    void concurrentAddVoltDoesNotLoseUpdates() throws Exception {
        AscendPlayerProgress progress = new AscendPlayerProgress();
        int threads = 8;
        int incrementsPerThread = 1_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        progress.economy().addVolt(BigNumber.ONE);
                    }
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(threads * incrementsPerThread, progress.economy().getVolt().toLong());
    }
}
