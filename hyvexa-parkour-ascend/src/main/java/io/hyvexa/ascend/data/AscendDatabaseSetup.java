package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;


public final class AscendDatabaseSetup {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private AscendDatabaseSetup() {
    }

    public static void ensureTables() {
        if (!DatabaseManager.get().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, skipping Ascend table setup");
            return;
        }

        try (Connection conn = DatabaseManager.get().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection for Ascend table setup");
                return;
            }
            try (Statement stmt = conn.createStatement()) {
                AscendCoreSchema.createAndMigrate(conn, stmt);
                AscendFeatureSchema.createAndMigrate(conn, stmt);
                AscendMineSchema.createAndMigrate(conn, stmt);
                AscendAutomationSchema.ensureColumns(conn);

                LOGGER.atInfo().log("Ascend database tables ensured");
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to create Ascend tables: " + e.getMessage());
        }
    }

    /**
     * Shared utility: check whether a column exists in a table.
     * Used by all schema classes in this package.
     */
    static boolean columnExists(Connection conn, String table, String column) {
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
}
