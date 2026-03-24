package io.hyvexa.common.ghost;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GhostSampleTest {

    @Test
    void toPositionArrayReturnsXYZ() {
        GhostSample sample = new GhostSample(1.5, 2.5, 3.5, 90f, 1000L);
        assertArrayEquals(new double[]{1.5, 2.5, 3.5}, sample.toPositionArray());
    }

    @Test
    void recordAccessorsReturnConstructorValues() {
        GhostSample sample = new GhostSample(10.0, 20.0, 30.0, 45f, 5000L);
        assertEquals(10.0, sample.x());
        assertEquals(20.0, sample.y());
        assertEquals(30.0, sample.z());
        assertEquals(45f, sample.yaw());
        assertEquals(5000L, sample.timestampMs());
    }

    @Test
    void equalSamplesAreEqual() {
        GhostSample a = new GhostSample(1.0, 2.0, 3.0, 90f, 1000L);
        GhostSample b = new GhostSample(1.0, 2.0, 3.0, 90f, 1000L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentSamplesAreNotEqual() {
        GhostSample a = new GhostSample(1.0, 2.0, 3.0, 90f, 1000L);
        GhostSample b = new GhostSample(4.0, 5.0, 6.0, 180f, 2000L);
        assertNotEquals(a, b);
    }
}
