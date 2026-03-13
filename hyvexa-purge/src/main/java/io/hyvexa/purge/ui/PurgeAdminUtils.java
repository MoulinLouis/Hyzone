package io.hyvexa.purge.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.manager.PurgeInstanceManager;
import io.hyvexa.purge.manager.PurgeVariantConfigManager;
import io.hyvexa.purge.manager.PurgeWaveConfigManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

/**
 * Shared helpers for purge admin pages.
 */
public final class PurgeAdminUtils {

    private PurgeAdminUtils() {}

    /**
     * Navigates back to the admin index page. Resolves player and playerRef from the store,
     * null-checks both, then opens {@link PurgeAdminIndexPage}.
     */
    public static void openAdminIndex(Ref<EntityStore> ref, Store<EntityStore> store,
                                       PurgeWaveConfigManager waveConfigManager,
                                       PurgeInstanceManager instanceManager,
                                       PurgeWeaponConfigManager weaponConfigManager,
                                       PurgeVariantConfigManager variantConfigManager) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgeAdminIndexPage(playerRef, waveConfigManager, instanceManager, weaponConfigManager, variantConfigManager));
    }

    /**
     * Resolves the player from the store and sends a chat message. Does nothing if the player
     * cannot be resolved.
     */
    public static void sendFeedback(Ref<EntityStore> ref, Store<EntityStore> store, String message) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(message));
        }
    }
}
