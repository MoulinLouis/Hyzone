package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.parkour.ParkourConstants;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

public class MapStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String MAP_ID_PATTERN = "^[a-zA-Z0-9_-]+$";
    private static final double MAX_COORDINATE = 30000000.0;

    private final java.util.Map<String, Map> maps = new LinkedHashMap<>();
    private final ReadWriteLock fileLock = new ReentrantReadWriteLock();
    private volatile Runnable onChangeListener;

    public MapStore() {
    }

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, MapStore will be empty");
            return;
        }

        String mapSql = """
            SELECT id, name, category, world, difficulty, display_order, first_completion_xp, mithril_sword_enabled,
                   mithril_daggers_enabled,
                   start_x, start_y, start_z, start_rot_x, start_rot_y, start_rot_z,
                   finish_x, finish_y, finish_z, finish_rot_x, finish_rot_y, finish_rot_z,
                   start_trigger_x, start_trigger_y, start_trigger_z, start_trigger_rot_x, start_trigger_rot_y, start_trigger_rot_z,
                   leave_trigger_x, leave_trigger_y, leave_trigger_z, leave_trigger_rot_x, leave_trigger_rot_y, leave_trigger_rot_z,
                   leave_teleport_x, leave_teleport_y, leave_teleport_z, leave_teleport_rot_x, leave_teleport_rot_y, leave_teleport_rot_z,
                   created_at, updated_at
            FROM maps ORDER BY display_order, id
            """;

        String checkpointSql = """
            SELECT map_id, checkpoint_index, x, y, z, rot_x, rot_y, rot_z
            FROM map_checkpoints ORDER BY map_id, checkpoint_index
            """;

        fileLock.writeLock().lock();
        try {
            maps.clear();

            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                // Load all maps
                try (PreparedStatement stmt = conn.prepareStatement(mapSql);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        Map map = new Map();
                        map.setId(rs.getString("id"));
                        map.setName(rs.getString("name"));
                        map.setCategory(rs.getString("category"));
                        map.setWorld(rs.getString("world"));
                        map.setDifficulty(rs.getInt("difficulty"));
                        map.setOrder(rs.getInt("display_order"));
                        map.setFirstCompletionXp(rs.getLong("first_completion_xp"));
                        map.setMithrilSwordEnabled(rs.getBoolean("mithril_sword_enabled"));
                        map.setMithrilDaggersEnabled(rs.getBoolean("mithril_daggers_enabled"));

                        map.setStart(readTransform(rs, "start_"));
                        map.setFinish(readTransform(rs, "finish_"));
                        map.setStartTrigger(readTransform(rs, "start_trigger_"));
                        map.setLeaveTrigger(readTransform(rs, "leave_trigger_"));
                        map.setLeaveTeleport(readTransform(rs, "leave_teleport_"));

                        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
                        java.sql.Timestamp updatedAt = rs.getTimestamp("updated_at");
                        map.setCreatedAt(createdAt != null ? createdAt.getTime() : 0L);
                        map.setUpdatedAt(updatedAt != null ? updatedAt.getTime() : map.getCreatedAt());

                        maps.put(map.getId(), map);
                    }
                }

                // Load all checkpoints
                try (PreparedStatement stmt = conn.prepareStatement(checkpointSql);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        String mapId = rs.getString("map_id");
                        Map map = maps.get(mapId);
                        if (map != null) {
                            TransformData checkpoint = new TransformData();
                            checkpoint.setX(rs.getDouble("x"));
                            checkpoint.setY(rs.getDouble("y"));
                            checkpoint.setZ(rs.getDouble("z"));
                            checkpoint.setRotX(rs.getFloat("rot_x"));
                            checkpoint.setRotY(rs.getFloat("rot_y"));
                            checkpoint.setRotZ(rs.getFloat("rot_z"));
                            map.getCheckpoints().add(checkpoint);
                        }
                    }
                }

                LOGGER.atInfo().log("MapStore loaded " + maps.size() + " maps from database");

            } catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to load MapStore from database: " + e.getMessage());
            }
        } finally {
            fileLock.writeLock().unlock();
        }
    }

    private TransformData readTransform(ResultSet rs, String prefix) throws SQLException {
        Double x = rs.getObject(prefix + "x", Double.class);
        if (x == null) {
            return null;
        }
        TransformData data = new TransformData();
        data.setX(validateCoordinate(x));
        data.setY(validateCoordinate(rs.getDouble(prefix + "y")));
        data.setZ(validateCoordinate(rs.getDouble(prefix + "z")));
        data.setRotX(rs.getFloat(prefix + "rot_x"));
        data.setRotY(rs.getFloat(prefix + "rot_y"));
        data.setRotZ(rs.getFloat(prefix + "rot_z"));
        return data;
    }

    public void setOnChangeListener(Runnable listener) {
        this.onChangeListener = listener;
    }

    private void notifyChange() {
        Runnable listener = this.onChangeListener;
        if (listener != null) {
            listener.run();
        }
    }

    public boolean hasMap(String id) {
        fileLock.readLock().lock();
        try {
            return this.maps.containsKey(id);
        } finally {
            fileLock.readLock().unlock();
        }
    }

    public Map getMap(String id) {
        fileLock.readLock().lock();
        try {
            return copyMap(this.maps.get(id));
        } finally {
            fileLock.readLock().unlock();
        }
    }

    public List<Map> listMaps() {
        fileLock.readLock().lock();
        try {
            List<Map> copies = new ArrayList<>(this.maps.size());
            for (Map map : this.maps.values()) {
                Map copy = copyMap(map);
                if (copy != null) {
                    copies.add(copy);
                }
            }
            return Collections.unmodifiableList(copies);
        } finally {
            fileLock.readLock().unlock();
        }
    }

    public Map findMapByStartTrigger(double x, double y, double z, double touchRadiusSq) {
        fileLock.readLock().lock();
        try {
            for (Map map : this.maps.values()) {
                TransformData trigger = map.getStartTrigger();
                if (trigger == null) {
                    continue;
                }
                double dx = x - trigger.getX();
                double dy = y - trigger.getY();
                double dz = z - trigger.getZ();
                if (dx * dx + dy * dy + dz * dz <= touchRadiusSq) {
                    return copyMap(map);
                }
            }
        } finally {
            fileLock.readLock().unlock();
        }
        return null;
    }

    public int getMapCount() {
        fileLock.readLock().lock();
        try {
            return this.maps.size();
        } finally {
            fileLock.readLock().unlock();
        }
    }

    public void addMap(Map map) {
        validateMap(map);
        Map stored = copyMap(map);

        fileLock.writeLock().lock();
        try {
            this.maps.put(stored.getId(), stored);
        } finally {
            fileLock.writeLock().unlock();
        }

        saveMapToDatabase(stored);
        notifyChange();
    }

    public void updateMap(Map map) {
        validateMap(map);
        Map stored = copyMap(map);

        fileLock.writeLock().lock();
        try {
            this.maps.put(stored.getId(), stored);
        } finally {
            fileLock.writeLock().unlock();
        }

        saveMapToDatabase(stored);
        notifyChange();
    }

    public boolean removeMap(String id) {
        fileLock.writeLock().lock();
        boolean removed;
        try {
            removed = this.maps.remove(id) != null;
        } finally {
            fileLock.writeLock().unlock();
        }

        if (removed) {
            deleteMapFromDatabase(id);
            notifyChange();
        }
        return removed;
    }

    private void saveMapToDatabase(Map map) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        String mapSql = """
            INSERT INTO maps (id, name, category, world, difficulty, display_order, first_completion_xp, mithril_sword_enabled,
                mithril_daggers_enabled,
                start_x, start_y, start_z, start_rot_x, start_rot_y, start_rot_z,
                finish_x, finish_y, finish_z, finish_rot_x, finish_rot_y, finish_rot_z,
                start_trigger_x, start_trigger_y, start_trigger_z, start_trigger_rot_x, start_trigger_rot_y, start_trigger_rot_z,
                leave_trigger_x, leave_trigger_y, leave_trigger_z, leave_trigger_rot_x, leave_trigger_rot_y, leave_trigger_rot_z,
                leave_teleport_x, leave_teleport_y, leave_teleport_z, leave_teleport_rot_x, leave_teleport_rot_y, leave_teleport_rot_z,
                created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name), category = VALUES(category), world = VALUES(world),
                difficulty = VALUES(difficulty), display_order = VALUES(display_order),
                first_completion_xp = VALUES(first_completion_xp), mithril_sword_enabled = VALUES(mithril_sword_enabled),
                mithril_daggers_enabled = VALUES(mithril_daggers_enabled),
                start_x = VALUES(start_x), start_y = VALUES(start_y), start_z = VALUES(start_z),
                start_rot_x = VALUES(start_rot_x), start_rot_y = VALUES(start_rot_y), start_rot_z = VALUES(start_rot_z),
                finish_x = VALUES(finish_x), finish_y = VALUES(finish_y), finish_z = VALUES(finish_z),
                finish_rot_x = VALUES(finish_rot_x), finish_rot_y = VALUES(finish_rot_y), finish_rot_z = VALUES(finish_rot_z),
                start_trigger_x = VALUES(start_trigger_x), start_trigger_y = VALUES(start_trigger_y), start_trigger_z = VALUES(start_trigger_z),
                start_trigger_rot_x = VALUES(start_trigger_rot_x), start_trigger_rot_y = VALUES(start_trigger_rot_y), start_trigger_rot_z = VALUES(start_trigger_rot_z),
                leave_trigger_x = VALUES(leave_trigger_x), leave_trigger_y = VALUES(leave_trigger_y), leave_trigger_z = VALUES(leave_trigger_z),
                leave_trigger_rot_x = VALUES(leave_trigger_rot_x), leave_trigger_rot_y = VALUES(leave_trigger_rot_y), leave_trigger_rot_z = VALUES(leave_trigger_rot_z),
                leave_teleport_x = VALUES(leave_teleport_x), leave_teleport_y = VALUES(leave_teleport_y), leave_teleport_z = VALUES(leave_teleport_z),
                leave_teleport_rot_x = VALUES(leave_teleport_rot_x), leave_teleport_rot_y = VALUES(leave_teleport_rot_y), leave_teleport_rot_z = VALUES(leave_teleport_rot_z),
                updated_at = VALUES(updated_at)
            """;

        String deleteCheckpointsSql = "DELETE FROM map_checkpoints WHERE map_id = ?";
        String insertCheckpointSql = """
            INSERT INTO map_checkpoints (map_id, checkpoint_index, x, y, z, rot_x, rot_y, rot_z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement mapStmt = conn.prepareStatement(mapSql);
                 PreparedStatement deleteStmt = conn.prepareStatement(deleteCheckpointsSql);
                 PreparedStatement cpStmt = conn.prepareStatement(insertCheckpointSql)) {

                // Insert/update map
                int idx = 1;
                mapStmt.setString(idx++, map.getId());
                mapStmt.setString(idx++, map.getName());
                mapStmt.setString(idx++, map.getCategory());
                mapStmt.setString(idx++, map.getWorld());
                mapStmt.setInt(idx++, map.getDifficulty());
                mapStmt.setInt(idx++, map.getOrder());
                mapStmt.setLong(idx++, map.getFirstCompletionXp());
                mapStmt.setBoolean(idx++, map.isMithrilSwordEnabled());
                mapStmt.setBoolean(idx++, map.isMithrilDaggersEnabled());

                idx = setTransform(mapStmt, idx, map.getStart());
                idx = setTransform(mapStmt, idx, map.getFinish());
                idx = setTransform(mapStmt, idx, map.getStartTrigger());
                idx = setTransform(mapStmt, idx, map.getLeaveTrigger());
                idx = setTransform(mapStmt, idx, map.getLeaveTeleport());

                mapStmt.setTimestamp(idx++, new java.sql.Timestamp(map.getCreatedAt()));
                mapStmt.setTimestamp(idx, new java.sql.Timestamp(map.getUpdatedAt()));

                mapStmt.executeUpdate();

                // Delete and re-insert checkpoints
                deleteStmt.setString(1, map.getId());
                deleteStmt.executeUpdate();

                List<TransformData> checkpoints = map.getCheckpoints();
                if (checkpoints != null) {
                    for (int i = 0; i < checkpoints.size(); i++) {
                        TransformData cp = checkpoints.get(i);
                        cpStmt.setString(1, map.getId());
                        cpStmt.setInt(2, i);
                        cpStmt.setDouble(3, cp.getX());
                        cpStmt.setDouble(4, cp.getY());
                        cpStmt.setDouble(5, cp.getZ());
                        cpStmt.setFloat(6, cp.getRotX());
                        cpStmt.setFloat(7, cp.getRotY());
                        cpStmt.setFloat(8, cp.getRotZ());
                        cpStmt.addBatch();
                    }
                    cpStmt.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save map to database: " + e.getMessage());
        }
    }

    private int setTransform(PreparedStatement stmt, int startIndex, TransformData transform) throws SQLException {
        if (transform != null) {
            stmt.setDouble(startIndex, transform.getX());
            stmt.setDouble(startIndex + 1, transform.getY());
            stmt.setDouble(startIndex + 2, transform.getZ());
            stmt.setFloat(startIndex + 3, transform.getRotX());
            stmt.setFloat(startIndex + 4, transform.getRotY());
            stmt.setFloat(startIndex + 5, transform.getRotZ());
        } else {
            stmt.setNull(startIndex, java.sql.Types.DOUBLE);
            stmt.setNull(startIndex + 1, java.sql.Types.DOUBLE);
            stmt.setNull(startIndex + 2, java.sql.Types.DOUBLE);
            stmt.setNull(startIndex + 3, java.sql.Types.FLOAT);
            stmt.setNull(startIndex + 4, java.sql.Types.FLOAT);
            stmt.setNull(startIndex + 5, java.sql.Types.FLOAT);
        }
        return startIndex + 6;
    }

    private void deleteMapFromDatabase(String mapId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        // Checkpoints will be deleted by CASCADE
        String sql = "DELETE FROM maps WHERE id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mapId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to delete map from database: " + e.getMessage());
        }
    }

    private static void validateMap(Map map) {
        if (map == null) {
            throw new IllegalArgumentException("Map cannot be null.");
        }
        String id = map.getId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Map ID cannot be empty.");
        }
        String trimmedId = id.trim();
        if (trimmedId.length() > 32) {
            throw new IllegalArgumentException("Map ID too long.");
        }
        if (!trimmedId.matches(MAP_ID_PATTERN)) {
            throw new IllegalArgumentException("Map ID can only contain letters, numbers, underscores, and hyphens.");
        }
        map.setId(trimmedId);
        String name = map.getName();
        String trimmedName = name != null ? name.trim() : "";
        if (trimmedName.isEmpty()) {
            trimmedName = trimmedId;
        }
        if (trimmedName.length() > 64) {
            throw new IllegalArgumentException("Map name too long.");
        }
        map.setName(trimmedName);
        String category = map.getCategory();
        String trimmedCategory = category != null ? category.trim() : "";
        if (!trimmedCategory.isEmpty() && trimmedCategory.length() > 32) {
            throw new IllegalArgumentException("Map category too long.");
        }
        if (!trimmedCategory.isEmpty()) {
            map.setCategory(trimmedCategory);
        }
        map.setFirstCompletionXp(Math.max(0L, map.getFirstCompletionXp()));
        int difficulty = map.getDifficulty();
        if (difficulty < 0) {
            map.setDifficulty(0);
        }
        int order = map.getOrder();
        if (order < 0) {
            map.setOrder(ParkourConstants.DEFAULT_MAP_ORDER);
        }
    }

    private static double validateCoordinate(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        if (value > MAX_COORDINATE) {
            return MAX_COORDINATE;
        }
        if (value < -MAX_COORDINATE) {
            return -MAX_COORDINATE;
        }
        return value;
    }

    private static Map copyMap(Map source) {
        if (source == null) {
            return null;
        }
        Map copy = new Map();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setCategory(source.getCategory());
        copy.setWorld(source.getWorld());
        copy.setStart(copyTransform(source.getStart()));
        copy.setFinish(copyTransform(source.getFinish()));
        copy.setStartTrigger(copyTransform(source.getStartTrigger()));
        copy.setLeaveTrigger(copyTransform(source.getLeaveTrigger()));
        copy.setLeaveTeleport(copyTransform(source.getLeaveTeleport()));
        copy.setFirstCompletionXp(source.getFirstCompletionXp());
        copy.setDifficulty(source.getDifficulty());
        copy.setOrder(source.getOrder());
        copy.setMithrilSwordEnabled(source.isMithrilSwordEnabled());
        copy.setMithrilDaggersEnabled(source.isMithrilDaggersEnabled());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        if (source.getCheckpoints() != null) {
            for (TransformData checkpoint : source.getCheckpoints()) {
                copy.getCheckpoints().add(copyTransform(checkpoint));
            }
        }
        return copy;
    }

    private static TransformData copyTransform(TransformData source) {
        if (source == null) {
            return null;
        }
        TransformData copy = new TransformData();
        copy.setX(source.getX());
        copy.setY(source.getY());
        copy.setZ(source.getZ());
        copy.setRotX(source.getRotX());
        copy.setRotY(source.getRotY());
        copy.setRotZ(source.getRotZ());
        return copy;
    }
}
