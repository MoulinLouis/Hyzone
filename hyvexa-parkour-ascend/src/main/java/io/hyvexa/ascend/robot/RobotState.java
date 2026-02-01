package io.hyvexa.ascend.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RobotState {

    private final UUID ownerId;
    private final String mapId;
    private volatile Ref<EntityStore> entityRef;
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

    public void setPreviousPosition(double[] previousPosition) {
        this.previousPosition = previousPosition;
    }
}
