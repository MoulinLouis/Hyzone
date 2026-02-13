package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public final class AscendDatabaseSetup {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private AscendDatabaseSetup() {
    }

    public static void ensureTables() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, skipping Ascend table setup");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection for Ascend table setup");
                return;
            }
            try (Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    vexa_mantissa DOUBLE NOT NULL DEFAULT 0,
                    vexa_exp10 INT NOT NULL DEFAULT 0,
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

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_upgrade_costs (
                    upgrade_type VARCHAR(32) NOT NULL,
                    level INT NOT NULL,
                    cost BIGINT NOT NULL,
                    PRIMARY KEY (upgrade_type, level)
                ) ENGINE=InnoDB
                """);

            // Summit System table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_player_summit (
                    player_uuid VARCHAR(36) NOT NULL,
                    category VARCHAR(32) NOT NULL,
                    xp BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, category),
                    FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);

            // Migrate old 'level' column to 'xp' if exists
            try {
                var rs = stmt.executeQuery("SHOW COLUMNS FROM ascend_player_summit LIKE 'level'");
                if (rs.next()) {
                    // Old schema exists, migrate level to xp
                    stmt.executeUpdate("ALTER TABLE ascend_player_summit ADD COLUMN IF NOT EXISTS xp BIGINT NOT NULL DEFAULT 0");
                    // Note: Migration of actual values handled in AscendPlayerStore load
                    stmt.executeUpdate("ALTER TABLE ascend_player_summit DROP COLUMN IF EXISTS level");
                }
                rs.close();
            } catch (Exception e) {
                // Column doesn't exist or already migrated, ignore
            }

            // Ascension System - skill tree unlocks
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_player_skills (
                    player_uuid VARCHAR(36) NOT NULL,
                    skill_node VARCHAR(64) NOT NULL,
                    unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_uuid, skill_node),
                    FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);

            // Achievement System
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_player_achievements (
                    player_uuid VARCHAR(36) NOT NULL,
                    achievement VARCHAR(64) NOT NULL,
                    unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_uuid, achievement),
                    FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);

            // Migrate Summit XP from old scale (level^2.5) to new scale (level^2.0)
            migrateSummitXpScale(conn);

            // Ensure new columns on ascend_players for extended progress tracking
            ensureProgressColumns(conn);
            ensureTutorialColumn(conn);

            // Migrate vexa/multiplier columns from DECIMAL to scientific notation (mantissa + exponent)
            migrateToScientificNotation(conn);

            // Rename coins_* columns to vexa_* (cosmetic DB rename)
            migrateCoinsColumnsToVexa(conn);

            // Ensure ghost recording table and best_time_ms column
            ensureGhostRecordingTable(conn);
            ensureBestTimeColumn(conn);

            // Global settings table (spawn and NPC positions)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_settings (
                    id INT NOT NULL PRIMARY KEY,
                    spawn_x DOUBLE NOT NULL DEFAULT 0,
                    spawn_y DOUBLE NOT NULL DEFAULT 0,
                    spawn_z DOUBLE NOT NULL DEFAULT 0,
                    spawn_rot_x FLOAT NOT NULL DEFAULT 0,
                    spawn_rot_y FLOAT NOT NULL DEFAULT 0,
                    spawn_rot_z FLOAT NOT NULL DEFAULT 0,
                    npc_x DOUBLE NOT NULL DEFAULT 0,
                    npc_y DOUBLE NOT NULL DEFAULT 0,
                    npc_z DOUBLE NOT NULL DEFAULT 0,
                    npc_rot_x FLOAT NOT NULL DEFAULT 0,
                    npc_rot_y FLOAT NOT NULL DEFAULT 0,
                    npc_rot_z FLOAT NOT NULL DEFAULT 0
                ) ENGINE=InnoDB
                """);
            ensureSpawnColumns(conn);
            ensureVoidYThresholdColumn(conn);
            ensurePlayerNameColumn(conn);
            ensureMapLeaderboardIndex(conn);

            // Challenge system tables
            ensureChallengeTables(conn);
            ensureBreakAscensionColumn(conn);

            LOGGER.atInfo().log("Ascend database tables ensured");
            } // close try (Statement stmt)

        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to create Ascend tables: " + e.getMessage());
        }
    }

    private static void migrateRebirthToElevation(Connection conn) {
        if (conn == null) {
            return;
        }
        if (!columnExists(conn, "ascend_players", "rebirth_multiplier")) {
            return;
        }
        if (columnExists(conn, "ascend_players", "elevation_multiplier")) {
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
        if (conn == null) {
            return;
        }
        if (columnExists(conn, "ascend_players", "elevation_multiplier")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN elevation_multiplier INT NOT NULL DEFAULT 1");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to add elevation_multiplier column: " + e.getMessage());
        }
    }

    private static void migrateToNewMultiplierSchema(Connection conn) {
        if (conn == null) {
            return;
        }
        if (columnExists(conn, "ascend_player_maps", "multiplier")) {
            return;
        }
        boolean wasAutoCommit = true;
        try {
            wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_player_maps ADD COLUMN multiplier DOUBLE NOT NULL DEFAULT 1.0");
                if (columnExists(conn, "ascend_player_maps", "multiplier_value")
                    && columnExists(conn, "ascend_player_maps", "robot_multiplier_bonus")) {
                    stmt.executeUpdate("""
                        UPDATE ascend_player_maps
                        SET multiplier = GREATEST(1.0, multiplier_value + robot_multiplier_bonus)
                        """);
                    LOGGER.atInfo().log("Migrated multiplier data to new schema");
                }
                if (columnExists(conn, "ascend_player_maps", "robot_count")) {
                    stmt.executeUpdate("""
                        UPDATE ascend_player_maps
                        SET has_robot = (robot_count > 0)
                        WHERE robot_count > 0 AND has_robot = FALSE
                        """);
                }
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException re) { /* ignore */ }
            LOGGER.atSevere().log("Failed to migrate multiplier schema (rolled back): " + e.getMessage());
        } finally {
            try { conn.setAutoCommit(wasAutoCommit); } catch (SQLException e) { /* ignore */ }
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
            LOGGER.atSevere().log("Failed to add robot_time_reduction_ms column: " + e.getMessage());
        }
    }

    private static void ensureRobotStarsColumn(Connection conn) {
        if (conn == null) {
            return;
        }
        if (columnExists(conn, "ascend_player_maps", "robot_stars")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_player_maps ADD COLUMN robot_stars INT NOT NULL DEFAULT 0");
            LOGGER.atInfo().log("Added robot_stars column to ascend_player_maps");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to add robot_stars column: " + e.getMessage());
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
            LOGGER.atSevere().log("Failed to check column " + column + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * One-time migration: convert Summit XP from old scale (level^2.5 + sqrt vexa)
     * to new scale (level^2.0 + vexa^(3/7)).
     * Conversion: new_xp = round(old_xp^(6/7)) preserves the same level for each player.
     * Uses marker column 'xp_scale_v2' to ensure migration runs only once.
     */
    private static void migrateSummitXpScale(Connection conn) {
        if (conn == null) {
            return;
        }
        if (columnExists(conn, "ascend_player_summit", "xp_scale_v2")) {
            return; // Already migrated
        }
        boolean wasAutoCommit = true;
        try {
            wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                // Validate row count before migration
                long rowCount = 0;
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM ascend_player_summit WHERE xp > 0")) {
                    if (rs.next()) rowCount = rs.getLong(1);
                }

                // Convert existing XP: new_xp = round(old_xp^(6/7)), with bounds check to avoid POW overflow
                stmt.executeUpdate("UPDATE ascend_player_summit SET xp = ROUND(POW(LEAST(GREATEST(xp, 1), 9223372036854775807), 0.857143)) WHERE xp > 0");

                // Validate row count after migration
                long postCount = 0;
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM ascend_player_summit WHERE xp > 0")) {
                    if (rs.next()) postCount = rs.getLong(1);
                }
                if (postCount < rowCount) {
                    throw new SQLException("Row count decreased during migration: " + rowCount + " -> " + postCount);
                }

                // Add marker column to prevent re-migration
                stmt.executeUpdate("ALTER TABLE ascend_player_summit ADD COLUMN xp_scale_v2 TINYINT NOT NULL DEFAULT 1");
            }
            conn.commit();
            LOGGER.atInfo().log("Migrated Summit XP to new scale (exponent 2.5 -> 2.0)");
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException re) { /* ignore */ }
            LOGGER.atSevere().log("Failed to migrate Summit XP scale (rolled back): " + e.getMessage());
        } finally {
            try { conn.setAutoCommit(wasAutoCommit); } catch (SQLException e) { /* ignore */ }
        }
    }

    private static void ensureProgressColumns(Connection conn) {
        if (conn == null) {
            return;
        }

        // Ascension count
        if (!columnExists(conn, "ascend_players", "ascension_count")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN ascension_count INT NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added ascension_count column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add ascension_count column: " + e.getMessage());
            }
        }

        // Skill tree points
        if (!columnExists(conn, "ascend_players", "skill_tree_points")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN skill_tree_points INT NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added skill_tree_points column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add skill_tree_points column: " + e.getMessage());
            }
        }

        // Total vexa earned (lifetime) — legacy column for pre-scientific-notation DBs
        if (!columnExists(conn, "ascend_players", "total_coins_earned")
                && !columnExists(conn, "ascend_players", "total_coins_earned_mantissa")
                && !columnExists(conn, "ascend_players", "total_vexa_earned_mantissa")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN total_coins_earned DOUBLE NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added total_coins_earned column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add total_coins_earned column: " + e.getMessage());
            }
        }

        // Migrate vexa columns from BIGINT to DOUBLE for decimal precision
        migrateCoinsToDouble(conn);

        // Migrate vexa columns from DOUBLE to DECIMAL for exact precision
        migrateCoinsToDecimal(conn);

        // Total manual runs
        if (!columnExists(conn, "ascend_players", "total_manual_runs")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN total_manual_runs INT NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added total_manual_runs column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add total_manual_runs column: " + e.getMessage());
            }
        }

        // Active title
        if (!columnExists(conn, "ascend_players", "active_title")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN active_title VARCHAR(64) DEFAULT NULL");
                LOGGER.atInfo().log("Added active_title column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add active_title column: " + e.getMessage());
            }
        }

        // Ascension timer columns (for stats tracking)
        if (!columnExists(conn, "ascend_players", "ascension_started_at")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN ascension_started_at BIGINT DEFAULT NULL");
                LOGGER.atInfo().log("Added ascension_started_at column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add ascension_started_at column: " + e.getMessage());
            }
        }

        if (!columnExists(conn, "ascend_players", "fastest_ascension_ms")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN fastest_ascension_ms BIGINT DEFAULT NULL");
                LOGGER.atInfo().log("Added fastest_ascension_ms column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add fastest_ascension_ms column: " + e.getMessage());
            }
        }

        // Last active timestamp for passive earnings
        if (!columnExists(conn, "ascend_players", "last_active_timestamp")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN last_active_timestamp BIGINT DEFAULT NULL");
                LOGGER.atInfo().log("Added last_active_timestamp column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add last_active_timestamp: " + e.getMessage());
            }
        }

        // Summit accumulated vexa (vexa earned since last Summit/Elevation)
        if (!columnExists(conn, "ascend_players", "summit_accumulated_coins")
                && !columnExists(conn, "ascend_players", "summit_accumulated_coins_mantissa")
                && !columnExists(conn, "ascend_players", "summit_accumulated_vexa_mantissa")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN summit_accumulated_coins DECIMAL(65,2) NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added summit_accumulated_coins column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add summit_accumulated_coins column: " + e.getMessage());
            }
        }

        // Has unclaimed passive flag
        if (!columnExists(conn, "ascend_players", "has_unclaimed_passive")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN has_unclaimed_passive BOOLEAN NOT NULL DEFAULT FALSE");
                LOGGER.atInfo().log("Added has_unclaimed_passive column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add has_unclaimed_passive: " + e.getMessage());
            }
        }

        // Elevation accumulated vexa (vexa earned since last Elevation/Summit/Ascension)
        if (!columnExists(conn, "ascend_players", "elevation_accumulated_coins")
                && !columnExists(conn, "ascend_players", "elevation_accumulated_coins_mantissa")
                && !columnExists(conn, "ascend_players", "elevation_accumulated_vexa_mantissa")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN elevation_accumulated_coins DECIMAL(65,2) NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added elevation_accumulated_coins column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add elevation_accumulated_coins column: " + e.getMessage());
            }
        }

        // Auto-upgrade toggle
        if (!columnExists(conn, "ascend_players", "auto_upgrade_enabled")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN auto_upgrade_enabled BOOLEAN NOT NULL DEFAULT FALSE");
                LOGGER.atInfo().log("Added auto_upgrade_enabled column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add auto_upgrade_enabled: " + e.getMessage());
            }
        }

        // Auto-evolution toggle
        if (!columnExists(conn, "ascend_players", "auto_evolution_enabled")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN auto_evolution_enabled BOOLEAN NOT NULL DEFAULT FALSE");
                LOGGER.atInfo().log("Added auto_evolution_enabled column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add auto_evolution_enabled: " + e.getMessage());
            }
        }

        // Hide other runners toggle
        if (!columnExists(conn, "ascend_players", "hide_other_runners")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN hide_other_runners BOOLEAN NOT NULL DEFAULT FALSE");
                LOGGER.atInfo().log("Added hide_other_runners column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add hide_other_runners: " + e.getMessage());
            }
        }
    }

    private static void ensureGhostRecordingTable(Connection conn) {
        if (conn == null) {
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_ghost_recordings (
                  player_uuid VARCHAR(36) NOT NULL,
                  map_id VARCHAR(32) NOT NULL,
                  recording_blob MEDIUMBLOB NOT NULL,
                  completion_time_ms BIGINT NOT NULL,
                  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (player_uuid, map_id),
                  FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE,
                  FOREIGN KEY (map_id) REFERENCES ascend_maps(id) ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);
            LOGGER.atInfo().log("Ensured ascend_ghost_recordings table");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to create ghost recordings table: " + e.getMessage());
        }
    }

    private static void ensureBestTimeColumn(Connection conn) {
        if (conn == null) {
            return;
        }

        if (columnExists(conn, "ascend_player_maps", "best_time_ms")) {
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_player_maps ADD COLUMN best_time_ms BIGINT DEFAULT NULL");
            LOGGER.atInfo().log("Added best_time_ms column to ascend_player_maps");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to add best_time_ms column: " + e.getMessage());
        }
    }

    private static void ensureSpawnColumns(Connection conn) {
        if (conn == null) {
            return;
        }

        // Add spawn columns if they don't exist (for existing databases)
        if (!columnExists(conn, "ascend_settings", "spawn_x")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_settings ADD COLUMN spawn_x DOUBLE NOT NULL DEFAULT 0");
                stmt.executeUpdate("ALTER TABLE ascend_settings ADD COLUMN spawn_y DOUBLE NOT NULL DEFAULT 0");
                stmt.executeUpdate("ALTER TABLE ascend_settings ADD COLUMN spawn_z DOUBLE NOT NULL DEFAULT 0");
                stmt.executeUpdate("ALTER TABLE ascend_settings ADD COLUMN spawn_rot_x FLOAT NOT NULL DEFAULT 0");
                stmt.executeUpdate("ALTER TABLE ascend_settings ADD COLUMN spawn_rot_y FLOAT NOT NULL DEFAULT 0");
                stmt.executeUpdate("ALTER TABLE ascend_settings ADD COLUMN spawn_rot_z FLOAT NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added spawn columns to ascend_settings");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add spawn columns: " + e.getMessage());
            }
        }
    }

    private static void ensurePlayerNameColumn(Connection conn) {
        if (conn == null) {
            return;
        }
        if (columnExists(conn, "ascend_players", "player_name")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN player_name VARCHAR(32) DEFAULT NULL");
            LOGGER.atInfo().log("Added player_name column to ascend_players");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to add player_name column: " + e.getMessage());
        }
    }

    private static void ensureVoidYThresholdColumn(Connection conn) {
        if (conn == null) {
            return;
        }
        if (columnExists(conn, "ascend_settings", "void_y_threshold")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_settings ADD COLUMN void_y_threshold DOUBLE DEFAULT NULL");
            LOGGER.atInfo().log("Added void_y_threshold column to ascend_settings");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to add void_y_threshold column: " + e.getMessage());
        }
    }

    private static void ensureMapLeaderboardIndex(Connection conn) {
        if (conn == null) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_map_best_time ON ascend_player_maps(map_id, best_time_ms)");
            LOGGER.atInfo().log("Ensured idx_map_best_time index on ascend_player_maps");
        } catch (SQLException e) {
            // Index may already exist under a different name or CREATE INDEX IF NOT EXISTS not supported
            LOGGER.atWarning().log("Could not create idx_map_best_time index (may already exist): " + e.getMessage());
        }
    }

    private static void migrateCoinsToDouble(Connection conn) {
        if (conn == null) {
            return;
        }
        boolean wasAutoCommit = true;
        try {
            wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Check if coins column is BIGINT and migrate to DOUBLE
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM ascend_players LIKE 'coins'")) {
                if (rs.next()) {
                    String type = rs.getString("Type").toUpperCase();
                    if (type.contains("BIGINT")) {
                        stmt.executeUpdate("ALTER TABLE ascend_players MODIFY COLUMN coins DOUBLE NOT NULL DEFAULT 0");
                        LOGGER.atInfo().log("Migrated coins column from BIGINT to DOUBLE");
                    }
                }
            }

            // Check if total_coins_earned column is BIGINT and migrate to DOUBLE
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM ascend_players LIKE 'total_coins_earned'")) {
                if (rs.next()) {
                    String type = rs.getString("Type").toUpperCase();
                    if (type.contains("BIGINT")) {
                        stmt.executeUpdate("ALTER TABLE ascend_players MODIFY COLUMN total_coins_earned DOUBLE NOT NULL DEFAULT 0");
                        LOGGER.atInfo().log("Migrated total_coins_earned column from BIGINT to DOUBLE");
                    }
                }
            }

            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException re) { /* ignore */ }
            LOGGER.atSevere().log("Failed to migrate coins columns to DOUBLE (rolled back): " + e.getMessage());
        } finally {
            try { conn.setAutoCommit(wasAutoCommit); } catch (SQLException e) { /* ignore */ }
        }
    }

    private static void ensureTutorialColumn(Connection conn) {
        if (conn == null) {
            return;
        }
        if (columnExists(conn, "ascend_players", "seen_tutorials")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN seen_tutorials INT NOT NULL DEFAULT 0");
            LOGGER.atInfo().log("Added seen_tutorials column to ascend_players");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to add seen_tutorials column: " + e.getMessage());
        }
    }

    private static void migrateCoinsToDecimal(Connection conn) {
        if (conn == null) {
            return;
        }
        boolean wasAutoCommit = true;
        try {
            wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Migrate coins column from DOUBLE to DECIMAL(65,2)
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM ascend_players LIKE 'coins'")) {
                if (rs.next()) {
                    String type = rs.getString("Type").toUpperCase();
                    if (type.contains("DOUBLE")) {
                        stmt.executeUpdate("ALTER TABLE ascend_players MODIFY COLUMN coins DECIMAL(65,2) NOT NULL DEFAULT 0");
                        LOGGER.atInfo().log("Migrated coins column from DOUBLE to DECIMAL(65,2)");
                    }
                }
            }

            // Migrate total_coins_earned column from DOUBLE to DECIMAL(65,2)
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM ascend_players LIKE 'total_coins_earned'")) {
                if (rs.next()) {
                    String type = rs.getString("Type").toUpperCase();
                    if (type.contains("DOUBLE")) {
                        stmt.executeUpdate("ALTER TABLE ascend_players MODIFY COLUMN total_coins_earned DECIMAL(65,2) NOT NULL DEFAULT 0");
                        LOGGER.atInfo().log("Migrated total_coins_earned column from DOUBLE to DECIMAL(65,2)");
                    }
                }
            }

            // Migrate multiplier column in ascend_player_maps from DOUBLE to DECIMAL(65,20)
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM ascend_player_maps LIKE 'multiplier'")) {
                if (rs.next()) {
                    String type = rs.getString("Type").toUpperCase();
                    if (type.contains("DOUBLE")) {
                        stmt.executeUpdate("ALTER TABLE ascend_player_maps MODIFY COLUMN multiplier DECIMAL(65,20) NOT NULL DEFAULT 1.0");
                        LOGGER.atInfo().log("Migrated multiplier column from DOUBLE to DECIMAL(65,20)");
                    }
                }
            }

            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException re) { /* ignore */ }
            LOGGER.atSevere().log("Failed to migrate columns to DECIMAL (rolled back): " + e.getMessage());
        } finally {
            try { conn.setAutoCommit(wasAutoCommit); } catch (SQLException e) { /* ignore */ }
        }
    }

    /**
     * Migrate vexa/multiplier columns from DECIMAL/DOUBLE to scientific notation (mantissa + exponent).
     * For each column: add _mantissa DOUBLE + _exp10 INT, populate from old values, drop old column.
     * Idempotent: checks for vexa_mantissa to determine if already migrated.
     */
    private static void migrateToScientificNotation(Connection conn) {
        if (conn == null) {
            return;
        }

        // Already migrated if vexa_mantissa (or legacy coins_mantissa) exists
        if (columnExists(conn, "ascend_players", "vexa_mantissa")
                || columnExists(conn, "ascend_players", "coins_mantissa")) {
            return;
        }

        LOGGER.atInfo().log("Starting migration to scientific notation (BigNumber)...");

        // Migrate ascend_players: coins, total_coins_earned, summit_accumulated_coins, elevation_accumulated_coins
        // Source columns use old names; target columns use new vexa_* names
        String[][] playerColumns = {
            {"coins", "vexa_mantissa", "vexa_exp10", "0"},
            {"total_coins_earned", "total_vexa_earned_mantissa", "total_vexa_earned_exp10", "0"},
            {"summit_accumulated_coins", "summit_accumulated_vexa_mantissa", "summit_accumulated_vexa_exp10", "0"},
            {"elevation_accumulated_coins", "elevation_accumulated_vexa_mantissa", "elevation_accumulated_vexa_exp10", "0"}
        };

        boolean wasAutoCommit = true;
        try {
            wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                // Add new columns for ascend_players
                for (String[] col : playerColumns) {
                    String oldCol = col[0];
                    String mantissaCol = col[1];
                    String exp10Col = col[2];

                    if (!columnExists(conn, "ascend_players", oldCol)) {
                        // Column doesn't exist yet (fresh install or already dropped) — ensure new columns exist
                        if (!columnExists(conn, "ascend_players", mantissaCol)) {
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
                if (columnExists(conn, "ascend_player_maps", "multiplier")
                    && !columnExists(conn, "ascend_player_maps", "multiplier_mantissa")) {

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
                } else if (!columnExists(conn, "ascend_player_maps", "multiplier_mantissa")) {
                    // Fresh install, no old multiplier column — ensure new columns
                    stmt.executeUpdate("ALTER TABLE ascend_player_maps ADD COLUMN multiplier_mantissa DOUBLE NOT NULL DEFAULT 1.0");
                    stmt.executeUpdate("ALTER TABLE ascend_player_maps ADD COLUMN multiplier_exp10 INT NOT NULL DEFAULT 0");
                }
            }
            conn.commit();
            LOGGER.atInfo().log("Scientific notation migration complete");
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException re) { /* ignore */ }
            LOGGER.atSevere().log("Failed to migrate to scientific notation (rolled back): " + e.getMessage());
        } finally {
            try { conn.setAutoCommit(wasAutoCommit); } catch (SQLException e) { /* ignore */ }
        }
    }

    private static void ensureChallengeTables(Connection conn) {
        if (conn == null) {
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            // Active challenge + snapshot for crash recovery (deleted on complete/quit)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_challenges (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    challenge_type_id INT NOT NULL,
                    started_at_ms BIGINT NOT NULL,
                    snapshot_json MEDIUMTEXT NOT NULL,
                    FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);

            // Permanent records: best time + completion count per challenge type
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ascend_challenge_records (
                    player_uuid VARCHAR(36) NOT NULL,
                    challenge_type_id INT NOT NULL,
                    best_time_ms BIGINT DEFAULT NULL,
                    completions INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, challenge_type_id),
                    FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);

            LOGGER.atInfo().log("Ensured challenge system tables");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to create challenge tables: " + e.getMessage());
        }
    }

    private static void ensureBreakAscensionColumn(Connection conn) {
        if (conn == null) {
            return;
        }
        if (columnExists(conn, "ascend_players", "break_ascension_enabled")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN break_ascension_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            LOGGER.atInfo().log("Added break_ascension_enabled column to ascend_players");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to add break_ascension_enabled column: " + e.getMessage());
        }
    }

    /**
     * Rename coins_* columns to vexa_* in ascend_players.
     * Idempotent: only runs if old coins_mantissa column still exists.
     */
    private static void migrateCoinsColumnsToVexa(Connection conn) {
        if (conn == null) {
            return;
        }

        // Already renamed if coins_mantissa doesn't exist
        if (!columnExists(conn, "ascend_players", "coins_mantissa")) {
            return;
        }

        String[][] renames = {
            {"coins_mantissa", "vexa_mantissa"},
            {"coins_exp10", "vexa_exp10"},
            {"total_coins_earned_mantissa", "total_vexa_earned_mantissa"},
            {"total_coins_earned_exp10", "total_vexa_earned_exp10"},
            {"summit_accumulated_coins_mantissa", "summit_accumulated_vexa_mantissa"},
            {"summit_accumulated_coins_exp10", "summit_accumulated_vexa_exp10"},
            {"elevation_accumulated_coins_mantissa", "elevation_accumulated_vexa_mantissa"},
            {"elevation_accumulated_coins_exp10", "elevation_accumulated_vexa_exp10"}
        };

        boolean wasAutoCommit = true;
        try {
            wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                for (String[] rename : renames) {
                    if (columnExists(conn, "ascend_players", rename[0])) {
                        stmt.executeUpdate("ALTER TABLE ascend_players RENAME COLUMN " + rename[0] + " TO " + rename[1]);
                    }
                }
            }
            conn.commit();
            LOGGER.atInfo().log("Renamed coins columns to vexa in ascend_players");
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException re) { /* ignore */ }
            LOGGER.atSevere().log("Failed to rename coins columns to vexa (rolled back): " + e.getMessage());
        } finally {
            try { conn.setAutoCommit(wasAutoCommit); } catch (SQLException e) { /* ignore */ }
        }
    }
}
