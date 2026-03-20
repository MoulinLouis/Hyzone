package io.hyvexa.ascend.mine.data;

public enum PickaxeTier {
    WOOD(0, "Wood Pickaxe", "Tool_Pickaxe_Wood", 1),
    STONE(1, "Stone Pickaxe", "Tool_Pickaxe_Stone", 12),
    IRON(2, "Iron Pickaxe", "Tool_Pickaxe_Iron", 34),
    CRYSTAL(3, "Crystal Pickaxe", "Tool_Pickaxe_Crystal", 78),
    VOID(4, "Void Pickaxe", "Tool_Pickaxe_Void", 166),
    PRISMATIC(5, "Prismatic Pickaxe", "Tool_Pickaxe_Prismatic", 342);

    public static final int MAX_ENHANCEMENT = 5;

    private final int tier;
    private final String displayName;
    private final String itemId;
    private final int baseDamage;

    PickaxeTier(int tier, String displayName, String itemId, int baseDamage) {
        this.tier = tier;
        this.displayName = displayName;
        this.itemId = itemId;
        this.baseDamage = baseDamage;
    }

    public int getTier() { return tier; }
    public String getDisplayName() { return displayName; }
    public String getItemId() { return itemId; }
    public int getBaseDamage() { return baseDamage; }

    public String getDisplayName(int enhancement) {
        if (enhancement <= 0) return displayName;
        return displayName + " +" + enhancement;
    }

    public static PickaxeTier fromTier(int tier) {
        for (PickaxeTier pt : values()) {
            if (pt.tier == tier) return pt;
        }
        return WOOD;
    }

    public PickaxeTier next() {
        int nextOrdinal = ordinal() + 1;
        if (nextOrdinal >= values().length) return null;
        return values()[nextOrdinal];
    }
}
