package io.hyvexa.ascend.mine.data;

import java.util.List;

public enum PickaxeTier {
    WOOD(0, "Wood Pickaxe", 1.0, 0, "Tool_Pickaxe_Wood", null),
    STONE(1, "Stone Pickaxe", 1.5, 500, "Tool_Pickaxe_Stone", null),
    IRON(2, "Iron Pickaxe", 2.0, 5_000, "Tool_Pickaxe_Iron", null),
    CRYSTAL(3, "Crystal Pickaxe", 3.0, 25_000, "Tool_Pickaxe_Crystal", "Mine 2 unlocked"),
    VOID(4, "Void Pickaxe", 4.0, 100_000, "Tool_Pickaxe_Void", "Mine 3 unlocked"),
    PRISMATIC(5, "Prismatic Pickaxe", 5.0, 500_000, "Tool_Pickaxe_Prismatic", "All mines unlocked");

    private final int tier;
    private final String displayName;
    private final double speedMultiplier;
    private final long unlockCost;
    private final String itemId;
    private final String requirementDescription;

    PickaxeTier(int tier, String displayName, double speedMultiplier, long unlockCost,
                String itemId, String requirementDescription) {
        this.tier = tier;
        this.displayName = displayName;
        this.speedMultiplier = speedMultiplier;
        this.unlockCost = unlockCost;
        this.itemId = itemId;
        this.requirementDescription = requirementDescription;
    }

    public int getTier() { return tier; }
    public String getDisplayName() { return displayName; }
    public double getSpeedMultiplier() { return speedMultiplier; }
    public long getUnlockCost() { return unlockCost; }
    public String getItemId() { return itemId; }
    public String getRequirementDescription() { return requirementDescription; }

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

    /**
     * Check if the player meets the unlock requirement for this tier.
     * @param unlockedMineIds list of mine IDs the player has unlocked (sorted by display order)
     * @param totalMineCount total number of mines in the system
     */
    public boolean meetsRequirement(List<String> unlockedMineIds, int totalMineCount) {
        return switch (this) {
            case WOOD, STONE, IRON -> true;
            case CRYSTAL -> unlockedMineIds.size() >= 2;
            case VOID -> unlockedMineIds.size() >= 3;
            case PRISMATIC -> totalMineCount > 0 && unlockedMineIds.size() >= totalMineCount;
        };
    }
}
