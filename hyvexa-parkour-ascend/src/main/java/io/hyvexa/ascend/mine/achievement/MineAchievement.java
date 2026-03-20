package io.hyvexa.ascend.mine.achievement;

/**
 * Flat achievement definitions for the mining system.
 * Each achievement has a unique ID, display name, description, and crystal reward.
 */
public enum MineAchievement {

    // Block milestones
    BLOCKS_100("blocks_100", "Novice Miner", "Break 100 blocks", 500),
    BLOCKS_1K("blocks_1k", "Seasoned Miner", "Break 1,000 blocks", 2_500),
    BLOCKS_10K("blocks_10k", "Expert Miner", "Break 10,000 blocks", 10_000),
    BLOCKS_100K("blocks_100k", "Legendary Miner", "Break 100,000 blocks", 50_000),

    // Economy milestones
    CRYSTALS_1K("crystals_1k", "Crystal Collector", "Earn 1,000 total crystals", 500),
    CRYSTALS_10K("crystals_10k", "Crystal Hoarder", "Earn 10,000 total crystals", 2_500),
    CRYSTALS_100K("crystals_100k", "Crystal Magnate", "Earn 100,000 total crystals", 10_000),
    CRYSTALS_1M("crystals_1m", "Crystal Tycoon", "Earn 1,000,000 total crystals", 50_000),

    // Upgrade milestones
    FIRST_EGG("first_egg", "Egg Hunter", "Find your first egg", 500),
    FIRST_LEGENDARY("first_legendary", "Golden Discovery", "Obtain a Legendary miner", 2_500),
    MAX_UPGRADES("max_upgrades", "Completionist", "Max all upgrades", 50_000),

    // Exploration
    EXPLORER("explorer", "Explorer", "Unlock all mines", 10_000);

    private final String id;
    private final String displayName;
    private final String description;
    private final long crystalReward;

    MineAchievement(String id, String displayName, String description, long crystalReward) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.crystalReward = crystalReward;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public long getCrystalReward() { return crystalReward; }

    /**
     * Returns the threshold for counter-based achievements, or -1 if not counter-based.
     */
    public long getThreshold() {
        return switch (this) {
            case BLOCKS_100 -> 100;
            case BLOCKS_1K -> 1_000;
            case BLOCKS_10K -> 10_000;
            case BLOCKS_100K -> 100_000;
            case CRYSTALS_1K -> 1_000;
            case CRYSTALS_10K -> 10_000;
            case CRYSTALS_100K -> 100_000;
            case CRYSTALS_1M -> 1_000_000;
            default -> -1;
        };
    }

    /**
     * Returns the stat type this achievement tracks, or null for event-based achievements.
     */
    public StatType getStatType() {
        return switch (this) {
            case BLOCKS_100, BLOCKS_1K, BLOCKS_10K, BLOCKS_100K -> StatType.TOTAL_BLOCKS_MINED;
            case CRYSTALS_1K, CRYSTALS_10K, CRYSTALS_100K, CRYSTALS_1M -> StatType.TOTAL_CRYSTALS_EARNED;
            default -> null;
        };
    }

    public static MineAchievement fromId(String id) {
        for (MineAchievement a : values()) {
            if (a.id.equals(id)) return a;
        }
        return null;
    }

    public enum StatType {
        TOTAL_BLOCKS_MINED,
        TOTAL_CRYSTALS_EARNED
    }
}
