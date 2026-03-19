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
     * Loads player settings from DB and populates in-memory maps.
     * Call on PlayerReadyEvent before other setup.
     */
    public static void loadFromDb(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PlayerSettingsPersistence persistence = PlayerSettingsPersistence.getInstance();
        if (persistence == null) {
            return;
        }
        PlayerSettingsPersistence.PlayerSettings settings = persistence.loadPlayer(playerId);
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
        persistAsync(playerId);
    }

    public static boolean toggleResetItemEnabled(UUID playerId) {
        if (playerId == null) {
            return true;
        }
        boolean enabled = RESET_ITEM_ENABLED.getOrDefault(playerId, true);
        boolean newValue = !enabled;
        RESET_ITEM_ENABLED.put(playerId, newValue);
        persistAsync(playerId);
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
        persistAsync(playerId);
    }

    public static boolean isDuelOpponentHidden(UUID playerId) {
        return playerId != null && DUEL_HIDE_OPPONENT.getOrDefault(playerId, false);
    }

    public static void setDuelOpponentHidden(UUID playerId, boolean hidden) {
        if (playerId == null) {
            return;
        }
        DUEL_HIDE_OPPONENT.put(playerId, hidden);
        persistAsync(playerId);
    }

    public static boolean toggleDuelOpponentHidden(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        boolean hidden = DUEL_HIDE_OPPONENT.getOrDefault(playerId, false);
        boolean newValue = !hidden;
        DUEL_HIDE_OPPONENT.put(playerId, newValue);
        persistAsync(playerId);
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
        persistAsync(playerId);
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
        persistAsync(playerId);
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

    public static void clear(UUID playerId) {
        clearSession(playerId);
    }

    /**
     * Builds a PlayerSettings snapshot from current in-memory state and persists it.
     * Includes music/SFX state from PlayerMusicPage to avoid overwriting those fields.
     */
    private static void persistAsync(UUID playerId) {
        PlayerSettingsPersistence persistence = PlayerSettingsPersistence.getInstance();
        if (persistence == null) {
            return;
        }
        PlayerSettingsPersistence.PlayerSettings settings = buildFullSettings(playerId);
        persistence.savePlayer(playerId, settings);
    }

    /**
     * Builds a PlayerSettings POJO from all current in-memory maps.
     * Only includes the 5 settings managed by this store — caller must fill in remaining fields.
     */
    public static PlayerSettingsPersistence.PlayerSettings buildCurrentSettings(UUID playerId) {
        PlayerSettingsPersistence.PlayerSettings s = new PlayerSettingsPersistence.PlayerSettings();
        s.resetItemEnabled = RESET_ITEM_ENABLED.getOrDefault(playerId, true);
        s.playersHidden = PLAYERS_HIDDEN.getOrDefault(playerId, false);
        s.duelHideOpponent = DUEL_HIDE_OPPONENT.getOrDefault(playerId, false);
        s.ghostVisible = GHOST_VISIBLE.getOrDefault(playerId, true);
        s.advancedHudEnabled = ADVANCED_HUD_ENABLED.getOrDefault(playerId, false);
        return s;
    }

    /**
     * Reads current DB row, then overlays in-memory toggle state.
     * Preserves music/SFX/HUD/VIP fields not managed by this store.
     */
    private static PlayerSettingsPersistence.PlayerSettings buildFullSettings(UUID playerId) {
        PlayerSettingsPersistence persistence = PlayerSettingsPersistence.getInstance();
        // Start from DB state to preserve fields managed by other stores
        PlayerSettingsPersistence.PlayerSettings s = persistence != null
                ? persistence.loadPlayer(playerId)
                : new PlayerSettingsPersistence.PlayerSettings();
        // Overlay the 5 fields managed by this store
        s.resetItemEnabled = RESET_ITEM_ENABLED.getOrDefault(playerId, true);
        s.playersHidden = PLAYERS_HIDDEN.getOrDefault(playerId, false);
        s.duelHideOpponent = DUEL_HIDE_OPPONENT.getOrDefault(playerId, false);
        s.ghostVisible = GHOST_VISIBLE.getOrDefault(playerId, true);
        s.advancedHudEnabled = ADVANCED_HUD_ENABLED.getOrDefault(playerId, false);
        return s;
    }
}
