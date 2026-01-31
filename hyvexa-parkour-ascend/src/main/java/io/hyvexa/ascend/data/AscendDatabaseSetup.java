package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public final class AscendDatabaseSetup {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private AscendDatabaseSetup() {
    }

    public static void ensureTables() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, skipping Ascend table setup");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    coins BIGINT NOT NULL DEFAULT 0,
                    rebirth_multiplier INT NOT NULL DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB
                """);
            ensureRebirthColumn(conn);

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
                    robot_count INT NOT NULL DEFAULT 0,
                    robot_speed_level INT NOT NULL DEFAULT 0,
                    robot_gains_level INT NOT NULL DEFAULT 0,
                    multiplier_value INT NOT NULL DEFAULT 1,
                    robot_multiplier_bonus DOUBLE NOT NULL DEFAULT 0,
                    last_collection_at TIMESTAMP NULL,
                    PRIMARY KEY (player_uuid, map_id),
                    FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE,
                    FOREIGN KEY (map_id) REFERENCES ascend_maps(id) ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);

            ensureMultiplierColumn(conn);
            ensureRobotCountColumn(conn);
            ensureRobotMultiplierBonusColumn(conn);
            ensureRobotTimeReductionColumn(conn);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_upgrade_costs (
                    upgrade_type VARCHAR(32) NOT NULL,
                    level INT NOT NULL,
                    cost BIGINT NOT NULL,
                    PRIMARY KEY (upgrade_type, level)
                ) ENGINE=InnoDB
                """);

            LOGGER.atInfo().log("Ascend database tables ensured");

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to create Ascend tables: " + e.getMessage());
        }
    }

    private static void ensureRebirthColumn(Connection conn) {
        if (conn == null) {
            return;
        }
        if (columnExists(conn, "ascend_players", "rebirth_multiplier")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN rebirth_multiplier INT NOT NULL DEFAULT 1");
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to add rebirth_multiplier column: " + e.getMessage());
        }
    }

    private static void ensureMultiplierColumn(Connection conn) {
        if (conn == null) {
            return;
        }
        if (columnExists(conn, "ascend_player_maps", "multiplier_value")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_player_maps ADD COLUMN multiplier_value INT NOT NULL DEFAULT 1");
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to add multiplier_value column: " + e.getMessage());
        }
    }

    private static void ensureRobotCountColumn(Connection conn) {
        if (conn == null) {
            return;
        }
        if (columnExists(conn, "ascend_player_maps", "robot_count")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_player_maps ADD COLUMN robot_count INT NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to add robot_count column: " + e.getMessage());
        }
    }

    private static void ensureRobotMultiplierBonusColumn(Connection conn) {
        if (conn == null) {
            return;
        }
        if (columnExists(conn, "ascend_player_maps", "robot_multiplier_bonus")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_player_maps ADD COLUMN robot_multiplier_bonus DOUBLE NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to add robot_multiplier_bonus column: " + e.getMessage());
        }
    }

    private static void ensureRobotTimeReductionColumn(Connection conn) {
        if (conn == null) {
            return;
        }
        if (columnExists(conn, "ascend_maps", "robot_time_reduction_ms")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_maps ADD COLUMN robot_time_reduction_ms BIGINT NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to add robot_time_reduction_ms column: " + e.getMessage());
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) {
        String sql = "SHOW COLUMNS FROM " + table + " LIKE ?";
        try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, column);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to check column " + column + ": " + e.getMessage());
            return false;
        }
    }
}
