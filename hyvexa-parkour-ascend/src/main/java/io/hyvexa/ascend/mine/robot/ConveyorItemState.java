package io.hyvexa.ascend.mine.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

public class ConveyorItemState {
    private final UUID ownerId;
    private final String mineId;
    private final String blockType;
    private final String worldName;
    private final long spawnTimeMs;
    private final long travelTimeMs;
    private Ref<EntityStore> entityRef;

    private final double[][] waypoints;
    private final double[] segmentDistances;
    private final double totalDistance;

    public ConveyorItemState(UUID ownerId, String mineId, String worldName,
                              String blockType, double speed, double[][] waypoints) {
        this.ownerId = ownerId;
        this.mineId = mineId;
        this.worldName = worldName;
        this.blockType = blockType;
        this.spawnTimeMs = System.currentTimeMillis();
        this.waypoints = waypoints;

        // Pre-compute segment distances
        this.segmentDistances = new double[waypoints.length - 1];
        double total = 0;
        for (int i = 0; i < segmentDistances.length; i++) {
            double dx = waypoints[i + 1][0] - waypoints[i][0];
            double dy = waypoints[i + 1][1] - waypoints[i][1];
            double dz = waypoints[i + 1][2] - waypoints[i][2];
            segmentDistances[i] = Math.sqrt(dx * dx + dy * dy + dz * dz);
            total += segmentDistances[i];
        }
        this.totalDistance = total;
        long computedMs = (long) (total / speed * 1000);
        this.travelTimeMs = Math.max(100, computedMs);
    }

    /** Returns 0.0 to 1.0+ based on elapsed time. */
    public double getProgress(long now) {
        long elapsed = now - spawnTimeMs;
        return (double) elapsed / travelTimeMs;
    }

    public boolean isComplete(long now) {
        return getProgress(now) >= 1.0;
    }

    /** Get interpolated position along the waypoint path. */
    public double[] getPosition(long now) {
        double t = Math.min(1.0, getProgress(now));
        if (totalDistance <= 0 || waypoints.length < 2) {
            return waypoints[waypoints.length - 1];
        }
        double targetDist = t * totalDistance;
        double walked = 0;
        for (int i = 0; i < segmentDistances.length; i++) {
            if (walked + segmentDistances[i] >= targetDist) {
                double local = segmentDistances[i] > 0
                    ? (targetDist - walked) / segmentDistances[i] : 0;
                return lerp(waypoints[i], waypoints[i + 1], local);
            }
            walked += segmentDistances[i];
        }
        return waypoints[waypoints.length - 1];
    }

    public double getX(long now) { return getPosition(now)[0]; }
    public double getY(long now) { return getPosition(now)[1]; }
    public double getZ(long now) { return getPosition(now)[2]; }

    private static double[] lerp(double[] a, double[] b, double t) {
        return new double[]{
            a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t
        };
    }

    public UUID getOwnerId() { return ownerId; }
    public String getMineId() { return mineId; }
    public String getWorldName() { return worldName; }
    public String getBlockType() { return blockType; }
    public Ref<EntityStore> getEntityRef() { return entityRef; }
    public void setEntityRef(Ref<EntityStore> entityRef) { this.entityRef = entityRef; }
}
