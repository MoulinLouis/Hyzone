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
    public static final double SPEED_UPGRADE_MULTIPLIER = 0.10; // +10% speed per level
    public static final double MANUAL_MULTIPLIER_INCREMENT = 1.0; // +1.0 per manual run
    public static final double RUNNER_MULTIPLIER_INCREMENT = 0.01; // +0.01 per runner completion
    public static final int MULTIPLIER_SLOTS = 5;

    // Map Level Defaults (indexed by displayOrder 0-4)
    // Base run times: faster maps = faster multiplier growth
    public static final long[] MAP_BASE_RUN_TIMES_MS = {
        5000L,    // Level 0 (Rouge)  - 5 sec
        15000L,   // Level 1 (Orange) - 15 sec
        30000L,   // Level 2 (Jaune)  - 30 sec
        60000L,   // Level 3 (Vert)   - 1 min
        120000L   // Level 4 (Bleu)   - 2 min
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

    // Runner (internal tick system)
    public static final String RUNNER_ENTITY_TYPE = "Kweebec_Sapling";
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
}
