package io.hyvexa.ascend.mine.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.mine.data.MinerRarity;
import io.hyvexa.common.npc.NPCEntityState;

import java.util.UUID;

public class MinerRobotState implements NPCEntityState {

    private final UUID ownerId;
    private final String mineId;
    private final int slotIndex;
    private final long minerId;
    private final String originLayerId;
    private final MinerRarity rarity;
    private Ref<EntityStore> entityRef;
    private UUID entityUuid;
    private String worldName;
    private long lastBreakTime;        // last time a block was broken
    private long lastAnimTime;         // last time mine anim was replayed
    private long lastMovStopTime;     // last time movement animation was suppressed
    private boolean animating;         // whether mine animation is playing
    private String currentBlockType;   // block type currently placed (for correct loot)
    private boolean stopped;           // true when bag is full (pause mining)

    private volatile int speedLevel = 0;

    public MinerRobotState(UUID ownerId, String mineId, int slotIndex,
                           long minerId, String originLayerId, MinerRarity rarity) {
        this.ownerId = ownerId;
        this.mineId = mineId;
        this.slotIndex = slotIndex;
        this.minerId = minerId;
        this.originLayerId = originLayerId;
        this.rarity = rarity;
    }

    // --- Getters & setters ---

    public UUID getOwnerId() { return ownerId; }
    public String getMineId() { return mineId; }
    public int getSlotIndex() { return slotIndex; }
    public long getMinerId() { return minerId; }
    public String getOriginLayerId() { return originLayerId; }
    public MinerRarity getRarity() { return rarity; }
    @Override public Ref<EntityStore> getEntityRef() { return entityRef; }
    @Override public void setEntityRef(Ref<EntityStore> entityRef) { this.entityRef = entityRef; }
    @Override public UUID getEntityUuid() { return entityUuid; }
    @Override public void setEntityUuid(UUID entityUuid) { this.entityUuid = entityUuid; }
    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }
    public int getSpeedLevel() { return speedLevel; }
    public void setSpeedLevel(int speedLevel) { this.speedLevel = speedLevel; }
    public long getLastBreakTime() { return lastBreakTime; }
    public void setLastBreakTime(long lastBreakTime) { this.lastBreakTime = lastBreakTime; }
    public long getLastAnimTime() { return lastAnimTime; }
    public void setLastAnimTime(long lastAnimTime) { this.lastAnimTime = lastAnimTime; }
    public long getLastMovStopTime() { return lastMovStopTime; }
    public void setLastMovStopTime(long lastMovStopTime) { this.lastMovStopTime = lastMovStopTime; }
    public boolean isAnimating() { return animating; }
    public void setAnimating(boolean animating) { this.animating = animating; }
    public String getCurrentBlockType() { return currentBlockType; }
    public void setCurrentBlockType(String currentBlockType) { this.currentBlockType = currentBlockType; }
    public boolean isStopped() { return stopped; }
    public void setStopped(boolean stopped) { this.stopped = stopped; }
}
