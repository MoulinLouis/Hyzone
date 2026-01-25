package io.hyvexa.parkour.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSettingsStore {

    private static final ConcurrentHashMap<UUID, Boolean> RESET_ITEM_ENABLED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> DUEL_HIDE_OPPONENT = new ConcurrentHashMap<>();

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

    public static boolean isDuelOpponentHidden(UUID playerId) {
        return playerId != null && DUEL_HIDE_OPPONENT.getOrDefault(playerId, false);
    }

    public static void setDuelOpponentHidden(UUID playerId, boolean hidden) {
        if (playerId == null) {
            return;
        }
        DUEL_HIDE_OPPONENT.put(playerId, hidden);
    }

    public static boolean toggleDuelOpponentHidden(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        boolean hidden = DUEL_HIDE_OPPONENT.getOrDefault(playerId, false);
        boolean newValue = !hidden;
        DUEL_HIDE_OPPONENT.put(playerId, newValue);
        return newValue;
    }

    public static void clearSession(UUID playerId) {
        if (playerId == null) {
            return;
        }
        RESET_ITEM_ENABLED.remove(playerId);
    }

    public static void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        RESET_ITEM_ENABLED.remove(playerId);
        DUEL_HIDE_OPPONENT.remove(playerId);
    }
}
