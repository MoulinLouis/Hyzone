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

    public String getDisplayName() {
        return switch (this) {
            case BAG_CAPACITY -> "Bag Capacity";
            case MOMENTUM -> "Momentum";
            case FORTUNE -> "Fortune";
            case JACKHAMMER -> "Jackhammer";
            case STOMP -> "Stomp";
            case BLAST -> "Blast";
            case HASTE -> "Haste";
        };
    }

    public String getDescription() {
        return switch (this) {
            case BAG_CAPACITY -> "Increases how many blocks your bag can hold.";
            case MOMENTUM -> "Build a combo while mining to deal more damage.";
            case FORTUNE -> "Chance to get bonus drops from mined blocks.";
            case JACKHAMMER -> "Breaks a column of blocks below the one you mine.";
            case STOMP -> "Breaks a layer of blocks around your feet on landing.";
            case BLAST -> "Breaks blocks in a sphere around your target.";
            case HASTE -> "Increases your mining speed permanently.";
        };
    }
}
