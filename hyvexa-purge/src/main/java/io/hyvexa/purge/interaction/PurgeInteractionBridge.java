package io.hyvexa.purge.interaction;

import io.hyvexa.purge.PurgeLoadoutService;
import io.hyvexa.purge.data.PurgeWeaponUpgradeStore;
import io.hyvexa.purge.manager.PurgePartyManager;
import io.hyvexa.purge.manager.PurgeSessionManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

/**
 * Narrow static bootstrap for codec-instantiated interactions.
 * Hytale requires no-arg handlers, so these interactions cannot receive
 * constructor injection directly from the plugin composition root.
 */
public final class PurgeInteractionBridge {

    private static volatile Services services;

    private PurgeInteractionBridge() {}

    public static void configure(Services services) {
        PurgeInteractionBridge.services = services;
    }

    public static Services get() {
        return services;
    }

    public static void clear() {
        services = null;
    }

    public record Services(
        PurgeSessionManager sessionManager,
        PurgePartyManager partyManager,
        PurgeWeaponConfigManager weaponConfigManager,
        PurgeLoadoutService loadoutService,
        io.hyvexa.common.skin.PurgeSkinStore purgeSkinStore,
        PurgeWeaponUpgradeStore weaponUpgradeStore
    ) {}
}
