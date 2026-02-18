package io.hyvexa.purge.data;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;

public record PurgeLocation(double x, double y, double z, float rotX, float rotY, float rotZ) {

    public Vector3d toPosition() {
        return new Vector3d(x, y, z);
    }

    public Vector3f toRotation() {
        return new Vector3f(rotX, rotY, rotZ);
    }
}
