package io.hyvexa.ascend.mine.data;

public enum MinerRarity {
    COMMON("Common", "#aaaaaa"),
    UNCOMMON("Uncommon", "#10b981"),
    RARE("Rare", "#3b82f6"),
    EPIC("Epic", "#a855f7"),
    LEGENDARY("Legendary", "#f59e0b");

    private final String displayName;
    private final String color;

    MinerRarity(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }

    public static MinerRarity fromName(String name) {
        if (name == null) return null;
        for (MinerRarity r : values()) {
            if (r.name().equalsIgnoreCase(name)) return r;
        }
        return null;
    }
}
