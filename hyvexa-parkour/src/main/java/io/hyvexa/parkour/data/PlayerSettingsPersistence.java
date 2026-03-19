package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Persists player settings (toggles, music, HUD, speed) to MySQL.
 * Follows the DuelPreferenceStore pattern: immediate write on change, lazy load per player.
 */
public class PlayerSettingsPersistence {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS player_settings (
            player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
            reset_item_enabled BOOLEAN NOT NULL DEFAULT TRUE,
            players_hidden BOOLEAN NOT NULL DEFAULT FALSE,
            duel_hide_opponent BOOLEAN NOT NULL DEFAULT FALSE,
            ghost_visible BOOLEAN NOT NULL DEFAULT TRUE,
            advanced_hud_enabled BOOLEAN NOT NULL DEFAULT FALSE,
            hud_hidden BOOLEAN NOT NULL DEFAULT FALSE,
            music_label VARCHAR(32) DEFAULT NULL,
            checkpoint_sfx_enabled BOOLEAN NOT NULL DEFAULT TRUE,
            victory_sfx_enabled BOOLEAN NOT NULL DEFAULT TRUE,
            vip_speed_multiplier FLOAT NOT NULL DEFAULT 1.0,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        ) ENGINE=InnoDB
        """;

    private static final String SELECT_SQL = """
        SELECT reset_item_enabled, players_hidden, duel_hide_opponent, ghost_visible,
               advanced_hud_enabled, hud_hidden, music_label,
               checkpoint_sfx_enabled, victory_sfx_enabled, vip_speed_multiplier
        FROM player_settings WHERE player_uuid = ?
        """;

    private static final String UPSERT_SQL = """
        INSERT INTO player_settings (player_uuid, reset_item_enabled, players_hidden, duel_hide_opponent,
            ghost_visible, advanced_hud_enabled, hud_hidden, music_label,
            checkpoint_sfx_enabled, victory_sfx_enabled, vip_speed_multiplier, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            reset_item_enabled = VALUES(reset_item_enabled),
            players_hidden = VALUES(players_hidden),
            duel_hide_opponent = VALUES(duel_hide_opponent),
            ghost_visible = VALUES(ghost_visible),
            advanced_hud_enabled = VALUES(advanced_hud_enabled),
            hud_hidden = VALUES(hud_hidden),
            music_label = VALUES(music_label),
            checkpoint_sfx_enabled = VALUES(checkpoint_sfx_enabled),
            victory_sfx_enabled = VALUES(victory_sfx_enabled),
            vip_speed_multiplier = VALUES(vip_speed_multiplier),
            updated_at = VALUES(updated_at)
        """;

    private static PlayerSettingsPersistence INSTANCE;

    public static PlayerSettingsPersistence getInstance() {
        return INSTANCE;
    }

    public PlayerSettingsPersistence() {
        INSTANCE = this;
    }

    public void ensureTable() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, player_settings table will not be created");
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE_SQL)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to create player_settings table: " + e.getMessage());
        }
    }

    @Nonnull
    public PlayerSettings loadPlayer(@Nonnull UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return new PlayerSettings();
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                return new PlayerSettings();
            }
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_SQL)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        PlayerSettings s = new PlayerSettings();
                        s.resetItemEnabled = rs.getBoolean("reset_item_enabled");
                        s.playersHidden = rs.getBoolean("players_hidden");
                        s.duelHideOpponent = rs.getBoolean("duel_hide_opponent");
                        s.ghostVisible = rs.getBoolean("ghost_visible");
                        s.advancedHudEnabled = rs.getBoolean("advanced_hud_enabled");
                        s.hudHidden = rs.getBoolean("hud_hidden");
                        s.musicLabel = rs.getString("music_label");
                        s.checkpointSfxEnabled = rs.getBoolean("checkpoint_sfx_enabled");
                        s.victorySfxEnabled = rs.getBoolean("victory_sfx_enabled");
                        s.vipSpeedMultiplier = rs.getFloat("vip_speed_multiplier");
                        return s;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to load player settings for " + playerId + ": " + e.getMessage());
        }
        return new PlayerSettings();
    }

    public void savePlayer(@Nonnull UUID playerId, @Nonnull PlayerSettings settings) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection for settings save");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                stmt.setBoolean(2, settings.resetItemEnabled);
                stmt.setBoolean(3, settings.playersHidden);
                stmt.setBoolean(4, settings.duelHideOpponent);
                stmt.setBoolean(5, settings.ghostVisible);
                stmt.setBoolean(6, settings.advancedHudEnabled);
                stmt.setBoolean(7, settings.hudHidden);
                stmt.setString(8, settings.musicLabel);
                stmt.setBoolean(9, settings.checkpointSfxEnabled);
                stmt.setBoolean(10, settings.victorySfxEnabled);
                stmt.setFloat(11, settings.vipSpeedMultiplier);
                stmt.setTimestamp(12, new Timestamp(System.currentTimeMillis()));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to save player settings for " + playerId + ": " + e.getMessage());
        }
    }

    /**
     * Loads the current DB row, applies the updater, and saves back.
     * Centralizes the read-modify-write pattern so callers don't need to
     * load/save manually or worry about overwriting fields from other stores.
     */
    public void updateField(@Nonnull UUID playerId, @Nonnull Consumer<PlayerSettings> updater) {
        PlayerSettings s = loadPlayer(playerId);
        updater.accept(s);
        savePlayer(playerId, s);
    }

    public static class PlayerSettings {
        public boolean resetItemEnabled = true;
        public boolean playersHidden = false;
        public boolean duelHideOpponent = false;
        public boolean ghostVisible = true;
        public boolean advancedHudEnabled = false;
        public boolean hudHidden = false;
        @Nullable public String musicLabel = null;
        public boolean checkpointSfxEnabled = true;
        public boolean victorySfxEnabled = true;
        public float vipSpeedMultiplier = 1.0f;
    }
}
