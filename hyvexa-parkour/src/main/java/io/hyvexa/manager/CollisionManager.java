package io.hyvexa.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollision;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.CompletableFuture;

/**
 * Manages player collision state (disabling player-player collision).
 */
public class CollisionManager {

    public CollisionManager() {
    }

    /**
     * Disable collision for all online players.
     */
    public void disableAllCollisions() {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            disablePlayerCollision(playerRef);
        }
    }

    /**
     * Disable collision for a specific player.
     */
    public void disablePlayerCollision(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        disablePlayerCollision(playerRef.getReference());
    }

    /**
     * Disable collision for a specific entity reference.
     */
    public void disablePlayerCollision(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> store.tryRemoveComponent(ref, HitboxCollision.getComponentType()), world);
    }
}
