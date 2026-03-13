package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class ParkourDatabaseSetup {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ParkourDatabaseSetup() {
    }

    public static void ensureTables() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, skipping Parkour table setup");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection for Parkour table setup");
                return;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS parkour_migrations ("
                        + "migration_key VARCHAR(64) NOT NULL PRIMARY KEY"
                        + ") ENGINE=InnoDB");
            }

            // --- Migrations (order matters: parkour_migrations table must exist first) ---
            migrateMapMedalTimes(conn);
            migrateMedalRewardFeathers(conn);

            LOGGER.atInfo().log("Parkour migrations setup complete (2 migrations checked)");

        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to set up Parkour tables: " + e.getMessage());
        }
    }

    private static void migrateMapMedalTimes(Connection conn) {
        try {
            // Check if migration already applied
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT 1 FROM parkour_migrations WHERE migration_key = 'map_medal_times_v1'")) {
                DatabaseManager.applyQueryTimeout(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return; // Already migrated
                    }
                }
            }

            DatabaseManager.addColumnIfMissing(conn, "maps", "bronze_time_ms", "BIGINT DEFAULT NULL");
            DatabaseManager.addColumnIfMissing(conn, "maps", "silver_time_ms", "BIGINT DEFAULT NULL");
            DatabaseManager.addColumnIfMissing(conn, "maps", "gold_time_ms", "BIGINT DEFAULT NULL");
            DatabaseManager.renameColumnIfExists(conn, "maps", "author_time_ms", "emerald_time_ms", "BIGINT DEFAULT NULL");
            DatabaseManager.addColumnIfMissing(conn, "maps", "emerald_time_ms", "BIGINT DEFAULT NULL");

            // Record migration
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO parkour_migrations (migration_key) VALUES ('map_medal_times_v1')")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            LOGGER.atInfo().log("Map medal times migration complete");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to run map medal times migration");
        }
    }

    private static void migrateMedalRewardFeathers(Connection conn) {
        try {
            // Check if migration already applied
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT 1 FROM parkour_migrations WHERE migration_key = 'medal_reward_feathers_v1'")) {
                DatabaseManager.applyQueryTimeout(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return; // Already migrated
                    }
                }
            }

            DatabaseManager.renameColumnIfExists(conn, "medal_rewards", "author_feathers", "emerald_feathers", "INT NOT NULL DEFAULT 0");
            DatabaseManager.renameColumnIfExists(conn, "medal_rewards", "platinum_feathers", "emerald_feathers", "INT NOT NULL DEFAULT 0");
            DatabaseManager.addColumnIfMissing(conn, "medal_rewards", "emerald_feathers", "INT NOT NULL DEFAULT 0");
            DatabaseManager.addColumnIfMissing(conn, "medal_rewards", "insane_feathers", "INT NOT NULL DEFAULT 0");

            // Record migration
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO parkour_migrations (migration_key) VALUES ('medal_reward_feathers_v1')")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            LOGGER.atInfo().log("Medal reward feathers migration complete");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to run medal reward feathers migration");
        }
    }
}
