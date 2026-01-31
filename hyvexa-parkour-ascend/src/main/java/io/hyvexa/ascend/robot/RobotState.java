package io.hyvexa.ascend.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

public class RobotState {

    private final UUID ownerId;
    private final String mapId;
    private Ref<EntityStore> entityRef;
    private int currentWaypointIndex;
    private long lastTickMs;
    private long waypointReachedMs;
    private boolean waiting;
    private long runsCompleted;
    private int robotCount;
    private long lastCompletionMs;

    public RobotState(UUID ownerId, String mapId) {
        this.ownerId = ownerId;
        this.mapId = mapId;
        this.currentWaypointIndex = 0;
        this.lastTickMs = System.currentTimeMillis();
        this.waiting = false;
        this.runsCompleted = 0;
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
        return currentWaypointIndex;
    }

    public void setCurrentWaypointIndex(int index) {
        this.currentWaypointIndex = index;
    }

    public void incrementWaypoint() {
        this.currentWaypointIndex++;
    }

    public long getLastTickMs() {
        return lastTickMs;
    }

    public void setLastTickMs(long lastTickMs) {
        this.lastTickMs = lastTickMs;
    }

    public long getWaypointReachedMs() {
        return waypointReachedMs;
    }

    public void setWaypointReachedMs(long ms) {
        this.waypointReachedMs = ms;
    }

    public boolean isWaiting() {
        return waiting;
    }

    public void setWaiting(boolean waiting) {
        this.waiting = waiting;
    }

    public long getRunsCompleted() {
        return runsCompleted;
    }

    public void incrementRunsCompleted() {
        this.runsCompleted++;
    }

    public void addRunsCompleted(long amount) {
        if (amount > 0L) {
            this.runsCompleted += amount;
        }
    }

    public int getRobotCount() {
        return robotCount;
    }

    public void setRobotCount(int robotCount) {
        this.robotCount = Math.max(0, robotCount);
    }

    public long getLastCompletionMs() {
        return lastCompletionMs;
    }

    public void setLastCompletionMs(long lastCompletionMs) {
        this.lastCompletionMs = lastCompletionMs;
    }

    public void resetForNewRun() {
        this.currentWaypointIndex = 0;
        this.waypointReachedMs = 0;
    }
}
