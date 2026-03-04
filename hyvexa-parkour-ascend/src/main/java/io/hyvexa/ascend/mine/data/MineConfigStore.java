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

    // Entry gate (id=1): teleports ascended players INTO the mine
    private volatile double entryMinX, entryMinY, entryMinZ;
    private volatile double entryMaxX, entryMaxY, entryMaxZ;
    private volatile double entryDestX, entryDestY, entryDestZ;
    private volatile float entryDestRotX, entryDestRotY, entryDestRotZ;
    private volatile boolean entryGateConfigured;

    // Exit gate (id=2): teleports players OUT of the mine
    private volatile double exitMinX, exitMinY, exitMinZ;
    private volatile double exitMaxX, exitMaxY, exitMaxZ;
    private volatile double exitDestX, exitDestY, exitDestZ;
    private volatile float exitDestRotX, exitDestRotY, exitDestRotZ;
    private volatile boolean exitGateConfigured;

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
        String sql = "SELECT id, min_x, min_y, min_z, max_x, max_y, max_z, " +
            "fallback_x, fallback_y, fallback_z, fallback_rot_x, fallback_rot_y, fallback_rot_z " +
            "FROM mine_gate WHERE id IN (1, 2)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (id == 1) {
                        entryMinX = rs.getDouble("min_x");
                        entryMinY = rs.getDouble("min_y");
                        entryMinZ = rs.getDouble("min_z");
                        entryMaxX = rs.getDouble("max_x");
                        entryMaxY = rs.getDouble("max_y");
                        entryMaxZ = rs.getDouble("max_z");
                        entryDestX = rs.getDouble("fallback_x");
                        entryDestY = rs.getDouble("fallback_y");
                        entryDestZ = rs.getDouble("fallback_z");
                        entryDestRotX = rs.getFloat("fallback_rot_x");
                        entryDestRotY = rs.getFloat("fallback_rot_y");
                        entryDestRotZ = rs.getFloat("fallback_rot_z");
                        entryGateConfigured = true;
                    } else if (id == 2) {
                        exitMinX = rs.getDouble("min_x");
                        exitMinY = rs.getDouble("min_y");
                        exitMinZ = rs.getDouble("min_z");
                        exitMaxX = rs.getDouble("max_x");
                        exitMaxY = rs.getDouble("max_y");
                        exitMaxZ = rs.getDouble("max_z");
                        exitDestX = rs.getDouble("fallback_x");
                        exitDestY = rs.getDouble("fallback_y");
                        exitDestZ = rs.getDouble("fallback_z");
                        exitDestRotX = rs.getFloat("fallback_rot_x");
                        exitDestRotY = rs.getFloat("fallback_rot_y");
                        exitDestRotZ = rs.getFloat("fallback_rot_z");
                        exitGateConfigured = true;
                    }
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

    public void saveEntryGate(double minX, double minY, double minZ,
                              double maxX, double maxY, double maxZ,
                              double destX, double destY, double destZ,
                              float destRotX, float destRotY, float destRotZ) {
        this.entryMinX = minX;
        this.entryMinY = minY;
        this.entryMinZ = minZ;
        this.entryMaxX = maxX;
        this.entryMaxY = maxY;
        this.entryMaxZ = maxZ;
        this.entryDestX = destX;
        this.entryDestY = destY;
        this.entryDestZ = destZ;
        this.entryDestRotX = destRotX;
        this.entryDestRotY = destRotY;
        this.entryDestRotZ = destRotZ;
        this.entryGateConfigured = true;

        saveGateToDatabase(1, minX, minY, minZ, maxX, maxY, maxZ,
            destX, destY, destZ, destRotX, destRotY, destRotZ);
    }

    public void saveExitGate(double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ,
                             double destX, double destY, double destZ,
                             float destRotX, float destRotY, float destRotZ) {
        this.exitMinX = minX;
        this.exitMinY = minY;
        this.exitMinZ = minZ;
        this.exitMaxX = maxX;
        this.exitMaxY = maxY;
        this.exitMaxZ = maxZ;
        this.exitDestX = destX;
        this.exitDestY = destY;
        this.exitDestZ = destZ;
        this.exitDestRotX = destRotX;
        this.exitDestRotY = destRotY;
        this.exitDestRotZ = destRotZ;
        this.exitGateConfigured = true;

        saveGateToDatabase(2, minX, minY, minZ, maxX, maxY, maxZ,
            destX, destY, destZ, destRotX, destRotY, destRotZ);
    }

    private void saveGateToDatabase(int gateId, double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ,
                                    double destX, double destY, double destZ,
                                    float destRotX, float destRotY, float destRotZ) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = """
            INSERT INTO mine_gate (id, min_x, min_y, min_z, max_x, max_y, max_z,
                fallback_x, fallback_y, fallback_z, fallback_rot_x, fallback_rot_y, fallback_rot_z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                stmt.setInt(i++, gateId);
                stmt.setDouble(i++, minX);
                stmt.setDouble(i++, minY);
                stmt.setDouble(i++, minZ);
                stmt.setDouble(i++, maxX);
                stmt.setDouble(i++, maxY);
                stmt.setDouble(i++, maxZ);
                stmt.setDouble(i++, destX);
                stmt.setDouble(i++, destY);
                stmt.setDouble(i++, destZ);
                stmt.setFloat(i++, destRotX);
                stmt.setFloat(i++, destRotY);
                stmt.setFloat(i, destRotZ);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save mine gate (id=" + gateId + "): " + e.getMessage());
        }
    }

    public boolean isInsideEntryGate(double x, double y, double z) {
        if (!entryGateConfigured) return false;
        return x >= entryMinX && x <= entryMaxX
            && y >= entryMinY && y <= entryMaxY
            && z >= entryMinZ && z <= entryMaxZ;
    }

    public boolean isInsideExitGate(double x, double y, double z) {
        if (!exitGateConfigured) return false;
        return x >= exitMinX && x <= exitMaxX
            && y >= exitMinY && y <= exitMaxY
            && z >= exitMinZ && z <= exitMaxZ;
    }

    // Entry gate
    public boolean isEntryGateConfigured() { return entryGateConfigured; }
    public double getEntryMinX() { return entryMinX; }
    public double getEntryMinY() { return entryMinY; }
    public double getEntryMinZ() { return entryMinZ; }
    public double getEntryMaxX() { return entryMaxX; }
    public double getEntryMaxY() { return entryMaxY; }
    public double getEntryMaxZ() { return entryMaxZ; }
    public double getEntryDestX() { return entryDestX; }
    public double getEntryDestY() { return entryDestY; }
    public double getEntryDestZ() { return entryDestZ; }
    public float getEntryDestRotX() { return entryDestRotX; }
    public float getEntryDestRotY() { return entryDestRotY; }
    public float getEntryDestRotZ() { return entryDestRotZ; }

    // Exit gate
    public boolean isExitGateConfigured() { return exitGateConfigured; }
    public double getExitMinX() { return exitMinX; }
    public double getExitMinY() { return exitMinY; }
    public double getExitMinZ() { return exitMinZ; }
    public double getExitMaxX() { return exitMaxX; }
    public double getExitMaxY() { return exitMaxY; }
    public double getExitMaxZ() { return exitMaxZ; }
    public double getExitDestX() { return exitDestX; }
    public double getExitDestY() { return exitDestY; }
    public double getExitDestZ() { return exitDestZ; }
    public float getExitDestRotX() { return exitDestRotX; }
    public float getExitDestRotY() { return exitDestRotY; }
    public float getExitDestRotZ() { return exitDestRotZ; }

    private void invalidateSortedCache() {
        sortedCache = null;
    }
}
