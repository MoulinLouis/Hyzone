package io.hyvexa.common.util;

import java.util.UUID;

/**
 * Global registry of players who should bypass the NoPlayerDamageSystem.
 * Uses JVM system properties so the flag is visible across plugin classloaders.
 */
public final class DamageBypassRegistry {

    private static final String KEY_PREFIX = "hyvexa.damage-bypass.";

    private DamageBypassRegistry() {}

    public static void add(UUID playerId) {
        if (playerId != null) {
            System.setProperty(key(playerId), "1");
        }
    }

    public static void remove(UUID playerId) {
        if (playerId != null) {
            System.clearProperty(key(playerId));
        }
    }

    public static boolean isBypassed(UUID playerId) {
        return playerId != null && System.getProperty(key(playerId)) != null;
    }

    private static String key(UUID playerId) {
        return KEY_PREFIX + playerId;
    }
}
