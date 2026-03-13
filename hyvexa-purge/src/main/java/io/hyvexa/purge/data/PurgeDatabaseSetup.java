package io.hyvexa.purge.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class PurgeDatabaseSetup {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PurgeDatabaseSetup() {
    }

    public static void ensureTables() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, skipping Purge table setup");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection for Purge table setup");
                return;
            }
            try (Statement stmt = conn.createStatement()) {

                // --- Store tables ---

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_player_stats ("
                        + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                        + "best_wave INT NOT NULL DEFAULT 0, "
                        + "total_kills INT NOT NULL DEFAULT 0, "
                        + "total_sessions INT NOT NULL DEFAULT 0"
                        + ") ENGINE=InnoDB");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_player_scrap ("
                        + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                        + "scrap BIGINT NOT NULL DEFAULT 0, "
                        + "lifetime_scrap_earned BIGINT NOT NULL DEFAULT 0"
                        + ") ENGINE=InnoDB");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_weapon_upgrades ("
                        + "uuid VARCHAR(36) NOT NULL, "
                        + "weapon_id VARCHAR(32) NOT NULL, "
                        + "level INT NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (uuid, weapon_id)"
                        + ") ENGINE=InnoDB");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_migrations ("
                        + "migration_key VARCHAR(64) NOT NULL PRIMARY KEY"
                        + ") ENGINE=InnoDB");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_weapon_xp ("
                        + "uuid VARCHAR(36) NOT NULL, "
                        + "weapon_id VARCHAR(32) NOT NULL, "
                        + "xp INT NOT NULL DEFAULT 0, "
                        + "level INT NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (uuid, weapon_id)"
                        + ") ENGINE=InnoDB");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_player_classes ("
                        + "uuid VARCHAR(36) NOT NULL, "
                        + "class_id VARCHAR(32) NOT NULL, "
                        + "PRIMARY KEY (uuid, class_id)"
                        + ") ENGINE=InnoDB");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_player_selected_class ("
                        + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                        + "selected_class VARCHAR(32) DEFAULT NULL"
                        + ") ENGINE=InnoDB");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_daily_missions ("
                        + "uuid VARCHAR(36) NOT NULL, "
                        + "mission_date DATE NOT NULL, "
                        + "total_kills INT NOT NULL DEFAULT 0, "
                        + "best_wave INT NOT NULL DEFAULT 0, "
                        + "best_combo INT NOT NULL DEFAULT 0, "
                        + "claimed_wave TINYINT NOT NULL DEFAULT 0, "
                        + "claimed_combo TINYINT NOT NULL DEFAULT 0, "
                        + "claimed_kill TINYINT NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (uuid, mission_date)"
                        + ") ENGINE=InnoDB");

                // --- Manager tables ---

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_zombie_variants ("
                        + "variant_key VARCHAR(32) NOT NULL PRIMARY KEY, "
                        + "label VARCHAR(64) NOT NULL, "
                        + "base_health INT NOT NULL DEFAULT 50, "
                        + "base_damage FLOAT NOT NULL DEFAULT 20, "
                        + "speed_multiplier DOUBLE NOT NULL DEFAULT 1.0, "
                        + "npc_type VARCHAR(64) NOT NULL DEFAULT 'Zombie', "
                        + "scrap_reward INT NOT NULL DEFAULT 10"
                        + ") ENGINE=InnoDB");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_weapon_levels ("
                        + "weapon_id VARCHAR(32) NOT NULL, "
                        + "level INT NOT NULL, "
                        + "damage INT NOT NULL, "
                        + "cost BIGINT NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (weapon_id, level)"
                        + ") ENGINE=InnoDB");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_weapon_defaults ("
                        + "weapon_id VARCHAR(32) NOT NULL PRIMARY KEY, "
                        + "default_unlocked BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "unlock_cost BIGINT NOT NULL DEFAULT 500, "
                        + "session_weapon BOOLEAN NOT NULL DEFAULT FALSE"
                        + ") ENGINE=InnoDB");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_settings ("
                        + "setting_key VARCHAR(64) NOT NULL PRIMARY KEY, "
                        + "setting_value VARCHAR(255) NOT NULL"
                        + ") ENGINE=InnoDB");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_waves ("
                        + "wave_number INT NOT NULL PRIMARY KEY, "
                        + "spawn_delay_ms INT NOT NULL DEFAULT 500, "
                        + "spawn_batch_size INT NOT NULL DEFAULT 5"
                        + ") ENGINE=InnoDB");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_wave_variant_counts ("
                        + "wave_number INT NOT NULL, "
                        + "variant_key VARCHAR(32) NOT NULL, "
                        + "count INT NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (wave_number, variant_key)"
                        + ") ENGINE=InnoDB");
            }

            // --- Migrations (order matters: purge_migrations table must exist first) ---
            runOwnershipMigration(conn);
            migrateAddNpcType(conn);
            migrateAddScrapReward(conn);
            ensureSettingsSchema(conn);
            migrateFromOldWaveColumns(conn);

            LOGGER.atInfo().log("Purge database setup complete (14 tables ensured, migrations checked)");

        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to set up Purge tables: " + e.getMessage());
        }
    }

    private static void runOwnershipMigration(Connection conn) {
        try {
            // Check if migration already applied
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT 1 FROM purge_migrations WHERE migration_key = 'weapon_ownership_v1'")) {
                DatabaseManager.applyQueryTimeout(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return; // Already migrated
                    }
                }
            }
            // Wipe existing upgrade data (fresh start)
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM purge_weapon_upgrades")) {
                DatabaseManager.applyQueryTimeout(stmt);
                int deleted = stmt.executeUpdate();
                LOGGER.atInfo().log("Weapon ownership migration: deleted " + deleted + " existing upgrade rows");
            }
            // Record migration
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO purge_migrations (migration_key) VALUES ('weapon_ownership_v1')")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            LOGGER.atInfo().log("Weapon ownership migration complete");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to run weapon ownership migration");
        }
    }

    private static void migrateAddNpcType(Connection conn) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "purge_zombie_variants", "npc_type")) {
            if (rs.next()) {
                return; // column already exists
            }
        } catch (SQLException e) {
            LOGGER.atFine().log("Could not check for npc_type column: " + e.getMessage());
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "ALTER TABLE purge_zombie_variants ADD COLUMN npc_type VARCHAR(64) NOT NULL DEFAULT 'Zombie'")) {
            stmt.executeUpdate();
            LOGGER.atInfo().log("Added npc_type column to purge_zombie_variants");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to add npc_type column");
        }
    }

    private static void migrateAddScrapReward(Connection conn) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "purge_zombie_variants", "scrap_reward")) {
            if (rs.next()) {
                return; // column already exists
            }
        } catch (SQLException e) {
            LOGGER.atFine().log("Could not check for scrap_reward column: " + e.getMessage());
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "ALTER TABLE purge_zombie_variants ADD COLUMN scrap_reward INT NOT NULL DEFAULT 10")) {
            stmt.executeUpdate();
            LOGGER.atInfo().log("Added scrap_reward column to purge_zombie_variants");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to add scrap_reward column");
        }
    }

    private static void ensureSettingsSchema(Connection conn) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "purge_settings", "setting_key")) {
            if (rs.next()) {
                return; // schema is correct
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to check purge_settings schema");
            return;
        }
        // Table exists but has wrong columns — drop and recreate
        try (PreparedStatement drop = conn.prepareStatement("DROP TABLE IF EXISTS purge_settings")) {
            DatabaseManager.applyQueryTimeout(drop);
            drop.executeUpdate();
            LOGGER.atInfo().log("Dropped purge_settings table with wrong schema, will recreate");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to drop old purge_settings table");
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS purge_settings ("
                + "setting_key VARCHAR(64) NOT NULL PRIMARY KEY, "
                + "setting_value VARCHAR(255) NOT NULL"
                + ") ENGINE=InnoDB")) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to recreate purge_settings table");
        }
    }

    private static void migrateFromOldWaveColumns(Connection conn) {
        boolean hasOldColumns;
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "purge_waves", "slow_count")) {
            hasOldColumns = rs.next();
        } catch (SQLException e) {
            LOGGER.atFine().log("Could not check for old wave columns: " + e.getMessage());
            return;
        }
        if (!hasOldColumns) {
            return;
        }

        LOGGER.atInfo().log("Migrating purge_waves from old slow/normal/fast columns to variant counts table");
        String selectSql = "SELECT wave_number, slow_count, normal_count, fast_count FROM purge_waves";
        String insertSql = "INSERT IGNORE INTO purge_wave_variant_counts (wave_number, variant_key, count) VALUES (?, ?, ?)";
        boolean migrated = DatabaseManager.getInstance().withTransaction(txConn -> {
            try (PreparedStatement selectStmt = txConn.prepareStatement(selectSql);
                 ResultSet rs = selectStmt.executeQuery()) {
                try (PreparedStatement insertStmt = txConn.prepareStatement(insertSql)) {
                    while (rs.next()) {
                        int waveNum = rs.getInt("wave_number");
                        int slow = rs.getInt("slow_count");
                        int normal = rs.getInt("normal_count");
                        int fast = rs.getInt("fast_count");
                        if (slow > 0) {
                            insertStmt.setInt(1, waveNum);
                            insertStmt.setString(2, "SLOW");
                            insertStmt.setInt(3, slow);
                            insertStmt.addBatch();
                        }
                        if (normal > 0) {
                            insertStmt.setInt(1, waveNum);
                            insertStmt.setString(2, "NORMAL");
                            insertStmt.setInt(3, normal);
                            insertStmt.addBatch();
                        }
                        if (fast > 0) {
                            insertStmt.setInt(1, waveNum);
                            insertStmt.setString(2, "FAST");
                            insertStmt.setInt(3, fast);
                            insertStmt.addBatch();
                        }
                    }
                    insertStmt.executeBatch();
                }
            }

            String[] dropSqls = {
                "ALTER TABLE purge_waves DROP COLUMN slow_count",
                "ALTER TABLE purge_waves DROP COLUMN normal_count",
                "ALTER TABLE purge_waves DROP COLUMN fast_count"
            };
            for (String drop : dropSqls) {
                try (PreparedStatement stmt = txConn.prepareStatement(drop)) {
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    LOGGER.atFine().log("Could not drop old column: " + e.getMessage());
                }
            }
        });
        if (migrated) {
            LOGGER.atInfo().log("Wave column migration complete: old columns removed");
        }
    }
}
