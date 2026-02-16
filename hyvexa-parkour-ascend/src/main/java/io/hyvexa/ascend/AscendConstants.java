package io.hyvexa.ascend;

import io.hyvexa.common.math.BigNumber;

import java.util.Set;

public final class AscendConstants {

    private AscendConstants() {
    }



    // Database
    public static final String TABLE_PREFIX = "ascend_";

    // Items - Menu
    public static final String ITEM_DEV_CINDERCLOTH = "Ingredient_Bolt_Cindercloth";
    public static final String ITEM_DEV_STORMSILK = "Ingredient_Bolt_Stormsilk";
    public static final String ITEM_DEV_COTTON = "Ingredient_Bolt_Cotton";
    public static final String ITEM_DEV_SHADOWEAVE = "Ingredient_Bolt_Shadoweave";
    public static final String ITEM_DEV_SILK = "Ingredient_Bolt_Silk";

    // Items - In-Run
    public static final String ITEM_RESET = "Ascend_Reset_Block";
    public static final String ITEM_LEAVE = "Ascend_Leave_Block";

    // Economy
    public static final double RUNNER_MULTIPLIER_INCREMENT = 0.1; // +0.1 per runner completion (base, scaled by stars+summit)
    public static final int MULTIPLIER_SLOTS = 5;
    public static final int MAP_UNLOCK_REQUIRED_RUNNER_LEVEL = 5; // Runner level required to unlock next map

    // Passive Earnings
    public static final long PASSIVE_OFFLINE_RATE_PERCENT = 10L; // 10% of normal production
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

    // Base vexa rewards per manual completion (before multiplier)
    // Actual reward = baseReward * totalMultiplier
    public static final long[] MAP_BASE_REWARDS = {
        1L,       // Level 0 (Rouge)  - 1 vexa/run base
        5L,       // Level 1 (Orange) - 5 vexa/run base
        25L,      // Level 2 (Jaune)  - 25 vexa/run base
        100L,     // Level 3 (Vert)   - 100 vexa/run base
        500L      // Level 4 (Bleu)   - 500 vexa/run base
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

    // Momentum System (temporary speed boost from manual runs)
    public static final double MOMENTUM_SPEED_MULTIPLIER = 2.0;
    public static final double MOMENTUM_SURGE_MULTIPLIER = 2.5;


    public static final long MOMENTUM_DURATION_MS = 60_000L;
    public static final long MOMENTUM_ENDURANCE_DURATION_MS = 90_000L;
    public static final double MOMENTUM_MASTERY_MULTIPLIER = 3.0;
    public static final long MOMENTUM_MASTERY_DURATION_MS = 120_000L;

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
    public static BigNumber getRunnerMultiplierIncrement(int stars) {
        return getRunnerMultiplierIncrement(stars, 1.0, 3.0, 0.0);
    }

    /**
     * Get the multiplier increment for a runner with Summit bonuses.
     * Base: (0.1 + baseIncrementBonus) per completion, raised by Evolution Power per star.
     * Formula: (base + baseIncrementBonus) × evolutionPower^stars × multiplierGainBonus
     * @param stars Evolution level - each star multiplies by evolutionPower
     * @param multiplierGainBonus Bonus from Summit Multiplier Gain (1.0 = no bonus)
     * @param evolutionPowerBonus Bonus from Summit Evolution Power (3.0 + 0.10 per level)
     * @param baseIncrementBonus Additive bonus to base increment (e.g. +0.10 from Multiplier Boost skill)
     * @return Multiplier increment per completion
     */
    public static BigNumber getRunnerMultiplierIncrement(int stars, double multiplierGainBonus, double evolutionPowerBonus, double baseIncrementBonus) {
        double base = RUNNER_MULTIPLIER_INCREMENT + baseIncrementBonus; // 0.1 + bonus
        if (stars > 0) {
            double power = Math.pow(evolutionPowerBonus, stars);
            if (!Double.isFinite(power)) {
                power = Double.MAX_VALUE;
            }
            base *= power;
        }
        base *= multiplierGainBonus;
        if (!Double.isFinite(base)) {
            base = Double.MAX_VALUE;
        }
        return BigNumber.fromDouble(base);
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
    public static BigNumber getRunnerUpgradeCost(int speedLevel, int mapDisplayOrder, int stars) {
        // Get map-specific scaling parameters
        int offset = getMapUpgradeOffset(mapDisplayOrder);
        double mapMult = getMapUpgradeMultiplier(mapDisplayOrder);

        // Calculate total levels bought across all evolutions
        int totalLevelsBought = stars * MAX_SPEED_LEVEL + speedLevel;
        int effectiveLevel = totalLevelsBought + offset;

        // Base formula: 5 × 2^level + level × 10
        // Use BigNumber pow for 2^level to handle large values
        BigNumber exponentialPart = BigNumber.of(2, 0).pow(effectiveLevel);
        BigNumber linearPart = BigNumber.fromLong((long) effectiveLevel * 10);

        BigNumber baseCost = BigNumber.of(5, 0).multiply(exponentialPart).add(linearPart);

        // Apply map multiplier
        baseCost = baseCost.multiply(BigNumber.fromDouble(mapMult));

        // Apply early-level boost
        double earlyBoost = calculateEarlyLevelBoost(speedLevel, mapDisplayOrder, stars);
        if (earlyBoost > 1.0) {
            baseCost = baseCost.multiply(BigNumber.fromDouble(earlyBoost));
        }

        return baseCost;
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

    // Elevation multiplier: level^1.05 (slightly super-linear to reward higher elevation)
    public static final double ELEVATION_MULTIPLIER_EXPONENT = 1.05;

    /**
     * Calculate the elevation multiplier for a given level.
     * Returns level^1.05 (slightly super-linear).
     */
    public static double getElevationMultiplier(int level) {
        if (level <= 0) return 1.0;
        return Math.pow(level, ELEVATION_MULTIPLIER_EXPONENT);
    }

    /**
     * Format the elevation multiplier for display (rounded to integer).
     */
    public static String formatElevationMultiplier(int level) {
        return "x" + Math.round(getElevationMultiplier(level));
    }

    // Elevation: cost curve flattened at high levels.
    // Cost formula: BASE_COST * COST_GROWTH^(effectiveLevel)
    // For level <= SOFT_CAP: effectiveLevel = level^0.72
    // For level > SOFT_CAP:  effectiveLevel = SOFT_CAP^0.72 + (level-SOFT_CAP)^0.58
    // Keeps identical level 1 cost, progressively cheaper at higher levels.
    public static final long ELEVATION_BASE_COST = 30000L;
    public static final double ELEVATION_COST_GROWTH = 1.15;
    public static final double ELEVATION_COST_CURVE = 0.72; // Early game exponent (level <= SOFT_CAP)
    public static final double ELEVATION_COST_CURVE_LATE = 0.58; // Late game exponent (level > SOFT_CAP)
    public static final int ELEVATION_SOFT_CAP = 300; // Level where late-game curve kicks in

    /**
     * Calculate the cost to reach the next elevation level.
     * Below SOFT_CAP: baseCost * 1.15^(level^0.72)
     * Above SOFT_CAP: baseCost * 1.15^(300^0.72 + (level-300)^0.58)
     * Keeps identical early game, much flatter late game.
     */
    public static BigNumber getElevationLevelUpCost(int currentLevel) {
        return getElevationLevelUpCost(currentLevel, BigNumber.ONE);
    }

    /**
     * Calculate the cost to reach the next elevation level with a cost multiplier.
     * @param currentLevel The player's current elevation level
     * @param costMultiplier Cost modifier (1.0 = full cost, 0.8 = 20% discount)
     */
    public static BigNumber getElevationLevelUpCost(int currentLevel, BigNumber costMultiplier) {
        int safeLevel = Math.max(0, currentLevel);

        // Two-phase cost curve: identical early game, flatter late game
        double effectiveLevel;
        if (safeLevel <= ELEVATION_SOFT_CAP) {
            effectiveLevel = Math.pow(safeLevel, ELEVATION_COST_CURVE);
        } else {
            double basePart = Math.pow(ELEVATION_SOFT_CAP, ELEVATION_COST_CURVE);
            double latePart = Math.pow(safeLevel - ELEVATION_SOFT_CAP, ELEVATION_COST_CURVE_LATE);
            effectiveLevel = basePart + latePart;
        }

        // cost = baseCost * growth^effectiveLevel
        // Compute in log10 space to avoid double overflow for large effectiveLevel
        double log10Cost = Math.log10(ELEVATION_BASE_COST) + effectiveLevel * Math.log10(ELEVATION_COST_GROWTH);
        if (!Double.isFinite(log10Cost) || log10Cost > 1_000_000_000) {
            return BigNumber.of(9.999, 999_999_999); // practical cap
        }
        int resultExp = (int) Math.floor(log10Cost);
        double resultMantissa = Math.pow(10.0, log10Cost - resultExp);
        if (!Double.isFinite(resultMantissa)) {
            resultMantissa = 1.0;
        }
        BigNumber cost = BigNumber.of(resultMantissa, resultExp);

        // Apply cost multiplier (skill discount)
        if (!costMultiplier.equals(BigNumber.ONE)) {
            cost = cost.multiply(costMultiplier);
        }

        return cost;
    }

    /**
     * Calculate how many levels can be purchased with given vexa at current level.
     * Returns the number of levels affordable and the total cost.
     */
    public static ElevationPurchaseResult calculateElevationPurchase(int currentLevel, BigNumber availableVexa) {
        return calculateElevationPurchase(currentLevel, availableVexa, BigNumber.ONE);
    }

    /**
     * Calculate how many levels can be purchased with given vexa and cost multiplier.
     * @param currentLevel The player's current elevation level
     * @param availableVexa Vexa available to spend
     * @param costMultiplier Cost modifier (1.0 = full cost, 0.8 = 20% discount)
     */
    private static final int MAX_ELEVATION_PURCHASE_ITERATIONS = 100_000;

    public static ElevationPurchaseResult calculateElevationPurchase(int currentLevel, BigNumber availableVexa, BigNumber costMultiplier) {
        if (availableVexa.lte(BigNumber.ZERO)
                || costMultiplier.lte(BigNumber.ZERO)) {
            return new ElevationPurchaseResult(0, BigNumber.ZERO);
        }

        int levelsAffordable = 0;
        BigNumber totalCost = BigNumber.ZERO;
        int level = currentLevel;

        while (levelsAffordable < MAX_ELEVATION_PURCHASE_ITERATIONS) {
            BigNumber nextCost = getElevationLevelUpCost(level, costMultiplier);
            BigNumber newTotal = totalCost.add(nextCost);

            if (newTotal.gt(availableVexa)) {
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
        public final BigNumber cost;

        public ElevationPurchaseResult(int levels, BigNumber cost) {
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

    // Summit soft cap: linear growth below, sqrt growth above
    public static final int SUMMIT_SOFT_CAP = 25;
    // Summit deep cap: sqrt growth below, fourth-root growth above (heavy diminishing returns)
    public static final int SUMMIT_DEEP_CAP = 500;
    // Summit hard cap: absolute maximum level per category
    public static final int SUMMIT_MAX_LEVEL = 1000;
    // Max cumulative XP needed (pre-computed for SUMMIT_MAX_LEVEL)
    public static final long SUMMIT_MAX_XP = getCumulativeXpForLevel(SUMMIT_MAX_LEVEL);

    public enum SummitCategory {
        MULTIPLIER_GAIN("Multiplier Gain", 1.0, 0.30),  // 1 + 0.30/level
        RUNNER_SPEED("Runner Speed", 1.0, 0.15),        // 1 + 0.15/level
        EVOLUTION_POWER("Evolution Power", 3.0, 0.10);   // 3 + 0.10/level

        private final String displayName;
        private final double base;
        private final double increment;

        SummitCategory(String displayName, double base, double increment) {
            this.displayName = displayName;
            this.base = base;
            this.increment = increment;
        }

        public String getDisplayName() {
            return displayName;
        }

        /**
         * Get the bonus multiplier for a given level.
         * Three growth zones:
         *   0-25 (soft cap): linear — base + increment × level
         *   25-500 (deep cap): sqrt — + increment × √(level - 25)
         *   500+ (deep cap): fourth root — + increment × ⁴√(level - 500)
         */
        public double getBonusForLevel(int level) {
            int safeLevel = Math.max(0, level);
            if (safeLevel <= SUMMIT_SOFT_CAP) {
                return base + increment * safeLevel;
            }
            double linearPart = increment * SUMMIT_SOFT_CAP;
            if (safeLevel <= SUMMIT_DEEP_CAP) {
                double sqrtPart = increment * Math.sqrt(safeLevel - SUMMIT_SOFT_CAP);
                return base + linearPart + sqrtPart;
            }
            // sqrt portion from soft cap to deep cap (frozen)
            double sqrtPart = increment * Math.sqrt(SUMMIT_DEEP_CAP - SUMMIT_SOFT_CAP);
            // fourth-root portion from deep cap onward (heavy diminishing returns)
            double fourthRootPart = increment * Math.pow(safeLevel - SUMMIT_DEEP_CAP, 0.25);
            return base + linearPart + sqrtPart + fourthRootPart;
        }
    }

    // ========================================
    // Summit XP System
    // ========================================

    public static final double SUMMIT_XP_LEVEL_EXPONENT = 2.0; // Exponent for level formula
    public static final long SUMMIT_MIN_VEXA = 1_000_000_000L; // Minimum vexa for 1 XP (1B)

    // Calibrated so 1 Decillion (10^33) accumulated vexa = exactly level 1000
    // Derived: power = log(SUMMIT_MAX_XP) / log(1Dc / MIN_VEXA)
    public static final double SUMMIT_XP_VEXA_POWER =
        Math.log(SUMMIT_MAX_XP) / Math.log(1e33 / SUMMIT_MIN_VEXA); // ~0.3552

    /**
     * Convert vexa to XP.
     * Formula: (vexa / SUMMIT_MIN_VEXA)^power  (power calibrated for 1Dc = level 1000)
     * At 1B = 1 XP, at 10B ≈ 2 XP, at 1T ≈ 12 XP, at 1Qa ≈ 135 XP, at 1Dc = 333.8M XP (level 1000).
     */
    public static long vexaToXp(BigNumber vexa) {
        if (vexa.lte(BigNumber.ZERO)) {
            return 0;
        }
        double ratio = vexa.divide(BigNumber.fromLong(SUMMIT_MIN_VEXA)).toDouble();
        if (!Double.isFinite(ratio) || ratio < 1.0) {
            return ratio < 1.0 ? 0 : Long.MAX_VALUE;
        }
        double xp = Math.pow(ratio, SUMMIT_XP_VEXA_POWER);
        if (!Double.isFinite(xp) || xp >= (double) Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.round(xp);
    }

    /**
     * Saturating addition for longs — clamps to Long.MAX_VALUE instead of wrapping.
     */
    public static long saturatingAdd(long a, long b) {
        long result = a + b;
        // Overflow: both positive but result negative, or both negative but result positive
        if (b > 0 && result < a) {
            return Long.MAX_VALUE;
        }
        if (b < 0 && result > a) {
            return Long.MIN_VALUE;
        }
        return result;
    }

    /**
     * Calculate vexa needed to reach a given XP amount.
     * Inverse of vexaToXp: vexa = xp^(1/power) × SUMMIT_MIN_VEXA
     */
    public static BigNumber xpToVexa(long xp) {
        if (xp <= 0) {
            return BigNumber.ZERO;
        }
        double vexa = Math.pow(xp, 1.0 / SUMMIT_XP_VEXA_POWER) * SUMMIT_MIN_VEXA;
        return BigNumber.fromDouble(vexa);
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
     * Closed-form formula: sum(i^2, i=1..n) = n*(n+1)*(2n+1)/6
     */
    public static long getCumulativeXpForLevel(int level) {
        if (level <= 0) return 0;
        long n = level;
        return n * (n + 1) * (2 * n + 1) / 6;
    }

    /**
     * Calculate the level achieved with given cumulative XP.
     * Uses binary search to find highest level where getCumulativeXpForLevel(level) <= xp.
     */
    public static int calculateLevelFromXp(long xp) {
        if (xp <= 0) return 0;
        // Dynamic upper bound: cumXp ~ n^3/3, so n ~ (3*xp)^(1/3)
        int lo = 0;
        int hi = Math.max(SUMMIT_MAX_LEVEL, (int) Math.ceil(Math.cbrt(3.0 * xp)) + 10);
        while (lo < hi) {
            int mid = lo + (hi - lo + 1) / 2;
            if (getCumulativeXpForLevel(mid) <= xp) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
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

    public static final BigNumber ASCENSION_VEXA_THRESHOLD = BigNumber.of(1, 33); // 1 Decillion (10^33)

    public enum SkillTreeNode {
        AUTO_RUNNERS("Auto-Upgrade + Momentum", "Auto-upgrade runners & momentum speed boost on manual runs"),
        AUTO_EVOLUTION("Auto-Evolution", "Runners auto-evolve at max speed level", AUTO_RUNNERS),
        RUNNER_SPEED("Runner Speed Boost", "x1.1 global runner speed", AUTO_EVOLUTION),
        EVOLUTION_POWER("Evolution Power+", "+1 base evolution power", AUTO_EVOLUTION),
        RUNNER_SPEED_2("Runner Speed II", "x1.2 global runner speed", RUNNER_SPEED, EVOLUTION_POWER),
        AUTO_SUMMIT("Auto-Summit", "Unlock automatic summit with per-category increment cycling.", RUNNER_SPEED_2),
        AUTO_ELEVATION("Auto-Elevation", "Unlock automatic elevation with configurable multiplier targets.", RUNNER_SPEED_2),
        ASCENSION_CHALLENGES("Ascension Challenges", "Unlock Ascension Challenges", 1, AUTO_SUMMIT, AUTO_ELEVATION),
        MOMENTUM_SURGE("Momentum Surge", "Momentum boost x2 -> x2.5", 3, ASCENSION_CHALLENGES),
        MOMENTUM_ENDURANCE("Momentum Endurance", "Momentum 60s -> 90s", 3, ASCENSION_CHALLENGES),
        MULTIPLIER_BOOST("Multiplier Boost", "+0.10 base multiplier gain", 5, MOMENTUM_SURGE, MOMENTUM_ENDURANCE),
        RUNNER_SPEED_3("Runner Speed III", "x1.3 global runner speed", 10, MULTIPLIER_BOOST),
        EVOLUTION_POWER_2("Evolution Power II", "+1 base evolution power", 10, MULTIPLIER_BOOST),
        RUNNER_SPEED_4("Runner Speed IV", "x1.5 global runner speed", 15, RUNNER_SPEED_3, EVOLUTION_POWER_2),
        EVOLUTION_POWER_3("Evolution Power III", "+2 base evolution power", 15, RUNNER_SPEED_3, EVOLUTION_POWER_2),
        MOMENTUM_MASTERY("Momentum Mastery", "Momentum x3.0 + 120s duration", 25, RUNNER_SPEED_4, EVOLUTION_POWER_3),
        MULTIPLIER_BOOST_2("Multiplier Boost II", "+0.25 base multiplier gain", 40, MOMENTUM_MASTERY),
        ELEVATION_BOOST("Elevation Boost", "Elevation cost -30%", 40, MOMENTUM_MASTERY),
        RUNNER_SPEED_5("Runner Speed V", "x2.0 global runner speed", 75, MULTIPLIER_BOOST_2, ELEVATION_BOOST);

        private final String name;
        private final String description;
        private final int cost;
        private final SkillTreeNode[] prerequisites;

        SkillTreeNode(String name, String description, SkillTreeNode... prerequisites) {
            this(name, description, 1, prerequisites);
        }

        SkillTreeNode(String name, String description, int cost, SkillTreeNode... prerequisites) {
            this.name = name;
            this.description = description;
            this.cost = cost;
            this.prerequisites = prerequisites;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public int getCost() {
            return cost;
        }

        public SkillTreeNode[] getPrerequisites() {
            return prerequisites;
        }

        /**
         * Checks if prerequisites are satisfied (OR logic: at least one prerequisite must be unlocked).
         * Nodes with no prerequisites are always satisfiable.
         */
        public boolean hasPrerequisitesSatisfied(java.util.Set<SkillTreeNode> unlocked) {
            if (prerequisites.length == 0) {
                return true;
            }
            for (SkillTreeNode prereq : prerequisites) {
                if (unlocked.contains(prereq)) {
                    return true;
                }
            }
            return false;
        }
    }

    public enum ChallengeType {
        CHALLENGE_1(1, "Challenge 1",
            "Complete an Ascension without map 5",
            "#10b981",
            Set.of(), Set.of(4), 1.0, 1.0, 1.0),
        CHALLENGE_2(2, "Challenge 2",
            "Complete an Ascension with 150% Runner Speed malus",
            "#f59e0b",
            Set.of(), Set.of(), -0.5, 1.0, 1.0),
        CHALLENGE_3(3, "Challenge 3",
            "Complete an Ascension with 150% Multiplier Gain malus",
            "#3b82f6",
            Set.of(), Set.of(), 1.0, -0.5, 1.0),
        CHALLENGE_4(4, "Challenge 4",
            "Complete an Ascension with 150% Evolution Power malus",
            "#ef4444",
            Set.of(), Set.of(), 1.0, 1.0, -0.5),
        CHALLENGE_5(5, "Challenge 5",
            "Complete an Ascension with no Runner Speed and Multiplier Gain bonus",
            "#8b5cf6",
            Set.of(), Set.of(), 0.0, 0.0, 1.0),
        CHALLENGE_6(6, "Challenge 6",
            "Complete an Ascension with no Summit bonuses",
            "#ec4899",
            Set.of(), Set.of(), 0.0, 0.0, 0.0),
        CHALLENGE_7(7, "Challenge 7",
            "Complete an Ascension without maps 4 and 5",
            "#f59e0b",
            Set.of(), Set.of(3, 4), 1.0, 1.0, 1.0);

        private final int id;
        private final String displayName;
        private final String description;
        private final String accentColor;
        private final Set<SummitCategory> blockedSummitCategories;
        private final Set<Integer> blockedMapDisplayOrders;
        private final double speedEffectiveness;
        private final double multiplierGainEffectiveness;
        private final double evolutionPowerEffectiveness;

        ChallengeType(int id, String displayName, String description, String accentColor,
                      Set<SummitCategory> blockedSummitCategories,
                      Set<Integer> blockedMapDisplayOrders, double speedEffectiveness,
                      double multiplierGainEffectiveness, double evolutionPowerEffectiveness) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.accentColor = accentColor;
            this.blockedSummitCategories = blockedSummitCategories;
            this.blockedMapDisplayOrders = blockedMapDisplayOrders;
            this.speedEffectiveness = speedEffectiveness;
            this.multiplierGainEffectiveness = multiplierGainEffectiveness;
            this.evolutionPowerEffectiveness = evolutionPowerEffectiveness;
        }

        public int getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public String getAccentColor() { return accentColor; }
        public Set<SummitCategory> getBlockedSummitCategories() { return blockedSummitCategories; }
        public Set<Integer> getBlockedMapDisplayOrders() { return blockedMapDisplayOrders; }
        public double getSpeedEffectiveness() { return speedEffectiveness; }
        public double getMultiplierGainEffectiveness() { return multiplierGainEffectiveness; }
        public double getEvolutionPowerEffectiveness() { return evolutionPowerEffectiveness; }

        public static ChallengeType fromId(int id) {
            for (ChallengeType type : values()) {
                if (type.id == id) return type;
            }
            return null;
        }
    }

    // ========================================
    // Achievement System
    // ========================================

    public enum AchievementCategory {
        MILESTONES("Milestones"),
        RUNNERS("Runners"),
        PRESTIGE("Prestige"),
        SKILLS("Skills"),
        CHALLENGES("Challenges"),
        SECRET("Secret");

        private final String displayName;

        AchievementCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum AchievementType {
        // Milestones - Manual Runs
        FIRST_STEPS("First Steps", "Complete your first manual run", AchievementCategory.MILESTONES),
        WARMING_UP("Warming Up", "Complete 10 manual runs", AchievementCategory.MILESTONES),
        DEDICATED("Dedicated", "Complete 100 manual runs", AchievementCategory.MILESTONES),
        HALFWAY_THERE("Halfway There", "Complete 500 manual runs", AchievementCategory.MILESTONES),
        MARATHON("Marathon", "Complete 1000 manual runs", AchievementCategory.MILESTONES),
        UNSTOPPABLE("Unstoppable", "Complete 5000 manual runs", AchievementCategory.MILESTONES),
        LIVING_LEGEND("Living Legend", "Complete 10000 manual runs", AchievementCategory.MILESTONES),

        // Runners - Automation
        FIRST_ROBOT("First Robot", "Buy your first runner", AchievementCategory.RUNNERS),
        ARMY("Army", "Have 5+ active runners", AchievementCategory.RUNNERS),
        EVOLVED("Evolved", "Evolve a runner to 1+ stars", AchievementCategory.RUNNERS),
        STAR_COLLECTOR("Star Collector", "Evolve a runner to max stars", AchievementCategory.RUNNERS),

        // Prestige - Progression
        FIRST_ELEVATION("First Elevation", "Complete your first Elevation", AchievementCategory.PRESTIGE),
        GOING_UP("Going Up", "Reach elevation 100", AchievementCategory.PRESTIGE),
        SKY_HIGH("Sky High", "Reach elevation 5,000", AchievementCategory.PRESTIGE),
        STRATOSPHERE("Stratosphere", "Reach elevation 20,000", AchievementCategory.PRESTIGE),
        SUMMIT_SEEKER("Summit Seeker", "Complete your first Summit", AchievementCategory.PRESTIGE),
        PEAK_PERFORMER("Peak Performer", "Reach summit level 10", AchievementCategory.PRESTIGE),
        MOUNTAINEER("Mountaineer", "Reach summit level 100", AchievementCategory.PRESTIGE),
        SUMMIT_LEGEND("Summit Legend", "Reach summit level 1,000", AchievementCategory.PRESTIGE),
        ASCENDED("Ascended", "Complete your first Ascension", AchievementCategory.PRESTIGE),
        VETERAN("Veteran", "Complete 5 ascensions", AchievementCategory.PRESTIGE),
        TRANSCENDENT("Transcendent", "Complete 10 ascensions", AchievementCategory.PRESTIGE),

        // Skills - Skill Tree
        NEW_POWERS("New Powers", "Unlock your first skill", AchievementCategory.SKILLS),

        // Challenges
        CHALLENGER("Challenger", "Complete your first challenge", AchievementCategory.CHALLENGES),
        CHALLENGE_MASTER("Challenge Master", "Complete all challenges", AchievementCategory.CHALLENGES),

        // Secret - Hidden
        CHAIN_RUNNER("Chain Runner", "Complete 25 consecutive runs", AchievementCategory.SECRET, true),
        ALL_STARS("All Stars", "Max-star runners on all maps", AchievementCategory.SECRET, true),
        COMPLETIONIST("Completionist", "Unlock all other achievements", AchievementCategory.SECRET, true);

        private final String name;
        private final String description;
        private final AchievementCategory category;
        private final boolean hidden;

        AchievementType(String name, String description, AchievementCategory category) {
            this(name, description, category, false);
        }

        AchievementType(String name, String description, AchievementCategory category, boolean hidden) {
            this.name = name;
            this.description = description;
            this.category = category;
            this.hidden = hidden;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public AchievementCategory getCategory() {
            return category;
        }

        public boolean isHidden() {
            return hidden;
        }
    }

    // Achievement thresholds
    public static final int ACHIEVEMENT_MANUAL_RUNS_10 = 10;
    public static final int ACHIEVEMENT_MANUAL_RUNS_100 = 100;
    public static final int ACHIEVEMENT_MANUAL_RUNS_500 = 500;
    public static final int ACHIEVEMENT_MANUAL_RUNS_1000 = 1000;
    public static final int ACHIEVEMENT_MANUAL_RUNS_5000 = 5000;
    public static final int ACHIEVEMENT_MANUAL_RUNS_10000 = 10000;
    public static final int ACHIEVEMENT_RUNNER_COUNT = 5;
    public static final int ACHIEVEMENT_ELEVATION_100 = 100;
    public static final int ACHIEVEMENT_ELEVATION_5000 = 5000;
    public static final int ACHIEVEMENT_ELEVATION_20000 = 20000;
    public static final int ACHIEVEMENT_SUMMIT_LEVEL_10 = 10;
    public static final int ACHIEVEMENT_SUMMIT_LEVEL_100 = 100;
    public static final int ACHIEVEMENT_SUMMIT_LEVEL_1000 = 1000;
    public static final int ACHIEVEMENT_ASCENSION_5 = 5;
    public static final int ACHIEVEMENT_ASCENSION_10 = 10;
    public static final int ACHIEVEMENT_CONSECUTIVE_RUNS_25 = 25;
}
