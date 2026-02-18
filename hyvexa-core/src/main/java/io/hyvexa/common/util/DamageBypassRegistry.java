package io.hyvexa.common.util;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry of players who should bypass the NoPlayerDamageSystem.
 * Used by modules (e.g. Purge) that need players to take damage during gameplay.
 */
public final class DamageBypassRegistry {

    private static final Set<UUID> bypassed = ConcurrentHashMap.newKeySet();

    private DamageBypassRegistry() {}

    public static void add(UUID playerId) {
        if (playerId != null) {
            bypassed.add(playerId);
        }
    }

    public static void remove(UUID playerId) {
        if (playerId != null) {
            bypassed.remove(playerId);
        }
    }

    public static boolean isBypassed(UUID playerId) {
        return playerId != null && bypassed.contains(playerId);
    }
}
