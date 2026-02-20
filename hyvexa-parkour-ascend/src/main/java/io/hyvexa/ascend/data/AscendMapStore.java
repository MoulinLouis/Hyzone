package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class AscendMapStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long LEGACY_ROBOT_TIME_REDUCTION_MS = 0L;
    private static final int LEGACY_STORAGE_CAPACITY = 100;

    private final Map<String, AscendMap> maps = new LinkedHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile Runnable onChangeListener;
    private volatile List<AscendMap> sortedMapsCache;

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, AscendMapStore will be empty");
            return;
        }

        // Runtime balance values are computed from display_order in AscendConstants.
        // Legacy balance/storage columns stay write-compatible for migration rollout.
        String sql = """
            SELECT id, name, world, start_x, start_y, start_z, start_rot_x, start_rot_y, start_rot_z,
                   finish_x, finish_y, finish_z, display_order
            FROM ascend_maps ORDER BY display_order, id
            """;

        lock.writeLock().lock();
        try {
            maps.clear();
            invalidateSortedCache();
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                if (conn == null) {
                    LOGGER.atWarning().log("Failed to acquire database connection");
                    return;
                }
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    DatabaseManager.applyQueryTimeout(stmt);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            AscendMap map = new AscendMap();
                            map.setId(rs.getString("id"));
                            map.setName(rs.getString("name"));
                            map.setWorld(rs.getString("world"));
                            map.setStartX(rs.getDouble("start_x"));
                            map.setStartY(rs.getDouble("start_y"));
                            map.setStartZ(rs.getDouble("start_z"));
                            map.setStartRotX(rs.getFloat("start_rot_x"));
                            map.setStartRotY(rs.getFloat("start_rot_y"));
                            map.setStartRotZ(rs.getFloat("start_rot_z"));
                            map.setFinishX(rs.getDouble("finish_x"));
                            map.setFinishY(rs.getDouble("finish_y"));
                            map.setFinishZ(rs.getDouble("finish_z"));
                            map.setDisplayOrder(rs.getInt("display_order"));

                            maps.put(map.getId(), map);
                        }
                    }
                }
                LOGGER.atInfo().log("AscendMapStore loaded " + maps.size() + " maps");
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to load AscendMapStore: " + e.getMessage());
            }
        } finally {
            lock.writeLock().unlock();
        }
        notifyChange();
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

    public AscendMap getMap(String id) {
        lock.readLock().lock();
        try {
            return maps.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<AscendMap> listMaps() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(maps.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<AscendMap> listMapsSorted() {
        List<AscendMap> cached = sortedMapsCache;
        if (cached != null) {
            return cached;
        }

        lock.readLock().lock();
        try {
            if (sortedMapsCache != null) {
                return sortedMapsCache;
            }
            List<AscendMap> sorted = new ArrayList<>(maps.values());
            sorted.sort(Comparator.comparingInt(AscendMap::getDisplayOrder)
                .thenComparing(map -> map.getName() != null ? map.getName() : map.getId(),
                    String.CASE_INSENSITIVE_ORDER));
            List<AscendMap> unmodifiable = Collections.unmodifiableList(sorted);
            sortedMapsCache = unmodifiable;
            return unmodifiable;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveMap(AscendMap map) {
        if (map == null || map.getId() == null) {
            return;
        }

        lock.writeLock().lock();
        try {
            maps.put(map.getId(), map);
            invalidateSortedCache();
        } finally {
            lock.writeLock().unlock();
        }

        saveMapToDatabase(map);
        notifyChange();
    }

    private void saveMapToDatabase(AscendMap map) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        // Compatibility write path: keep legacy columns populated for existing schemas/tools.
        // Runtime logic reads effective values from AscendConstants via display_order.
        String sql = """
            INSERT INTO ascend_maps (id, name, price, robot_price, base_reward, base_run_time_ms,
                robot_time_reduction_ms, storage_capacity, world, start_x, start_y, start_z, start_rot_x, start_rot_y,
                start_rot_z,
                finish_x, finish_y, finish_z, display_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name), price = VALUES(price), robot_price = VALUES(robot_price),
                base_reward = VALUES(base_reward), base_run_time_ms = VALUES(base_run_time_ms),
                robot_time_reduction_ms = VALUES(robot_time_reduction_ms),
                storage_capacity = VALUES(storage_capacity), world = VALUES(world),
                start_x = VALUES(start_x), start_y = VALUES(start_y), start_z = VALUES(start_z),
                start_rot_x = VALUES(start_rot_x), start_rot_y = VALUES(start_rot_y), start_rot_z = VALUES(start_rot_z),
                finish_x = VALUES(finish_x), finish_y = VALUES(finish_y), finish_z = VALUES(finish_z),
                display_order = VALUES(display_order)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                int i = 1;
                stmt.setString(i++, map.getId());
                stmt.setString(i++, map.getName());
                stmt.setLong(i++, map.getEffectivePrice());
                stmt.setLong(i++, map.getEffectiveRobotPrice());
                stmt.setLong(i++, map.getEffectiveBaseReward());
                stmt.setLong(i++, map.getEffectiveBaseRunTimeMs());
                stmt.setLong(i++, LEGACY_ROBOT_TIME_REDUCTION_MS);
                stmt.setInt(i++, LEGACY_STORAGE_CAPACITY);
                stmt.setString(i++, map.getWorld());
                stmt.setDouble(i++, map.getStartX());
                stmt.setDouble(i++, map.getStartY());
                stmt.setDouble(i++, map.getStartZ());
                stmt.setFloat(i++, map.getStartRotX());
                stmt.setFloat(i++, map.getStartRotY());
                stmt.setFloat(i++, map.getStartRotZ());
                stmt.setDouble(i++, map.getFinishX());
                stmt.setDouble(i++, map.getFinishY());
                stmt.setDouble(i++, map.getFinishZ());
                stmt.setInt(i, map.getDisplayOrder());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save map: " + e.getMessage());
        }
    }

    public boolean deleteMap(String id) {
        lock.writeLock().lock();
        boolean removed;
        try {
            removed = maps.remove(id) != null;
            if (removed) {
                invalidateSortedCache();
            }
        } finally {
            lock.writeLock().unlock();
        }

        if (removed) {
            deleteMapFromDatabase(id);
            notifyChange();
        }
        return removed;
    }

    private void deleteMapFromDatabase(String id) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM ascend_maps WHERE id = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, id);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to delete map: " + e.getMessage());
        }
    }

    private void invalidateSortedCache() {
        sortedMapsCache = null;
    }
}
