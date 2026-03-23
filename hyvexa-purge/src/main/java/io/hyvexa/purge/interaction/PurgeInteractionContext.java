package io.hyvexa.purge.interaction;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;

import java.util.UUID;

/**
 * Resolves common components needed by purge interaction handlers.
 */
public record PurgeInteractionContext(
        Store<EntityStore> store,
        Player player,
        PlayerRef playerRef,
        UUID playerId,
        PurgeInteractionBridge.Services services
) {

    /**
     * Resolves player, playerRef, UUID, and bridge services from the given entity ref.
     * Returns null if any component is missing or invalid.
     */
    public static PurgeInteractionContext resolve(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        var store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return null;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return null;
        }
        PurgeInteractionBridge.Services services = PurgeInteractionBridge.get();
        if (services == null) {
            return null;
        }
        return new PurgeInteractionContext(store, player, playerRef, playerId, services);
    }
}
