package io.hyvexa.common.ghost;

/**
 * Represents a single position snapshot in a ghost recording.
 * Captured every 50ms during parkour runs.
 */
public record GhostSample(
    double x,
    double y,
    double z,
    float yaw,
    long timestampMs
) {
    /**
     * Convert position to array format for compatibility with existing teleport code.
     */
    public double[] toPositionArray() {
        return new double[]{x, y, z};
    }
}
