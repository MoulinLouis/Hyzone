package io.hyvexa.common.util;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.common.skin.PurgeSkinStore;
import io.hyvexa.core.cosmetic.CosmeticManager;
import io.hyvexa.core.cosmetic.CosmeticStore;
import io.hyvexa.core.discord.DiscordLinkStore;
import io.hyvexa.core.economy.FeatherStore;
import io.hyvexa.core.economy.VexaStore;

import java.util.UUID;

/**
 * Centralized disconnect cleanup for all shared stores.
 * Called once per disconnect from the core plugin's global handler,
 * so non-core plugins don't need to evict shared stores individually.
 */
public final class SharedStoreCleanup {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private SharedStoreCleanup() {}

    public static void evictPlayer(UUID playerId) {
        if (playerId == null) return;
        evictSafe(() -> VexaStore.get().evictPlayer(playerId), "VexaStore");
        evictSafe(() -> FeatherStore.get().evictPlayer(playerId), "FeatherStore");
        evictSafe(() -> CosmeticStore.get().evictPlayer(playerId), "CosmeticStore");
        evictSafe(() -> DiscordLinkStore.get().evictPlayer(playerId), "DiscordLinkStore");
        evictSafe(() -> CosmeticManager.get().cleanupOnDisconnect(playerId), "CosmeticManager");
        evictSafe(() -> MultiHudBridge.evictPlayer(playerId), "MultiHudBridge");
        if (PurgeSkinStore.isInitialized()) {
            evictSafe(() -> PurgeSkinStore.get().evictPlayer(playerId), "PurgeSkinStore");
        }
    }

    private static void evictSafe(Runnable action, String name) {
        try {
            action.run();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Disconnect cleanup: " + name);
        }
    }
}
