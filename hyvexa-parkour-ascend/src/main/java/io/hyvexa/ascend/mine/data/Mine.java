package io.hyvexa.ascend.mine.data;

import io.hyvexa.common.math.BigNumber;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Mine {
    private final String id;
    private String name;
    private int displayOrder;
    private BigNumber unlockCost = BigNumber.ZERO;
    private String world = "";
    private double spawnX, spawnY, spawnZ;
    private float spawnRotX, spawnRotY, spawnRotZ;
    private final List<MineZone> zones = new CopyOnWriteArrayList<>();

    public Mine(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getDisplayOrder() { return displayOrder; }
    public BigNumber getUnlockCost() { return unlockCost; }
    public String getWorld() { return world; }
    public double getSpawnX() { return spawnX; }
    public double getSpawnY() { return spawnY; }
    public double getSpawnZ() { return spawnZ; }
    public float getSpawnRotX() { return spawnRotX; }
    public float getSpawnRotY() { return spawnRotY; }
    public float getSpawnRotZ() { return spawnRotZ; }
    public List<MineZone> getZones() { return zones; }

    public void setName(String name) { this.name = name; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public void setUnlockCost(BigNumber unlockCost) { this.unlockCost = unlockCost; }
    public void setWorld(String world) { this.world = world; }
    public void setSpawnX(double spawnX) { this.spawnX = spawnX; }
    public void setSpawnY(double spawnY) { this.spawnY = spawnY; }
    public void setSpawnZ(double spawnZ) { this.spawnZ = spawnZ; }
    public void setSpawnRotX(float spawnRotX) { this.spawnRotX = spawnRotX; }
    public void setSpawnRotY(float spawnRotY) { this.spawnRotY = spawnRotY; }
    public void setSpawnRotZ(float spawnRotZ) { this.spawnRotZ = spawnRotZ; }

    public boolean hasSpawn() {
        return world != null && !world.isEmpty();
    }
}
