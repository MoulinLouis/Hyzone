package io.hyvexa.ascend.ghost;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a complete ghost recording with interpolation logic for smooth playback.
 * Samples are captured at 50ms intervals and interpolated to 60fps during playback.
 */
public class GhostRecording {
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

    /**
     * Interpolate ghost position at a specific progress point.
     *
     * @param progress 0.0 to 1.0 representing position through the run
     * @param speedMultiplier Time compression factor (e.g., 2.0 = 2x speed)
     * @return Interpolated sample at the target position
     */
    public GhostSample interpolateAt(double progress, double speedMultiplier) {
        if (samples.isEmpty()) {
            return new GhostSample(0, 0, 0, 0, 0);
        }

        if (samples.size() == 1 || progress <= 0.0) {
            return samples.get(0);
        }

        if (progress >= 1.0) {
            return samples.get(samples.size() - 1);
        }

        // Calculate target timestamp with speed multiplier applied
        long targetTimestamp = (long) (progress * completionTimeMs);

        // Find the two nearest samples surrounding the target timestamp
        int lowerIndex = 0;
        int upperIndex = samples.size() - 1;

        for (int i = 0; i < samples.size() - 1; i++) {
            if (samples.get(i).timestampMs() <= targetTimestamp &&
                samples.get(i + 1).timestampMs() >= targetTimestamp) {
                lowerIndex = i;
                upperIndex = i + 1;
                break;
            }
        }

        GhostSample lower = samples.get(lowerIndex);
        GhostSample upper = samples.get(upperIndex);

        // If timestamps are identical, return lower sample
        if (lower.timestampMs() == upper.timestampMs()) {
            return lower;
        }

        // Calculate interpolation factor between the two samples
        double timeDiff = upper.timestampMs() - lower.timestampMs();
        double timeOffset = targetTimestamp - lower.timestampMs();
        double t = timeOffset / timeDiff;

        // Linear interpolation for position
        double x = lerp(lower.x(), upper.x(), t);
        double y = lerp(lower.y(), upper.y(), t);
        double z = lerp(lower.z(), upper.z(), t);

        // Angular interpolation for yaw (handle 0-360 wrapping)
        float yaw = lerpAngle(lower.yaw(), upper.yaw(), (float) t);

        return new GhostSample(x, y, z, yaw, targetTimestamp);
    }

    /**
     * Linear interpolation between two values.
     */
    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Angular interpolation with wrapping for yaw rotation.
     * Handles the 0-360 degree boundary correctly.
     */
    private float lerpAngle(float a, float b, float t) {
        // Normalize angles to 0-360 range
        a = normalizeAngle(a);
        b = normalizeAngle(b);

        // Calculate shortest path
        float diff = b - a;
        if (diff > 180) {
            diff -= 360;
        } else if (diff < -180) {
            diff += 360;
        }

        return normalizeAngle(a + diff * t);
    }

    /**
     * Normalize angle to 0-360 range.
     */
    private float normalizeAngle(float angle) {
        angle = angle % 360;
        if (angle < 0) {
            angle += 360;
        }
        return angle;
    }
}
