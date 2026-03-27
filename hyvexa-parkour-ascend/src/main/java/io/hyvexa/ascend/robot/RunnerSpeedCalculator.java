package io.hyvexa.ascend.robot;

import io.hyvexa.ascend.RunnerEconomyConstants;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.GameplayState;
import io.hyvexa.ascend.mine.MineBonusCalculator;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.summit.SummitManager;

import java.util.UUID;

/**
 * Centralizes runner speed multiplier calculation so UI, passive earnings,
 * and runner simulation use the same injected dependencies.
 */
public class RunnerSpeedCalculator {

    private final SummitManager summitManager;
    private final AscensionManager ascensionManager;
    private final AscendPlayerStore playerStore;
    private final MineBonusCalculator mineBonusCalculator;
    private final MinePlayerStore minePlayerStore;

    public RunnerSpeedCalculator(SummitManager summitManager,
                                 AscensionManager ascensionManager,
                                 AscendPlayerStore playerStore,
                                 MineBonusCalculator mineBonusCalculator,
                                 MinePlayerStore minePlayerStore) {
        this.summitManager = summitManager;
        this.ascensionManager = ascensionManager;
        this.playerStore = playerStore;
        this.mineBonusCalculator = mineBonusCalculator;
        this.minePlayerStore = minePlayerStore;
    }

    public double calculateSpeedMultiplier(AscendMap map, int speedLevel, UUID ownerId) {
        double speedMultiplier = 1.0 + (speedLevel * RunnerEconomyConstants.getMapSpeedMultiplier());

        if (summitManager != null) {
            speedMultiplier *= summitManager.getRunnerSpeedBonus(ownerId);
        }

        if (ascensionManager != null) {
            if (ascensionManager.hasRunnerSpeedBoost(ownerId)) {
                speedMultiplier *= 1.1;
            }
            if (ascensionManager.hasRunnerSpeedBoost2(ownerId)) {
                speedMultiplier *= 1.2;
            }
            if (ascensionManager.hasRunnerSpeedBoost3(ownerId)) {
                speedMultiplier *= 1.3;
            }
            if (ascensionManager.hasRunnerSpeedBoost4(ownerId)) {
                speedMultiplier *= 1.5;
            }
            if (ascensionManager.hasRunnerSpeedBoost5(ownerId)) {
                speedMultiplier *= 2.0;
            }
        }

        if (playerStore != null) {
            AscendPlayerProgress progress = playerStore.getPlayer(ownerId);
            if (progress != null) {
                GameplayState.MapProgress mapProgress = progress.gameplay().getMapProgress().get(map.getId());
                if (mapProgress != null && mapProgress.isMomentumActive()) {
                    double momentumMultiplier;
                    if (ascensionManager != null && ascensionManager.hasMomentumMastery(ownerId)) {
                        momentumMultiplier = RunnerEconomyConstants.MOMENTUM_MASTERY_MULTIPLIER;
                    } else if (ascensionManager != null && ascensionManager.hasMomentumSurge(ownerId)) {
                        momentumMultiplier = RunnerEconomyConstants.MOMENTUM_SURGE_MULTIPLIER;
                    } else {
                        momentumMultiplier = RunnerEconomyConstants.MOMENTUM_SPEED_MULTIPLIER;
                    }
                    speedMultiplier *= momentumMultiplier;
                }
            }
        }

        if (mineBonusCalculator != null && minePlayerStore != null) {
            MinePlayerProgress mineProgress = minePlayerStore.getPlayer(ownerId);
            if (mineProgress != null) {
                speedMultiplier *= mineBonusCalculator.getRunnerSpeedMultiplier(mineProgress);
            }
        }

        return speedMultiplier;
    }
}
