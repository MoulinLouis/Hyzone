package io.hyvexa.ascend.mine.data;

public class MinerSlot {
    private final String mineId;
    private final int slotIndex;
    private double npcX, npcY, npcZ;
    private float npcYaw;
    private int blockX, blockY, blockZ;
    private double intervalSeconds = 5.0;
    private boolean configured;
    private double conveyorSpeed = 2.0; // blocks per second

    public MinerSlot(String mineId) {
        this(mineId, 0);
    }

    public MinerSlot(String mineId, int slotIndex) {
        this.mineId = mineId;
        this.slotIndex = slotIndex;
    }

    public boolean isConfigured() { return configured; }

    public String getMineId() { return mineId; }
    public int getSlotIndex() { return slotIndex; }
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

    public double getConveyorSpeed() { return conveyorSpeed; }
    public void setConveyorSpeed(double speed) { this.conveyorSpeed = speed; }
}
