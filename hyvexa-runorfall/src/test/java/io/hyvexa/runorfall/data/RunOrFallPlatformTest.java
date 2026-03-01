package io.hyvexa.runorfall.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunOrFallPlatformTest {

    @Test
    void constructorNormalizesCoordinates() {
        // Pass swapped min/max
        RunOrFallPlatform p = new RunOrFallPlatform(10, 20, 30, 0, 5, 10);
        assertEquals(0, p.minX);
        assertEquals(5, p.minY);
        assertEquals(10, p.minZ);
        assertEquals(10, p.maxX);
        assertEquals(20, p.maxY);
        assertEquals(30, p.maxZ);
    }

    @Test
    void containsInside() {
        RunOrFallPlatform p = new RunOrFallPlatform(0, 0, 0, 10, 10, 10);
        assertTrue(p.contains(5, 5, 5));
    }

    @Test
    void containsOnBoundary() {
        RunOrFallPlatform p = new RunOrFallPlatform(0, 0, 0, 10, 10, 10);
        assertTrue(p.contains(0, 0, 0));
        assertTrue(p.contains(10, 10, 10));
    }

    @Test
    void containsAtCorners() {
        RunOrFallPlatform p = new RunOrFallPlatform(0, 0, 0, 10, 10, 10);
        assertTrue(p.contains(0, 10, 0));
        assertTrue(p.contains(10, 0, 10));
    }

    @Test
    void containsFalseOutsideX() {
        RunOrFallPlatform p = new RunOrFallPlatform(0, 0, 0, 10, 10, 10);
        assertFalse(p.contains(-1, 5, 5));
        assertFalse(p.contains(11, 5, 5));
    }

    @Test
    void containsFalseOutsideY() {
        RunOrFallPlatform p = new RunOrFallPlatform(0, 0, 0, 10, 10, 10);
        assertFalse(p.contains(5, -1, 5));
        assertFalse(p.contains(5, 11, 5));
    }

    @Test
    void containsFalseOutsideZ() {
        RunOrFallPlatform p = new RunOrFallPlatform(0, 0, 0, 10, 10, 10);
        assertFalse(p.contains(5, 5, -1));
        assertFalse(p.contains(5, 5, 11));
    }

    @Test
    void copyCreatesIndependentCopy() {
        RunOrFallPlatform original = new RunOrFallPlatform(1, 2, 3, 4, 5, 6, "stone");
        RunOrFallPlatform copy = original.copy();

        assertEquals(original.minX, copy.minX);
        assertEquals(original.maxY, copy.maxY);
        assertEquals("stone", copy.targetBlockItemId);

        // Mutating copy doesn't affect original
        copy.minX = 99;
        assertEquals(1, original.minX);
    }

    @Test
    void nullTargetBlockItemIdDefaultsToEmpty() {
        RunOrFallPlatform p = new RunOrFallPlatform(0, 0, 0, 1, 1, 1, null);
        assertEquals("", p.targetBlockItemId);
    }

    @Test
    void defaultConstructor() {
        RunOrFallPlatform p = new RunOrFallPlatform();
        assertEquals(0, p.minX);
        assertEquals("", p.targetBlockItemId);
    }

    @Test
    void twoArgConstructorDefaultsTargetBlock() {
        RunOrFallPlatform p = new RunOrFallPlatform(0, 0, 0, 5, 5, 5);
        assertEquals("", p.targetBlockItemId);
    }
}
