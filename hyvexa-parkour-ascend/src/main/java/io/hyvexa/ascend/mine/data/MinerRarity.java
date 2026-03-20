package io.hyvexa.ascend.mine.data;

public enum MinerRarity {
    COMMON("Common", "#aaaaaa", "Kweebec_Seedling"),
    UNCOMMON("Uncommon", "#10b981", "Kweebec_Sproutling"),
    RARE("Rare", "#3b82f6", "Kweebec_Sapling_Pink"),
    EPIC("Epic", "#a855f7", "Kweebec_Razorleaf"),
    LEGENDARY("Legendary", "#f59e0b", "Kweebec_Rootling");

    private final String displayName;
    private final String color;
    private final String entityType;

    MinerRarity(String displayName, String color, String entityType) {
        this.displayName = displayName;
        this.color = color;
        this.entityType = entityType;
    }

    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }
    public String getEntityType() { return entityType; }

    public static MinerRarity fromName(String name) {
        if (name == null) return null;
        for (MinerRarity r : values()) {
            if (r.name().equalsIgnoreCase(name)) return r;
        }
        return null;
    }
}
