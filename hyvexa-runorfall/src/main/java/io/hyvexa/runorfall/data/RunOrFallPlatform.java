package io.hyvexa.runorfall.data;

public class RunOrFallPlatform {
    public String name;
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;

    public RunOrFallPlatform() {
    }

    public RunOrFallPlatform(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
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

    public RunOrFallPlatform copy() {
        return new RunOrFallPlatform(name, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
