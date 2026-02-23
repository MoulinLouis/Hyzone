package io.hyvexa.purge.data;

public enum PurgeUpgradeType {

    HP("HP", 1, "HP"),
    AMMO("Ammo", 1, "Max Ammo"),
    SPEED("Speed", 5, "% Speed"),
    LUCK("Luck", 1, "Luck");

    private final String displayName;
    private final int baseValue;
    private final String unit;

    PurgeUpgradeType(String displayName, int baseValue, String unit) {
        this.displayName = displayName;
        this.baseValue = baseValue;
        this.unit = unit;
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
}
