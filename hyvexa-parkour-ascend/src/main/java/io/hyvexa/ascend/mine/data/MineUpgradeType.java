package io.hyvexa.ascend.mine.data;

public enum MineUpgradeType {
    MINING_SPEED(100),
    BAG_CAPACITY(50),
    MULTI_BREAK(20);

    private final int maxLevel;

    MineUpgradeType(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public int getMaxLevel() { return maxLevel; }

    public long getCost(int currentLevel) {
        return switch (this) {
            case MINING_SPEED -> Math.round(10 * Math.pow(1.15, currentLevel));
            case BAG_CAPACITY -> Math.round(25 * Math.pow(1.2, currentLevel));
            case MULTI_BREAK -> Math.round(100 * Math.pow(1.5, currentLevel));
        };
    }

    public double getEffect(int level) {
        return switch (this) {
            case MINING_SPEED -> 1.0 + level * 0.1;
            case BAG_CAPACITY -> 50 + level * 10.0;
            case MULTI_BREAK -> level * 5.0;
        };
    }
}
