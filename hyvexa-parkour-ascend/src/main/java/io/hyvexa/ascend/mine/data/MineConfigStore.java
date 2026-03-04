package io.hyvexa.ascend.mine.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MineConfigStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();

    private final Map<String, Mine> mines = new LinkedHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile List<Mine> sortedCache;

    // Gate
    private volatile double gateMinX, gateMinY, gateMinZ;
    private volatile double gateMaxX, gateMaxY, gateMaxZ;
    private volatile double fallbackX, fallbackY, fallbackZ;
    private volatile float fallbackRotX, fallbackRotY, fallbackRotZ;
    private volatile boolean gateConfigured;

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, MineConfigStore will be empty");
            return;
        }

        lock.writeLock().lock();
        try {
            mines.clear();
            invalidateSortedCache();

            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                if (conn == null) {
                    LOGGER.atWarning().log("Failed to acquire database connection for mine config");
                    return;
                }
                loadMines(conn);
                loadZones(conn);
                loadGate(conn);
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to load MineConfigStore: " + e.getMessage());
            }

            LOGGER.atInfo().log("MineConfigStore loaded " + mines.size() + " mines");
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void loadMines(Connection conn) throws SQLException {
        String sql = "SELECT id, name, display_order, unlock_cost_mantissa, unlock_cost_exp10, " +
            "world, spawn_x, spawn_y, spawn_z, spawn_rot_x, spawn_rot_y, spawn_rot_z " +
            "FROM mine_definitions ORDER BY display_order, id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Mine mine = new Mine(rs.getString("id"), rs.getString("name"));
                    mine.setDisplayOrder(rs.getInt("display_order"));
                    mine.setUnlockCost(BigNumber.of(
                        rs.getDouble("unlock_cost_mantissa"),
                        rs.getInt("unlock_cost_exp10")
                    ));
                    mine.setWorld(rs.getString("world"));
                    mine.setSpawnX(rs.getDouble("spawn_x"));
                    mine.setSpawnY(rs.getDouble("spawn_y"));
                    mine.setSpawnZ(rs.getDouble("spawn_z"));
                    mine.setSpawnRotX(rs.getFloat("spawn_rot_x"));
                    mine.setSpawnRotY(rs.getFloat("spawn_rot_y"));
                    mine.setSpawnRotZ(rs.getFloat("spawn_rot_z"));
                    mines.put(mine.getId(), mine);
                }
            }
        }
    }

    private void loadZones(Connection conn) throws SQLException {
        String sql = "SELECT id, mine_id, min_x, min_y, min_z, max_x, max_y, max_z, " +
            "block_table_json, regen_threshold, regen_cooldown_seconds FROM mine_zones";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String mineId = rs.getString("mine_id");
                    Mine mine = mines.get(mineId);
                    if (mine == null) continue;

                    MineZone zone = new MineZone(
                        rs.getString("id"), mineId,
                        rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                        rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z")
                    );

                    String json = rs.getString("block_table_json");
                    if (json != null && !json.isEmpty()) {
                        Map<String, Double> table = GSON.fromJson(json,
                            new TypeToken<Map<String, Double>>(){}.getType());
                        if (table != null) {
                            zone.getBlockTable().putAll(table);
                        }
                    }

                    zone.setRegenThreshold(rs.getDouble("regen_threshold"));
                    zone.setRegenCooldownSeconds(rs.getInt("regen_cooldown_seconds"));
                    mine.getZones().add(zone);
                }
            }
        }
    }

    private void loadGate(Connection conn) throws SQLException {
        String sql = "SELECT min_x, min_y, min_z, max_x, max_y, max_z, " +
            "fallback_x, fallback_y, fallback_z, fallback_rot_x, fallback_rot_y, fallback_rot_z FROM mine_gate WHERE id = 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    gateMinX = rs.getDouble("min_x");
                    gateMinY = rs.getDouble("min_y");
                    gateMinZ = rs.getDouble("min_z");
                    gateMaxX = rs.getDouble("max_x");
                    gateMaxY = rs.getDouble("max_y");
                    gateMaxZ = rs.getDouble("max_z");
                    fallbackX = rs.getDouble("fallback_x");
                    fallbackY = rs.getDouble("fallback_y");
                    fallbackZ = rs.getDouble("fallback_z");
                    fallbackRotX = rs.getFloat("fallback_rot_x");
                    fallbackRotY = rs.getFloat("fallback_rot_y");
                    fallbackRotZ = rs.getFloat("fallback_rot_z");
                    gateConfigured = true;
                }
            }
        }
    }

    // --- Mine CRUD ---

    public Mine getMine(String id) {
        lock.readLock().lock();
        try {
            return mines.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Mine> listMinesSorted() {
        List<Mine> cached = sortedCache;
        if (cached != null) return cached;

        lock.readLock().lock();
        try {
            if (sortedCache != null) return sortedCache;
            List<Mine> sorted = new ArrayList<>(mines.values());
            sorted.sort(Comparator.comparingInt(Mine::getDisplayOrder)
                .thenComparing(m -> m.getName() != null ? m.getName() : m.getId(),
                    String.CASE_INSENSITIVE_ORDER));
            List<Mine> unmodifiable = Collections.unmodifiableList(sorted);
            sortedCache = unmodifiable;
            return unmodifiable;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void saveMine(Mine mine) {
        if (mine == null || mine.getId() == null) return;

        lock.writeLock().lock();
        try {
            mines.put(mine.getId(), mine);
            invalidateSortedCache();
        } finally {
            lock.writeLock().unlock();
        }

        saveMineToDatabase(mine);
    }

    private void saveMineToDatabase(Mine mine) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = """
            INSERT INTO mine_definitions (id, name, display_order, unlock_cost_mantissa, unlock_cost_exp10,
                world, spawn_x, spawn_y, spawn_z, spawn_rot_x, spawn_rot_y, spawn_rot_z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name), display_order = VALUES(display_order),
                unlock_cost_mantissa = VALUES(unlock_cost_mantissa), unlock_cost_exp10 = VALUES(unlock_cost_exp10),
                world = VALUES(world),
                spawn_x = VALUES(spawn_x), spawn_y = VALUES(spawn_y), spawn_z = VALUES(spawn_z),
                spawn_rot_x = VALUES(spawn_rot_x), spawn_rot_y = VALUES(spawn_rot_y), spawn_rot_z = VALUES(spawn_rot_z)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                int i = 1;
                stmt.setString(i++, mine.getId());
                stmt.setString(i++, mine.getName());
                stmt.setInt(i++, mine.getDisplayOrder());
                stmt.setDouble(i++, mine.getUnlockCost().getMantissa());
                stmt.setInt(i++, mine.getUnlockCost().getExponent());
                stmt.setString(i++, mine.getWorld() != null ? mine.getWorld() : "");
                stmt.setDouble(i++, mine.getSpawnX());
                stmt.setDouble(i++, mine.getSpawnY());
                stmt.setDouble(i++, mine.getSpawnZ());
                stmt.setFloat(i++, mine.getSpawnRotX());
                stmt.setFloat(i++, mine.getSpawnRotY());
                stmt.setFloat(i, mine.getSpawnRotZ());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save mine: " + e.getMessage());
        }
    }

    public boolean deleteMine(String id) {
        lock.writeLock().lock();
        boolean removed;
        try {
            removed = mines.remove(id) != null;
            if (removed) invalidateSortedCache();
        } finally {
            lock.writeLock().unlock();
        }

        if (removed) {
            deleteMineFromDatabase(id);
        }
        return removed;
    }

    private void deleteMineFromDatabase(String id) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM mine_definitions WHERE id = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, id);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to delete mine: " + e.getMessage());
        }
    }

    // --- Zone CRUD ---

    public void saveZone(MineZone zone) {
        if (zone == null || zone.getId() == null) return;

        lock.writeLock().lock();
        try {
            Mine mine = mines.get(zone.getMineId());
            if (mine != null) {
                mine.getZones().removeIf(z -> z.getId().equals(zone.getId()));
                mine.getZones().add(zone);
            }
        } finally {
            lock.writeLock().unlock();
        }

        saveZoneToDatabase(zone);
    }

    private void saveZoneToDatabase(MineZone zone) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = """
            INSERT INTO mine_zones (id, mine_id, min_x, min_y, min_z, max_x, max_y, max_z,
                block_table_json, regen_threshold, regen_cooldown_seconds)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                min_x = VALUES(min_x), min_y = VALUES(min_y), min_z = VALUES(min_z),
                max_x = VALUES(max_x), max_y = VALUES(max_y), max_z = VALUES(max_z),
                block_table_json = VALUES(block_table_json),
                regen_threshold = VALUES(regen_threshold),
                regen_cooldown_seconds = VALUES(regen_cooldown_seconds)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                int i = 1;
                stmt.setString(i++, zone.getId());
                stmt.setString(i++, zone.getMineId());
                stmt.setInt(i++, zone.getMinX());
                stmt.setInt(i++, zone.getMinY());
                stmt.setInt(i++, zone.getMinZ());
                stmt.setInt(i++, zone.getMaxX());
                stmt.setInt(i++, zone.getMaxY());
                stmt.setInt(i++, zone.getMaxZ());
                stmt.setString(i++, GSON.toJson(zone.getBlockTable()));
                stmt.setDouble(i++, zone.getRegenThreshold());
                stmt.setInt(i, zone.getRegenCooldownSeconds());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save zone: " + e.getMessage());
        }
    }

    public boolean deleteZone(String zoneId) {
        lock.writeLock().lock();
        boolean removed = false;
        try {
            for (Mine mine : mines.values()) {
                if (mine.getZones().removeIf(z -> z.getId().equals(zoneId))) {
                    removed = true;
                    break;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }

        if (removed) {
            deleteZoneFromDatabase(zoneId);
        }
        return removed;
    }

    private void deleteZoneFromDatabase(String zoneId) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM mine_zones WHERE id = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, zoneId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to delete zone: " + e.getMessage());
        }
    }

    // --- Gate ---

    public void saveGate(double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ,
                         double fbX, double fbY, double fbZ,
                         float fbRotX, float fbRotY, float fbRotZ) {
        this.gateMinX = minX;
        this.gateMinY = minY;
        this.gateMinZ = minZ;
        this.gateMaxX = maxX;
        this.gateMaxY = maxY;
        this.gateMaxZ = maxZ;
        this.fallbackX = fbX;
        this.fallbackY = fbY;
        this.fallbackZ = fbZ;
        this.fallbackRotX = fbRotX;
        this.fallbackRotY = fbRotY;
        this.fallbackRotZ = fbRotZ;
        this.gateConfigured = true;

        saveGateToDatabase();
    }

    private void saveGateToDatabase() {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = """
            INSERT INTO mine_gate (id, min_x, min_y, min_z, max_x, max_y, max_z,
                fallback_x, fallback_y, fallback_z, fallback_rot_x, fallback_rot_y, fallback_rot_z)
            VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                min_x = VALUES(min_x), min_y = VALUES(min_y), min_z = VALUES(min_z),
                max_x = VALUES(max_x), max_y = VALUES(max_y), max_z = VALUES(max_z),
                fallback_x = VALUES(fallback_x), fallback_y = VALUES(fallback_y), fallback_z = VALUES(fallback_z),
                fallback_rot_x = VALUES(fallback_rot_x), fallback_rot_y = VALUES(fallback_rot_y),
                fallback_rot_z = VALUES(fallback_rot_z)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                int i = 1;
                stmt.setDouble(i++, gateMinX);
                stmt.setDouble(i++, gateMinY);
                stmt.setDouble(i++, gateMinZ);
                stmt.setDouble(i++, gateMaxX);
                stmt.setDouble(i++, gateMaxY);
                stmt.setDouble(i++, gateMaxZ);
                stmt.setDouble(i++, fallbackX);
                stmt.setDouble(i++, fallbackY);
                stmt.setDouble(i++, fallbackZ);
                stmt.setFloat(i++, fallbackRotX);
                stmt.setFloat(i++, fallbackRotY);
                stmt.setFloat(i, fallbackRotZ);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save mine gate: " + e.getMessage());
        }
    }

    public boolean isInsideGate(double x, double y, double z) {
        if (!gateConfigured) return false;
        return x >= gateMinX && x <= gateMaxX
            && y >= gateMinY && y <= gateMaxY
            && z >= gateMinZ && z <= gateMaxZ;
    }

    public boolean isGateConfigured() { return gateConfigured; }
    public double getGateMinX() { return gateMinX; }
    public double getGateMinY() { return gateMinY; }
    public double getGateMinZ() { return gateMinZ; }
    public double getGateMaxX() { return gateMaxX; }
    public double getGateMaxY() { return gateMaxY; }
    public double getGateMaxZ() { return gateMaxZ; }
    public double getFallbackX() { return fallbackX; }
    public double getFallbackY() { return fallbackY; }
    public double getFallbackZ() { return fallbackZ; }
    public float getFallbackRotX() { return fallbackRotX; }
    public float getFallbackRotY() { return fallbackRotY; }
    public float getFallbackRotZ() { return fallbackRotZ; }

    private void invalidateSortedCache() {
        sortedCache = null;
    }
}
