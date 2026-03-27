package io.hyvexa.runorfall.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class RunOrFallDatabaseSetup {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private RunOrFallDatabaseSetup() {
    }

    public static void ensureTables() {
        if (!DatabaseManager.get().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, skipping RunOrFall table setup");
            return;
        }
        try (Connection conn = DatabaseManager.get().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection for RunOrFall table setup");
                return;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS runorfall_migrations ("
                        + "migration_key VARCHAR(64) NOT NULL PRIMARY KEY"
                        + ") ENGINE=InnoDB");
            }
            // --- Migrations ---
            migrateSettingsColumns(conn);
            migrateMapColumns(conn);
            migratePlatformColumns(conn);
            LOGGER.atInfo().log("RunOrFall migrations setup complete (3 migrations checked)");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to set up RunOrFall tables: " + e.getMessage());
        }
    }

    private static void migrateSettingsColumns(Connection conn) {
        try {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT 1 FROM runorfall_migrations WHERE migration_key = 'settings_columns_v1'")) {
                DatabaseManager.applyQueryTimeout(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return;
                }
            }
            DatabaseManager.addColumnIfMissing(conn, "runorfall_settings", "active_map_id", "VARCHAR(64) NULL");
            DatabaseManager.addColumnIfMissing(conn, "runorfall_settings", "min_players", "INT NOT NULL DEFAULT 2");
            DatabaseManager.addColumnIfMissing(conn, "runorfall_settings", "min_players_time_seconds", "INT NOT NULL DEFAULT 300");
            DatabaseManager.addColumnIfMissing(conn, "runorfall_settings", "optimal_players", "INT NOT NULL DEFAULT 4");
            DatabaseManager.addColumnIfMissing(conn, "runorfall_settings", "optimal_players_time_seconds", "INT NOT NULL DEFAULT 60");
            DatabaseManager.addColumnIfMissing(conn, "runorfall_settings", "blink_distance_blocks", "INT NOT NULL DEFAULT 7");
            DatabaseManager.addColumnIfMissing(conn, "runorfall_settings", "blink_start_charges", "INT NOT NULL DEFAULT 1");
            DatabaseManager.addColumnIfMissing(conn, "runorfall_settings", "blink_charge_every_blocks_broken", "INT NOT NULL DEFAULT 100");
            DatabaseManager.addColumnIfMissing(conn, "runorfall_settings", "feathers_per_minute_alive", "INT NOT NULL DEFAULT 1");
            DatabaseManager.addColumnIfMissing(conn, "runorfall_settings", "feathers_per_player_eliminated", "INT NOT NULL DEFAULT 5");
            DatabaseManager.addColumnIfMissing(conn, "runorfall_settings", "feathers_for_win", "INT NOT NULL DEFAULT 25");
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO runorfall_migrations (migration_key) VALUES ('settings_columns_v1')")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            LOGGER.atInfo().log("Settings columns migration complete");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to run settings columns migration");
        }
    }

    private static void migrateMapColumns(Connection conn) {
        try {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT 1 FROM runorfall_migrations WHERE migration_key = 'map_columns_v1'")) {
                DatabaseManager.applyQueryTimeout(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return;
                }
            }
            DatabaseManager.addColumnIfMissing(conn, "runorfall_maps", "min_players", "INT NOT NULL DEFAULT 2");
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO runorfall_migrations (migration_key) VALUES ('map_columns_v1')")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            LOGGER.atInfo().log("Map columns migration complete");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to run map columns migration");
        }
    }

    private static void migratePlatformColumns(Connection conn) {
        try {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT 1 FROM runorfall_migrations WHERE migration_key = 'platform_columns_v1'")) {
                DatabaseManager.applyQueryTimeout(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return;
                }
            }
            DatabaseManager.addColumnIfMissing(conn, "runorfall_map_platforms", "target_block_item_id", "VARCHAR(128) NULL");
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO runorfall_migrations (migration_key) VALUES ('platform_columns_v1')")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            LOGGER.atInfo().log("Platform columns migration complete");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to run platform columns migration");
        }
    }
}
