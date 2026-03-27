package io.hyvexa.ascend;

import io.hyvexa.common.math.BigNumber;

public final class ElevationConstants {

    private ElevationConstants() {
    }

    public static double getElevationMultiplier(int level) {
        return Math.max(1, level);
    }

    public static String formatElevationMultiplier(int level) {
        return "x" + (int) getElevationMultiplier(level);
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

    public static BigNumber getElevationLevelUpCost(int currentLevel) {
        return getElevationLevelUpCost(currentLevel, BigNumber.ONE);
    }

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

    public static ElevationPurchaseResult calculateElevationPurchase(int currentLevel, BigNumber availableVolt) {
        return calculateElevationPurchase(currentLevel, availableVolt, BigNumber.ONE);
    }

    // Exponential probing + binary search for upper bound, then precise iteration.
    public static ElevationPurchaseResult calculateElevationPurchase(int currentLevel, BigNumber availableVolt, BigNumber costMultiplier) {
        if (availableVolt.lte(BigNumber.ZERO)
                || costMultiplier.lte(BigNumber.ZERO)) {
            return new ElevationPurchaseResult(0, BigNumber.ZERO);
        }

        // Find upper bound: max level where a single level's cost <= budget.
        // Beyond this, no level can be afforded even individually.
        int upperBound = currentLevel;
        int step = 1;
        while (getElevationLevelUpCost(upperBound + step, costMultiplier).lte(availableVolt)) {
            upperBound += step;
            step *= 2;
        }
        // Binary search for exact boundary
        int lo = upperBound, hi = upperBound + step;
        while (lo < hi) {
            int mid = lo + (hi - lo + 1) / 2;
            if (getElevationLevelUpCost(mid, costMultiplier).lte(availableVolt)) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        int maxLevel = lo;

        // Iterate precisely from currentLevel to maxLevel, summing costs
        int levelsAffordable = 0;
        BigNumber totalCost = BigNumber.ZERO;
        int level = currentLevel;

        while (level <= maxLevel) {
            BigNumber nextCost = getElevationLevelUpCost(level, costMultiplier);
            BigNumber newTotal = totalCost.add(nextCost);

            if (newTotal.gt(availableVolt)) {
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
}
