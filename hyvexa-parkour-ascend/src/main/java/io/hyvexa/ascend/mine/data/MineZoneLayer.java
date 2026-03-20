package io.hyvexa.ascend.mine.data;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class MineZoneLayer {
    private final String id;
    private final String zoneId;
    private int minY;
    private int maxY;
    private final Map<String, Double> blockTable = new HashMap<>();
    private double eggDropChance = 0.5;
    private String displayName = "";
    private final Map<MinerRarity, Map<String, Double>> rarityBlockTables = new EnumMap<>(MinerRarity.class);

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
    public double getEggDropChance() { return eggDropChance; }
    public void setEggDropChance(double eggDropChance) { this.eggDropChance = eggDropChance; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName != null ? displayName : ""; }
    public Map<MinerRarity, Map<String, Double>> getRarityBlockTables() { return rarityBlockTables; }

    /**
     * Returns the block table for the given rarity.
     * Falls back to the base blockTable if no rarity-specific table exists.
     */
    public Map<String, Double> getBlockTableForRarity(MinerRarity rarity) {
        if (rarity != null) {
            Map<String, Double> table = rarityBlockTables.get(rarity);
            if (table != null && !table.isEmpty()) return table;
        }
        return blockTable;
    }

    public void setMinY(int minY) { this.minY = minY; }
    public void setMaxY(int maxY) { this.maxY = maxY; }
}
