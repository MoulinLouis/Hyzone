package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.parkour.data.TransformData;
import io.hyvexa.parkour.ui.PlayerMusicPage;

import java.util.List;
import java.util.Set;

public final class TrackerUtils {

    private TrackerUtils() {
    }

    public static final class FallState {
        public Long fallStartTime;
        public Double lastY;

        public void reset() {
            fallStartTime = null;
            lastY = null;
        }
    }

    public static void playFinishSound(PlayerRef playerRef) {
        if (playerRef == null || !PlayerMusicPage.isVictorySfxEnabled(playerRef.getUuid())) {
            return;
        }
        int soundIndex = SoundEvent.getAssetMap().getIndex("SFX_Parkour_Victory");
        if (soundIndex <= SoundEvent.EMPTY_ID) {
            return;
        }
        SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, com.hypixel.hytale.protocol.SoundCategory.SFX);
    }

    public static void playCheckpointSound(PlayerRef playerRef) {
        if (playerRef == null || !PlayerMusicPage.isCheckpointSfxEnabled(playerRef.getUuid())) {
            return;
        }
        int soundIndex = SoundEvent.getAssetMap().getIndex("SFX_Parkour_Checkpoint");
        if (soundIndex <= SoundEvent.EMPTY_ID) {
            return;
        }
        SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, com.hypixel.hytale.protocol.SoundCategory.SFX);
    }

    /**
     * Squared distance with a vertical bonus that reduces the Y component when the player is above
     * the target, making it easier to hit checkpoints from above (e.g., falling onto platforms).
     */
    public static double distanceSqWithVerticalBonus(Vector3d position, TransformData target, double verticalBonus) {
        double dx = position.getX() - target.getX();
        double dy = position.getY() - target.getY();
        if (dy > 0) {
            dy = Math.max(0.0, dy - verticalBonus);
        }
        double dz = position.getZ() - target.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public static int resolveCheckpointIndex(int lastCheckpointIndex, Set<Integer> touchedCheckpoints,
                                      List<TransformData> checkpoints) {
        if (checkpoints == null) {
            return -1;
        }
        if (lastCheckpointIndex >= 0 && lastCheckpointIndex < checkpoints.size()) {
            return lastCheckpointIndex;
        }
        int best = -1;
        for (Integer touched : touchedCheckpoints) {
            if (touched == null) {
                continue;
            }
            int candidate = touched;
            if (candidate >= 0 && candidate < checkpoints.size()) {
                best = Math.max(best, candidate);
            }
        }
        return best;
    }

    public static boolean shouldRespawnFromFall(FallState state, double currentY, boolean blocked, long fallTimeoutMs) {
        if (blocked) {
            state.fallStartTime = null;
            state.lastY = currentY;
            return false;
        }
        if (state.lastY == null) {
            state.lastY = currentY;
            state.fallStartTime = null;
            return false;
        }
        long now = System.currentTimeMillis();
        if (currentY < state.lastY) {
            if (state.fallStartTime == null) {
                state.fallStartTime = now;
            }
            if (now - state.fallStartTime >= fallTimeoutMs) {
                state.lastY = currentY;
                return true;
            }
        } else {
            state.fallStartTime = null;
        }
        state.lastY = currentY;
        return false;
    }

    public static boolean isFallTrackingBlocked(MovementStates movementStates) {
        return movementStates != null && (movementStates.climbing || movementStates.onGround);
    }

    public static void teleportToSpawn(Ref<EntityStore> ref, Store<EntityStore> store, TransformComponent transform,
                                CommandBuffer<EntityStore> buffer) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        com.hypixel.hytale.math.vector.Transform spawnTransform = null;
        var worldConfig = world.getWorldConfig();
        if (worldConfig != null && worldConfig.getSpawnProvider() != null) {
            spawnTransform = worldConfig.getSpawnProvider().getSpawnPoint(world, playerRef.getUuid());
        }
        Vector3d position = spawnTransform != null
                ? spawnTransform.getPosition()
                : (transform != null ? transform.getPosition() : new Vector3d(0.0, 0.0, 0.0));
        Vector3f rotation = spawnTransform != null
                ? spawnTransform.getRotation()
                : (transform != null ? transform.getRotation() : new Vector3f(0f, 0f, 0f));
        Teleport teleport = new Teleport(world, position, rotation);
        if (buffer != null) {
            buffer.addComponent(ref, Teleport.getComponentType(), teleport);
        } else {
            store.addComponent(ref, Teleport.getComponentType(), teleport);
        }
    }
}
