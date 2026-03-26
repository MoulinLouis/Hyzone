package io.hyvexa.ascend.mine.data;

public class CollectedMiner {
    private long id;
    private final String layerId;
    private final MinerRarity rarity;
    private int speedLevel;

    public CollectedMiner(long id, String layerId, MinerRarity rarity, int speedLevel) {
        this.id = id;
        this.layerId = layerId;
        this.rarity = rarity;
        this.speedLevel = speedLevel;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getLayerId() { return layerId; }
    public MinerRarity getRarity() { return rarity; }
    public int getSpeedLevel() { return speedLevel; }
    public void setSpeedLevel(int speedLevel) { this.speedLevel = speedLevel; }

    public double getProductionRate() {
        return getProductionRate(speedLevel);
    }

    public static double getProductionRate(int speedLevel) {
        return 12.0 * (1.0 + speedLevel * 0.10);
    }

    public static long getSpeedUpgradeCost(int speedLevel) {
        return Math.round(50 * Math.pow(1.15, speedLevel));
    }
}
