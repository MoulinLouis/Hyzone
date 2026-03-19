package io.hyvexa.ascend.mine.data;

public class MinerSlot {
    private final String mineId;
    private double npcX, npcY, npcZ;
    private float npcYaw;
    private int blockX, blockY, blockZ;
    private double intervalSeconds = 5.0;
    private boolean configured;

    public MinerSlot(String mineId) {
        this.mineId = mineId;
    }

    public boolean isConfigured() { return configured; }

    public String getMineId() { return mineId; }
    public double getNpcX() { return npcX; }
    public double getNpcY() { return npcY; }
    public double getNpcZ() { return npcZ; }
    public float getNpcYaw() { return npcYaw; }
    public int getBlockX() { return blockX; }
    public int getBlockY() { return blockY; }
    public int getBlockZ() { return blockZ; }
    public double getIntervalSeconds() { return intervalSeconds; }

    public void setNpcPosition(double x, double y, double z, float yaw) {
        this.npcX = x;
        this.npcY = y;
        this.npcZ = z;
        this.npcYaw = yaw;
        this.configured = true;
    }

    public void setBlockPosition(int x, int y, int z) {
        this.blockX = x;
        this.blockY = y;
        this.blockZ = z;
    }

    public void setIntervalSeconds(double intervalSeconds) { this.intervalSeconds = intervalSeconds; }
}
