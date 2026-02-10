package io.hyvexa.common.ghost;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GhostInterpolationTest {

    private static final GhostInterpolation.SampleAdapter<Sample> SAMPLE_ADAPTER =
            new GhostInterpolation.SampleAdapter<>() {
                @Override
                public long timestampMs(Sample sample) {
                    return sample.timestampMs;
                }

                @Override
                public double x(Sample sample) {
                    return sample.x;
                }

                @Override
                public double y(Sample sample) {
                    return sample.y;
                }

                @Override
                public double z(Sample sample) {
                    return sample.z;
                }

                @Override
                public float yaw(Sample sample) {
                    return sample.yaw;
                }

                @Override
                public Sample create(double x, double y, double z, float yaw, long timestampMs) {
                    return new Sample(x, y, z, yaw, timestampMs);
                }
            };

    @Test
    void interpolateAtReturnsBoundarySamples() {
        List<Sample> samples = List.of(
                new Sample(0, 0, 0, 10f, 0),
                new Sample(10, 10, 10, 20f, 1000)
        );

        Sample start = GhostInterpolation.interpolateAt(samples, 1000, 0.0, SAMPLE_ADAPTER,
                () -> new Sample(0, 0, 0, 0, 0));
        Sample end = GhostInterpolation.interpolateAt(samples, 1000, 1.0, SAMPLE_ADAPTER,
                () -> new Sample(0, 0, 0, 0, 0));

        assertEquals(0.0, start.x, 0.0001);
        assertEquals(10.0, end.x, 0.0001);
    }

    @Test
    void interpolateAtInterpolatesPositionAndYawWithWrap() {
        List<Sample> samples = List.of(
                new Sample(0, 0, 0, 170f, 0),
                new Sample(10, 10, 10, -170f, 1000)
        );

        Sample mid = GhostInterpolation.interpolateAt(samples, 1000, 0.5, SAMPLE_ADAPTER,
                () -> new Sample(0, 0, 0, 0, 0));

        assertEquals(5.0, mid.x, 0.0001);
        assertEquals(5.0, mid.y, 0.0001);
        assertEquals(5.0, mid.z, 0.0001);
        assertEquals(180.0f, mid.yaw, 0.0001f);
        assertEquals(500L, mid.timestampMs);
    }

    @Test
    void interpolateAtReturnsEmptySampleForEmptyList() {
        Sample empty = GhostInterpolation.interpolateAt(List.of(), 1000, 0.5, SAMPLE_ADAPTER,
                () -> new Sample(1, 2, 3, 4, 5));

        assertEquals(1.0, empty.x, 0.0001);
        assertEquals(2.0, empty.y, 0.0001);
        assertEquals(3.0, empty.z, 0.0001);
        assertEquals(4.0f, empty.yaw, 0.0001f);
        assertEquals(5L, empty.timestampMs);
    }

    private static final class Sample {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final long timestampMs;

        private Sample(double x, double y, double z, float yaw, long timestampMs) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.timestampMs = timestampMs;
        }
    }
}
