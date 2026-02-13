package io.hyvexa.common.math;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Lightweight immutable scientific notation number for idle/incremental games.
 * Stores value as mantissa × 10^exponent where mantissa ∈ [1,10) (or 0 for zero).
 * Thread-safe (immutable) — use with {@code AtomicReference<BigNumber>}.
 */
public final class BigNumber implements Comparable<BigNumber> {

    private final double mantissa; // [1,10) or 0; can be negative
    private final int exponent;    // power of 10

    public static final BigNumber ZERO = new BigNumber(0, 0);
    public static final BigNumber ONE = new BigNumber(1, 0);

    private BigNumber(double mantissa, int exponent) {
        this.mantissa = mantissa;
        this.exponent = exponent;
    }

    // Factories

    /**
     * Create a BigNumber from mantissa and exponent, normalizing automatically.
     */
    public static BigNumber of(double mantissa, int exponent) {
        return normalize(mantissa, exponent);
    }

    public static BigNumber fromDouble(double value) {
        if (value == 0.0 || Double.isNaN(value)) {
            return ZERO;
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? of(9.999999999, Integer.MAX_VALUE / 2) : of(-9.999999999, Integer.MAX_VALUE / 2);
        }
        double abs = Math.abs(value);
        int exp = (int) Math.floor(Math.log10(abs));
        double m = value / Math.pow(10.0, exp);
        return normalize(m, exp);
    }

    public static BigNumber fromLong(long value) {
        if (value == 0L) {
            return ZERO;
        }
        return fromDouble((double) value);
    }

    /**
     * Convert from BigDecimal without double overflow.
     * Uses precision() - scale() - 1 for exponent, then scaleByPowerOfTen for mantissa.
     * Note: mantissa is stored as double, so precision beyond ~15 significant digits is lost.
     */
    public static BigNumber fromBigDecimal(BigDecimal bd) {
        if (bd == null || bd.signum() == 0) {
            return ZERO;
        }
        // Number of digits left of decimal point minus 1 = exponent
        // precision() gives total significant digits, scale() gives digits after decimal
        // integerPartDigits = precision - scale (when positive)
        int exp = bd.precision() - bd.scale() - 1;
        // mantissa = bd / 10^exp
        BigDecimal mantissaBd = bd.scaleByPowerOfTen(-exp);
        double m = mantissaBd.doubleValue();
        return normalize(m, exp);
    }

    // Normalization

    private static BigNumber normalize(double mantissa, int exponent) {
        if (mantissa == 0.0 || Double.isNaN(mantissa)) {
            return ZERO;
        }

        double abs = Math.abs(mantissa);

        // Shift mantissa into [1, 10) using O(1) log10
        int shift = (int) Math.floor(Math.log10(abs));
        if (shift != 0) {
            abs /= Math.pow(10.0, shift);
            exponent += shift;
        }

        // Restore sign
        if (mantissa < 0) {
            abs = -abs;
        }

        return new BigNumber(abs, exponent);
    }

    // Arithmetic

    public BigNumber add(BigNumber other) {
        if (this.isZero()) return other;
        if (other.isZero()) return this;

        int expDiff = this.exponent - other.exponent;

        // If exponent difference > 15, smaller value is lost in double precision
        if (expDiff > 15) return this;
        if (expDiff < -15) return other;

        // Align to larger exponent
        double m1, m2;
        int resultExp;
        if (this.exponent >= other.exponent) {
            resultExp = this.exponent;
            m1 = this.mantissa;
            m2 = other.mantissa / Math.pow(10.0, expDiff);
        } else {
            resultExp = other.exponent;
            m1 = this.mantissa / Math.pow(10.0, -expDiff);
            m2 = other.mantissa;
        }

        return normalize(m1 + m2, resultExp);
    }

    public BigNumber subtract(BigNumber other) {
        if (other.isZero()) return this;
        return this.add(other.negate());
    }

    public BigNumber multiply(BigNumber other) {
        if (this.isZero() || other.isZero()) return ZERO;
        double m = this.mantissa * other.mantissa;
        int e = this.exponent + other.exponent;
        return normalize(m, e);
    }

    public BigNumber divide(BigNumber other) {
        if (other.isZero()) {
            throw new ArithmeticException("Division by zero");
        }
        if (this.isZero()) return ZERO;
        double m = this.mantissa / other.mantissa;
        int e = this.exponent - other.exponent;
        return normalize(m, e);
    }

    /**
     * Raise to integer power using log10 approach.
     * result_log10 = n * (log10(mantissa) + exponent)
     */
    public BigNumber pow(int n) {
        if (n == 0) return ONE;
        if (this.isZero()) return ZERO;
        if (n == 1) return this;

        // Use log10 for large powers to avoid overflow
        double log10 = n * (Math.log10(Math.abs(this.mantissa)) + this.exponent);
        int resultExp = (int) Math.floor(log10);
        double resultMantissa = Math.pow(10.0, log10 - resultExp);

        // Handle sign: negative base with odd power = negative
        if (this.mantissa < 0 && (n & 1) == 1) {
            resultMantissa = -resultMantissa;
        }

        return normalize(resultMantissa, resultExp);
    }

    public BigNumber negate() {
        if (isZero()) return ZERO;
        return new BigNumber(-mantissa, exponent);
    }

    public BigNumber abs() {
        if (mantissa >= 0) return this;
        return new BigNumber(-mantissa, exponent);
    }

    public BigNumber max(BigNumber other) {
        return this.compareTo(other) >= 0 ? this : other;
    }

    public BigNumber min(BigNumber other) {
        return this.compareTo(other) <= 0 ? this : other;
    }

    // Comparison

    @Override
    public int compareTo(BigNumber other) {
        // Handle signs
        boolean thisNeg = this.mantissa < 0;
        boolean otherNeg = other.mantissa < 0;
        if (this.isZero() && other.isZero()) return 0;
        if (this.isZero()) return otherNeg ? 1 : -1;
        if (other.isZero()) return thisNeg ? -1 : 1;
        if (thisNeg && !otherNeg) return -1;
        if (!thisNeg && otherNeg) return 1;

        // Same sign — compare by exponent first
        int expCmp = Integer.compare(this.exponent, other.exponent);
        if (expCmp != 0) {
            // For negative numbers, higher exponent means more negative = smaller
            return thisNeg ? -expCmp : expCmp;
        }

        // Same exponent — compare mantissa
        return Double.compare(this.mantissa, other.mantissa);
    }

    public boolean gte(BigNumber other) {
        return compareTo(other) >= 0;
    }

    public boolean lte(BigNumber other) {
        return compareTo(other) <= 0;
    }

    public boolean gt(BigNumber other) {
        return compareTo(other) > 0;
    }

    public boolean lt(BigNumber other) {
        return compareTo(other) < 0;
    }

    public boolean isZero() {
        return mantissa == 0.0;
    }

    public boolean isNegative() {
        return mantissa < 0;
    }

    // Conversion

    /**
     * Convert to double. For values beyond double range, returns
     * Double.MAX_VALUE or Double.POSITIVE_INFINITY.
     */
    public double toDouble() {
        if (isZero()) return 0.0;
        if (exponent > 308) return mantissa > 0 ? Double.MAX_VALUE : -Double.MAX_VALUE;
        if (exponent < -308) return 0.0;
        return mantissa * Math.pow(10.0, exponent);
    }

    /**
     * Convert to long (truncated). Returns Long.MAX_VALUE if too large.
     */
    public long toLong() {
        if (isZero()) return 0L;
        if (exponent > 18) return mantissa > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        if (exponent < 0) return 0L;
        double value = mantissa * Math.pow(10.0, exponent);
        if (value > Long.MAX_VALUE) return Long.MAX_VALUE;
        if (value < Long.MIN_VALUE) return Long.MIN_VALUE;
        return (long) value;
    }

    // Accessors

    public double getMantissa() {
        return mantissa;
    }

    public int getExponent() {
        return exponent;
    }

    // Object overrides

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BigNumber other)) return false;
        if (this.isZero() && other.isZero()) return true;
        return Double.compare(this.mantissa, other.mantissa) == 0
            && this.exponent == other.exponent;
    }

    @Override
    public int hashCode() {
        if (isZero()) return 0;
        return 31 * Double.hashCode(mantissa) + exponent;
    }

    @Override
    public String toString() {
        if (isZero()) return "0";
        if (exponent == 0) return String.format(Locale.ROOT, "%.4f", mantissa);
        return String.format(Locale.ROOT, "%.4fe%d", mantissa, exponent);
    }
}
