package io.hyvexa.ascend;

import io.hyvexa.common.math.BigNumber;

public final class RunnerEconomyConstants {

    private RunnerEconomyConstants() {
    }

    private static long clampedLookup(int index, long[] array) {
        if (index < 0 || index >= array.length) return array[array.length - 1];
        return array[index];
    }

    private static int clampedLookup(int index, int[] array) {
        if (index < 0 || index >= array.length) return array[array.length - 1];
        return array[index];
    }

    private static double clampedLookup(int index, double[] array) {
        if (index < 0 || index >= array.length) return array[array.length - 1];
        return array[index];
    }

    // Economy
    public static final double RUNNER_MULTIPLIER_INCREMENT = 0.1; // +0.1 per runner completion (base, scaled by stars+summit)
    public static final int MULTIPLIER_SLOTS = 5;
    public static final int MAP_UNLOCK_REQUIRED_RUNNER_LEVEL = 5; // Runner level required to unlock next map

    // Speed upgrade multiplier: uniform +10% per level for all maps
    public static final double MAP_SPEED_MULTIPLIER = 0.10;

    public static double getMapSpeedMultiplier(int displayOrder) {
        return MAP_SPEED_MULTIPLIER;
    }

    // Map Level Defaults (indexed by displayOrder 0-5)
    // Base run times: faster maps = faster multiplier growth
    public static final long[] MAP_BASE_RUN_TIMES_MS = {
        5000L,    // Level 0 (Rouge)  - 5 sec
        10000L,   // Level 1 (Orange) - 10 sec
        16000L,   // Level 2 (Jaune)  - 16 sec
        26000L,   // Level 3 (Vert)   - 26 sec
        42000L,   // Level 4 (Bleu)   - 42 sec
        68000L    // Level 5 (Gold)   - 68 sec (Transcendence)
    };

    // Base volt rewards per manual completion (before multiplier)
    // Actual reward = baseReward * totalMultiplier
    public static final long[] MAP_BASE_REWARDS = {
        1L,       // Level 0 (Rouge)  - 1 volt/run base
        5L,       // Level 1 (Orange) - 5 volt/run base
        25L,      // Level 2 (Jaune)  - 25 volt/run base
        100L,     // Level 3 (Vert)   - 100 volt/run base
        500L,     // Level 4 (Bleu)   - 500 volt/run base
        2500L     // Level 5 (Gold)   - 2500 volt/run base (Transcendence)
    };

    // Map unlock prices: first map free, then increasing
    public static final long[] MAP_UNLOCK_PRICES = {
        0L,       // Level 0 (Rouge)  - Gratuit (map de depart)
        100L,     // Level 1 (Orange)
        500L,     // Level 2 (Jaune)
        2500L,    // Level 3 (Vert)
        10000L,   // Level 4 (Bleu)
        50000L    // Level 5 (Gold)   - Transcendence milestone required
    };

    // Runner upgrade cost scaling by map level
    // Higher maps have both cost offset (start further in formula) and multiplier (scale up all costs)
    public static final int[] MAP_UPGRADE_OFFSET = {
        0,    // Level 0 (Rouge)  - no offset
        1,    // Level 1 (Orange) - +1 level offset
        2,    // Level 2 (Jaune)  - +2 level offset
        3,    // Level 3 (Vert)   - +3 level offset
        4,    // Level 4 (Bleu)   - +4 level offset
        5     // Level 5 (Gold)   - +5 level offset (Transcendence)
    };

    public static final double[] MAP_UPGRADE_MULTIPLIER = {
        1.0,  // Level 0 (Rouge)  - 1.0x cost
        1.4,  // Level 1 (Orange) - 1.4x cost
        1.9,  // Level 2 (Jaune)  - 1.9x cost
        2.6,  // Level 3 (Vert)   - 2.6x cost
        3.5,  // Level 4 (Bleu)   - 3.5x cost
        4.7   // Level 5 (Gold)   - 4.7x cost (Transcendence)
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
        3.0,  // Map 4 (Bleu)   - 3.0x max boost at level 0
        3.5   // Map 5 (Gold)   - 3.5x max boost at level 0 (Transcendence)
    };

    public static double getMapEarlyLevelBoost(int displayOrder) {
        return clampedLookup(displayOrder, MAP_EARLY_LEVEL_BOOST);
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
        return clampedLookup(displayOrder, MAP_UPGRADE_OFFSET);
    }

    public static double getMapUpgradeMultiplier(int displayOrder) {
        return clampedLookup(displayOrder, MAP_UPGRADE_MULTIPLIER);
    }

    public static long getMapBaseRunTimeMs(int displayOrder) {
        return clampedLookup(displayOrder, MAP_BASE_RUN_TIMES_MS);
    }

    public static long getMapUnlockPrice(int displayOrder) {
        return clampedLookup(displayOrder, MAP_UNLOCK_PRICES);
    }

    public static long getMapBaseReward(int displayOrder) {
        return clampedLookup(displayOrder, MAP_BASE_REWARDS);
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
     * Formula: (base + baseIncrementBonus) * evolutionPower^stars * multiplierGainBonus
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
     * Formula: baseCost(totalLevel + mapOffset) * mapMultiplier * earlyLevelBoost
     * Where totalLevel = stars * MAX_SPEED_LEVEL + speedLevel (accumulates across evolutions)
     * and baseCost(L) = 5 * 2^L + L * 10
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

        // Base formula: 5 * 2^level + level * 10
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
}
