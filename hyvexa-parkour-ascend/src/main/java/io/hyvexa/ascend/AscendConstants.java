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
    public static final String ITEM_DEV_SHADOWEAVE = "Ingredient_Bolt_Shadoweave";

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
    // Uniform speed gain: all maps use +10% per level
    public static final double[] MAP_SPEED_MULTIPLIERS = {
        0.10,  // Map 0 (Rouge)  - +10% per level
        0.10,  // Map 1 (Orange) - +10% per level
        0.10,  // Map 2 (Jaune)  - +10% per level
        0.10,  // Map 3 (Vert)   - +10% per level
        0.10   // Map 4 (Bleu)   - +10% per level
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

    // Early-game unlock pacing: decaying boost for levels 0-9 on maps 2+
    // Creates more time between map unlocks without affecting late-game progression
    // Boost decays linearly: level 0 = full boost, level 9 = 10% of boost, level 10+ = no boost
    public static final int EARLY_LEVEL_BOOST_THRESHOLD = 10;
    public static final double[] MAP_EARLY_LEVEL_BOOST = {
        1.0,  // Map 0 (Rouge)  - no boost (starting map)
        1.5,  // Map 1 (Orange) - 1.5x max boost at level 0
        2.0,  // Map 2 (Jaune)  - 2.0x max boost at level 0
        2.5,  // Map 3 (Vert)   - 2.5x max boost at level 0
        3.0   // Map 4 (Bleu)   - 3.0x max boost at level 0
    };

    public static double getMapEarlyLevelBoost(int displayOrder) {
        if (displayOrder < 0 || displayOrder >= MAP_EARLY_LEVEL_BOOST.length) {
            return MAP_EARLY_LEVEL_BOOST[MAP_EARLY_LEVEL_BOOST.length - 1];
        }
        return MAP_EARLY_LEVEL_BOOST[displayOrder];
    }

    /**
     * Calculate the early-level boost multiplier for a given speed level and map.
     * Boost decays linearly from max at level 0 to 1.0 at EARLY_LEVEL_BOOST_THRESHOLD.
     * Only applies during the first evolution cycle (0 stars).
     */
    public static double calculateEarlyLevelBoost(int speedLevel, int mapDisplayOrder, int stars) {
        // Only apply boost during first evolution cycle
        if (stars > 0 || speedLevel >= EARLY_LEVEL_BOOST_THRESHOLD) {
            return 1.0;
        }
        double maxBoost = getMapEarlyLevelBoost(mapDisplayOrder);
        if (maxBoost <= 1.0) {
            return 1.0;
        }
        // Linear decay: factor goes from 1.0 at level 0 to 0.0 at threshold
        double decayFactor = (double) (EARLY_LEVEL_BOOST_THRESHOLD - speedLevel) / EARLY_LEVEL_BOOST_THRESHOLD;
        return 1.0 + (maxBoost - 1.0) * decayFactor;
    }

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
     * Get the multiplier increment for a runner (no Summit bonuses).
     * Base: 0.1 per completion
     * @param stars Evolution level
     * @return Multiplier increment per completion (base 0.1)
     */
    public static BigDecimal getRunnerMultiplierIncrement(int stars) {
        return getRunnerMultiplierIncrement(stars, 1.0, 3.0);
    }

    /**
     * Get the multiplier increment for a runner with Summit bonuses.
     * Base: 0.1 per completion, raised by Evolution Power per star.
     * Formula: base × evolutionPower^stars × multiplierGainBonus
     * Example with evolutionPower=3: 0★=0.1, 1★=0.3, 2★=0.9, 3★=2.7, 5★=24.3
     * @param stars Evolution level - each star multiplies by evolutionPower
     * @param multiplierGainBonus Bonus from Summit Multiplier Gain (1.0 = no bonus)
     * @param evolutionPowerBonus Bonus from Summit Evolution Power (3.0 at level 0, asymptote ~4.5)
     * @return Multiplier increment per completion
     */
    public static BigDecimal getRunnerMultiplierIncrement(int stars, double multiplierGainBonus, double evolutionPowerBonus) {
        BigDecimal base = new BigDecimal("0.1");  // RUNNER_MULTIPLIER_INCREMENT
        // Apply Evolution Power exponentially per star
        if (stars > 0) {
            base = base.multiply(BigDecimal.valueOf(Math.pow(evolutionPowerBonus, stars)), MULTIPLIER_CTX);
        }
        return base.multiply(BigDecimal.valueOf(multiplierGainBonus), MULTIPLIER_CTX)
                   .setScale(20, RoundingMode.HALF_UP);
    }

    // Max speed level per evolution cycle (used for total level calculation)
    public static final int MAX_SPEED_LEVEL = 20;

    /**
     * Calculate runner speed upgrade cost.
     * Formula: baseCost(totalLevel + mapOffset) × mapMultiplier × earlyLevelBoost
     * Where totalLevel = stars × MAX_SPEED_LEVEL + speedLevel (accumulates across evolutions)
     * and baseCost(L) = 5 × 2^L + L × 10
     *
     * Early-level boost applies a decaying multiplier for levels 0-9 on maps 2+ during first evolution.
     * This creates more time between early map unlocks without affecting late-game progression.
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

        // Apply early-level boost (decaying multiplier for levels 0-9 on maps 2+ during first evolution)
        double earlyBoost = calculateEarlyLevelBoost(speedLevel, mapDisplayOrder, stars);
        if (earlyBoost > 1.0) {
            baseCost = baseCost.multiply(BigDecimal.valueOf(earlyBoost), CALC_CTX);
        }

        return baseCost.setScale(0, RoundingMode.CEILING);
    }

    // Runner (internal tick system)
    public static final long RUNNER_TICK_INTERVAL_MS = 16L; // ~60 ticks/second for smooth movement
    public static final long RUNNER_REFRESH_INTERVAL_MS = 1000L;
    public static final long RUNNER_INVALID_RECOVERY_MS = 3000L; // Force respawn after entity invalid for 3s

    // Timing
    public static final long SAVE_DEBOUNCE_MS = 5000L;

    // ========================================
    // Elevation System (First Prestige)
    // ========================================

    // Elevation: level = multiplier (1:1), cost curve flattened at high levels.
    // Cost formula: BASE_COST * COST_GROWTH^(effectiveLevel)
    // For level <= SOFT_CAP: effectiveLevel = level^COST_CURVE
    // For level > SOFT_CAP:  effectiveLevel = SOFT_CAP^COST_CURVE + (level-SOFT_CAP)^COST_CURVE_LATE
    // This keeps the same early game curve but makes late-game elevation much more accessible.
    public static final long ELEVATION_BASE_COST = 30000L;
    public static final double ELEVATION_COST_GROWTH = 1.15;
    public static final double ELEVATION_COST_CURVE = 0.77; // Early game exponent (level <= SOFT_CAP)
    public static final double ELEVATION_COST_CURVE_LATE = 0.63; // Late game exponent (level > SOFT_CAP)
    public static final int ELEVATION_SOFT_CAP = 300; // Level where late-game curve kicks in

    /**
     * Calculate the cost to reach the next elevation level.
     * Below SOFT_CAP: baseCost * 1.15^(level^0.77)
     * Above SOFT_CAP: baseCost * 1.15^(300^0.77 + (level-300)^0.63)
     * Keeps identical early game, much flatter late game.
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
        int safeLevel = Math.max(0, currentLevel);

        BigDecimal base = BigDecimal.valueOf(ELEVATION_BASE_COST);

        // Two-phase cost curve: identical early game, flatter late game
        double effectiveLevel;
        if (safeLevel <= ELEVATION_SOFT_CAP) {
            effectiveLevel = Math.pow(safeLevel, ELEVATION_COST_CURVE);
        } else {
            double basePart = Math.pow(ELEVATION_SOFT_CAP, ELEVATION_COST_CURVE);
            double latePart = Math.pow(safeLevel - ELEVATION_SOFT_CAP, ELEVATION_COST_CURVE_LATE);
            effectiveLevel = basePart + latePart;
        }
        BigDecimal growthPower = BigDecimal.valueOf(Math.pow(ELEVATION_COST_GROWTH, effectiveLevel));
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
    private static final int MAX_ELEVATION_PURCHASE_ITERATIONS = 100_000;

    public static ElevationPurchaseResult calculateElevationPurchase(int currentLevel, BigDecimal availableCoins, BigDecimal costMultiplier) {
        if (availableCoins.compareTo(BigDecimal.ZERO) <= 0
                || costMultiplier.compareTo(BigDecimal.ZERO) <= 0) {
            return new ElevationPurchaseResult(0, BigDecimal.ZERO);
        }

        int levelsAffordable = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        int level = currentLevel;

        while (levelsAffordable < MAX_ELEVATION_PURCHASE_ITERATIONS) {
            BigDecimal nextCost = getElevationLevelUpCost(level, costMultiplier);
            BigDecimal newTotal = totalCost.add(nextCost, CALC_CTX);

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
        {"#7c3aed", "#b794f6"},  // Violet -> Light violet (Parkour 1)
        {"#ef4444", "#fca5a5"},  // Red -> Light red (Parkour 2)
        {"#f59e0b", "#fbbf24"},  // Orange -> Light orange (Parkour 3)
        {"#10b981", "#34d399"},  // Green -> Light green (Parkour 4)
        {"#3b82f6", "#93c5fd"}   // Blue -> Light blue (Parkour 5)
    };

    // ========================================
    // Summit System (Middle Prestige)
    // ========================================

    public enum SummitCategory {
        RUNNER_SPEED("Runner Speed"),      // 1 + 0.45 × √niveau
        MULTIPLIER_GAIN("Multiplier Gain"), // 1 + 0.5 × niveau^0.8
        EVOLUTION_POWER("Evolution Power"); // 2 + 1.5 × niveau / (niveau + 10), asymptote ~3.5

        private final String displayName;

        SummitCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        /**
         * Get the bonus multiplier for a given level.
         * - RUNNER_SPEED: 1 + 0.45 × √niveau
         * - MULTIPLIER_GAIN: 1 + 0.5 × niveau^0.8
         * - EVOLUTION_POWER: 3 + 1.5 × niveau / (niveau + 10) — asymptote ~4.5
         */
        public double getBonusForLevel(int level) {
            int safeLevel = Math.max(0, level);
            return switch (this) {
                case RUNNER_SPEED -> 1.0 + 0.45 * Math.sqrt(safeLevel);
                case MULTIPLIER_GAIN -> 1.0 + 0.5 * Math.pow(safeLevel, 0.8);
                case EVOLUTION_POWER -> 3.0 + 1.5 * safeLevel / (safeLevel + 10.0);
            };
        }
    }

    // ========================================
    // Summit XP System
    // ========================================

    public static final double SUMMIT_XP_LEVEL_EXPONENT = 2.0; // Exponent for level formula
    public static final double SUMMIT_XP_COIN_POWER = 3.0 / 7.0; // ~0.4286, compression for coins → XP
    public static final long SUMMIT_MIN_COINS = 1_000_000_000L; // Minimum coins for 1 XP (1B)

    /**
     * Convert coins to XP.
     * Formula: (coins / SUMMIT_MIN_COINS)^(3/7)
     * At 1B = 1 XP, at 10B ≈ 2 XP, at 1T ≈ 19 XP, at 1Q ≈ 372 XP.
     * Uses power 3/7 (paired with level^2.0) to preserve the same coin→level mapping
     * as the old system (sqrt + level^2.5) while producing smaller XP numbers.
     */
    public static long coinsToXp(BigDecimal coins) {
        if (coins.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        double ratio = coins.divide(BigDecimal.valueOf(SUMMIT_MIN_COINS), CALC_CTX).doubleValue();
        if (ratio < 1.0) {
            return 0;
        }
        return (long) Math.pow(ratio, SUMMIT_XP_COIN_POWER);
    }

    /**
     * Calculate coins needed to reach a given XP amount.
     * Inverse of coinsToXp: coins = xp^(7/3) × SUMMIT_MIN_COINS
     */
    public static BigDecimal xpToCoins(long xp) {
        if (xp <= 0) {
            return BigDecimal.ZERO;
        }
        double coins = Math.pow(xp, 7.0 / 3.0) * SUMMIT_MIN_COINS;
        return BigDecimal.valueOf(coins);
    }

    /**
     * Calculate XP required to reach a specific level (from level-1).
     * Formula: level^2.0
     */
    public static long getXpForLevel(int level) {
        if (level <= 0) return 0;
        return (long) Math.pow(level, SUMMIT_XP_LEVEL_EXPONENT);
    }

    /**
     * Calculate cumulative XP required to reach a level (total from 0).
     */
    public static long getCumulativeXpForLevel(int level) {
        long total = 0;
        for (int i = 1; i <= level; i++) {
            total += getXpForLevel(i);
        }
        return total;
    }

    /**
     * Calculate the level achieved with given cumulative XP.
     */
    public static int calculateLevelFromXp(long xp) {
        int level = 0;
        long cumulative = 0;
        while (true) {
            long nextLevelXp = getXpForLevel(level + 1);
            if (cumulative + nextLevelXp > xp) {
                break;
            }
            cumulative += nextLevelXp;
            level++;
        }
        return level;
    }

    /**
     * Calculate XP progress within current level.
     * Returns [currentXpInLevel, xpRequiredForNextLevel]
     */
    public static long[] getXpProgress(long totalXp) {
        int level = calculateLevelFromXp(totalXp);
        long xpForCurrentLevel = getCumulativeXpForLevel(level);
        long xpInLevel = totalXp - xpForCurrentLevel;
        long xpForNextLevel = getXpForLevel(level + 1);
        return new long[]{xpInLevel, xpForNextLevel};
    }

    // ========================================
    // Ascension System (Ultimate Prestige)
    // ========================================

    public static final long ASCENSION_COIN_THRESHOLD = 10_000_000_000_000_000L; // 10 quadrillion coins

    public enum SkillTreeNode {
        AUTO_RUNNERS("Automate Runners", "Auto-upgrade runners (cheapest first, no evolution)");

        private final String name;
        private final String description;

        SkillTreeNode(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

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
