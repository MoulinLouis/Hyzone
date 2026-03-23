package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Shared utility for admin pages that navigate back to the admin index.
 */
public final class AdminPageUtils {

    private static volatile ParkourAdminNavigator navigator;

    private AdminPageUtils() {
    }

    public static void configure(ParkourAdminNavigator navigator) {
        AdminPageUtils.navigator = navigator;
    }

    public static void clear() {
        navigator = null;
    }

    /**
     * Opens the {@link AdminIndexPage} for the given player via the configured navigator.
     */
    public static void openIndex(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (navigator != null) {
            navigator.openIndex(ref, store);
        }
    }
}
