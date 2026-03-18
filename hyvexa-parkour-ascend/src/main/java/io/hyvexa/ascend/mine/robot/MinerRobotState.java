package io.hyvexa.ascend.mine.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

public class MinerRobotState {
    public enum MinerPhase { IDLE, MOVING, MINING, STOPPED }

    public static final int MAX_SPEED_PER_STAR = 25;
    public static final int MAX_STARS = 5;

    private final UUID ownerId;
    private final String mineId;
    private Ref<EntityStore> entityRef;
    private UUID entityUuid;

    private volatile int speedLevel = 0;
    private volatile int stars = 0;
    private volatile MinerPhase phase = MinerPhase.IDLE;
    private long phaseStartTime = 0;
    private long cycleStartTime = 0;

    // Target block to mine
    private int targetBlockX, targetBlockY, targetBlockZ;
    private boolean hasTarget = false;

    // Current interpolated position (for smooth movement)
    private double currentX, currentY, currentZ;
    private boolean positionInitialized = false;

    // World ref name for resolving world each tick
    private String worldName;

    public MinerRobotState(UUID ownerId, String mineId) {
        this.ownerId = ownerId;
        this.mineId = mineId;
    }

    /** Blocks produced per minute at current speed/star level. */
    public double getProductionRate() {
        return getProductionRate(speedLevel, stars);
    }

    public static double getProductionRate(int speedLevel, int stars) {
        double base = 6.0;
        double speedMult = 1.0 + speedLevel * 0.10;
        double starMult = 1.0 + stars * 0.5;
        return base * speedMult * starMult;
    }

    /** Milliseconds between each block production. */
    public long getProductionIntervalMs() {
        double blocksPerMinute = getProductionRate();
        return (long) (60_000.0 / blocksPerMinute);
    }

    /** Milliseconds allocated for walking to the target block. */
    public long getMoveDurationMs() {
        return (long) (getProductionIntervalMs() * 0.40);
    }

    /** Milliseconds allocated for standing at the block before breaking it. */
    public long getMineDurationMs() {
        return (long) (getProductionIntervalMs() * 0.50);
    }

    public void setTargetBlock(int x, int y, int z) {
        this.targetBlockX = x;
        this.targetBlockY = y;
        this.targetBlockZ = z;
        this.hasTarget = true;
    }

    public void clearTarget() {
        this.hasTarget = false;
    }

    public void setCurrentPosition(double x, double y, double z) {
        this.currentX = x;
        this.currentY = y;
        this.currentZ = z;
        this.positionInitialized = true;
    }

    public void resetPhaseForEvolution() {
        this.phase = MinerPhase.IDLE;
        this.phaseStartTime = 0;
        this.hasTarget = false;
    }

    public UUID getOwnerId() { return ownerId; }
    public String getMineId() { return mineId; }
    public Ref<EntityStore> getEntityRef() { return entityRef; }
    public void setEntityRef(Ref<EntityStore> entityRef) { this.entityRef = entityRef; }
    public UUID getEntityUuid() { return entityUuid; }
    public void setEntityUuid(UUID entityUuid) { this.entityUuid = entityUuid; }
    public int getSpeedLevel() { return speedLevel; }
    public void setSpeedLevel(int speedLevel) { this.speedLevel = speedLevel; }
    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
    public MinerPhase getPhase() { return phase; }
    public void setPhase(MinerPhase phase) { this.phase = phase; }
    public long getPhaseStartTime() { return phaseStartTime; }
    public void setPhaseStartTime(long phaseStartTime) { this.phaseStartTime = phaseStartTime; }
    public long getCycleStartTime() { return cycleStartTime; }
    public void setCycleStartTime(long cycleStartTime) { this.cycleStartTime = cycleStartTime; }
    public int getTargetBlockX() { return targetBlockX; }
    public int getTargetBlockY() { return targetBlockY; }
    public int getTargetBlockZ() { return targetBlockZ; }
    public boolean hasTarget() { return hasTarget; }
    public double getCurrentX() { return currentX; }
    public double getCurrentY() { return currentY; }
    public double getCurrentZ() { return currentZ; }
    public boolean isPositionInitialized() { return positionInitialized; }
    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public static long getMinerBuyCost() {
        return 1000L;
    }

    public static long getMinerSpeedCost(int speedLevel, int stars) {
        int totalLevel = stars * MAX_SPEED_PER_STAR + speedLevel;
        return Math.round(50 * Math.pow(1.15, totalLevel));
    }

    public static long getMinerEvolveCost(int stars) {
        return Math.round(5000 * Math.pow(3, stars));
    }
}
