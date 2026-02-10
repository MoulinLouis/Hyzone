package io.hyvexa.common.whitelist;

import javax.annotation.Nullable;

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
