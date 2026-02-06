package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Stores global Ascend settings (single row, id = 1).
 * Holds spawn and NPC teleport locations.
 */
public class AscendSettingsStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int SETTINGS_ID = 1;

    // Spawn position
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float spawnRotX;
    private float spawnRotY;
    private float spawnRotZ;

    // NPC position
    private double npcX;
    private double npcY;
    private double npcZ;
    private float npcRotX;
    private float npcRotY;
    private float npcRotZ;

    // Void Y threshold (null = disabled)
    private Double voidYThreshold;

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, AscendSettingsStore will use defaults");
            return;
        }

        String sql = """
            SELECT spawn_x, spawn_y, spawn_z, spawn_rot_x, spawn_rot_y, spawn_rot_z,
                   npc_x, npc_y, npc_z, npc_rot_x, npc_rot_y, npc_rot_z,
                   void_y_threshold
            FROM ascend_settings WHERE id = ?
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setInt(1, SETTINGS_ID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    spawnX = rs.getDouble("spawn_x");
                    spawnY = rs.getDouble("spawn_y");
                    spawnZ = rs.getDouble("spawn_z");
                    spawnRotX = rs.getFloat("spawn_rot_x");
                    spawnRotY = rs.getFloat("spawn_rot_y");
                    spawnRotZ = rs.getFloat("spawn_rot_z");
                    npcX = rs.getDouble("npc_x");
                    npcY = rs.getDouble("npc_y");
                    npcZ = rs.getDouble("npc_z");
                    npcRotX = rs.getFloat("npc_rot_x");
                    npcRotY = rs.getFloat("npc_rot_y");
                    npcRotZ = rs.getFloat("npc_rot_z");
                    double voidY = rs.getDouble("void_y_threshold");
                    voidYThreshold = rs.wasNull() ? null : voidY;
                    LOGGER.atInfo().log("AscendSettingsStore loaded");
                } else {
                    LOGGER.atInfo().log("No settings row found, inserting default");
                    insertDefault();
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load AscendSettingsStore: " + e.getMessage());
        }
    }

    private void insertDefault() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        String sql = """
            INSERT INTO ascend_settings (id, spawn_x, spawn_y, spawn_z, spawn_rot_x, spawn_rot_y, spawn_rot_z,
                                         npc_x, npc_y, npc_z, npc_rot_x, npc_rot_y, npc_rot_z)
            VALUES (?, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            ON DUPLICATE KEY UPDATE id = id
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setInt(1, SETTINGS_ID);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to insert default settings: " + e.getMessage());
        }
    }

    public void setSpawnPosition(double x, double y, double z, float rotX, float rotY, float rotZ) {
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.spawnRotX = rotX;
        this.spawnRotY = rotY;
        this.spawnRotZ = rotZ;
        saveToDatabase();
    }

    public void setNpcPosition(double x, double y, double z, float rotX, float rotY, float rotZ) {
        this.npcX = x;
        this.npcY = y;
        this.npcZ = z;
        this.npcRotX = rotX;
        this.npcRotY = rotY;
        this.npcRotZ = rotZ;
        saveToDatabase();
    }

    private void saveToDatabase() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        String sql = """
            INSERT INTO ascend_settings (id, spawn_x, spawn_y, spawn_z, spawn_rot_x, spawn_rot_y, spawn_rot_z,
                                         npc_x, npc_y, npc_z, npc_rot_x, npc_rot_y, npc_rot_z, void_y_threshold)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                spawn_x = VALUES(spawn_x), spawn_y = VALUES(spawn_y), spawn_z = VALUES(spawn_z),
                spawn_rot_x = VALUES(spawn_rot_x), spawn_rot_y = VALUES(spawn_rot_y), spawn_rot_z = VALUES(spawn_rot_z),
                npc_x = VALUES(npc_x), npc_y = VALUES(npc_y), npc_z = VALUES(npc_z),
                npc_rot_x = VALUES(npc_rot_x), npc_rot_y = VALUES(npc_rot_y), npc_rot_z = VALUES(npc_rot_z),
                void_y_threshold = VALUES(void_y_threshold)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            int i = 1;
            stmt.setInt(i++, SETTINGS_ID);
            stmt.setDouble(i++, spawnX);
            stmt.setDouble(i++, spawnY);
            stmt.setDouble(i++, spawnZ);
            stmt.setFloat(i++, spawnRotX);
            stmt.setFloat(i++, spawnRotY);
            stmt.setFloat(i++, spawnRotZ);
            stmt.setDouble(i++, npcX);
            stmt.setDouble(i++, npcY);
            stmt.setDouble(i++, npcZ);
            stmt.setFloat(i++, npcRotX);
            stmt.setFloat(i++, npcRotY);
            stmt.setFloat(i++, npcRotZ);
            if (voidYThreshold != null) {
                stmt.setDouble(i, voidYThreshold);
            } else {
                stmt.setNull(i, java.sql.Types.DOUBLE);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save settings: " + e.getMessage());
        }
    }

    public Vector3d getSpawnPosition() {
        return new Vector3d(spawnX, spawnY, spawnZ);
    }

    public Vector3f getSpawnRotation() {
        return new Vector3f(spawnRotX, spawnRotY, spawnRotZ);
    }

    public boolean hasSpawnPosition() {
        return spawnX != 0 || spawnY != 0 || spawnZ != 0;
    }

    public Vector3d getNpcPosition() {
        return new Vector3d(npcX, npcY, npcZ);
    }

    public Vector3f getNpcRotation() {
        return new Vector3f(npcRotX, npcRotY, npcRotZ);
    }

    public boolean hasNpcPosition() {
        return npcX != 0 || npcY != 0 || npcZ != 0;
    }

    public Double getVoidYThreshold() {
        return voidYThreshold;
    }

    public void setVoidYThreshold(Double voidYThreshold) {
        this.voidYThreshold = voidYThreshold;
        saveToDatabase();
    }
}
