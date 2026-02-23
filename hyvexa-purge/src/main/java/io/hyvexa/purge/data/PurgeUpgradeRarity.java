package io.hyvexa.purge.data;

public enum PurgeUpgradeRarity {

    COMMON("Common", "#9ca3af", 1),
    UNCOMMON("Uncommon", "#22c55e", 2),
    RARE("Rare", "#3b82f6", 3),
    EPIC("Epic", "#a855f7", 4),
    LEGENDARY("Legendary", "#eab308", 5);

    private final String displayName;
    private final String color;
    private final int multiplier;

    PurgeUpgradeRarity(String displayName, String color, int multiplier) {
        this.displayName = displayName;
        this.color = color;
        this.multiplier = multiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public int getMultiplier() {
        return multiplier;
    }
}
