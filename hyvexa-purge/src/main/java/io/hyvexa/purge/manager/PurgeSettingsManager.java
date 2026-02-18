package io.hyvexa.purge.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.purge.data.PurgeLocation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PurgeSettingsManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int SETTINGS_ID = 1;

    private volatile PurgeLocation sessionStartPoint;
    private volatile PurgeLocation sessionExitPoint;

    public PurgeSettingsManager() {
        createTable();
        load();
    }

    public PurgeLocation getSessionStartPoint() {
        return sessionStartPoint;
    }

    public PurgeLocation getSessionExitPoint() {
        return sessionExitPoint;
    }

    public boolean hasSessionStartPoint() {
        return sessionStartPoint != null;
    }

    public boolean hasSessionExitPoint() {
        return sessionExitPoint != null;
    }

    public synchronized void setSessionStartPoint(double x, double y, double z, float rotX, float rotY, float rotZ) {
        sessionStartPoint = new PurgeLocation(x, y, z, rotX, rotY, rotZ);
        save();
    }

    public synchronized void setSessionExitPoint(double x, double y, double z, float rotX, float rotY, float rotZ) {
        sessionExitPoint = new PurgeLocation(x, y, z, rotX, rotY, rotZ);
        save();
    }

    private void createTable() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS purge_settings ("
                + "id INT NOT NULL PRIMARY KEY, "
                + "start_x DOUBLE NULL, "
                + "start_y DOUBLE NULL, "
                + "start_z DOUBLE NULL, "
                + "start_rot_x FLOAT NULL, "
                + "start_rot_y FLOAT NULL, "
                + "start_rot_z FLOAT NULL, "
                + "stop_x DOUBLE NULL, "
                + "stop_y DOUBLE NULL, "
                + "stop_z DOUBLE NULL, "
                + "stop_rot_x FLOAT NULL, "
                + "stop_rot_y FLOAT NULL, "
                + "stop_rot_z FLOAT NULL"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge_settings table");
        }
    }

    private synchronized void load() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = """
                SELECT start_x, start_y, start_z, start_rot_x, start_rot_y, start_rot_z,
                       stop_x, stop_y, stop_z, stop_rot_x, stop_rot_y, stop_rot_z
                FROM purge_settings
                WHERE id = ?
                """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setInt(1, SETTINGS_ID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    sessionStartPoint = readLocation(rs, "start_");
                    sessionExitPoint = readLocation(rs, "stop_");
                    return;
                }
            }
            insertDefaultRow(conn);
            sessionStartPoint = null;
            sessionExitPoint = null;
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load purge settings");
        }
    }

    private static PurgeLocation readLocation(ResultSet rs, String prefix) throws SQLException {
        Double x = rs.getObject(prefix + "x", Double.class);
        Double y = rs.getObject(prefix + "y", Double.class);
        Double z = rs.getObject(prefix + "z", Double.class);
        if (x == null || y == null || z == null) {
            return null;
        }
        Float rotX = rs.getObject(prefix + "rot_x", Float.class);
        Float rotY = rs.getObject(prefix + "rot_y", Float.class);
        Float rotZ = rs.getObject(prefix + "rot_z", Float.class);
        return new PurgeLocation(
                x,
                y,
                z,
                rotX != null ? rotX : 0f,
                rotY != null ? rotY : 0f,
                rotZ != null ? rotZ : 0f
        );
    }

    private void insertDefaultRow(Connection conn) throws SQLException {
        String sql = "INSERT INTO purge_settings (id) VALUES (?) ON DUPLICATE KEY UPDATE id = id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setInt(1, SETTINGS_ID);
            stmt.executeUpdate();
        }
    }

    private synchronized void save() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = """
                INSERT INTO purge_settings (
                    id,
                    start_x, start_y, start_z, start_rot_x, start_rot_y, start_rot_z,
                    stop_x, stop_y, stop_z, stop_rot_x, stop_rot_y, stop_rot_z
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    start_x = VALUES(start_x),
                    start_y = VALUES(start_y),
                    start_z = VALUES(start_z),
                    start_rot_x = VALUES(start_rot_x),
                    start_rot_y = VALUES(start_rot_y),
                    start_rot_z = VALUES(start_rot_z),
                    stop_x = VALUES(stop_x),
                    stop_y = VALUES(stop_y),
                    stop_z = VALUES(stop_z),
                    stop_rot_x = VALUES(stop_rot_x),
                    stop_rot_y = VALUES(stop_rot_y),
                    stop_rot_z = VALUES(stop_rot_z)
                """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setInt(1, SETTINGS_ID);
            bindLocation(stmt, 2, sessionStartPoint);
            bindLocation(stmt, 8, sessionExitPoint);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save purge settings");
        }
    }

    private static void bindLocation(PreparedStatement stmt, int startIndex, PurgeLocation location) throws SQLException {
        if (location == null) {
            stmt.setNull(startIndex, java.sql.Types.DOUBLE);
            stmt.setNull(startIndex + 1, java.sql.Types.DOUBLE);
            stmt.setNull(startIndex + 2, java.sql.Types.DOUBLE);
            stmt.setNull(startIndex + 3, java.sql.Types.FLOAT);
            stmt.setNull(startIndex + 4, java.sql.Types.FLOAT);
            stmt.setNull(startIndex + 5, java.sql.Types.FLOAT);
            return;
        }
        stmt.setDouble(startIndex, location.x());
        stmt.setDouble(startIndex + 1, location.y());
        stmt.setDouble(startIndex + 2, location.z());
        stmt.setFloat(startIndex + 3, location.rotX());
        stmt.setFloat(startIndex + 4, location.rotY());
        stmt.setFloat(startIndex + 5, location.rotZ());
    }
}
