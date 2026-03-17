package io.hyvexa.ascend.mine.data;

public enum MineUpgradeType {
    BAG_CAPACITY(50),
    MOMENTUM(25),
    FORTUNE(25),
    JACKHAMMER(10),
    STOMP(15),
    BLAST(15),
    HASTE(20);

    private final int maxLevel;

    MineUpgradeType(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public int getMaxLevel() { return maxLevel; }

    public long getCost(int currentLevel) {
        return switch (this) {
            case BAG_CAPACITY -> Math.round(25 * Math.pow(1.2, currentLevel));
            case MOMENTUM -> Math.round(50 * Math.pow(1.22, currentLevel));
            case FORTUNE -> Math.round(60 * Math.pow(1.22, currentLevel));
            case JACKHAMMER -> Math.round(150 * Math.pow(1.28, currentLevel));
            case STOMP -> Math.round(200 * Math.pow(1.30, currentLevel));
            case BLAST -> Math.round(250 * Math.pow(1.30, currentLevel));
            case HASTE -> Math.round(40 * Math.pow(1.20, currentLevel));
        };
    }

    public double getEffect(int level) {
        return switch (this) {
            case BAG_CAPACITY -> 50 + level * 10.0;
            case MOMENTUM -> 5 + level * 3.0;       // max combo count
            case FORTUNE -> level * 2.0;              // double drop chance %
            case JACKHAMMER -> (double) level;        // column depth
            case STOMP -> 1 + Math.floor(level / 5.0); // radius
            case BLAST -> 1 + Math.floor(level / 5.0); // radius
            case HASTE -> level * 5.0;                // speed bonus %
        };
    }

    /**
     * Returns the DB column name for this upgrade type.
     */
    public String getColumnName() {
        return switch (this) {
            case BAG_CAPACITY -> "bag_capacity_level";
            case MOMENTUM -> "upgrade_momentum";
            case FORTUNE -> "upgrade_fortune";
            case JACKHAMMER -> "upgrade_jackhammer";
            case STOMP -> "upgrade_stomp";
            case BLAST -> "upgrade_blast";
            case HASTE -> "upgrade_haste";
        };
    }
}
