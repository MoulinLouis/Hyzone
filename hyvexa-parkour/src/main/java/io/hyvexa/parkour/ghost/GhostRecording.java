package io.hyvexa.parkour.ghost;

import io.hyvexa.common.ghost.GhostInterpolation;

import java.util.ArrayList;
import java.util.List;

public class GhostRecording {
    private static final GhostInterpolation.SampleAdapter<GhostSample> SAMPLE_ADAPTER =
            new GhostInterpolation.SampleAdapter<>() {
                @Override
                public long timestampMs(GhostSample sample) {
                    return sample.timestampMs();
                }

                @Override
                public double x(GhostSample sample) {
                    return sample.x();
                }

                @Override
                public double y(GhostSample sample) {
                    return sample.y();
                }

                @Override
                public double z(GhostSample sample) {
                    return sample.z();
                }

                @Override
                public float yaw(GhostSample sample) {
                    return sample.yaw();
                }

                @Override
                public GhostSample create(double x, double y, double z, float yaw, long timestampMs) {
                    return new GhostSample(x, y, z, yaw, timestampMs);
                }
            };

    private final List<GhostSample> samples;
    private final long completionTimeMs;

    public GhostRecording(List<GhostSample> samples, long completionTimeMs) {
        this.samples = new ArrayList<>(samples);
        this.completionTimeMs = completionTimeMs;
    }

    public List<GhostSample> getSamples() {
        return samples;
    }

    public long getCompletionTimeMs() {
        return completionTimeMs;
    }

    public GhostSample interpolateAt(double progress) {
        return GhostInterpolation.interpolateAt(
                samples,
                completionTimeMs,
                progress,
                SAMPLE_ADAPTER,
                () -> new GhostSample(0, 0, 0, 0, 0)
        );
    }
}
