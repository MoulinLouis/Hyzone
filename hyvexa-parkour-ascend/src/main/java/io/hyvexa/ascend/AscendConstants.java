package io.hyvexa.ascend;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class AscendConstants {

    private AscendConstants() {
    }

    // Math contexts for precision calculations
    private static final MathContext CALC_CTX = new MathContext(30, RoundingMode.HALF_UP);
    private static final MathContext MULTIPLIER_CTX = new MathContext(30, RoundingMode.HALF_UP);

    // Database
    public static final String TABLE_PREFIX = "ascend_";

    // Items - Menu
    public static final String ITEM_DEV_CINDERCLOTH = "Ingredient_Bolt_Cindercloth";
    public static final String ITEM_DEV_STORMSILK = "Ingredient_Bolt_Stormsilk";
    public static final String ITEM_DEV_COTTON = "Ingredient_Bolt_Cotton";

    // Items - In-Run
    public static final String ITEM_RESET = "Ascend_Reset_Block";
    public static final String ITEM_LEAVE = "Ascend_Leave_Block";

    // Economy
    public static final double MANUAL_MULTIPLIER_INCREMENT = 0.1; // +0.1 per manual run
    public static final double RUNNER_MULTIPLIER_INCREMENT = 0.1; // +0.1 per runner completion (×10 inflation)
    public static final int MULTIPLIER_SLOTS = 5;
    public static final int MAP_UNLOCK_REQUIRED_RUNNER_LEVEL = 5; // Runner level required to unlock next map

    // Passive Earnings
    public static final long PASSIVE_OFFLINE_RATE_PERCENT = 25L; // 25% of normal production
    public static final long PASSIVE_MAX_TIME_MS = 24 * 60 * 60 * 1000L; // 24 hours
    public static final long PASSIVE_MIN_TIME_MS = 60 * 1000L; // 1 minute

    // Speed upgrade multipliers by map (indexed by displayOrder 0-4)
    // Higher level maps = faster speed gains per upgrade
    public static final double[] MAP_SPEED_MULTIPLIERS = {
        0.10,  // Map 0 (Rouge)  - +10% per level
        0.15,  // Map 1 (Orange) - +15% per level
        0.20,  // Map 2 (Jaune)  - +20% per level
        0.25,  // Map 3 (Vert)   - +25% per level
        0.30   // Map 4 (Bleu)   - +30% per level
    };

    public static double getMapSpeedMultiplier(int displayOrder) {
        if (displayOrder < 0 || displayOrder >= MAP_SPEED_MULTIPLIERS.length) {
            return MAP_SPEED_MULTIPLIERS[MAP_SPEED_MULTIPLIERS.length - 1];
        }
        return MAP_SPEED_MULTIPLIERS[displayOrder];
    }

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

    // Runner upgrade cost scaling by map level
    // Higher maps have both cost offset (start further in formula) and multiplier (scale up all costs)
    public static final int[] MAP_UPGRADE_OFFSET = {
        0,    // Level 0 (Rouge)  - no offset
        1,    // Level 1 (Orange) - +1 level offset
        2,    // Level 2 (Jaune)  - +2 level offset
        3,    // Level 3 (Vert)   - +3 level offset
        4     // Level 4 (Bleu)   - +4 level offset
    };

    public static final double[] MAP_UPGRADE_MULTIPLIER = {
        1.0,  // Level 0 (Rouge)  - 1.0x cost
        1.4,  // Level 1 (Orange) - 1.4x cost
        1.9,  // Level 2 (Jaune)  - 1.9x cost
        2.6,  // Level 3 (Vert)   - 2.6x cost
        3.5   // Level 4 (Bleu)   - 3.5x cost
    };

    public static int getMapUpgradeOffset(int displayOrder) {
        if (displayOrder < 0 || displayOrder >= MAP_UPGRADE_OFFSET.length) {
            return MAP_UPGRADE_OFFSET[MAP_UPGRADE_OFFSET.length - 1];
        }
        return MAP_UPGRADE_OFFSET[displayOrder];
    }

    public static double getMapUpgradeMultiplier(int displayOrder) {
        if (displayOrder < 0 || displayOrder >= MAP_UPGRADE_MULTIPLIER.length) {
            return MAP_UPGRADE_MULTIPLIER[MAP_UPGRADE_MULTIPLIER.length - 1];
        }
        return MAP_UPGRADE_MULTIPLIER[displayOrder];
    }

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

    /**
     * Returns the NPC entity type for a runner based on its evolution level (stars).
     * Visual progression: Seedling -> Sapling -> Sproutling -> Pink Sapling -> Razorleaf -> Rootling (adulte)
     */
    public static String getRunnerEntityType(int stars) {
        return switch (stars) {
            case 0 -> "Kweebec_Seedling";      // Petit truc cool
            case 1 -> "Kweebec_Sapling";       // Bonhomme vert standard
            case 2 -> "Kweebec_Sproutling";    // Petit bonhomme vert
            case 3 -> "Kweebec_Sapling_Pink";  // Petite fille avec fleur rose
            case 4 -> "Kweebec_Razorleaf";     // Soldat avec casque + lance
            case 5 -> "Kweebec_Rootling";      // Adulte (le plus grand)
            default -> "Kweebec_Sapling";      // Fallback
        };
    }

    /**
     * Get the multiplier increment for a runner based on its stars (evolution level).
     * Base formula: 0.1 × 2^stars
     * With Evolution Power: 0.1 × (2 + evolutionBonus)^stars
     * @param stars Runner's evolution level (0-5)
     * @return Multiplier increment per completion
     */
    public static BigDecimal getRunnerMultiplierIncrement(int stars) {
        return getRunnerMultiplierIncrement(stars, 0.0);
    }

    /**
     * Get the multiplier increment for a runner with Evolution Power bonus.
     * Formula: 0.1 × (2 + evolutionBonus)^stars
     * At Summit Evolution Power level 10: base becomes 4, so 4^5 = 1024 instead of 32
     * @param stars Runner's evolution level (0-5)
     * @param evolutionBonus Bonus from Summit Evolution Power (0.20 per level)
     * @return Multiplier increment per completion
     */
    public static BigDecimal getRunnerMultiplierIncrement(int stars, double evolutionBonus) {
        BigDecimal baseIncrement = new BigDecimal("0.1");  // RUNNER_MULTIPLIER_INCREMENT

        // Evolution base: 2 + evolutionBonus
        // At Summit level 10 with +0.20/level: 2 + 2.0 = 4
        BigDecimal evolutionBase = new BigDecimal("2").add(
            BigDecimal.valueOf(evolutionBonus), MULTIPLIER_CTX);

        // (evolutionBase)^stars
        BigDecimal starPower;
        int safeStars = Math.max(0, stars);
        if (safeStars == 0) {
            starPower = BigDecimal.ONE;
        } else {
            // Use double for pow since BigDecimal.pow only works with integers
            starPower = BigDecimal.valueOf(Math.pow(evolutionBase.doubleValue(), safeStars));
        }

        return baseIncrement.multiply(starPower, MULTIPLIER_CTX)
                           .setScale(20, RoundingMode.HALF_UP);
    }

    // Max speed level per evolution cycle (used for total level calculation)
    public static final int MAX_SPEED_LEVEL = 20;

    /**
     * Calculate runner speed upgrade cost.
     * Formula: baseCost(totalLevel + mapOffset) × mapMultiplier
     * Where totalLevel = stars × MAX_SPEED_LEVEL + speedLevel (accumulates across evolutions)
     * and baseCost(L) = 5 × 2^L + L × 10
     *
     * This gives a smooth ~2x growth per level with no artificial jumps.
     */
    public static BigDecimal getRunnerUpgradeCost(int speedLevel, int mapDisplayOrder, int stars) {
        // Get map-specific scaling parameters
        int offset = getMapUpgradeOffset(mapDisplayOrder);
        BigDecimal mapMultiplier = BigDecimal.valueOf(getMapUpgradeMultiplier(mapDisplayOrder));

        // Calculate total levels bought across all evolutions
        // Each evolution represents MAX_SPEED_LEVEL levels already purchased
        int totalLevelsBought = stars * MAX_SPEED_LEVEL + speedLevel;

        // Apply offset to total level for base cost calculation
        int effectiveLevel = totalLevelsBought + offset;

        // Base formula: 5 × 2^level + level × 10
        // Smooth ~2x growth per level, no artificial boosts or jumps
        BigDecimal five = new BigDecimal("5");
        BigDecimal two = new BigDecimal("2");
        BigDecimal ten = new BigDecimal("10");

        BigDecimal exponentialPart = two.pow(effectiveLevel, CALC_CTX);
        BigDecimal linearPart = BigDecimal.valueOf(effectiveLevel).multiply(ten, CALC_CTX);

        BigDecimal baseCost = five.multiply(exponentialPart, CALC_CTX).add(linearPart, CALC_CTX);

        // Apply map multiplier
        baseCost = baseCost.multiply(mapMultiplier, CALC_CTX);

        return baseCost.setScale(0, RoundingMode.CEILING);
    }

    // Runner (internal tick system)
    public static final long RUNNER_TICK_INTERVAL_MS = 16L; // ~60 ticks/second for smooth movement
    public static final long RUNNER_REFRESH_INTERVAL_MS = 1000L;
    public static final long RUNNER_INVALID_RECOVERY_MS = 3000L; // Force respawn after entity invalid for 3s
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

    // Elevation is a direct multiplier (elevation value = multiplier value)
    // Cost formula: BASE_COST * COST_GROWTH^currentElevation
    public static final long ELEVATION_BASE_COST = 30000L; // ×6 inflation from 5000
    public static final double ELEVATION_COST_GROWTH = 1.15;  // +15% cost per elevation

    /**
     * Calculate the cost to reach the next elevation level.
     * Cost grows exponentially: baseCost * 1.15^currentLevel
     */
    public static BigDecimal getElevationLevelUpCost(int currentLevel) {
        return getElevationLevelUpCost(currentLevel, BigDecimal.ONE);
    }

    /**
     * Calculate the cost to reach the next elevation level with a cost multiplier.
     * @param currentLevel The player's current elevation level
     * @param costMultiplier Cost modifier (1.0 = full cost, 0.8 = 20% discount)
     */
    public static BigDecimal getElevationLevelUpCost(int currentLevel, BigDecimal costMultiplier) {
        // Cap level to prevent overflow
        int cappedLevel = Math.min(Math.max(0, currentLevel), 1000);

        // Pure BigDecimal calculation
        BigDecimal base = BigDecimal.valueOf(ELEVATION_BASE_COST);
        BigDecimal growth = new BigDecimal("1.15"); // ELEVATION_COST_GROWTH

        // Calculate growth^level using BigDecimal (no double intermediate)
        BigDecimal growthPower = growth.pow(cappedLevel, CALC_CTX);
        BigDecimal cost = base.multiply(growthPower, CALC_CTX);

        // Apply cost multiplier (skill discount) - NO double conversion
        if (costMultiplier.compareTo(BigDecimal.ONE) != 0) {
            cost = cost.multiply(costMultiplier, CALC_CTX);
        }

        return cost.setScale(2, RoundingMode.CEILING);
    }

    /**
     * Calculate how many levels can be purchased with given coins at current level.
     * Returns the number of levels affordable and the total cost.
     */
    public static ElevationPurchaseResult calculateElevationPurchase(int currentLevel, BigDecimal availableCoins) {
        return calculateElevationPurchase(currentLevel, availableCoins, BigDecimal.ONE);
    }

    /**
     * Calculate how many levels can be purchased with given coins and cost multiplier.
     * @param currentLevel The player's current elevation level
     * @param availableCoins Coins available to spend
     * @param costMultiplier Cost modifier (1.0 = full cost, 0.8 = 20% discount)
     */
    public static ElevationPurchaseResult calculateElevationPurchase(int currentLevel, BigDecimal availableCoins, BigDecimal costMultiplier) {
        int levelsAffordable = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        int level = currentLevel;

        while (true) {
            BigDecimal nextCost = getElevationLevelUpCost(level, costMultiplier);
            BigDecimal newTotal = totalCost.add(nextCost, CALC_CTX);

            // Pure BigDecimal comparison (no double)
            if (newTotal.compareTo(availableCoins) > 0) {
                break;
            }

            totalCost = newTotal;
            levelsAffordable++;
            level++;
        }

        return new ElevationPurchaseResult(levelsAffordable, totalCost);
    }

    public static class ElevationPurchaseResult {
        public final int levels;
        public final BigDecimal cost;

        public ElevationPurchaseResult(int levels, BigDecimal cost) {
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
        COIN_FLOW("Coin Flow", 0.20),       // ×1.20^level multiplicative coin earnings
        RUNNER_SPEED("Runner Speed", 0.15), // +15% runner completion speed per level (additive)
        EVOLUTION_POWER("Evolution Power", 0.20); // +0.20 evolution base per level

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

        /**
         * Get the bonus for a given level.
         * - COIN_FLOW: Returns the multiplicative factor (1.20^level), e.g., level 5 → 2.49
         * - RUNNER_SPEED: Returns additive bonus (0.15 × level)
         * - EVOLUTION_POWER: Returns evolution base bonus (0.20 × level)
         */
        public double getBonusForLevel(int level) {
            int safeLevel = Math.max(0, level);
            if (this == COIN_FLOW) {
                // Multiplicative: 1.20^level
                // Returns the multiplier directly (e.g., 1.0 at level 0, 2.49 at level 5)
                return Math.pow(1.0 + bonusPerLevel, safeLevel);
            }
            // Additive: bonusPerLevel × level
            return bonusPerLevel * safeLevel;
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

    public static int calculateSummitLevel(BigDecimal coinsSpent) {
        BigDecimal firstThreshold = BigDecimal.valueOf(SUMMIT_LEVEL_THRESHOLDS[0]);
        if (coinsSpent.compareTo(firstThreshold) < 0) {
            return 0;
        }

        BigDecimal cumulative = BigDecimal.ZERO;
        for (int i = 0; i < SUMMIT_LEVEL_THRESHOLDS.length; i++) {
            BigDecimal threshold = BigDecimal.valueOf(SUMMIT_LEVEL_THRESHOLDS[i]);
            cumulative = cumulative.add(threshold, CALC_CTX);

            // Pure BigDecimal comparison
            if (coinsSpent.compareTo(cumulative) < 0) {
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
