package io.hyvexa.ascend.mine;

import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;

/**
 * Calculates cross-progression bonuses from mining milestones
 * that apply as permanent passive benefits to parkour systems.
 *
 * With a single mine, multi-mine unlock bonuses are always inactive.
 * The "all miners" bonus checks if any miner slot is purchased.
 */
public class MineBonusCalculator {

    private static final double MULT_BONUS_ALL_MINERS = 0.20;

    private final MineConfigStore mineConfigStore;

    public MineBonusCalculator(MineConfigStore mineConfigStore) {
        this.mineConfigStore = mineConfigStore;
    }

    /**
     * Runner speed multiplier from mining milestones.
     * With single mine, always 1.0 (no mine2 to unlock).
     */
    public double getRunnerSpeedMultiplier(MinePlayerProgress progress) {
        return 1.0;
    }

    /**
     * Multiplier gain multiplier from mining milestones.
     * +0.20 if all miner slots have miners.
     */
    public double getMultiplierGainMultiplier(MinePlayerProgress progress) {
        if (progress == null) return 1.0;

        double multiplier = 1.0;

        if (allSlotsHaveMiners(progress)) {
            multiplier += MULT_BONUS_ALL_MINERS;
        }

        return multiplier;
    }

    /**
     * Volt gain multiplier from mining milestones.
     * With single mine, always 1.0 (no mine4 to unlock).
     */
    public double getVoltGainMultiplier(MinePlayerProgress progress) {
        return 1.0;
    }

    /**
     * Checks whether all configured miner slots have a miner for the given player.
     */
    private boolean allSlotsHaveMiners(MinePlayerProgress progress) {
        var slots = mineConfigStore.getMinerSlots();
        if (slots.isEmpty()) return false;

        for (var slot : slots) {
            if (!progress.isSlotAssigned(slot.getSlotIndex())) {
                return false;
            }
        }
        return true;
    }
}
