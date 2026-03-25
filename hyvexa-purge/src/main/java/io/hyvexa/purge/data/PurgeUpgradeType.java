package io.hyvexa.purge.data;

public enum PurgeUpgradeType {

    HP("HP", 1, "HP", 100),
    AMMO("Ammo", 1, "Max Ammo", 50),
    SPEED("Speed", 5, "% Speed", 50),
    LUCK("Luck", 1, "Luck", 20);

    private final String displayName;
    private final int baseValue;
    private final String unit;
    private final int maxAccumulated;

    PurgeUpgradeType(String displayName, int baseValue, String unit, int maxAccumulated) {
        this.displayName = displayName;
        this.baseValue = baseValue;
        this.unit = unit;
        this.maxAccumulated = maxAccumulated;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getBaseValue() {
        return baseValue;
    }

    public String getUnit() {
        return unit;
    }

    public int getMaxAccumulated() {
        return maxAccumulated;
    }
}
