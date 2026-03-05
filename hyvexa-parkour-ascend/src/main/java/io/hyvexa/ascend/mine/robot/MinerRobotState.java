package io.hyvexa.ascend.mine.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

public class MinerRobotState {
    private final UUID ownerId;
    private final String mineId;
    private Ref<EntityStore> entityRef;
    private UUID entityUuid;

    private int speedLevel = 0;
    private int stars = 0;
    private long lastProductionTick = 0;

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
    public long getLastProductionTick() { return lastProductionTick; }
    public void setLastProductionTick(long lastProductionTick) { this.lastProductionTick = lastProductionTick; }
}
