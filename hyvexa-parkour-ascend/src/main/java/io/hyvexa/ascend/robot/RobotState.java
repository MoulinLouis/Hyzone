package io.hyvexa.ascend.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.common.ghost.GhostRecording;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RobotState {

    private final UUID ownerId;
    private final String mapId;
    private volatile Ref<EntityStore> entityRef;
    private volatile UUID entityUuid;
    private final AtomicInteger currentWaypointIndex = new AtomicInteger(0);
    private final AtomicLong lastTickMs = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong waypointReachedMs = new AtomicLong(0);
    private volatile boolean waiting;
    private volatile boolean spawning;
    private final AtomicLong runsCompleted = new AtomicLong(0);
    private final AtomicInteger speedLevel = new AtomicInteger(0);
    private final AtomicInteger stars = new AtomicInteger(0);
    private final AtomicLong lastCompletionMs = new AtomicLong(0);
    private volatile double[] previousPosition;  // For calculating movement direction/rotation [x, y, z]
    private final AtomicLong invalidSinceMs = new AtomicLong(0);  // When entityRef became invalid (0 = valid)
    private volatile AscendMap cachedMap;
    private volatile World cachedWorld;
    private volatile GhostRecording cachedGhost;
    private final AtomicLong cacheRefreshedAtMs = new AtomicLong(0);
    private final AtomicLong nextMovementAtMs = new AtomicLong(0);
    private final AtomicLong movementIntervalMs = new AtomicLong(50L);
    private volatile boolean entityDesired;

    public RobotState(UUID ownerId, String mapId) {
        this.ownerId = ownerId;
        this.mapId = mapId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getMapId() {
        return mapId;
    }

    public Ref<EntityStore> getEntityRef() {
        return entityRef;
    }

    public void setEntityRef(Ref<EntityStore> entityRef) {
        this.entityRef = entityRef;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public void setEntityUuid(UUID entityUuid) {
        this.entityUuid = entityUuid;
    }

    public int getCurrentWaypointIndex() {
        return currentWaypointIndex.get();
    }

    public void setCurrentWaypointIndex(int index) {
        this.currentWaypointIndex.set(index);
    }

    public void incrementWaypoint() {
        this.currentWaypointIndex.incrementAndGet();
    }

    public long getLastTickMs() {
        return lastTickMs.get();
    }

    public void setLastTickMs(long lastTickMs) {
        this.lastTickMs.set(lastTickMs);
    }

    public long getWaypointReachedMs() {
        return waypointReachedMs.get();
    }

    public void setWaypointReachedMs(long ms) {
        this.waypointReachedMs.set(ms);
    }

    public boolean isWaiting() {
        return waiting;
    }

    public void setWaiting(boolean waiting) {
        this.waiting = waiting;
    }

    public boolean isSpawning() {
        return spawning;
    }

    public void setSpawning(boolean spawning) {
        this.spawning = spawning;
    }

    public long getRunsCompleted() {
        return runsCompleted.get();
    }

    public void incrementRunsCompleted() {
        this.runsCompleted.incrementAndGet();
    }

    public void addRunsCompleted(long amount) {
        if (amount > 0L) {
            this.runsCompleted.addAndGet(amount);
        }
    }

    public int getSpeedLevel() {
        return speedLevel.get();
    }

    public void setSpeedLevel(int speedLevel) {
        this.speedLevel.set(Math.max(0, speedLevel));
    }

    public int getStars() {
        return stars.get();
    }

    public void setStars(int stars) {
        this.stars.set(Math.max(0, stars));
    }

    public long getLastCompletionMs() {
        return lastCompletionMs.get();
    }

    public void setLastCompletionMs(long lastCompletionMs) {
        this.lastCompletionMs.set(lastCompletionMs);
    }

    public void resetForNewRun() {
        this.currentWaypointIndex.set(0);
        this.waypointReachedMs.set(0);
        this.previousPosition = null;
    }

    public double[] getPreviousPosition() {
        return previousPosition;
    }

    public void setPreviousPosition(double x, double y, double z) {
        this.previousPosition = new double[]{x, y, z};
    }

    public void clearPreviousPosition() {
        this.previousPosition = null;
    }

    public long getInvalidSinceMs() {
        return invalidSinceMs.get();
    }

    public void setInvalidSinceMs(long ms) {
        this.invalidSinceMs.set(ms);
    }

    /**
     * Mark the entity reference as invalid starting now.
     * Call this when entityRef.isValid() becomes false.
     */
    public void markInvalid(long nowMs) {
        invalidSinceMs.compareAndSet(0, nowMs);
    }

    public void clearInvalid() {
        invalidSinceMs.set(0);
    }

    public boolean isInvalidForTooLong(long nowMs, long thresholdMs) {
        long since = invalidSinceMs.get();
        return since > 0 && (nowMs - since) > thresholdMs;
    }

    public AscendMap getCachedMap() {
        return cachedMap;
    }

    public void setCachedMap(AscendMap cachedMap) {
        this.cachedMap = cachedMap;
    }

    public World getCachedWorld() {
        return cachedWorld;
    }

    public void setCachedWorld(World cachedWorld) {
        this.cachedWorld = cachedWorld;
    }

    public GhostRecording getCachedGhost() {
        return cachedGhost;
    }

    public void setCachedGhost(GhostRecording cachedGhost) {
        this.cachedGhost = cachedGhost;
    }

    public long getCacheRefreshedAtMs() {
        return cacheRefreshedAtMs.get();
    }

    public void setCacheRefreshedAtMs(long cacheRefreshedAtMs) {
        this.cacheRefreshedAtMs.set(cacheRefreshedAtMs);
    }

    public long getNextMovementAtMs() {
        return nextMovementAtMs.get();
    }

    public void setNextMovementAtMs(long nextMovementAtMs) {
        this.nextMovementAtMs.set(nextMovementAtMs);
    }

    public long getMovementIntervalMs() {
        return movementIntervalMs.get();
    }

    public void setMovementIntervalMs(long movementIntervalMs) {
        this.movementIntervalMs.set(Math.max(1L, movementIntervalMs));
    }

    public boolean isEntityDesired() {
        return entityDesired;
    }

    public void setEntityDesired(boolean entityDesired) {
        this.entityDesired = entityDesired;
    }
}
