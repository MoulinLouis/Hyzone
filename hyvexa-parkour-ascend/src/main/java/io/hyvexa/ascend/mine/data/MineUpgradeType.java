package io.hyvexa.ascend.mine.data;

import io.hyvexa.common.math.BigNumber;

public enum MineUpgradeType {
    MINING_SPEED(100),
    BAG_CAPACITY(50),
    MULTI_BREAK(20),
    AUTO_SELL(1);

    private final int maxLevel;

    MineUpgradeType(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public int getMaxLevel() { return maxLevel; }

    public BigNumber getCost(int currentLevel) {
        return switch (this) {
            case MINING_SPEED -> BigNumber.of(10 * Math.pow(1.15, currentLevel), 0);
            case BAG_CAPACITY -> BigNumber.of(25 * Math.pow(1.2, currentLevel), 0);
            case MULTI_BREAK -> BigNumber.of(100 * Math.pow(1.5, currentLevel), 0);
            case AUTO_SELL -> BigNumber.of(500, 0);
        };
    }

    public double getEffect(int level) {
        return switch (this) {
            case MINING_SPEED -> 1.0 + level * 0.1;
            case BAG_CAPACITY -> 50 + level * 10.0;
            case MULTI_BREAK -> level * 5.0;
            case AUTO_SELL -> level >= 1 ? 1.0 : 0.0;
        };
    }
}
