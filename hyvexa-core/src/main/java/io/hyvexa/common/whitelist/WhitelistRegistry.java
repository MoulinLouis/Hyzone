package io.hyvexa.common.whitelist;

import javax.annotation.Nullable;

/**
 * Global registry for the Ascend whitelist manager.
 * Thread safety: register/unregister must be called from the main thread at startup/shutdown only.
 * getInstance() is safe to call from any thread (volatile read).
 */
public class WhitelistRegistry {
    private static volatile AscendWhitelistManager instance;

    private WhitelistRegistry() {
    }

    public static void register(AscendWhitelistManager manager) {
        instance = manager;
    }

    @Nullable
    public static AscendWhitelistManager getInstance() {
        return instance;
    }

    public static void unregister() {
        instance = null;
    }
}
