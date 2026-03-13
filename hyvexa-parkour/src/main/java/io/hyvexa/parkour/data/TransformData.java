package io.hyvexa.parkour.data;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;

public class TransformData {
    private double x;
    private double y;
    private double z;
    private float rotX;
    private float rotY;
    private float rotZ;

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public float getRotX() {
        return rotX;
    }

    public void setRotX(float rotX) {
        this.rotX = rotX;
    }

    public float getRotY() {
        return rotY;
    }

    public void setRotY(float rotY) {
        this.rotY = rotY;
    }

    public float getRotZ() {
        return rotZ;
    }

    public void setRotZ(float rotZ) {
        this.rotZ = rotZ;
    }

    public Vector3d toPosition() {
        return new Vector3d(x, y, z);
    }

    public Vector3f toRotation() {
        return new Vector3f(rotX, rotY, rotZ);
    }

    public TransformData copy() {
        TransformData copy = new TransformData();
        copy.setX(x);
        copy.setY(y);
        copy.setZ(z);
        copy.setRotX(rotX);
        copy.setRotY(rotY);
        copy.setRotZ(rotZ);
        return copy;
    }
}
