package io.hyvexa.ascend.mine.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MineZoneLayer {
    private final String id;
    private final String zoneId;
    private int minY;
    private int maxY;
    private final Map<String, Double> blockTable = new ConcurrentHashMap<>();

    public MineZoneLayer(String id, String zoneId, int minY, int maxY) {
        this.id = id;
        this.zoneId = zoneId;
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
    }

    public boolean containsY(int y) {
        return y >= minY && y <= maxY;
    }

    public String getId() { return id; }
    public String getZoneId() { return zoneId; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
    public Map<String, Double> getBlockTable() { return blockTable; }

    public void setMinY(int minY) { this.minY = minY; }
    public void setMaxY(int maxY) { this.maxY = maxY; }
}
