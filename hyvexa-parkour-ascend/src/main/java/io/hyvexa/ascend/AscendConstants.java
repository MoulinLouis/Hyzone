package io.hyvexa.ascend;

public final class AscendConstants {

    private AscendConstants() {
    }

    // Database
    public static final String TABLE_PREFIX = "ascend_";

    // Items
    public static final String ITEM_DEV_CINDERCLOTH = "Ingredient_Bolt_Cindercloth";
    public static final String ITEM_DEV_STORMSILK = "Ingredient_Bolt_Stormsilk";
    public static final String ITEM_DEV_COTTON = "Ingredient_Bolt_Cotton";

    // Economy
    public static final double SPEED_UPGRADE_MULTIPLIER = 0.15; // +15% speed per level
    public static final double MANUAL_MULTIPLIER_INCREMENT = 0.1; // +0.1 per manual run
    public static final double RUNNER_MULTIPLIER_INCREMENT = 0.01; // +0.01 per runner completion
    public static final int MULTIPLIER_SLOTS = 5;
    public static final int MAP_UNLOCK_REQUIRED_RUNNER_LEVEL = 3; // Runner level required to unlock next map

    // Map Level Defaults (indexed by displayOrder 0-4)
    // Base run times: faster maps = faster multiplier growth
    public static final long[] MAP_BASE_RUN_TIMES_MS = {
        5000L,    // Level 0 (Rouge)  - 5 sec
        10000L,   // Level 1 (Orange) - 10 sec
        16000L,   // Level 2 (Jaune)  - 16 sec
        26000L,   // Level 3 (Vert)   - 26 sec
        42000L    // Level 4 (Bleu)   - 42 sec
    };

    // Base coin rewards per manual completion (before multiplier)
    // Actual reward = baseReward * totalMultiplier
    public static final long[] MAP_BASE_REWARDS = {
        1L,       // Level 0 (Rouge)  - 1 coin/run base
        5L,       // Level 1 (Orange) - 5 coins/run base
        25L,      // Level 2 (Jaune)  - 25 coins/run base
        100L,     // Level 3 (Vert)   - 100 coins/run base
        500L      // Level 4 (Bleu)   - 500 coins/run base
    };

    // Map unlock prices: first map free, then increasing
    public static final long[] MAP_UNLOCK_PRICES = {
        0L,       // Level 0 (Rouge)  - Gratuit (map de départ)
        100L,     // Level 1 (Orange)
        500L,     // Level 2 (Jaune)
        2500L,    // Level 3 (Vert)
        10000L    // Level 4 (Bleu)
    };

    // Runner purchase prices
    public static final long[] MAP_RUNNER_PRICES = {
        50L,      // Level 0 (Rouge)
        200L,     // Level 1 (Orange)
        1000L,    // Level 2 (Jaune)
        5000L,    // Level 3 (Vert)
        20000L    // Level 4 (Bleu)
    };

    public static long getMapBaseRunTimeMs(int displayOrder) {
        if (displayOrder < 0 || displayOrder >= MAP_BASE_RUN_TIMES_MS.length) {
            return MAP_BASE_RUN_TIMES_MS[MAP_BASE_RUN_TIMES_MS.length - 1];
        }
        return MAP_BASE_RUN_TIMES_MS[displayOrder];
    }

    public static long getMapUnlockPrice(int displayOrder) {
        if (displayOrder < 0 || displayOrder >= MAP_UNLOCK_PRICES.length) {
            return MAP_UNLOCK_PRICES[MAP_UNLOCK_PRICES.length - 1];
        }
        return MAP_UNLOCK_PRICES[displayOrder];
    }

    public static long getMapRunnerPrice(int displayOrder) {
        if (displayOrder < 0 || displayOrder >= MAP_RUNNER_PRICES.length) {
            return MAP_RUNNER_PRICES[MAP_RUNNER_PRICES.length - 1];
        }
        return MAP_RUNNER_PRICES[displayOrder];
    }

    public static long getMapBaseReward(int displayOrder) {
        if (displayOrder < 0 || displayOrder >= MAP_BASE_REWARDS.length) {
            return MAP_BASE_REWARDS[MAP_BASE_REWARDS.length - 1];
        }
        return MAP_BASE_REWARDS[displayOrder];
    }

    // Runner star evolution
    public static final int MAX_ROBOT_STARS = 5;
    // Only Kweebec_Sapling and Kweebec_Sapling_Orange are valid entity types
    public static final String RUNNER_ENTITY_BASE = "Kweebec_Sapling";
    public static final String RUNNER_ENTITY_EVOLVED = "Kweebec_Sapling_Orange";

    public static String getRunnerEntityType(int stars) {
        // Alternate between base and evolved appearance
        // 0, 2, 4 stars = base (green), 1, 3, 5 stars = evolved (orange)
        return (stars % 2 == 0) ? RUNNER_ENTITY_BASE : RUNNER_ENTITY_EVOLVED;
    }

    public static double getRunnerMultiplierIncrement(int stars) {
        // Base increment doubles with each star: 0.01, 0.02, 0.04, 0.08, 0.16, 0.32
        return RUNNER_MULTIPLIER_INCREMENT * Math.pow(2, Math.max(0, stars));
    }

    // Runner (internal tick system)
    public static final long RUNNER_TICK_INTERVAL_MS = 16L; // ~60 ticks/second for smooth movement
    public static final long RUNNER_REFRESH_INTERVAL_MS = 1000L;
    public static final double RUNNER_BASE_SPEED = 5.0;
    public static final double RUNNER_JUMP_FORCE = 8.0;
    public static final double WAYPOINT_REACH_DISTANCE = 1.5;

    // Runner jump animation
    public static final double RUNNER_DEFAULT_JUMP_HEIGHT = 1.5;      // Minimum arc height in blocks
    public static final double RUNNER_JUMP_CLEARANCE = 1.0;           // Extra height above landing point
    public static final double RUNNER_JUMP_DISTANCE_FACTOR = 0.2;     // Height increase per block beyond threshold
    public static final double RUNNER_JUMP_DISTANCE_THRESHOLD = 3.0;  // Horizontal distance before scaling
    // Auto-detection thresholds
    public static final double RUNNER_JUMP_AUTO_UP_THRESHOLD = 0.5;   // Min Y increase to trigger jump
    public static final double RUNNER_JUMP_AUTO_DOWN_THRESHOLD = 1.0; // Min Y decrease to trigger jump (gaps/falls)
    public static final double RUNNER_JUMP_AUTO_HORIZ_THRESHOLD = 2.0; // Min horizontal distance to trigger jump (gaps)

    // Timing
    public static final long SAVE_DEBOUNCE_MS = 5000L;

    // ========================================
    // Elevation System (First Prestige)
    // ========================================

    // Elevation uses a level-based system with exponential costs and diminishing returns
    // Formula: cost(level) = BASE_COST * COST_GROWTH^level
    // Formula: multiplier(level) = 1 + MULT_COEFFICIENT * level^MULT_EXPONENT
    public static final long ELEVATION_BASE_COST = 1000L;
    public static final double ELEVATION_COST_GROWTH = 1.08;  // +8% cost per level
    public static final double ELEVATION_MULT_COEFFICIENT = 0.1;  // Base multiplier gain
    public static final double ELEVATION_MULT_EXPONENT = 0.65;  // Diminishing returns exponent

    /**
     * Calculate the cost to reach the next elevation level.
     * Cost grows exponentially: baseCost * 1.08^currentLevel
     */
    public static long getElevationLevelUpCost(int currentLevel) {
        return getElevationLevelUpCost(currentLevel, 1.0);
    }

    /**
     * Calculate the cost to reach the next elevation level with a cost multiplier.
     * @param currentLevel The player's current elevation level
     * @param costMultiplier Cost modifier (1.0 = full cost, 0.8 = 20% discount)
     */
    public static long getElevationLevelUpCost(int currentLevel, double costMultiplier) {
        double baseCost = ELEVATION_BASE_COST * Math.pow(ELEVATION_COST_GROWTH, Math.max(0, currentLevel));
        return Math.round(baseCost * Math.max(0.1, costMultiplier));
    }

    /**
     * Calculate the actual multiplier value for a given elevation level.
     * Multiplier = 1 + 0.1 * level^0.65 (diminishing returns)
     */
    public static double calculateElevationMultiplier(int level) {
        if (level <= 0) {
            return 1.0;
        }
        return 1.0 + ELEVATION_MULT_COEFFICIENT * Math.pow(level, ELEVATION_MULT_EXPONENT);
    }

    /**
     * Calculate how many levels can be purchased with given coins at current level.
     * Returns the number of levels affordable and the total cost.
     */
    public static ElevationPurchaseResult calculateElevationPurchase(int currentLevel, long availableCoins) {
        return calculateElevationPurchase(currentLevel, availableCoins, 1.0);
    }

    /**
     * Calculate how many levels can be purchased with given coins and cost multiplier.
     * @param currentLevel The player's current elevation level
     * @param availableCoins Coins available to spend
     * @param costMultiplier Cost modifier (1.0 = full cost, 0.8 = 20% discount)
     */
    public static ElevationPurchaseResult calculateElevationPurchase(int currentLevel, long availableCoins, double costMultiplier) {
        int levelsAffordable = 0;
        long totalCost = 0;
        int level = currentLevel;

        while (true) {
            long nextCost = getElevationLevelUpCost(level, costMultiplier);
            if (totalCost + nextCost > availableCoins) {
                break;
            }
            totalCost += nextCost;
            levelsAffordable++;
            level++;
        }

        return new ElevationPurchaseResult(levelsAffordable, totalCost);
    }

    public static class ElevationPurchaseResult {
        public final int levels;
        public final long cost;

        public ElevationPurchaseResult(int levels, long cost) {
            this.levels = levels;
            this.cost = cost;
        }
    }

    // ========================================
    // HUD Visual Effects
    // ========================================

    // Multiplier flash colors - lighter versions of each multiplier color
    // Single-cycle flash (200ms): flash → restore on next HUD update
    // Format: [original, lighter] for each slot
    public static final String[][] MULTIPLIER_COLORS = {
        {"#7c3aed", "#b794f6"},  // Purple -> Light purple (Rouge/Red slot)
        {"#3b82f6", "#93c5fd"},  // Blue -> Light blue (Orange slot)
        {"#06b6d4", "#67e8f9"},  // Cyan -> Light cyan (Jaune/Yellow slot)
        {"#f59e0b", "#fbbf24"},  // Amber -> Light amber (Vert/Green slot)
        {"#ef4444", "#fca5a5"}   // Red -> Light red (Bleu/Blue slot)
    };

    // ========================================
    // Summit System (Middle Prestige)
    // ========================================

    public enum SummitCategory {
        COIN_FLOW("Coin Flow", 0.20),      // +20% base coin earnings per level
        RUNNER_SPEED("Runner Speed", 0.15), // +15% runner completion speed per level
        MANUAL_MASTERY("Manual Mastery", 0.25); // +25% manual run multiplier per level

        private final String displayName;
        private final double bonusPerLevel;

        SummitCategory(String displayName, double bonusPerLevel) {
            this.displayName = displayName;
            this.bonusPerLevel = bonusPerLevel;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getBonusPerLevel() {
            return bonusPerLevel;
        }

        public double getBonusForLevel(int level) {
            return bonusPerLevel * Math.max(0, level);
        }
    }

    // Summit level thresholds (coins required for each level)
    // Level calculation: find highest level where cumulative <= coins spent
    public static final long[] SUMMIT_LEVEL_THRESHOLDS = {
        10_000L,        // Level 1
        50_000L,        // Level 2 (cumulative: 60K)
        200_000L,       // Level 3 (cumulative: 260K)
        1_000_000L,     // Level 4 (cumulative: 1.26M)
        5_000_000L,     // Level 5 (cumulative: 6.26M)
        20_000_000L,    // Level 6 (cumulative: 26.26M)
        80_000_000L,    // Level 7 (cumulative: 106.26M)
        300_000_000L,   // Level 8 (cumulative: 406.26M)
        1_000_000_000L, // Level 9 (cumulative: 1.406B)
        5_000_000_000L  // Level 10 (cumulative: 6.406B)
    };

    public static final int SUMMIT_MAX_LEVEL = SUMMIT_LEVEL_THRESHOLDS.length;
    public static final long SUMMIT_MIN_COINS = SUMMIT_LEVEL_THRESHOLDS[0];

    public static int calculateSummitLevel(long coinsSpent) {
        if (coinsSpent < SUMMIT_LEVEL_THRESHOLDS[0]) {
            return 0;
        }
        long cumulative = 0;
        for (int i = 0; i < SUMMIT_LEVEL_THRESHOLDS.length; i++) {
            cumulative += SUMMIT_LEVEL_THRESHOLDS[i];
            if (coinsSpent < cumulative) {
                return i;
            }
        }
        return SUMMIT_LEVEL_THRESHOLDS.length;
    }

    public static long getCoinsForSummitLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        long cumulative = 0;
        for (int i = 0; i < Math.min(level, SUMMIT_LEVEL_THRESHOLDS.length); i++) {
            cumulative += SUMMIT_LEVEL_THRESHOLDS[i];
        }
        return cumulative;
    }

    public static long getCoinsForNextSummitLevel(int currentLevel) {
        if (currentLevel >= SUMMIT_MAX_LEVEL) {
            return Long.MAX_VALUE;
        }
        return getCoinsForSummitLevel(currentLevel + 1);
    }

    // ========================================
    // Ascension System (Ultimate Prestige)
    // ========================================

    public static final long ASCENSION_COIN_THRESHOLD = 1_000_000_000_000L; // 1 trillion coins

    public enum SkillTreeNode {
        // Coin Path (5 nodes)
        COIN_T1_STARTING_COINS("Starting Coins", "Start with 1,000 coins after Ascension", SkillTreePath.COIN, 1, null),
        COIN_T2_BASE_REWARD("Base Reward +25%", "+25% base coin rewards", SkillTreePath.COIN, 2, "COIN_T1_STARTING_COINS"),
        COIN_T3_ELEVATION_COST("Elevation Cost -20%", "Elevate at 800 coins instead of 1,000", SkillTreePath.COIN, 3, "COIN_T2_BASE_REWARD"),
        COIN_T4_SUMMIT_COST("Summit Cost -15%", "Summit category upgrades cost 15% less", SkillTreePath.COIN, 4, "COIN_T3_ELEVATION_COST"),
        COIN_T5_AUTO_ELEVATION("Auto Elevation", "Automatically elevate when threshold reached", SkillTreePath.COIN, 5, "COIN_T4_SUMMIT_COST"),

        // Speed Path (5 nodes)
        SPEED_T1_BASE_SPEED("Base Speed +10%", "Runners move 10% faster baseline", SkillTreePath.SPEED, 1, null),
        SPEED_T2_MAX_LEVEL("Max Level +5", "Speed upgrades can reach level 25", SkillTreePath.SPEED, 2, "SPEED_T1_BASE_SPEED"),
        SPEED_T3_EVOLUTION_COST("Evolution Cost -50%", "Runner evolution costs 50% less", SkillTreePath.SPEED, 3, "SPEED_T2_MAX_LEVEL"),
        SPEED_T4_DOUBLE_LAP("Double Lap", "Runners complete 2 laps per cycle", SkillTreePath.SPEED, 4, "SPEED_T3_EVOLUTION_COST"),
        SPEED_T5_INSTANT_EVOLVE("Instant Evolution", "Evolution doesn't reset speed level", SkillTreePath.SPEED, 5, "SPEED_T4_DOUBLE_LAP"),

        // Manual Path (5 nodes)
        MANUAL_T1_MULTIPLIER("Manual +50%", "+50% manual run multiplier", SkillTreePath.MANUAL, 1, null),
        MANUAL_T2_CHAIN_BONUS("Chain Bonus", "+10% per consecutive run (max +100%)", SkillTreePath.MANUAL, 2, "MANUAL_T1_MULTIPLIER"),
        MANUAL_T3_SESSION_BONUS("Session Bonus", "First run of session: 3x multiplier", SkillTreePath.MANUAL, 3, "MANUAL_T2_CHAIN_BONUS"),
        MANUAL_T4_RUNNER_BOOST("Runner Boost", "Manual runs boost runner speed temporarily", SkillTreePath.MANUAL, 4, "MANUAL_T3_SESSION_BONUS"),
        MANUAL_T5_PERSONAL_BEST("Personal Best", "Bonus for beating personal best time", SkillTreePath.MANUAL, 5, "MANUAL_T4_RUNNER_BOOST"),

        // Hybrid Nodes (requires points in multiple paths)
        HYBRID_OFFLINE_EARNINGS("Offline Earnings", "Earn coins while offline (capped)", SkillTreePath.HYBRID, 1, null),
        HYBRID_SUMMIT_PERSIST("Summit Persistence", "Summit levels partially persist on Ascension", SkillTreePath.HYBRID, 2, null),

        // Ultimate Node
        ULTIMATE_GLOBAL_BOOST("Global Boost", "+100% to ALL systems", SkillTreePath.ULTIMATE, 1, null);

        private final String name;
        private final String description;
        private final SkillTreePath path;
        private final int tier;
        private final String prerequisiteId;

        SkillTreeNode(String name, String description, SkillTreePath path, int tier, String prerequisiteId) {
            this.name = name;
            this.description = description;
            this.path = path;
            this.tier = tier;
            this.prerequisiteId = prerequisiteId;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public SkillTreePath getPath() {
            return path;
        }

        public int getTier() {
            return tier;
        }

        public String getPrerequisiteId() {
            return prerequisiteId;
        }

        public SkillTreeNode getPrerequisite() {
            if (prerequisiteId == null) {
                return null;
            }
            try {
                return SkillTreeNode.valueOf(prerequisiteId);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public enum SkillTreePath {
        COIN, SPEED, MANUAL, HYBRID, ULTIMATE
    }

    // Hybrid node requirements: points in specific paths
    public static final int HYBRID_PATH_REQUIREMENT = 3; // 3 points in each required path
    public static final int ULTIMATE_TOTAL_REQUIREMENT = 12; // 12 total points to unlock ultimate

    // ========================================
    // Achievement System
    // ========================================

    public enum AchievementType {
        // Milestones
        FIRST_STEPS("First Steps", "Complete first manual run", "Beginner"),
        COIN_HOARDER("Coin Hoarder", "Earn 100K coins total", "Collector"),
        MILLIONAIRE("Millionaire", "Earn 1M coins total", "Millionaire"),
        DEDICATED("Dedicated", "Complete 100 manual runs", "Dedicated"),
        MARATHON("Marathon", "Complete 1000 manual runs", "Marathoner"),

        // Runners
        FIRST_ROBOT("First Robot", "Buy your first runner", "Automator"),
        ARMY("Army", "Have 5+ active runners", "Commander"),
        EVOLVED("Evolved", "Evolve a runner to 1+ stars", "Evolver"),

        // Prestige
        SUMMIT_SEEKER("Summit Seeker", "Complete first Summit", "Summiter"),
        SUMMIT_MASTER("Summit Master", "Reach Summit Lv.10 in any category", "Summit Master"),
        ASCENDED("Ascended", "Complete first Ascension", "Ascended"),

        // Challenge
        PERFECTIONIST("Perfectionist", "Max a runner (5★ Lv.20)", "Perfectionist");

        private final String name;
        private final String description;
        private final String title;

        AchievementType(String name, String description, String title) {
            this.name = name;
            this.description = description;
            this.title = title;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getTitle() {
            return title;
        }
    }

    // Achievement thresholds
    public static final long ACHIEVEMENT_COINS_100K = 100_000L;
    public static final long ACHIEVEMENT_COINS_1M = 1_000_000L;
    public static final int ACHIEVEMENT_MANUAL_RUNS_100 = 100;
    public static final int ACHIEVEMENT_MANUAL_RUNS_1000 = 1000;
    public static final int ACHIEVEMENT_RUNNER_COUNT = 5;
    public static final int ACHIEVEMENT_SUMMIT_MAX_LEVEL = 10;
    public static final int ACHIEVEMENT_RUNNER_MAX_STARS = 5;
    public static final int ACHIEVEMENT_RUNNER_MAX_SPEED = 20;
}
