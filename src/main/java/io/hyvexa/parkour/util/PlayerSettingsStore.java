package io.hyvexa.parkour.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSettingsStore {

    private static final ConcurrentHashMap<UUID, Boolean> RESET_ITEM_ENABLED = new ConcurrentHashMap<>();

    private PlayerSettingsStore() {
    }

    public static boolean isResetItemEnabled(UUID playerId) {
        return playerId == null || RESET_ITEM_ENABLED.getOrDefault(playerId, true);
    }

    public static void setResetItemEnabled(UUID playerId, boolean enabled) {
        if (playerId == null) {
            return;
        }
        RESET_ITEM_ENABLED.put(playerId, enabled);
    }

    public static boolean toggleResetItemEnabled(UUID playerId) {
        if (playerId == null) {
            return true;
        }
        boolean enabled = RESET_ITEM_ENABLED.getOrDefault(playerId, true);
        boolean newValue = !enabled;
        RESET_ITEM_ENABLED.put(playerId, newValue);
        return newValue;
    }

    public static void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        RESET_ITEM_ENABLED.remove(playerId);
    }
}
