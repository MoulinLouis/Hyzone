package io.hyvexa.ascend.mine.data;

public enum MineUpgradeType {
    BAG_CAPACITY(50);

    private final int maxLevel;

    MineUpgradeType(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public int getMaxLevel() { return maxLevel; }

    public long getCost(int currentLevel) {
        return switch (this) {
            case BAG_CAPACITY -> Math.round(25 * Math.pow(1.2, currentLevel));
        };
    }

    public double getEffect(int level) {
        return switch (this) {
            case BAG_CAPACITY -> 50 + level * 10.0;
        };
    }
}
