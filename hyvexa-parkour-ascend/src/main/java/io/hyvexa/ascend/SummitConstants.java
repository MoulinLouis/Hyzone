package io.hyvexa.ascend;

import io.hyvexa.common.math.BigNumber;

public final class SummitConstants {

    private SummitConstants() {
    }

    // Summit soft cap: linear growth below, sqrt growth above
    public static final int SUMMIT_SOFT_CAP = 25;
    // Summit deep cap: sqrt growth below, fourth-root growth above
    public static final int SUMMIT_DEEP_CAP = 500;
    // Summit XP softcap: levels above this cost increasingly more XP (exponent rises from 2.0 to 3.0)
    public static final int SUMMIT_XP_SOFTCAP = 1000;
    // Post-cap exponent: above XP softcap, bonus = anchorAt1000 * (level/1000)^exponent
    // Soft scaling — diminishing returns, prevents runaway bonus inflation
    public static final double SUMMIT_POST_CAP_EXPONENT = 0.3;

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
         * Four growth zones:
         *   0-25 (soft cap): linear — base + increment * level
         *   25-500 (deep cap): sqrt — + increment * sqrt(level - 25)
         *   500-1000: fourth root — + increment * fourthRoot(level - 500)
         *   1000+ (soft): anchor * (level / 1000)^0.3 — diminishing returns
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
            if (safeLevel <= SUMMIT_XP_SOFTCAP) {
                // fourth-root portion from deep cap to XP softcap
                double fourthRootPart = increment * Math.pow(safeLevel - SUMMIT_DEEP_CAP, 0.25);
                return base + linearPart + sqrtPart + fourthRootPart;
            }
            // Post-cap zone: anchor at 1000 scaled by soft power function
            double fourthRootPart = increment * Math.pow(SUMMIT_XP_SOFTCAP - SUMMIT_DEEP_CAP, 0.25);
            double anchor = base + linearPart + sqrtPart + fourthRootPart;
            return anchor * Math.pow((double) safeLevel / SUMMIT_XP_SOFTCAP, SUMMIT_POST_CAP_EXPONENT);
        }
    }

    // ========================================
    // Summit XP System
    // ========================================

    public static final double SUMMIT_XP_LEVEL_EXPONENT = 2.0; // Exponent for level formula (below softcap)
    public static final double SUMMIT_XP_LATE_EXPONENT = 4.0; // Exponent for level formula (above softcap)
    public static final long SUMMIT_MIN_VOLT = 1_000_000_000L; // Minimum volt for 1 XP (1B)
    // Divisor for late zone continuity: SOFTCAP^(LATE_EXP - LEVEL_EXP) = 1000^2 = 1,000,000
    private static final double SUMMIT_XP_LATE_DIVISOR =
        Math.pow(SUMMIT_XP_SOFTCAP, SUMMIT_XP_LATE_EXPONENT - SUMMIT_XP_LEVEL_EXPONENT);

    // Calibrated so 1 Decillion (10^33) accumulated volt = exactly level 1000 (SUMMIT_XP_SOFTCAP)
    // Derived: power = log(cumXp(1000)) / log(1Dc / MIN_VOLT)
    public static final double SUMMIT_XP_VOLT_POWER =
        Math.log(getCumulativeXpForLevel(SUMMIT_XP_SOFTCAP)) / Math.log(1e33 / SUMMIT_MIN_VOLT); // ~0.3552

    // Piecewise: above 1Dc, XP grows much slower (power 0.08 on the ratio above 1Dc)
    // At 1e100 volt -> ~level 13K. Keeps post-1000 progression meaningful but bounded.
    private static final double SUMMIT_XP_POST_DC_POWER = 0.08;
    private static final double SUMMIT_XP_AT_1DC = getCumulativeXpForLevel(SUMMIT_XP_SOFTCAP); // ~333.8M

    /**
     * Convert volt to XP.
     * Piecewise formula:
     *   Below 1Dc: (volt / MIN_VOLT)^0.3552  (calibrated for 1Dc = level 1000)
     *   Above 1Dc: XP_at_1Dc + ((volt / 1Dc)^0.08 - 1) * XP_at_1Dc
     */
    public static double voltToXp(BigNumber volt) {
        if (volt.lte(BigNumber.ZERO)) {
            return 0.0;
        }
        double ratio = volt.divide(BigNumber.fromLong(SUMMIT_MIN_VOLT)).toDouble();
        if (!Double.isFinite(ratio) || ratio < 1.0) {
            return 0.0;
        }
        // Below 1Dc: original power formula
        double dcRatio = 1e33 / SUMMIT_MIN_VOLT; // 1e24
        if (ratio <= dcRatio) {
            double xp = Math.pow(ratio, SUMMIT_XP_VOLT_POWER);
            if (!Double.isFinite(xp)) {
                return Double.MAX_VALUE;
            }
            return Math.floor(xp);
        }
        // Above 1Dc: piecewise soft growth
        double aboveDcRatio = ratio / dcRatio; // how many times above 1Dc
        double extraXp = (Math.pow(aboveDcRatio, SUMMIT_XP_POST_DC_POWER) - 1) * SUMMIT_XP_AT_1DC;
        double xp = SUMMIT_XP_AT_1DC + extraXp;
        if (!Double.isFinite(xp)) {
            return Double.MAX_VALUE;
        }
        return Math.floor(xp);
    }

    /**
     * Calculate volt needed to reach a given XP amount.
     * Inverse of voltToXp (piecewise).
     */
    public static BigNumber xpToVolt(double xp) {
        if (xp <= 0) {
            return BigNumber.ZERO;
        }
        // Below 1Dc threshold (XP <= XP_at_1Dc): original inverse
        if (xp <= SUMMIT_XP_AT_1DC) {
            double volt = Math.pow(xp, 1.0 / SUMMIT_XP_VOLT_POWER) * SUMMIT_MIN_VOLT;
            return BigNumber.fromDouble(volt);
        }
        // Above 1Dc: inverse of piecewise formula
        // xp = XP_at_1Dc + (aboveDcRatio^p - 1) * XP_at_1Dc
        // aboveDcRatio = ((xp - XP_at_1Dc) / XP_at_1Dc + 1)^(1/p)
        // volt = aboveDcRatio * 1Dc
        double scaledExtra = (xp - SUMMIT_XP_AT_1DC) / SUMMIT_XP_AT_1DC + 1.0;
        double aboveDcRatio = Math.pow(scaledExtra, 1.0 / SUMMIT_XP_POST_DC_POWER);
        double volt = aboveDcRatio * 1e33;
        return BigNumber.fromDouble(volt);
    }

    /**
     * Calculate XP required to reach a specific level (from level-1).
     * Below softcap: level^2
     * Above softcap: level^4 / 1000^2 (continuous at softcap boundary)
     */
    public static double getXpForLevel(int level) {
        if (level <= 0) return 0.0;
        if (level <= SUMMIT_XP_SOFTCAP) {
            return Math.pow(level, SUMMIT_XP_LEVEL_EXPONENT);
        }
        // level^E / SOFTCAP^(E-2) ensures continuity: at SOFTCAP, SOFTCAP^E/SOFTCAP^(E-2) = SOFTCAP^2
        return Math.pow(level, SUMMIT_XP_LATE_EXPONENT) / SUMMIT_XP_LATE_DIVISOR;
    }

    /**
     * Calculate cumulative XP required to reach a level (total from 0).
     * Below softcap: sum(i^2, i=1..n) = n*(n+1)*(2n+1)/6
     * Above softcap: base + sum(i^4/1000^2, i=SOFTCAP+1..n)
     */
    public static double getCumulativeXpForLevel(int level) {
        if (level <= 0) return 0.0;
        long n = level;
        if (level <= SUMMIT_XP_SOFTCAP) {
            return n * (n + 1) * (2 * n + 1) / 6.0;
        }
        // Cumulative up to softcap (closed-form sum of squares)
        long sc = SUMMIT_XP_SOFTCAP;
        double baseCum = sc * (sc + 1) * (2 * sc + 1) / 6.0;
        // Late zone: sum(i^4, i=SC+1..n) / LATE_DIVISOR
        // sum(i^4, i=1..n) = n(n+1)(2n+1)(3n^2+3n-1)/30
        double lateCum = (sumOfFourthPowers(n) - sumOfFourthPowers(sc)) / SUMMIT_XP_LATE_DIVISOR;
        return baseCum + lateCum;
    }

    /**
     * Closed-form sum of fourth powers: sum(i^4, i=1..n) = n(n+1)(2n+1)(3n^2+3n-1)/30
     * Computed in double to avoid intermediate overflow.
     */
    private static double sumOfFourthPowers(long n) {
        double d = (double) n;
        return d * (d + 1) * (2 * d + 1) * (3 * d * d + 3 * d - 1) / 30.0;
    }

    /**
     * Calculate the level achieved with given cumulative XP.
     * Uses binary search to find the highest level where
     * {@code getCumulativeXpForLevel(level) <= xp}.
     */
    public static int calculateLevelFromXp(double xp) {
        if (xp <= 0) return 0;
        // Dynamic upper bound: late zone cumXp ~ n^(E+1) / ((E+1) * DIVISOR)
        int hi = (int) Math.ceil(Math.pow((SUMMIT_XP_LATE_EXPONENT + 1) * SUMMIT_XP_LATE_DIVISOR * xp,
                1.0 / (SUMMIT_XP_LATE_EXPONENT + 1))) + 10;
        int lo = 0;
        while (lo < hi) {
            int mid = lo + (hi - lo + 1) / 2;
            double cumXp = getCumulativeXpForLevel(mid);
            if (!Double.isFinite(cumXp) || cumXp > xp) {
                hi = mid - 1;
            } else {
                lo = mid;
            }
        }
        return lo;
    }

    /**
     * Calculate XP progress within current level.
     * Returns [currentXpInLevel, xpRequiredForNextLevel]
     */
    public static double[] getXpProgress(double totalXp) {
        int level = calculateLevelFromXp(totalXp);
        double xpForCurrentLevel = getCumulativeXpForLevel(level);
        double xpInLevel = totalXp - xpForCurrentLevel;
        double xpForNextLevel = getXpForLevel(level + 1);
        return new double[]{xpInLevel, xpForNextLevel};
    }
}
