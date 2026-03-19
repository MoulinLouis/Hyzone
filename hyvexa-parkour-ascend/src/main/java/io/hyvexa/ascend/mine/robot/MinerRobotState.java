package io.hyvexa.ascend.mine.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

public class MinerRobotState {
    public static final int MAX_SPEED_PER_STAR = 25;
    public static final int MAX_STARS = 5;

    private final UUID ownerId;
    private final String mineId;
    private Ref<EntityStore> entityRef;
    private UUID entityUuid;
    private String worldName;
    private long lastBreakTime;        // last time a block was broken
    private long lastAnimTime;         // last time mine anim was replayed
    private boolean animating;         // whether mine animation is playing
    private String currentBlockType;   // block type currently placed (for correct loot)
    private boolean stopped;           // true when bag is full (pause mining)

    // Kept for UI rendering in MinePage.java — read by getMinerSnapshot()
    private volatile int speedLevel = 0;
    private volatile int stars = 0;

    public MinerRobotState(UUID ownerId, String mineId) {
        this.ownerId = ownerId;
        this.mineId = mineId;
    }

    // --- Static cost/rate methods (used by MinePage.java for UI) ---

    public double getProductionRate() {
        return getProductionRate(speedLevel, stars);
    }

    public static double getProductionRate(int speedLevel, int stars) {
        double base = 6.0;
        double speedMult = 1.0 + speedLevel * 0.10;
        double starMult = 1.0 + stars * 0.5;
        return base * speedMult * starMult;
    }

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

    // --- Getters & setters ---

    public UUID getOwnerId() { return ownerId; }
    public String getMineId() { return mineId; }
    public Ref<EntityStore> getEntityRef() { return entityRef; }
    public void setEntityRef(Ref<EntityStore> entityRef) { this.entityRef = entityRef; }
    public UUID getEntityUuid() { return entityUuid; }
    public void setEntityUuid(UUID entityUuid) { this.entityUuid = entityUuid; }
    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }
    public int getSpeedLevel() { return speedLevel; }
    public void setSpeedLevel(int speedLevel) { this.speedLevel = speedLevel; }
    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
    public long getLastBreakTime() { return lastBreakTime; }
    public void setLastBreakTime(long lastBreakTime) { this.lastBreakTime = lastBreakTime; }
    public long getLastAnimTime() { return lastAnimTime; }
    public void setLastAnimTime(long lastAnimTime) { this.lastAnimTime = lastAnimTime; }
    public boolean isAnimating() { return animating; }
    public void setAnimating(boolean animating) { this.animating = animating; }
    public String getCurrentBlockType() { return currentBlockType; }
    public void setCurrentBlockType(String currentBlockType) { this.currentBlockType = currentBlockType; }
    public boolean isStopped() { return stopped; }
    public void setStopped(boolean stopped) { this.stopped = stopped; }
}
