package io.hyvexa.runorfall.data;

public class RunOrFallLocation {
    public double x;
    public double y;
    public double z;
    public float rotX;
    public float rotY;
    public float rotZ;

    public RunOrFallLocation() {
    }

    public RunOrFallLocation(double x, double y, double z, float rotX, float rotY, float rotZ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
    }

    public RunOrFallLocation copy() {
        return new RunOrFallLocation(x, y, z, rotX, rotY, rotZ);
    }
}
