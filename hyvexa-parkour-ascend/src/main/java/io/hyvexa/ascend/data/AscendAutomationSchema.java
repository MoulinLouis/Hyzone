package io.hyvexa.ascend.data;

import java.sql.Connection;

/**
 * Automation column migrations: auto-elevation, auto-summit, auto-ascend,
 * transcendence, and break-ascension.
 */
final class AscendAutomationSchema {

    private AscendAutomationSchema() {
    }

    static void ensureColumns(Connection conn) {
        // Auto-elevation
        AscendDatabaseSetup.ensureColumn(conn, "ascend_players", "auto_elevation_enabled", "BOOLEAN NOT NULL DEFAULT FALSE");
        AscendDatabaseSetup.ensureColumn(conn, "ascend_players", "auto_elevation_timer_seconds", "INT NOT NULL DEFAULT 0");
        AscendDatabaseSetup.ensureColumn(conn, "ascend_players", "auto_elevation_targets", "TEXT DEFAULT '[]'");
        AscendDatabaseSetup.ensureColumn(conn, "ascend_players", "auto_elevation_target_index", "INT NOT NULL DEFAULT 0");

        // Auto-summit
        AscendDatabaseSetup.ensureColumn(conn, "ascend_players", "auto_summit_enabled", "BOOLEAN NOT NULL DEFAULT FALSE");
        AscendDatabaseSetup.ensureColumn(conn, "ascend_players", "auto_summit_timer_seconds", "INT NOT NULL DEFAULT 0");
        AscendDatabaseSetup.ensureColumn(conn, "ascend_players", "auto_summit_config",
                "TEXT DEFAULT '[{\"enabled\":false,\"increment\":0},{\"enabled\":false,\"increment\":0},{\"enabled\":false,\"increment\":0}]'");
        AscendDatabaseSetup.ensureColumn(conn, "ascend_players", "auto_summit_rotation_index", "INT NOT NULL DEFAULT 0");

        // Transcendence
        AscendDatabaseSetup.ensureColumn(conn, "ascend_players", "transcendence_count", "INT NOT NULL DEFAULT 0");

        // Auto-ascend
        AscendDatabaseSetup.ensureColumn(conn, "ascend_players", "auto_ascend_enabled", "BOOLEAN NOT NULL DEFAULT FALSE");

        // Break-ascension
        AscendDatabaseSetup.ensureColumn(conn, "ascend_players", "break_ascension_enabled", "BOOLEAN NOT NULL DEFAULT FALSE");
    }
}
