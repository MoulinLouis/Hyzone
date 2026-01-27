package io.hyvexa.core.state;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerModeStateStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final PlayerModeStateStore INSTANCE = new PlayerModeStateStore();

    private final ConcurrentHashMap<UUID, PlayerModeState> states = new ConcurrentHashMap<>();
    private volatile boolean ready;

    private PlayerModeStateStore() {
    }

    public static PlayerModeStateStore getInstance() {
        return INSTANCE;
    }

    public boolean isReady() {
        return ready;
    }

    public void ensureTable() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            createTable(conn);
            migrateDepthsColumns(conn);
            ready = true;
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to ensure player_mode_state table: " + e.getMessage());
        }
    }

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, skipping PlayerModeStateStore load");
            return;
        }
        ensureTable();
        String sql = "SELECT * FROM player_mode_state";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = readUuid(rs, "player_uuid");
                    if (playerId == null) {
                        continue;
                    }
                    PlayerModeState state = new PlayerModeState();
                    state.setCurrentMode(parseMode(rs.getString("current_mode")));
                    state.setParkourReturnLocation(readLocation(rs, "parkour"));
                    state.setAscendReturnLocation(readLocation(rs, "ascend"));
                    states.put(playerId, state);
                }
            }
            ready = true;
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to load player_mode_state: " + e.getMessage());
        }
    }

    public PlayerModeState getState(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return states.get(playerId);
    }

    public PlayerMode getCurrentMode(UUID playerId) {
        PlayerModeState state = getState(playerId);
        return state != null ? state.getCurrentMode() : PlayerMode.NONE;
    }

    public PlayerModeState getOrCreate(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return states.computeIfAbsent(playerId, ignored -> new PlayerModeState());
    }

    public void setMode(UUID playerId, PlayerMode mode) {
        if (playerId == null) {
            return;
        }
        PlayerModeState state = getOrCreate(playerId);
        state.setCurrentMode(mode != null ? mode : PlayerMode.NONE);
        saveState(playerId, state);
    }

    public void saveState(UUID playerId, PlayerModeState state) {
        if (playerId == null || state == null) {
            return;
        }
        states.put(playerId, state);
        saveToDatabase(playerId, state);
    }

    public Map<UUID, PlayerModeState> snapshot() {
        return Map.copyOf(states);
    }

    private void saveToDatabase(UUID playerId, PlayerModeState state) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        ensureTable();
        String sql = """
            INSERT INTO player_mode_state (
                player_uuid, current_mode,
                parkour_world, parkour_x, parkour_y, parkour_z, parkour_rot_x, parkour_rot_y, parkour_rot_z,
                ascend_world, ascend_x, ascend_y, ascend_z, ascend_rot_x, ascend_rot_y, ascend_rot_z
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                current_mode = VALUES(current_mode),
                parkour_world = VALUES(parkour_world),
                parkour_x = VALUES(parkour_x),
                parkour_y = VALUES(parkour_y),
                parkour_z = VALUES(parkour_z),
                parkour_rot_x = VALUES(parkour_rot_x),
                parkour_rot_y = VALUES(parkour_rot_y),
                parkour_rot_z = VALUES(parkour_rot_z),
                ascend_world = VALUES(ascend_world),
                ascend_x = VALUES(ascend_x),
                ascend_y = VALUES(ascend_y),
                ascend_z = VALUES(ascend_z),
                ascend_rot_x = VALUES(ascend_rot_x),
                ascend_rot_y = VALUES(ascend_rot_y),
                ascend_rot_z = VALUES(ascend_rot_z)
            """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, state.getCurrentMode() != null ? state.getCurrentMode().name() : PlayerMode.NONE.name());
            int index = 3;
            index = bindLocation(stmt, index, state.getParkourReturnLocation());
            bindLocation(stmt, index, state.getAscendReturnLocation());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to save player_mode_state: " + e.getMessage());
        }
    }

    private void createTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS player_mode_state (
                player_uuid CHAR(36) PRIMARY KEY,
                current_mode VARCHAR(16) NOT NULL,
                parkour_world VARCHAR(64) NULL,
                parkour_x DOUBLE NULL,
                parkour_y DOUBLE NULL,
                parkour_z DOUBLE NULL,
                parkour_rot_x FLOAT NULL,
                parkour_rot_y FLOAT NULL,
                parkour_rot_z FLOAT NULL,
                ascend_world VARCHAR(64) NULL,
                ascend_x DOUBLE NULL,
                ascend_y DOUBLE NULL,
                ascend_z DOUBLE NULL,
                ascend_rot_x FLOAT NULL,
                ascend_rot_y FLOAT NULL,
                ascend_rot_z FLOAT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB
            """;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private void migrateDepthsColumns(Connection conn) throws SQLException {
        if (conn == null) {
            return;
        }
        if (!columnExists(conn, "player_mode_state", "depths_world")) {
            return;
        }
        if (columnExists(conn, "player_mode_state", "ascend_world")) {
            return;
        }
        String[] columns = {
                "depths_world|ascend_world|VARCHAR(64) NULL",
                "depths_x|ascend_x|DOUBLE NULL",
                "depths_y|ascend_y|DOUBLE NULL",
                "depths_z|ascend_z|DOUBLE NULL",
                "depths_rot_x|ascend_rot_x|FLOAT NULL",
                "depths_rot_y|ascend_rot_y|FLOAT NULL",
                "depths_rot_z|ascend_rot_z|FLOAT NULL"
        };
        try (Statement stmt = conn.createStatement()) {
            for (String entry : columns) {
                String[] parts = entry.split("\\|", 3);
                String from = parts[0];
                String to = parts[1];
                String type = parts[2];
                stmt.executeUpdate("ALTER TABLE player_mode_state CHANGE COLUMN " + from + " " + to + " " + type);
            }
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = """
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, tableName);
            stmt.setString(2, columnName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int bindLocation(PreparedStatement stmt, int index, Location location) throws SQLException {
        if (location == null) {
            stmt.setObject(index++, null);
            for (int i = 0; i < 6; i++) {
                stmt.setObject(index++, null);
            }
            return index;
        }
        stmt.setString(index++, location.getWorld());
        Vector3d position = location.getPosition();
        if (position == null) {
            for (int i = 0; i < 6; i++) {
                stmt.setObject(index++, null);
            }
            return index;
        }
        stmt.setObject(index++, position.getX());
        stmt.setObject(index++, position.getY());
        stmt.setObject(index++, position.getZ());
        Vector3f rotation = location.getRotation();
        if (rotation == null) {
            for (int i = 0; i < 3; i++) {
                stmt.setObject(index++, null);
            }
            return index;
        }
        stmt.setObject(index++, rotation.getX());
        stmt.setObject(index++, rotation.getY());
        stmt.setObject(index++, rotation.getZ());
        return index;
    }

    private static Location readLocation(ResultSet rs, String prefix) throws SQLException {
        String world = rs.getString(prefix + "_world");
        Double x = rs.getObject(prefix + "_x", Double.class);
        Double y = rs.getObject(prefix + "_y", Double.class);
        Double z = rs.getObject(prefix + "_z", Double.class);
        if (x == null || y == null || z == null) {
            return null;
        }
        Float rotX = rs.getObject(prefix + "_rot_x", Float.class);
        Float rotY = rs.getObject(prefix + "_rot_y", Float.class);
        Float rotZ = rs.getObject(prefix + "_rot_z", Float.class);
        float resolvedRotX = rotX != null ? rotX : 0.0f;
        float resolvedRotY = rotY != null ? rotY : 0.0f;
        float resolvedRotZ = rotZ != null ? rotZ : 0.0f;
        return new Location(world, x, y, z, resolvedRotX, resolvedRotY, resolvedRotZ);
    }

    private static PlayerMode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return PlayerMode.NONE;
        }
        try {
            String normalized = value.trim().toUpperCase();
            if ("DEPTHS".equals(normalized)) {
                return PlayerMode.ASCEND;
            }
            return PlayerMode.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return PlayerMode.NONE;
        }
    }

    private static UUID readUuid(ResultSet rs, String column) throws SQLException {
        String raw = rs.getString(column);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
