package io.hyvexa.ascend.data;

import io.hyvexa.ascend.AscendConstants;

/**
 * Runtime balance source-of-truth:
 * - Computed from AscendConstants using displayOrder
 * - Not loaded from legacy ascend_maps balance/storage columns
 */
public class AscendMap {

    private String id;
    private String name;
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

    public long getEffectivePrice() {
        return AscendConstants.getMapUnlockPrice(displayOrder);
    }

    public long getEffectiveRobotPrice() {
        return 0L; // Runners are free
    }

    public long getEffectiveBaseRunTimeMs() {
        return AscendConstants.getMapBaseRunTimeMs(displayOrder);
    }

    public long getEffectiveBaseReward() {
        return AscendConstants.getMapBaseReward(displayOrder);
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

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
