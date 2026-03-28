package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Core table creation and migrations for ascend_players, ascend_maps, and ascend_player_maps.
 */
final class AscendCoreSchema {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private AscendCoreSchema() {
    }

    static void createAndMigrate(Connection conn, Statement stmt) throws SQLException {
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS ascend_players (
                uuid VARCHAR(36) PRIMARY KEY,
                volt_mantissa DOUBLE NOT NULL DEFAULT 0,
                volt_exp10 INT NOT NULL DEFAULT 0,
                elevation_multiplier INT NOT NULL DEFAULT 1,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB
            """);
        migrateRebirthToElevation(conn);
        ensureElevationColumn(conn);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS ascend_maps (
                id VARCHAR(32) PRIMARY KEY,
                name VARCHAR(64) NOT NULL,
                price BIGINT NOT NULL DEFAULT 0,
                robot_price BIGINT NOT NULL,
                base_reward BIGINT NOT NULL,
                base_run_time_ms BIGINT NOT NULL,
                robot_time_reduction_ms BIGINT NOT NULL DEFAULT 0,
                storage_capacity INT NOT NULL DEFAULT 100,
                world VARCHAR(64) NOT NULL,
                start_x DOUBLE NOT NULL,
                start_y DOUBLE NOT NULL,
                start_z DOUBLE NOT NULL,
                start_rot_x FLOAT NOT NULL DEFAULT 0,
                start_rot_y FLOAT NOT NULL DEFAULT 0,
                start_rot_z FLOAT NOT NULL DEFAULT 0,
                finish_x DOUBLE NOT NULL,
                finish_y DOUBLE NOT NULL,
                finish_z DOUBLE NOT NULL,
                waypoints_json TEXT,
                display_order INT NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB
            """);

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS ascend_player_maps (
                player_uuid VARCHAR(36) NOT NULL,
                map_id VARCHAR(32) NOT NULL,
                unlocked BOOLEAN NOT NULL DEFAULT FALSE,
                completed_manually BOOLEAN NOT NULL DEFAULT FALSE,
                has_robot BOOLEAN NOT NULL DEFAULT FALSE,
                robot_speed_level INT NOT NULL DEFAULT 0,
                multiplier_mantissa DOUBLE NOT NULL DEFAULT 1.0,
                multiplier_exp10 INT NOT NULL DEFAULT 0,
                last_collection_at TIMESTAMP NULL,
                PRIMARY KEY (player_uuid, map_id),
                FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE,
                FOREIGN KEY (map_id) REFERENCES ascend_maps(id) ON DELETE CASCADE
            ) ENGINE=InnoDB
            """);

        migrateToNewMultiplierSchema(conn);
        ensureRobotTimeReductionColumn(conn);
        ensureRobotStarsColumn(conn);

        // Migrate volt/multiplier columns from DECIMAL to scientific notation (mantissa + exponent)
        migrateToScientificNotation(conn);

        // Rename coins_* columns to volt_* (cosmetic DB rename)
        migrateCoinsColumnsToVolt(conn);

        // Rename prior volt-era columns to new volt_* names
        migrateVexaColumnsToVolt(conn);

        ensurePlayerNameColumn(conn);
    }

    private static void migrateRebirthToElevation(Connection conn) {
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "rebirth_multiplier")) {
            return;
        }
        if (AscendDatabaseSetup.columnExists(conn, "ascend_players", "elevation_multiplier")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_players CHANGE COLUMN rebirth_multiplier elevation_multiplier INT NOT NULL DEFAULT 1");
            LOGGER.atInfo().log("Migrated rebirth_multiplier to elevation_multiplier");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to rename rebirth_multiplier column: " + e.getMessage());
        }
    }

    private static void ensureElevationColumn(Connection conn) {
        AscendDatabaseSetup.ensureColumn(conn, "ascend_players", "elevation_multiplier", "INT NOT NULL DEFAULT 1");
    }

    private static void migrateToNewMultiplierSchema(Connection conn) {
        if (AscendDatabaseSetup.columnExists(conn, "ascend_player_maps", "multiplier")) {
            return;
        }
        try {
            DatabaseManager.withTransaction(conn, c -> {
                try (Statement stmt = c.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE ascend_player_maps ADD COLUMN multiplier DOUBLE NOT NULL DEFAULT 1.0");
                    if (AscendDatabaseSetup.columnExists(c, "ascend_player_maps", "multiplier_value")
                        && AscendDatabaseSetup.columnExists(c, "ascend_player_maps", "robot_multiplier_bonus")) {
                        stmt.executeUpdate("""
                            UPDATE ascend_player_maps
                            SET multiplier = GREATEST(1.0, multiplier_value + robot_multiplier_bonus)
                            """);
                        LOGGER.atInfo().log("Migrated multiplier data to new schema");
                    }
                    if (AscendDatabaseSetup.columnExists(c, "ascend_player_maps", "robot_count")) {
                        stmt.executeUpdate("""
                            UPDATE ascend_player_maps
                            SET has_robot = (robot_count > 0)
                            WHERE robot_count > 0 AND has_robot = FALSE
                            """);
                    }
                }
            });
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to migrate multiplier schema (rolled back): " + e.getMessage());
        }
    }

    private static void ensureRobotTimeReductionColumn(Connection conn) {
        AscendDatabaseSetup.ensureColumn(conn, "ascend_maps", "robot_time_reduction_ms", "BIGINT NOT NULL DEFAULT 0");
    }

    private static void ensureRobotStarsColumn(Connection conn) {
        AscendDatabaseSetup.ensureColumn(conn, "ascend_player_maps", "robot_stars", "INT NOT NULL DEFAULT 0");
    }

    /**
     * Migrate volt/multiplier columns from DECIMAL/DOUBLE to scientific notation (mantissa + exponent).
     * For each column: add _mantissa DOUBLE + _exp10 INT, populate from old values, drop old column.
     * Idempotent: checks for volt_mantissa to determine if already migrated.
     */
    private static void migrateToScientificNotation(Connection conn) {
        // Already migrated if volt_mantissa (or legacy coins_mantissa) exists
        if (AscendDatabaseSetup.columnExists(conn, "ascend_players", "volt_mantissa")
                || AscendDatabaseSetup.columnExists(conn, "ascend_players", "vexa_mantissa")
                || AscendDatabaseSetup.columnExists(conn, "ascend_players", "coins_mantissa")) {
            return;
        }

        LOGGER.atInfo().log("Starting migration to scientific notation (BigNumber)...");

        // Migrate ascend_players: coins, total_coins_earned, summit_accumulated_coins, elevation_accumulated_coins
        // Source columns use old names; target columns use new volt_* names
        String[][] playerColumns = {
            {"coins", "volt_mantissa", "volt_exp10", "0"},
            {"total_coins_earned", "total_volt_earned_mantissa", "total_volt_earned_exp10", "0"},
            {"summit_accumulated_coins", "summit_accumulated_volt_mantissa", "summit_accumulated_volt_exp10", "0"},
            {"elevation_accumulated_coins", "elevation_accumulated_volt_mantissa", "elevation_accumulated_volt_exp10", "0"}
        };

        try {
            DatabaseManager.withTransaction(conn, c -> {
                try (Statement stmt = c.createStatement()) {
                    // Add new columns for ascend_players
                    for (String[] col : playerColumns) {
                        String oldCol = col[0];
                        String mantissaCol = col[1];
                        String exp10Col = col[2];

                        if (!AscendDatabaseSetup.columnExists(c, "ascend_players", oldCol)) {
                            // Column doesn't exist yet (fresh install or already dropped) — ensure new columns exist
                            if (!AscendDatabaseSetup.columnExists(c, "ascend_players", mantissaCol)) {
                                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN " + mantissaCol + " DOUBLE NOT NULL DEFAULT 0");
                                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN " + exp10Col + " INT NOT NULL DEFAULT 0");
                            }
                            continue;
                        }

                        // Add new columns
                        stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN " + mantissaCol + " DOUBLE NOT NULL DEFAULT 0");
                        stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN " + exp10Col + " INT NOT NULL DEFAULT 0");

                        // Populate from old values (with bounds check: ABS must be > 0 for LOG10)
                        stmt.executeUpdate(
                            "UPDATE ascend_players SET " +
                            mantissaCol + " = CASE WHEN " + oldCol + " = 0 THEN 0 ELSE " + oldCol + " / POW(10, FLOOR(LOG10(GREATEST(ABS(" + oldCol + "), 1)))) END, " +
                            exp10Col + " = CASE WHEN " + oldCol + " = 0 THEN 0 ELSE FLOOR(LOG10(GREATEST(ABS(" + oldCol + "), 1))) END " +
                            "WHERE " + oldCol + " IS NOT NULL AND " + oldCol + " != 0"
                        );

                        // Drop old column
                        stmt.executeUpdate("ALTER TABLE ascend_players DROP COLUMN " + oldCol);
                        LOGGER.atInfo().log("Migrated " + oldCol + " to " + mantissaCol + " + " + exp10Col);
                    }

                    // Migrate ascend_player_maps: multiplier
                    if (AscendDatabaseSetup.columnExists(c, "ascend_player_maps", "multiplier")
                        && !AscendDatabaseSetup.columnExists(c, "ascend_player_maps", "multiplier_mantissa")) {

                        stmt.executeUpdate("ALTER TABLE ascend_player_maps ADD COLUMN multiplier_mantissa DOUBLE NOT NULL DEFAULT 1.0");
                        stmt.executeUpdate("ALTER TABLE ascend_player_maps ADD COLUMN multiplier_exp10 INT NOT NULL DEFAULT 0");

                        // Populate: multiplier is always >= 1.0
                        stmt.executeUpdate(
                            "UPDATE ascend_player_maps SET " +
                            "multiplier_mantissa = CASE WHEN multiplier <= 0 THEN 1.0 " +
                                "WHEN multiplier < 10 THEN multiplier " +
                                "ELSE multiplier / POW(10, FLOOR(LOG10(GREATEST(ABS(multiplier), 1)))) END, " +
                            "multiplier_exp10 = CASE WHEN multiplier < 10 THEN 0 " +
                                "ELSE FLOOR(LOG10(GREATEST(ABS(multiplier), 1))) END " +
                            "WHERE multiplier IS NOT NULL"
                        );

                        stmt.executeUpdate("ALTER TABLE ascend_player_maps DROP COLUMN multiplier");
                        LOGGER.atInfo().log("Migrated multiplier to multiplier_mantissa + multiplier_exp10");
                    } else if (!AscendDatabaseSetup.columnExists(c, "ascend_player_maps", "multiplier_mantissa")) {
                        // Fresh install, no old multiplier column — ensure new columns
                        stmt.executeUpdate("ALTER TABLE ascend_player_maps ADD COLUMN multiplier_mantissa DOUBLE NOT NULL DEFAULT 1.0");
                        stmt.executeUpdate("ALTER TABLE ascend_player_maps ADD COLUMN multiplier_exp10 INT NOT NULL DEFAULT 0");
                    }
                }
            });
            LOGGER.atInfo().log("Scientific notation migration complete");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to migrate to scientific notation (rolled back): " + e.getMessage());
        }
    }

    /**
     * Rename coins_* columns to volt_* in ascend_players.
     * Idempotent: only runs if old coins_mantissa column still exists.
     */
    private static void migrateCoinsColumnsToVolt(Connection conn) {
        // Already renamed if coins_mantissa doesn't exist
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "coins_mantissa")) {
            return;
        }

        String[][] renames = {
            {"coins_mantissa", "volt_mantissa"},
            {"coins_exp10", "volt_exp10"},
            {"total_coins_earned_mantissa", "total_volt_earned_mantissa"},
            {"total_coins_earned_exp10", "total_volt_earned_exp10"},
            {"summit_accumulated_coins_mantissa", "summit_accumulated_volt_mantissa"},
            {"summit_accumulated_coins_exp10", "summit_accumulated_volt_exp10"},
            {"elevation_accumulated_coins_mantissa", "elevation_accumulated_volt_mantissa"},
            {"elevation_accumulated_coins_exp10", "elevation_accumulated_volt_exp10"}
        };

        try {
            DatabaseManager.withTransaction(conn, c -> {
                try (Statement stmt = c.createStatement()) {
                    for (String[] rename : renames) {
                        if (AscendDatabaseSetup.columnExists(c, "ascend_players", rename[0])
                                && !AscendDatabaseSetup.columnExists(c, "ascend_players", rename[1])) {
                            stmt.executeUpdate("ALTER TABLE ascend_players RENAME COLUMN " + rename[0] + " TO " + rename[1]);
                        }
                    }
                }
            });
            LOGGER.atInfo().log("Renamed coins columns to volt in ascend_players");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to rename coins columns to volt (rolled back): " + e.getMessage());
        }
    }

    /**
     * Rename prior vexa_* columns to volt_* in ascend_players.
     * Idempotent: checks per-column old/new names before renaming.
     */
    private static void migrateVexaColumnsToVolt(Connection conn) {
        String[][] renames = {
            {"vexa_mantissa", "volt_mantissa"},
            {"vexa_exp10", "volt_exp10"},
            {"total_vexa_earned_mantissa", "total_volt_earned_mantissa"},
            {"total_vexa_earned_exp10", "total_volt_earned_exp10"},
            {"summit_accumulated_vexa_mantissa", "summit_accumulated_volt_mantissa"},
            {"summit_accumulated_vexa_exp10", "summit_accumulated_volt_exp10"},
            {"elevation_accumulated_vexa_mantissa", "elevation_accumulated_volt_mantissa"},
            {"elevation_accumulated_vexa_exp10", "elevation_accumulated_volt_exp10"}
        };

        boolean hasOldColumns = false;
        for (String[] rename : renames) {
            if (AscendDatabaseSetup.columnExists(conn, "ascend_players", rename[0])) {
                hasOldColumns = true;
                break;
            }
        }
        if (!hasOldColumns) {
            return;
        }

        try {
            DatabaseManager.withTransaction(conn, c -> {
                try (Statement stmt = c.createStatement()) {
                    for (String[] rename : renames) {
                        if (AscendDatabaseSetup.columnExists(c, "ascend_players", rename[0])
                                && !AscendDatabaseSetup.columnExists(c, "ascend_players", rename[1])) {
                            stmt.executeUpdate("ALTER TABLE ascend_players RENAME COLUMN " + rename[0] + " TO " + rename[1]);
                        }
                    }
                }
            });
            LOGGER.atInfo().log("Renamed vexa columns to volt in ascend_players");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to rename vexa columns to volt (rolled back): " + e.getMessage());
        }
    }

    private static void ensurePlayerNameColumn(Connection conn) {
        AscendDatabaseSetup.ensureColumn(conn, "ascend_players", "player_name", "VARCHAR(32) DEFAULT NULL");
    }
}
