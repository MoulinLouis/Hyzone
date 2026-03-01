package io.hyvexa.common.math;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BigNumberTest {

    // --- Factories ---

    @Test
    void ofNormalizesMantissa() {
        BigNumber bn = BigNumber.of(50, 3);
        // 50e3 = 5e4
        assertEquals(4, bn.getExponent());
        assertEquals(5.0, bn.getMantissa(), 1e-9);
    }

    @Test
    void ofSmallMantissa() {
        BigNumber bn = BigNumber.of(0.05, 3);
        // 0.05e3 = 5e1
        assertEquals(1, bn.getExponent());
        assertEquals(5.0, bn.getMantissa(), 1e-9);
    }

    @Test
    void fromDoubleZero() {
        assertSame(BigNumber.ZERO, BigNumber.fromDouble(0.0));
    }

    @Test
    void fromDoubleNaN() {
        assertSame(BigNumber.ZERO, BigNumber.fromDouble(Double.NaN));
    }

    @Test
    void fromDoubleInfinity() {
        BigNumber pos = BigNumber.fromDouble(Double.POSITIVE_INFINITY);
        assertTrue(pos.getMantissa() > 0);
        BigNumber neg = BigNumber.fromDouble(Double.NEGATIVE_INFINITY);
        assertTrue(neg.getMantissa() < 0);
    }

    @Test
    void fromDoubleNormal() {
        BigNumber bn = BigNumber.fromDouble(1234.5);
        assertEquals(3, bn.getExponent());
        assertEquals(1.2345, bn.getMantissa(), 1e-9);
    }

    @Test
    void fromDoubleNegative() {
        BigNumber bn = BigNumber.fromDouble(-42.0);
        assertEquals(1, bn.getExponent());
        assertEquals(-4.2, bn.getMantissa(), 1e-9);
    }

    @Test
    void fromLongZero() {
        assertSame(BigNumber.ZERO, BigNumber.fromLong(0L));
    }

    @Test
    void fromLongNormal() {
        BigNumber bn = BigNumber.fromLong(5000L);
        assertEquals(3, bn.getExponent());
        assertEquals(5.0, bn.getMantissa(), 1e-9);
    }

    @Test
    void fromBigDecimalNull() {
        assertSame(BigNumber.ZERO, BigNumber.fromBigDecimal(null));
    }

    @Test
    void fromBigDecimalZero() {
        assertSame(BigNumber.ZERO, BigNumber.fromBigDecimal(BigDecimal.ZERO));
    }

    @Test
    void fromBigDecimalNormal() {
        BigNumber bn = BigNumber.fromBigDecimal(new BigDecimal("12345.678"));
        assertEquals(4, bn.getExponent());
        assertEquals(1.2345678, bn.getMantissa(), 1e-6);
    }

    // --- Arithmetic ---

    @Test
    void addSameExponent() {
        BigNumber a = BigNumber.of(3, 5);
        BigNumber b = BigNumber.of(4, 5);
        BigNumber result = a.add(b);
        assertEquals(5, result.getExponent());
        assertEquals(7.0, result.getMantissa(), 1e-9);
    }

    @Test
    void addDifferentExponents() {
        BigNumber a = BigNumber.of(1, 10);
        BigNumber b = BigNumber.of(5, 8);
        BigNumber result = a.add(b);
        // 1e10 + 5e8 = 1e10 + 0.05e10 = 1.05e10
        assertEquals(10, result.getExponent());
        assertEquals(1.05, result.getMantissa(), 1e-9);
    }

    @Test
    void addExponentDiffGreaterThan15LosesSmallerValue() {
        BigNumber big = BigNumber.of(1, 20);
        BigNumber tiny = BigNumber.of(1, 0);
        BigNumber result = big.add(tiny);
        // Difference is 20 > 15, smaller is lost
        assertEquals(big, result);
    }

    @Test
    void addZero() {
        BigNumber a = BigNumber.of(5, 3);
        assertSame(a, a.add(BigNumber.ZERO));
        assertSame(a, BigNumber.ZERO.add(a));
    }

    @Test
    void subtract() {
        BigNumber a = BigNumber.of(5, 3);
        BigNumber b = BigNumber.of(2, 3);
        BigNumber result = a.subtract(b);
        assertEquals(3, result.getExponent());
        assertEquals(3.0, result.getMantissa(), 1e-9);
    }

    @Test
    void subtractZero() {
        BigNumber a = BigNumber.of(5, 3);
        assertSame(a, a.subtract(BigNumber.ZERO));
    }

    @Test
    void multiply() {
        BigNumber a = BigNumber.of(3, 2); // 300
        BigNumber b = BigNumber.of(4, 3); // 4000
        BigNumber result = a.multiply(b);
        // 300 * 4000 = 1,200,000 = 1.2e6
        assertEquals(6, result.getExponent());
        assertEquals(1.2, result.getMantissa(), 1e-9);
    }

    @Test
    void multiplyByZero() {
        BigNumber a = BigNumber.of(5, 10);
        assertSame(BigNumber.ZERO, a.multiply(BigNumber.ZERO));
        assertSame(BigNumber.ZERO, BigNumber.ZERO.multiply(a));
    }

    @Test
    void divide() {
        BigNumber a = BigNumber.of(6, 6); // 6e6
        BigNumber b = BigNumber.of(3, 2); // 3e2
        BigNumber result = a.divide(b);
        // 6e6 / 3e2 = 2e4
        assertEquals(4, result.getExponent());
        assertEquals(2.0, result.getMantissa(), 1e-9);
    }

    @Test
    void divideByZeroThrows() {
        BigNumber a = BigNumber.of(1, 0);
        assertThrows(ArithmeticException.class, () -> a.divide(BigNumber.ZERO));
    }

    @Test
    void divideZeroByNonZero() {
        assertSame(BigNumber.ZERO, BigNumber.ZERO.divide(BigNumber.ONE));
    }

    // --- Power ---

    @Test
    void powZero() {
        BigNumber a = BigNumber.of(5, 3);
        assertEquals(BigNumber.ONE, a.pow(0));
    }

    @Test
    void powOne() {
        BigNumber a = BigNumber.of(5, 3);
        assertSame(a, a.pow(1));
    }

    @Test
    void powOfZero() {
        assertSame(BigNumber.ZERO, BigNumber.ZERO.pow(5));
    }

    @Test
    void powNormal() {
        BigNumber a = BigNumber.of(2, 0); // 2
        BigNumber result = a.pow(10); // 1024
        assertEquals(1024.0, result.toDouble(), 1.0);
    }

    @Test
    void powNegativeBaseOddPower() {
        BigNumber a = BigNumber.of(-3, 0); // -3
        BigNumber result = a.pow(3); // -27
        assertTrue(result.isNegative());
        assertEquals(-27.0, result.toDouble(), 0.1);
    }

    @Test
    void powNegativeBaseEvenPower() {
        BigNumber a = BigNumber.of(-3, 0); // -3
        BigNumber result = a.pow(2); // 9
        assertFalse(result.isNegative());
        assertEquals(9.0, result.toDouble(), 0.1);
    }

    // --- Comparison ---

    @Test
    void compareZeroVsZero() {
        assertEquals(0, BigNumber.ZERO.compareTo(BigNumber.of(0, 5)));
    }

    @Test
    void comparePositiveVsNegative() {
        assertTrue(BigNumber.of(1, 0).compareTo(BigNumber.of(-1, 0)) > 0);
    }

    @Test
    void compareNegativeVsPositive() {
        assertTrue(BigNumber.of(-1, 0).compareTo(BigNumber.of(1, 0)) < 0);
    }

    @Test
    void compareSameSignDifferentExponents() {
        assertTrue(BigNumber.of(1, 5).compareTo(BigNumber.of(9, 4)) > 0);
    }

    @Test
    void compareSameExponentDifferentMantissa() {
        assertTrue(BigNumber.of(5, 3).compareTo(BigNumber.of(3, 3)) > 0);
    }

    @Test
    void compareNegativeHigherExponentIsSmaller() {
        // -1e5 < -1e3 (more negative)
        assertTrue(BigNumber.of(-1, 5).compareTo(BigNumber.of(-1, 3)) < 0);
    }

    @Test
    void gteAndLte() {
        BigNumber a = BigNumber.of(5, 3);
        BigNumber b = BigNumber.of(5, 3);
        assertTrue(a.gte(b));
        assertTrue(a.lte(b));
    }

    @Test
    void gtAndLt() {
        BigNumber a = BigNumber.of(5, 3);
        BigNumber b = BigNumber.of(3, 3);
        assertTrue(a.gt(b));
        assertTrue(b.lt(a));
    }

    // --- Conversion ---

    @Test
    void toDoubleHugeExponent() {
        BigNumber bn = BigNumber.of(5, 500);
        assertEquals(Double.MAX_VALUE, bn.toDouble());
    }

    @Test
    void toDoubleHugeNegativeExponent() {
        BigNumber bn = BigNumber.of(5, 500);
        BigNumber neg = bn.negate();
        assertEquals(-Double.MAX_VALUE, neg.toDouble());
    }

    @Test
    void toDoubleTinyExponent() {
        BigNumber bn = BigNumber.of(5, -500);
        assertEquals(0.0, bn.toDouble());
    }

    @Test
    void toDoubleNormal() {
        BigNumber bn = BigNumber.of(2.5, 3);
        assertEquals(2500.0, bn.toDouble(), 1e-9);
    }

    @Test
    void toLongOverflow() {
        BigNumber bn = BigNumber.of(1, 20);
        assertEquals(Long.MAX_VALUE, bn.toLong());
    }

    @Test
    void toLongNegativeOverflow() {
        BigNumber bn = BigNumber.of(-1, 20);
        assertEquals(Long.MIN_VALUE, bn.toLong());
    }

    @Test
    void toLongNormal() {
        BigNumber bn = BigNumber.of(5, 3);
        assertEquals(5000L, bn.toLong());
    }

    @Test
    void toLongNegativeExponent() {
        BigNumber bn = BigNumber.of(5, -1);
        assertEquals(0L, bn.toLong());
    }

    // --- Equality ---

    @Test
    void zeroEqualsZeroWithDifferentExponent() {
        assertEquals(BigNumber.ZERO, BigNumber.of(0, 5));
    }

    @Test
    void equalNumbers() {
        assertEquals(BigNumber.of(5, 3), BigNumber.of(5, 3));
    }

    @Test
    void unequalMantissa() {
        assertNotEquals(BigNumber.of(5, 3), BigNumber.of(4, 3));
    }

    @Test
    void unequalExponent() {
        assertNotEquals(BigNumber.of(5, 3), BigNumber.of(5, 4));
    }

    // --- Misc ---

    @Test
    void negate() {
        BigNumber a = BigNumber.of(5, 3);
        BigNumber neg = a.negate();
        assertEquals(-5.0, neg.getMantissa(), 1e-9);
        assertEquals(3, neg.getExponent());
    }

    @Test
    void negateZero() {
        assertSame(BigNumber.ZERO, BigNumber.ZERO.negate());
    }

    @Test
    void abs() {
        BigNumber a = BigNumber.of(-5, 3);
        BigNumber result = a.abs();
        assertEquals(5.0, result.getMantissa(), 1e-9);
    }

    @Test
    void maxAndMin() {
        BigNumber a = BigNumber.of(5, 3);
        BigNumber b = BigNumber.of(3, 3);
        assertSame(a, a.max(b));
        assertSame(b, a.min(b));
    }

    @Test
    void toStringZero() {
        assertEquals("0", BigNumber.ZERO.toString());
    }

    @Test
    void toStringNoExponent() {
        assertEquals("1.0000", BigNumber.ONE.toString());
    }

    @Test
    void toStringWithExponent() {
        BigNumber bn = BigNumber.of(2.5, 10);
        assertEquals("2.5000e10", bn.toString());
    }

    @Test
    void isZero() {
        assertTrue(BigNumber.ZERO.isZero());
        assertFalse(BigNumber.ONE.isZero());
    }

    @Test
    void isNegative() {
        assertTrue(BigNumber.of(-3, 0).isNegative());
        assertFalse(BigNumber.of(3, 0).isNegative());
    }
}
