package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;

/**
 * Shared utility for admin pages that navigate back to the admin index.
 */
public final class AdminPageUtils {

    private AdminPageUtils() {
    }

    /**
     * Opens the {@link AdminIndexPage} for the given player, pulling all stores from the plugin singleton.
     */
    public static void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new AdminIndexPage(playerRef, plugin.getMapStore(), plugin.getProgressStore(),
                        plugin.getSettingsStore(), plugin.getPlayerCountStore()));
    }
}
