package io.hyvexa.ascend.mine.data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MineZone {
    private final String id;
    private final String mineId;
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;
    private final Map<String, Double> blockTable = new ConcurrentHashMap<>();
    private final Map<String, Integer> blockHpTable = new ConcurrentHashMap<>();
    private double regenThreshold = 0.8;
    private int regenCooldownSeconds = 45;
    private final List<MineZoneLayer> layers = new CopyOnWriteArrayList<>();

    public MineZone(String id, String mineId, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.id = id;
        this.mineId = mineId;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public int getTotalBlocks() {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public String getId() { return id; }
    public String getMineId() { return mineId; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
    public Map<String, Double> getBlockTable() { return blockTable; }
    public Map<String, Integer> getBlockHpTable() { return blockHpTable; }
    public List<MineZoneLayer> getLayers() { return layers; }
    public double getRegenThreshold() { return regenThreshold; }
    public int getRegenCooldownSeconds() { return regenCooldownSeconds; }

    /**
     * Returns the block table for the given Y coordinate.
     * If a layer covers this Y, returns its table. Otherwise returns the zone-level fallback.
     */
    public Map<String, Double> getBlockTableForY(int y) {
        for (MineZoneLayer layer : layers) {
            if (layer.containsY(y)) {
                return layer.getBlockTable();
            }
        }
        return blockTable;
    }

    /**
     * Returns the HP for a block type at the given Y coordinate.
     * Checks layer HP table first, then zone-level fallback. Returns 1 if not configured.
     */
    public int getBlockHpForY(String blockTypeId, int y) {
        for (MineZoneLayer layer : layers) {
            if (layer.containsY(y)) {
                Integer layerHp = layer.getBlockHpTable().get(blockTypeId);
                if (layerHp != null) return layerHp;
            }
        }
        return blockHpTable.getOrDefault(blockTypeId, 1);
    }

    public void setMinX(int minX) { this.minX = minX; }
    public void setMinY(int minY) { this.minY = minY; }
    public void setMinZ(int minZ) { this.minZ = minZ; }
    public void setMaxX(int maxX) { this.maxX = maxX; }
    public void setMaxY(int maxY) { this.maxY = maxY; }
    public void setMaxZ(int maxZ) { this.maxZ = maxZ; }
    public void setRegenThreshold(double regenThreshold) { this.regenThreshold = regenThreshold; }
    public void setRegenCooldownSeconds(int regenCooldownSeconds) { this.regenCooldownSeconds = regenCooldownSeconds; }
}
