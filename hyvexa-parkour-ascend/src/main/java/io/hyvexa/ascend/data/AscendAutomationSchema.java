package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Automation column migrations: auto-elevation, auto-summit, auto-ascend,
 * transcendence, and break-ascension.
 */
final class AscendAutomationSchema {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private AscendAutomationSchema() {
    }

    static void ensureColumns(Connection conn) {
        ensureAutoElevationColumns(conn);
        ensureAutoSummitColumns(conn);
        ensureTranscendenceColumns(conn);
        ensureAutoAscendColumn(conn);
        ensureBreakAscensionColumn(conn);
    }

    private static void ensureAutoElevationColumns(Connection conn) {
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "auto_elevation_enabled")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN auto_elevation_enabled BOOLEAN NOT NULL DEFAULT FALSE");
                LOGGER.atInfo().log("Added auto_elevation_enabled column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add auto_elevation_enabled: " + e.getMessage());
            }
        }

        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "auto_elevation_timer_seconds")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN auto_elevation_timer_seconds INT NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added auto_elevation_timer_seconds column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add auto_elevation_timer_seconds: " + e.getMessage());
            }
        }

        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "auto_elevation_targets")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN auto_elevation_targets TEXT DEFAULT '[]'");
                LOGGER.atInfo().log("Added auto_elevation_targets column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add auto_elevation_targets: " + e.getMessage());
            }
        }

        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "auto_elevation_target_index")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN auto_elevation_target_index INT NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added auto_elevation_target_index column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add auto_elevation_target_index: " + e.getMessage());
            }
        }
    }

    private static void ensureAutoSummitColumns(Connection conn) {
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "auto_summit_enabled")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN auto_summit_enabled BOOLEAN NOT NULL DEFAULT FALSE");
                LOGGER.atInfo().log("Added auto_summit_enabled column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add auto_summit_enabled: " + e.getMessage());
            }
        }

        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "auto_summit_timer_seconds")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN auto_summit_timer_seconds INT NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added auto_summit_timer_seconds column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add auto_summit_timer_seconds: " + e.getMessage());
            }
        }

        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "auto_summit_config")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN auto_summit_config TEXT DEFAULT '[{\"enabled\":false,\"increment\":0},{\"enabled\":false,\"increment\":0},{\"enabled\":false,\"increment\":0}]'");
                LOGGER.atInfo().log("Added auto_summit_config column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add auto_summit_config: " + e.getMessage());
            }
        }

        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "auto_summit_rotation_index")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN auto_summit_rotation_index INT NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added auto_summit_rotation_index column");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add auto_summit_rotation_index: " + e.getMessage());
            }
        }
    }

    private static void ensureTranscendenceColumns(Connection conn) {
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "transcendence_count")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN transcendence_count INT NOT NULL DEFAULT 0");
                LOGGER.atInfo().log("Added transcendence_count column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add transcendence_count column: " + e.getMessage());
            }
        }
    }

    private static void ensureAutoAscendColumn(Connection conn) {
        if (!AscendDatabaseSetup.columnExists(conn, "ascend_players", "auto_ascend_enabled")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN auto_ascend_enabled BOOLEAN NOT NULL DEFAULT FALSE");
                LOGGER.atInfo().log("Added auto_ascend_enabled column to ascend_players");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to add auto_ascend_enabled column: " + e.getMessage());
            }
        }
    }

    private static void ensureBreakAscensionColumn(Connection conn) {
        if (AscendDatabaseSetup.columnExists(conn, "ascend_players", "break_ascension_enabled")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE ascend_players ADD COLUMN break_ascension_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            LOGGER.atInfo().log("Added break_ascension_enabled column to ascend_players");
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to add break_ascension_enabled column: " + e.getMessage());
        }
    }
}
