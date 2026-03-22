package io.hyvexa.ascend.data;

import io.hyvexa.ascend.AscendConstants;
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

        progress.setVolt(initial);

        assertFalse(progress.casVolt(BigNumber.fromLong(10), updated));
        assertTrue(progress.casVolt(initial, updated));
        assertEquals(updated, progress.getVolt());

        progress.addVolt(BigNumber.fromLong(-100));
        assertEquals(BigNumber.ZERO, progress.getVolt());
    }

    @Test
    void totalVoltTrackingOnlyAddsPositiveValues() {
        AscendPlayerProgress progress = new AscendPlayerProgress();

        progress.addTotalVoltEarned(BigNumber.fromLong(5));
        progress.addTotalVoltEarned(BigNumber.fromLong(-10));
        progress.addSummitAccumulatedVolt(BigNumber.fromLong(3));
        progress.addElevationAccumulatedVolt(BigNumber.fromLong(7));

        assertEquals(5.0, progress.getTotalVoltEarned().toDouble(), 1e-9);
        assertEquals(3.0, progress.getSummitAccumulatedVolt().toDouble(), 1e-9);
        assertEquals(7.0, progress.getElevationAccumulatedVolt().toDouble(), 1e-9);
    }

    @Test
    void summitXpClampsAtZeroAndLevelUsesCumulativeXpThresholds() {
        AscendPlayerProgress progress = new AscendPlayerProgress();
        double levelFiveXp = AscendConstants.getCumulativeXpForLevel(5);

        assertEquals(0.0, progress.addSummitXp(AscendConstants.SummitCategory.MULTIPLIER_GAIN, -25), 1e-9);

        progress.setSummitXp(AscendConstants.SummitCategory.MULTIPLIER_GAIN, levelFiveXp);

        assertEquals(levelFiveXp, progress.getSummitXp(AscendConstants.SummitCategory.MULTIPLIER_GAIN), 1e-9);
        assertEquals(5, progress.getSummitLevel(AscendConstants.SummitCategory.MULTIPLIER_GAIN));
        assertEquals(5, progress.getSummitLevels().get(AscendConstants.SummitCategory.MULTIPLIER_GAIN));
    }

    @Test
    void ascensionAndSkillNodesTrackSpentAndAvailablePoints() {
        AscendPlayerProgress progress = new AscendPlayerProgress();

        assertEquals(1, progress.incrementAscensionCount());
        progress.setSkillTreePoints(10);

        assertTrue(progress.unlockSkillNode(AscendConstants.SkillTreeNode.AUTO_RUNNERS));
        assertFalse(progress.unlockSkillNode(AscendConstants.SkillTreeNode.AUTO_RUNNERS));
        assertTrue(progress.hasSkillNode(AscendConstants.SkillTreeNode.AUTO_RUNNERS));
        assertEquals(10, progress.getSkillTreePoints());
        assertEquals(1, progress.getSpentSkillPoints());
        assertEquals(9, progress.getAvailableSkillPoints());
    }

    @Test
    void achievementsAndChallengeRewardsTrackCompletion() {
        AscendPlayerProgress progress = new AscendPlayerProgress();

        assertTrue(progress.unlockAchievement(AscendConstants.AchievementType.FIRST_STEPS));
        assertFalse(progress.unlockAchievement(AscendConstants.AchievementType.FIRST_STEPS));
        progress.addChallengeReward(AscendConstants.ChallengeType.CHALLENGE_1);
        progress.addChallengeReward(AscendConstants.ChallengeType.CHALLENGE_3);

        assertTrue(progress.hasAchievement(AscendConstants.AchievementType.FIRST_STEPS));
        assertTrue(progress.hasChallengeReward(AscendConstants.ChallengeType.CHALLENGE_1));
        assertEquals(2, progress.getCompletedChallengeCount());
    }

    @Test
    void automationAndAutoSettingsRoundTrip() {
        AscendPlayerProgress progress = new AscendPlayerProgress();

        progress.setAutoUpgradeEnabled(true);
        progress.setAutoEvolutionEnabled(true);
        progress.setHideOtherRunners(true);
        progress.setBreakAscensionEnabled(true);
        progress.setAutoAscendEnabled(true);
        progress.setAutoElevationEnabled(true);
        progress.setAutoElevationTimerSeconds(15);
        progress.setAutoElevationTargets(List.of(100L, 250L));
        progress.setAutoElevationTargetIndex(1);
        progress.setAutoSummitEnabled(true);
        progress.setAutoSummitTimerSeconds(20);
        progress.setAutoSummitConfig(List.of(
            new AscendPlayerProgress.AutoSummitCategoryConfig(true, 10),
            new AscendPlayerProgress.AutoSummitCategoryConfig(false, 20),
            new AscendPlayerProgress.AutoSummitCategoryConfig(true, 30)
        ));
        progress.setAutoSummitRotationIndex(2);

        assertTrue(progress.isAutoUpgradeEnabled());
        assertTrue(progress.isAutoEvolutionEnabled());
        assertTrue(progress.isHideOtherRunners());
        assertTrue(progress.isBreakAscensionEnabled());
        assertTrue(progress.isAutoAscendEnabled());
        assertTrue(progress.isAutoElevationEnabled());
        assertEquals(15, progress.getAutoElevationTimerSeconds());
        assertEquals(List.of(100L, 250L), progress.getAutoElevationTargets());
        assertEquals(1, progress.getAutoElevationTargetIndex());
        assertTrue(progress.isAutoSummitEnabled());
        assertEquals(20, progress.getAutoSummitTimerSeconds());
        assertEquals(3, progress.getAutoSummitConfig().size());
        assertEquals(2, progress.getAutoSummitRotationIndex());
    }

    @Test
    void resetMapProgressPreservingPbsKeepsOnlyBestTimes() {
        AscendPlayerProgress progress = new AscendPlayerProgress();
        AscendPlayerProgress.MapProgress mapOne = progress.getOrCreateMapProgress("map-1");
        mapOne.setUnlocked(true);
        mapOne.setHasRobot(true);
        mapOne.setRobotSpeedLevel(7);
        mapOne.setBestTimeMs(1234L);

        AscendPlayerProgress.MapProgress mapTwo = progress.getOrCreateMapProgress("map-2");
        mapTwo.setUnlocked(true);
        mapTwo.setRobotStars(3);

        progress.resetMapProgressPreservingPBs();

        assertEquals(1, progress.getMapProgress().size());
        AscendPlayerProgress.MapProgress preserved = progress.getMapProgress().get("map-1");
        assertNotNull(preserved);
        assertEquals(1234L, preserved.getBestTimeMs());
        assertFalse(preserved.isUnlocked());
        assertFalse(preserved.hasRobot());
        assertEquals(0, preserved.getRobotSpeedLevel());
    }

    @Test
    void mapProgressTracksMomentumRobotStateAndMultiplierClamping() {
        AscendPlayerProgress.MapProgress progress = new AscendPlayerProgress.MapProgress();

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
                        progress.addVolt(BigNumber.ONE);
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
        assertEquals(threads * incrementsPerThread, progress.getVolt().toLong());
    }
}
