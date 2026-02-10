package io.hyvexa.ascend.ghost;

import java.util.ArrayList;
import java.util.List;

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

        // Angular interpolation for yaw (handle -180/+180 wrapping with shortest path)
        float yaw = lerpAngle(lower.yaw(), upper.yaw(), (float) t);

        return new GhostSample(x, y, z, yaw, targetTimestamp);
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Angular interpolation with wrapping for yaw rotation.
     * Handles the -180 to +180 degree boundary correctly.
     * Uses shortest path interpolation to avoid spinning through 360 degrees.
     */
    private float lerpAngle(float a, float b, float t) {
        // Normalize angles to -180 to +180 range (Hytale's native format)
        a = normalizeAngle(a);
        b = normalizeAngle(b);

        // Calculate shortest angular path
        float diff = b - a;

        // If difference is greater than 180°, we're going the long way around
        // Adjust to take the shorter path by wrapping
        if (diff > 180.0f) {
            diff -= 360.0f;
        } else if (diff < -180.0f) {
            diff += 360.0f;
        }

        // Interpolate along the shortest path
        float result = a + diff * t;

        // Normalize result back to -180 to +180 range
        return normalizeAngle(result);
    }

    /**
     * Normalize angle to -180 to +180 range (Hytale's yaw convention).
     * This prevents discontinuities when interpolating across the ±180° boundary.
     */
    private float normalizeAngle(float angle) {
        // Reduce to -360 to +360 range first
        angle = angle % 360.0f;

        // Map to -180 to +180 range
        if (angle > 180.0f) {
            angle -= 360.0f;
        } else if (angle < -180.0f) {
            angle += 360.0f;
        }

        return angle;
    }
}
