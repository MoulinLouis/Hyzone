package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Feature table creation and migrations: summit, skills, achievements, cats, challenges,
 * ghost recordings, settings, progress columns, and tutorial.
 */
final class AscendFeatureSchema {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private AscendFeatureSchema() {
    }

    static void createAndMigrate(Connection conn, Statement stmt) throws SQLException {
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
                xp DOUBLE NOT NULL DEFAULT 0,
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

        // Easter Egg - Cat Collector
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS ascend_player_cats (
                player_uuid VARCHAR(36) NOT NULL,
                cat_token VARCHAR(16) NOT NULL,
                found_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (player_uuid, cat_token),
                FOREIGN KEY (player_uuid) REFERENCES ascend_players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB
            """);

        // Migrate Summit XP from old scale (level^2.5) to new scale (level^2.0)
        migrateSummitXpScale(conn);

        // Ensure new columns on ascend_players for extended progress tracking
        ensureProgressColumns(conn);
        ensureTutorialColumn(conn);

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
        ensureMapLeaderboardIndex(conn);

        // Challenge system tables
        ensureChallengeTables(conn);

        // Player settings columns (hud_hidden, players_hidden)
        ensurePlayerSettingsColumns(conn);

        // Summit XP column: BIGINT -> DOUBLE (uncapped progression)
        migrateSummitXpToDouble(conn);
    }

    /**
     * One-time migration: convert Summit XP from old scale (level^2.5 + sqrt volt)
     * to new scale (level^2.0 + volt^(3/7)).
     * Conversion: new_xp = round(old_xp^(6/7)) preserves the same level for each player.
     * Uses marker column 'xp_scale_v2' to ensure migration runs only once.
     */
    private static void migrateSummitXpScale(Connection conn) {
        if (AscendDatabaseSetup.columnExists(conn, "ascend_player_summit", "xp_scale_v2")) {
            return; // Already migrated
        }
        try {
            DatabaseManager.withTransaction(conn, c -> {
                try (Statement stmt = c.createStatement()) {
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
            });
            LOGGER.atInfo().log("Migrated Summit XP to new scale (exponent 2.5 -> 2.0)");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to migrate Summit XP scale (rolled back): " + e.getMessage());
        }
    }

    private static void ensureProgressColumns(Connection conn) {
        // Ascension count
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "ascension_count")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN ascension_count INT NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added ascension_count column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add ascension_count column: " + e.getMessage());
            }
        }

        // Skill tree points
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "skill_tree_points")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN skill_tree_points INT NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added skill_tree_points column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add skill_tree_points column: " + e.getMessage());
            }
        }

        // Total volt earned (lifetime) — legacy column for pre-scientific-notation DBs
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "total_coins_earned")
                && !AscendDatabaseSetup.columnExists(conn, "ascend_players", "total_coins_earned_mantissa")
                && !AscendDatabaseSetup.columnExists(conn, "ascend_players", "total_vexa_earned_mantissa")
                && !AscendDatabaseSetup.columnExists(conn, "ascend_players", "total_volt_earned_mantissa")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN total_coins_earned DOUBLE NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added total_coins_earned column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add total_coins_earned column: " + e.getMessage());
            }
        }

        // Migrate volt columns from BIGINT to DOUBLE for decimal precision
        migrateCoinsToDouble(conn);

        // Migrate volt columns from DOUBLE to DECIMAL for exact precision
        migrateCoinsToDecimal(conn);

        // Total manual runs
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "total_manual_runs")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN total_manual_runs INT NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added total_manual_runs column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add total_manual_runs column: " + e.getMessage());
            }
        }

        // Active title
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "active_title")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN active_title VARCHAR(64) DEFAULT NULL");
                LOGGER.atInfo().log("Added active_title column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add active_title column: " + e.getMessage());
            }
        }

        // Ascension timer columns (for stats tracking)
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "ascension_started_at")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN ascension_started_at BIGINT DEFAULT NULL");
                LOGGER.atInfo().log("Added ascension_started_at column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add ascension_started_at column: " + e.getMessage());
            }
        }

        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "fastest_ascension_ms")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN fastest_ascension_ms BIGINT DEFAULT NULL");
                LOGGER.atInfo().log("Added fastest_ascension_ms column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add fastest_ascension_ms column: " + e.getMessage());
            }
        }

        // Summit accumulated volt (volt earned since last Summit/Elevation)
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "summit_accumulated_coins")
                && !AscendDatabaseSetup.columnExists(conn, "ascend_players", "summit_accumulated_coins_mantissa")
                && !AscendDatabaseSetup.columnExists(conn, "ascend_players", "summit_accumulated_vexa_mantissa")
                && !AscendDatabaseSetup.columnExists(conn, "ascend_players", "summit_accumulated_volt_mantissa")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN summit_accumulated_coins DECIMAL(65,2) NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added summit_accumulated_coins column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add summit_accumulated_coins column: " + e.getMessage());
            }
        }

        // Elevation accumulated volt (volt earned since last Elevation/Summit/Ascension)
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "elevation_accumulated_coins")
                && !AscendDatabaseSetup.columnExists(conn, "ascend_players", "elevation_accumulated_coins_mantissa")
                && !AscendDatabaseSetup.columnExists(conn, "ascend_players", "elevation_accumulated_vexa_mantissa")
                && !AscendDatabaseSetup.columnExists(conn, "ascend_players", "elevation_accumulated_volt_mantissa")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN elevation_accumulated_coins DECIMAL(65,2) NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added elevation_accumulated_coins column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add elevation_accumulated_coins column: " + e.getMessage());
            }
        }

        // Auto-upgrade toggle
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "auto_upgrade_enabled")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN auto_upgrade_enabled BOOLEAN NOT NULL DEFAULT FALSE");
                LOGGER.atInfo().log("Added auto_upgrade_enabled column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add auto_upgrade_enabled: " + e.getMessage());
            }
        }

        // Auto-evolution toggle
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "auto_evolution_enabled")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN auto_evolution_enabled BOOLEAN NOT NULL DEFAULT FALSE");
                LOGGER.atInfo().log("Added auto_evolution_enabled column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add auto_evolution_enabled: " + e.getMessage());
            }
        }

        // Hide other runners toggle
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "hide_other_runners")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN hide_other_runners BOOLEAN NOT NULL DEFAULT FALSE");
                LOGGER.atInfo().log("Added hide_other_runners column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add hide_other_runners: " + e.getMessage());
            }
        }
    }

    private static void migrateCoinsToDouble(Connection conn) {
        try {
            DatabaseManager.withTransaction(conn, c -> {
                // Check if coins column is BIGINT and migrate to DOUBLE
                try (Statement stmt = c.createStatement();
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
                try (Statement stmt = c.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM ascend_players LIKE 'total_coins_earned'")) {
                    if (rs.next()) {
                        String type = rs.getString("Type").toUpperCase();
                        if (type.contains("BIGINT")) {
                            stmt.executeUpdate("ALTER TABLE ascend_players MODIFY COLUMN total_coins_earned DOUBLE NOT NULL DEFAULT 0");
                            LOGGER.atInfo().log("Migrated total_coins_earned column from BIGINT to DOUBLE");
                        }
                    }
                }
            });
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to migrate coins columns to DOUBLE (rolled back): " + e.getMessage());
        }
    }

    private static void migrateCoinsToDecimal(Connection conn) {
        try {
            DatabaseManager.withTransaction(conn, c -> {
                // Migrate coins column from DOUBLE to DECIMAL(65,2)
                try (Statement stmt = c.createStatement();
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
                try (Statement stmt = c.createStatement();
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
                try (Statement stmt = c.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM ascend_player_maps LIKE 'multiplier'")) {
                    if (rs.next()) {
                        String type = rs.getString("Type").toUpperCase();
                        if (type.contains("DOUBLE")) {
                            stmt.executeUpdate("ALTER TABLE ascend_player_maps MODIFY COLUMN multiplier DECIMAL(65,20) NOT NULL DEFAULT 1.0");
                            LOGGER.atInfo().log("Migrated multiplier column from DOUBLE to DECIMAL(65,20)");
                        }
                    }
                }
            });
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to migrate columns to DECIMAL (rolled back): " + e.getMessage());
        }
    }

    private static void ensureTutorialColumn(Connection conn) {
        if (AscendDatabaseSetup.columnExists(conn, "ascend_players", "seen_tutorials")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN seen_tutorials INT NOT NULL DEFAULT 0");
            LOGGER.atInfo().log("Added seen_tutorials column to ascend_players");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to add seen_tutorials column: " + e.getMessage());
        }
    }

    private static void ensureGhostRecordingTable(Connection conn) {
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
        if (AscendDatabaseSetup.columnExists(conn, "ascend_player_maps", "best_time_ms")) {
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
        // Add spawn columns if they don't exist (for existing databases)
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_settings", "spawn_x")) {
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

    private static void ensureVoidYThresholdColumn(Connection conn) {
        if (AscendDatabaseSetup.columnExists(conn, "ascend_settings", "void_y_threshold")) {
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
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_map_best_time ON ascend_player_maps(map_id, best_time_ms)");
            LOGGER.atInfo().log("Ensured idx_map_best_time index on ascend_player_maps");
        } catch (SQLException e) {
            // Index may already exist under a different name or CREATE INDEX IF NOT EXISTS not supported
            LOGGER.atWarning().log("Could not create idx_map_best_time index (may already exist): " + e.getMessage());
        }
    }

    private static void ensureChallengeTables(Connection conn) {
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

    private static void ensurePlayerSettingsColumns(Connection conn) {
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "hud_hidden")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN hud_hidden BOOLEAN NOT NULL DEFAULT FALSE");
                LOGGER.atInfo().log("Added hud_hidden column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add hud_hidden column: " + e.getMessage());
            }
        }
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "players_hidden")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN players_hidden BOOLEAN NOT NULL DEFAULT FALSE");
                LOGGER.atInfo().log("Added players_hidden column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add players_hidden column: " + e.getMessage());
            }
        }
    }

    /**
     * Migrate summit XP column from BIGINT to DOUBLE for uncapped progression.
     * Idempotent: checks current column type before altering.
     */
    private static void migrateSummitXpToDouble(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM ascend_player_summit LIKE 'xp'")) {
            if (rs.next()) {
                String type = rs.getString("Type").toUpperCase();
                if (type.contains("BIGINT")) {
                    stmt.executeUpdate("ALTER TABLE ascend_player_summit MODIFY COLUMN xp DOUBLE NOT NULL DEFAULT 0");
                    LOGGER.atInfo().log("Migrated summit XP column from BIGINT to DOUBLE");
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to migrate summit XP to DOUBLE: " + e.getMessage());
        }
    }
}
