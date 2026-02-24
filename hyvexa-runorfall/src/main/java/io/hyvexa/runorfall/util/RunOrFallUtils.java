package io.hyvexa.runorfall.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.runorfall.data.RunOrFallLocation;

import java.util.Locale;

public final class RunOrFallUtils {

    public static final int AIR_BLOCK_ID = resolveAirBlockId();

    private RunOrFallUtils() {
    }

    public static RunOrFallLocation readLocation(Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return null;
        }
        Vector3d position = transform.getPosition();
        Vector3f rotation = transform.getRotation();
        float rotX = rotation != null ? rotation.getX() : 0f;
        float rotY = rotation != null ? rotation.getY() : 0f;
        float rotZ = rotation != null ? rotation.getZ() : 0f;
        return new RunOrFallLocation(position.getX(), position.getY(), position.getZ(), rotX, rotY, rotZ);
    }

    public static Vector3i readCurrentBlock(Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return null;
        }
        Vector3d position = transform.getPosition();
        int x = (int) Math.floor(position.getX());
        int y = (int) Math.floor(position.getY() - 0.2d);
        int z = (int) Math.floor(position.getZ());
        return new Vector3i(x, y, z);
    }

    public static String formatDuration(long millis) {
        long safeMillis = Math.max(0L, millis);
        long totalSeconds = safeMillis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        long centiseconds = (safeMillis % 1000L) / 10L;
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centiseconds);
    }

    public static Integer readBlockId(World world, int x, int y, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        var chunk = world.getChunkIfInMemory(chunkIndex);
        if (chunk == null) {
            chunk = world.loadChunkIfInMemory(chunkIndex);
        }
        if (chunk == null) {
            return null;
        }
        return chunk.getBlock(x, y, z);
    }

    private static int resolveAirBlockId() {
        int resolved = BlockType.getAssetMap().getIndex("Air");
        return resolved >= 0 ? resolved : 0;
    }
}
