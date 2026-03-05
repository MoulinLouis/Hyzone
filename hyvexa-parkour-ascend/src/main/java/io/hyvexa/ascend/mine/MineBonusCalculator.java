package io.hyvexa.ascend.mine;

import io.hyvexa.ascend.mine.data.Mine;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MineUpgradeType;

import java.util.List;

/**
 * Calculates cross-progression bonuses from mining milestones
 * that apply as permanent passive benefits to parkour systems.
 *
 * Bonus values are hardcoded constants; could be made configurable later.
 */
public class MineBonusCalculator {

    // Runner speed bonuses
    private static final double SPEED_BONUS_MINE2_UNLOCKED = 0.05;
    private static final double SPEED_BONUS_MINING_SPEED_MAXED = 0.10;

    // Multiplier gain bonuses
    private static final double MULT_BONUS_MINE3_UNLOCKED = 0.10;
    private static final double MULT_BONUS_ALL_MINERS = 0.20;

    // Volt gain bonuses
    private static final double VOLT_BONUS_MINE4_UNLOCKED = 0.15;

    private final MineConfigStore mineConfigStore;

    public MineBonusCalculator(MineConfigStore mineConfigStore) {
        this.mineConfigStore = mineConfigStore;
    }

    /**
     * Runner speed multiplier from mining milestones.
     * Base 1.0, +0.05 if mine 2 unlocked, +0.10 if MINING_SPEED at max level.
     */
    public double getRunnerSpeedMultiplier(MinePlayerProgress progress) {
        if (progress == null) return 1.0;

        double multiplier = 1.0;

        String mine2Id = getMineIdByIndex(1);
        if (mine2Id != null && progress.getMineState(mine2Id).isUnlocked()) {
            multiplier += SPEED_BONUS_MINE2_UNLOCKED;
        }

        if (progress.getUpgradeLevel(MineUpgradeType.MINING_SPEED) >= MineUpgradeType.MINING_SPEED.getMaxLevel()) {
            multiplier += SPEED_BONUS_MINING_SPEED_MAXED;
        }

        return multiplier;
    }

    /**
     * Multiplier gain multiplier from mining milestones.
     * Base 1.0, +0.10 if mine 3 unlocked, +0.20 if all mines have miners.
     */
    public double getMultiplierGainMultiplier(MinePlayerProgress progress) {
        if (progress == null) return 1.0;

        double multiplier = 1.0;

        String mine3Id = getMineIdByIndex(2);
        if (mine3Id != null && progress.getMineState(mine3Id).isUnlocked()) {
            multiplier += MULT_BONUS_MINE3_UNLOCKED;
        }

        if (allMinesHaveMiners(progress)) {
            multiplier += MULT_BONUS_ALL_MINERS;
        }

        return multiplier;
    }

    /**
     * Volt gain multiplier from mining milestones.
     * Base 1.0, +0.15 if mine 4 unlocked.
     */
    public double getVoltGainMultiplier(MinePlayerProgress progress) {
        if (progress == null) return 1.0;

        double multiplier = 1.0;

        String mine4Id = getMineIdByIndex(3);
        if (mine4Id != null && progress.getMineState(mine4Id).isUnlocked()) {
            multiplier += VOLT_BONUS_MINE4_UNLOCKED;
        }

        return multiplier;
    }

    /**
     * Returns the mine ID at the given index (0-based) from the sorted mine list,
     * or null if the index is out of bounds.
     */
    private String getMineIdByIndex(int index) {
        List<Mine> mines = mineConfigStore.listMinesSorted();
        if (index < 0 || index >= mines.size()) return null;
        return mines.get(index).getId();
    }

    /**
     * Checks whether all configured mines have a miner assigned for the given player.
     * Returns false if there are no mines configured.
     */
    private boolean allMinesHaveMiners(MinePlayerProgress progress) {
        List<Mine> mines = mineConfigStore.listMinesSorted();
        if (mines.isEmpty()) return false;

        for (Mine mine : mines) {
            if (!progress.getMinerState(mine.getId()).isHasMiner()) {
                return false;
            }
        }
        return true;
    }
}
