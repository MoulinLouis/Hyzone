package io.hyvexa.ascend.data;

import java.util.ArrayList;
import java.util.List;

public class AscendMap {

    private String id;
    private String name;
    private long price;
    private long robotPrice;
    private long baseReward;
    private long baseRunTimeMs;
    private long robotTimeReductionMs;
    private int storageCapacity;
    private String world;
    private double startX;
    private double startY;
    private double startZ;
    private float startRotX;
    private float startRotY;
    private float startRotZ;
    private double finishX;
    private double finishY;
    private double finishZ;
    private List<Waypoint> waypoints = new ArrayList<>();
    private int displayOrder;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public long getRobotPrice() {
        return robotPrice;
    }

    public void setRobotPrice(long robotPrice) {
        this.robotPrice = robotPrice;
    }

    public long getBaseReward() {
        return baseReward;
    }

    public void setBaseReward(long baseReward) {
        this.baseReward = baseReward;
    }

    public long getBaseRunTimeMs() {
        return baseRunTimeMs;
    }

    public void setBaseRunTimeMs(long baseRunTimeMs) {
        this.baseRunTimeMs = baseRunTimeMs;
    }

    public long getRobotTimeReductionMs() {
        return robotTimeReductionMs;
    }

    public void setRobotTimeReductionMs(long robotTimeReductionMs) {
        this.robotTimeReductionMs = robotTimeReductionMs;
    }

    public int getStorageCapacity() {
        return storageCapacity;
    }

    public void setStorageCapacity(int storageCapacity) {
        this.storageCapacity = storageCapacity;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public double getStartX() {
        return startX;
    }

    public void setStartX(double startX) {
        this.startX = startX;
    }

    public double getStartY() {
        return startY;
    }

    public void setStartY(double startY) {
        this.startY = startY;
    }

    public double getStartZ() {
        return startZ;
    }

    public void setStartZ(double startZ) {
        this.startZ = startZ;
    }

    public float getStartRotX() {
        return startRotX;
    }

    public void setStartRotX(float startRotX) {
        this.startRotX = startRotX;
    }

    public float getStartRotY() {
        return startRotY;
    }

    public void setStartRotY(float startRotY) {
        this.startRotY = startRotY;
    }

    public float getStartRotZ() {
        return startRotZ;
    }

    public void setStartRotZ(float startRotZ) {
        this.startRotZ = startRotZ;
    }

    public double getFinishX() {
        return finishX;
    }

    public void setFinishX(double finishX) {
        this.finishX = finishX;
    }

    public double getFinishY() {
        return finishY;
    }

    public void setFinishY(double finishY) {
        this.finishY = finishY;
    }

    public double getFinishZ() {
        return finishZ;
    }

    public void setFinishZ(double finishZ) {
        this.finishZ = finishZ;
    }

    public List<Waypoint> getWaypoints() {
        return waypoints;
    }

    public void setWaypoints(List<Waypoint> waypoints) {
        this.waypoints = waypoints;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public static class Waypoint {
        private double x;
        private double y;
        private double z;
        private boolean jump;
        private long delayMs;

        public Waypoint() {
        }

        public Waypoint(double x, double y, double z, boolean jump, long delayMs) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.jump = jump;
            this.delayMs = delayMs;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getZ() {
            return z;
        }

        public void setZ(double z) {
            this.z = z;
        }

        public boolean isJump() {
            return jump;
        }

        public void setJump(boolean jump) {
            this.jump = jump;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }
    }
}
