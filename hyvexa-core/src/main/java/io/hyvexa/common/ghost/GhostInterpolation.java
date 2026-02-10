package io.hyvexa.common.ghost;

import java.util.List;
import java.util.function.Supplier;

/** Shared interpolation math for ghost playback across modes. */
public final class GhostInterpolation {

    private GhostInterpolation() {
    }

    public interface SampleAdapter<TSample> {
        long timestampMs(TSample sample);

        double x(TSample sample);

        double y(TSample sample);

        double z(TSample sample);

        float yaw(TSample sample);

        TSample create(double x, double y, double z, float yaw, long timestampMs);
    }

    public static <TSample> TSample interpolateAt(List<TSample> samples, long completionTimeMs, double progress,
                                                  SampleAdapter<TSample> adapter,
                                                  Supplier<TSample> emptySupplier) {
        if (samples == null || samples.isEmpty()) {
            return emptySupplier.get();
        }
        if (samples.size() == 1 || progress <= 0.0) {
            return samples.get(0);
        }
        if (progress >= 1.0) {
            return samples.get(samples.size() - 1);
        }

        long targetTimestamp = (long) (progress * completionTimeMs);

        int low = 0;
        int high = samples.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long timestamp = adapter.timestampMs(samples.get(mid));
            if (timestamp < targetTimestamp) {
                low = mid + 1;
            } else if (timestamp > targetTimestamp) {
                high = mid - 1;
            } else {
                return samples.get(mid);
            }
        }

        int upperIndex = Math.min(samples.size() - 1, Math.max(0, low));
        int lowerIndex = Math.max(0, upperIndex - 1);

        TSample lower = samples.get(lowerIndex);
        TSample upper = samples.get(upperIndex);

        long lowerTimestamp = adapter.timestampMs(lower);
        long upperTimestamp = adapter.timestampMs(upper);
        if (lowerTimestamp == upperTimestamp) {
            return lower;
        }

        double timeDiff = upperTimestamp - lowerTimestamp;
        double timeOffset = targetTimestamp - lowerTimestamp;
        double t = timeOffset / timeDiff;

        double x = lerp(adapter.x(lower), adapter.x(upper), t);
        double y = lerp(adapter.y(lower), adapter.y(upper), t);
        double z = lerp(adapter.z(lower), adapter.z(upper), t);
        float yaw = lerpAngle(adapter.yaw(lower), adapter.yaw(upper), (float) t);

        return adapter.create(x, y, z, yaw, targetTimestamp);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        a = normalizeAngle(a);
        b = normalizeAngle(b);

        float diff = b - a;
        if (diff > 180.0f) {
            diff -= 360.0f;
        } else if (diff < -180.0f) {
            diff += 360.0f;
        }

        float result = a + diff * t;
        return normalizeAngle(result);
    }

    private static float normalizeAngle(float angle) {
        angle = angle % 360.0f;
        if (angle > 180.0f) {
            angle -= 360.0f;
        } else if (angle < -180.0f) {
            angle += 360.0f;
        }
        return angle;
    }
}
