package io.hyvexa.common.whitelist;

import javax.annotation.Nullable;

/**
 * Global registry for the Ascend whitelist manager.
 * Allows modules to access the whitelist without creating circular dependencies.
 */
public class WhitelistRegistry {
    private static volatile AscendWhitelistManager instance;

    private WhitelistRegistry() {
        // Utility class
    }

    /**
     * Registers the whitelist manager instance.
     * Should be called by the Ascend plugin during initialization.
     */
    public static void register(AscendWhitelistManager manager) {
        instance = manager;
    }

    /**
     * Gets the registered whitelist manager instance.
     * @return The whitelist manager, or null if not registered
     */
    @Nullable
    public static AscendWhitelistManager getInstance() {
        return instance;
    }

    /**
     * Clears the registered instance.
     * Should be called during plugin shutdown.
     */
    public static void unregister() {
        instance = null;
    }
}
