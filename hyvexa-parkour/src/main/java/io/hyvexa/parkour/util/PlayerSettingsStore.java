package io.hyvexa.parkour.util;

import io.hyvexa.parkour.data.PlayerSettingsPersistence;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSettingsStore {

    private static final ConcurrentHashMap<UUID, Boolean> RESET_ITEM_ENABLED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> DUEL_HIDE_OPPONENT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> GHOST_VISIBLE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> PLAYERS_HIDDEN = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> ADVANCED_HUD_ENABLED = new ConcurrentHashMap<>();

    private PlayerSettingsStore() {
    }

    /**
     * Populates in-memory maps from a pre-loaded PlayerSettings.
     * Call on PlayerReadyEvent with the result of a single persistence.loadPlayer() call.
     */
    public static void loadFrom(UUID playerId, PlayerSettingsPersistence.PlayerSettings settings) {
        if (playerId == null || settings == null) {
            return;
        }
        RESET_ITEM_ENABLED.put(playerId, settings.resetItemEnabled);
        PLAYERS_HIDDEN.put(playerId, settings.playersHidden);
        DUEL_HIDE_OPPONENT.put(playerId, settings.duelHideOpponent);
        GHOST_VISIBLE.put(playerId, settings.ghostVisible);
        ADVANCED_HUD_ENABLED.put(playerId, settings.advancedHudEnabled);
    }

    public static boolean isResetItemEnabled(UUID playerId) {
        return playerId == null || RESET_ITEM_ENABLED.getOrDefault(playerId, true);
    }

    public static void setResetItemEnabled(UUID playerId, boolean enabled) {
        if (playerId == null) {
            return;
        }
        RESET_ITEM_ENABLED.put(playerId, enabled);
        persistField(playerId, s -> s.resetItemEnabled = enabled);
    }

    public static boolean toggleResetItemEnabled(UUID playerId) {
        if (playerId == null) {
            return true;
        }
        boolean enabled = RESET_ITEM_ENABLED.getOrDefault(playerId, true);
        boolean newValue = !enabled;
        RESET_ITEM_ENABLED.put(playerId, newValue);
        persistField(playerId, s -> s.resetItemEnabled = newValue);
        return newValue;
    }

    public static boolean isPlayersHidden(UUID playerId) {
        return playerId != null && PLAYERS_HIDDEN.getOrDefault(playerId, false);
    }

    public static void setPlayersHidden(UUID playerId, boolean hidden) {
        if (playerId == null) {
            return;
        }
        PLAYERS_HIDDEN.put(playerId, hidden);
        persistField(playerId, s -> s.playersHidden = hidden);
    }

    public static boolean isDuelOpponentHidden(UUID playerId) {
        return playerId != null && DUEL_HIDE_OPPONENT.getOrDefault(playerId, false);
    }

    public static void setDuelOpponentHidden(UUID playerId, boolean hidden) {
        if (playerId == null) {
            return;
        }
        DUEL_HIDE_OPPONENT.put(playerId, hidden);
        persistField(playerId, s -> s.duelHideOpponent = hidden);
    }

    public static boolean toggleDuelOpponentHidden(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        boolean hidden = DUEL_HIDE_OPPONENT.getOrDefault(playerId, false);
        boolean newValue = !hidden;
        DUEL_HIDE_OPPONENT.put(playerId, newValue);
        persistField(playerId, s -> s.duelHideOpponent = newValue);
        return newValue;
    }

    public static boolean isGhostVisible(UUID playerId) {
        return playerId == null || GHOST_VISIBLE.getOrDefault(playerId, true);
    }

    public static boolean toggleGhostVisible(UUID playerId) {
        if (playerId == null) {
            return true;
        }
        boolean visible = GHOST_VISIBLE.getOrDefault(playerId, true);
        boolean newValue = !visible;
        GHOST_VISIBLE.put(playerId, newValue);
        persistField(playerId, s -> s.ghostVisible = newValue);
        return newValue;
    }

    public static boolean isAdvancedHudEnabled(UUID playerId) {
        return playerId != null && ADVANCED_HUD_ENABLED.getOrDefault(playerId, false);
    }

    public static boolean toggleAdvancedHud(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        boolean enabled = ADVANCED_HUD_ENABLED.getOrDefault(playerId, false);
        boolean newValue = !enabled;
        ADVANCED_HUD_ENABLED.put(playerId, newValue);
        persistField(playerId, s -> s.advancedHudEnabled = newValue);
        return newValue;
    }

    public static void clearSession(UUID playerId) {
        if (playerId == null) {
            return;
        }
        RESET_ITEM_ENABLED.remove(playerId);
        DUEL_HIDE_OPPONENT.remove(playerId);
        GHOST_VISIBLE.remove(playerId);
        PLAYERS_HIDDEN.remove(playerId);
        ADVANCED_HUD_ENABLED.remove(playerId);
    }

    private static void persistField(UUID playerId, java.util.function.Consumer<PlayerSettingsPersistence.PlayerSettings> updater) {
        PlayerSettingsPersistence persistence = PlayerSettingsPersistence.getInstance();
        if (persistence == null) {
            return;
        }
        persistence.updateField(playerId, updater);
    }
}
