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
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MineConfigStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final int GATE_ENTRY = 1;
    private static final int GATE_EXIT = 2;

    private record GateConfig(
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        double destX, double destY, double destZ,
        float destRotX, float destRotY, float destRotZ
    ) {
        boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    private final Map<String, Mine> mines = new LinkedHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile List<Mine> sortedCache;

    // mineId -> blockTypeId -> price
    private final Map<String, Map<String, BigNumber>> blockPrices = new ConcurrentHashMap<>();

    private volatile GateConfig entryGate;
    private volatile GateConfig exitGate;

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
                loadLayers(conn);
                loadGate(conn);
                loadBlockPrices(conn);
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
            "block_table_json, block_hp_json, regen_threshold, regen_cooldown_seconds FROM mine_zones";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String mineId = rs.getString("mine_id");
                    Mine mine = mines.get(mineId);
                    if (mine == null) continue;

                    int minX = rs.getInt("min_x");
                    int minY = rs.getInt("min_y");
                    int minZ = rs.getInt("min_z");
                    int maxX = rs.getInt("max_x");
                    int maxY = rs.getInt("max_y");
                    int maxZ = rs.getInt("max_z");

                    MineZone zone = new MineZone(
                        rs.getString("id"), mineId,
                        minX, minY, minZ, maxX, maxY, maxZ
                    );

                    String json = rs.getString("block_table_json");
                    if (json != null && !json.isEmpty()) {
                        Map<String, Double> table = GSON.fromJson(json,
                            new TypeToken<Map<String, Double>>(){}.getType());
                        if (table != null) {
                            zone.getBlockTable().putAll(table);
                        }
                    }

                    String hpJson = rs.getString("block_hp_json");
                    if (hpJson != null && !hpJson.isEmpty()) {
                        Map<String, Integer> hpTable = GSON.fromJson(hpJson,
                            new TypeToken<Map<String, Integer>>(){}.getType());
                        if (hpTable != null) {
                            zone.getBlockHpTable().putAll(hpTable);
                        }
                    }

                    zone.setRegenThreshold(rs.getDouble("regen_threshold"));
                    zone.setRegenCooldownSeconds(rs.getInt("regen_cooldown_seconds"));
                    mine.getZones().add(zone);
                }
            }
        }
    }

    private void loadLayers(Connection conn) throws SQLException {
        String sql = "SELECT id, zone_id, min_y, max_y, block_table_json, block_hp_json " +
            "FROM mine_zone_layers ORDER BY zone_id, min_y, max_y, id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String zoneId = rs.getString("zone_id");
                    MineZoneLayer layer = new MineZoneLayer(
                        rs.getString("id"), zoneId,
                        rs.getInt("min_y"), rs.getInt("max_y")
                    );
                    String json = rs.getString("block_table_json");
                    if (json != null && !json.isEmpty()) {
                        Map<String, Double> table = GSON.fromJson(json,
                            new TypeToken<Map<String, Double>>(){}.getType());
                        if (table != null) {
                            layer.getBlockTable().putAll(table);
                        }
                    }
                    String hpJson = rs.getString("block_hp_json");
                    if (hpJson != null && !hpJson.isEmpty()) {
                        Map<String, Integer> hpTable = GSON.fromJson(hpJson,
                            new TypeToken<Map<String, Integer>>(){}.getType());
                        if (hpTable != null) {
                            layer.getBlockHpTable().putAll(hpTable);
                        }
                    }
                    boolean attached = false;
                    for (Mine mine : mines.values()) {
                        for (MineZone zone : mine.getZones()) {
                            if (zone.getId().equals(zoneId)) {
                                zone.getLayers().add(layer);
                                sortLayers(zone);
                                attached = true;
                                break;
                            }
                        }
                        if (attached) break;
                    }
                }
            }
        }
    }

    private void loadGate(Connection conn) throws SQLException {
        String sql = "SELECT id, min_x, min_y, min_z, max_x, max_y, max_z, " +
            "fallback_x, fallback_y, fallback_z, fallback_rot_x, fallback_rot_y, fallback_rot_z " +
            "FROM mine_gate WHERE id IN (" + GATE_ENTRY + ", " + GATE_EXIT + ")";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    GateConfig gate = new GateConfig(
                        rs.getDouble("min_x"), rs.getDouble("min_y"), rs.getDouble("min_z"),
                        rs.getDouble("max_x"), rs.getDouble("max_y"), rs.getDouble("max_z"),
                        rs.getDouble("fallback_x"), rs.getDouble("fallback_y"), rs.getDouble("fallback_z"),
                        rs.getFloat("fallback_rot_x"), rs.getFloat("fallback_rot_y"), rs.getFloat("fallback_rot_z")
                    );
                    if (id == GATE_ENTRY) {
                        entryGate = gate;
                    } else if (id == GATE_EXIT) {
                        exitGate = gate;
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
                block_table_json, block_hp_json, regen_threshold, regen_cooldown_seconds)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                min_x = VALUES(min_x), min_y = VALUES(min_y), min_z = VALUES(min_z),
                max_x = VALUES(max_x), max_y = VALUES(max_y), max_z = VALUES(max_z),
                block_table_json = VALUES(block_table_json),
                block_hp_json = VALUES(block_hp_json),
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
                stmt.setString(i++, GSON.toJson(zone.getBlockHpTable()));
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
        this.entryGate = new GateConfig(minX, minY, minZ, maxX, maxY, maxZ,
            destX, destY, destZ, destRotX, destRotY, destRotZ);

        saveGateToDatabase(GATE_ENTRY, minX, minY, minZ, maxX, maxY, maxZ,
            destX, destY, destZ, destRotX, destRotY, destRotZ);
    }

    public void saveExitGate(double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ,
                             double destX, double destY, double destZ,
                             float destRotX, float destRotY, float destRotZ) {
        this.exitGate = new GateConfig(minX, minY, minZ, maxX, maxY, maxZ,
            destX, destY, destZ, destRotX, destRotY, destRotZ);

        saveGateToDatabase(GATE_EXIT, minX, minY, minZ, maxX, maxY, maxZ,
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
        GateConfig gate = entryGate;
        return gate != null && gate.contains(x, y, z);
    }

    public boolean isInsideExitGate(double x, double y, double z) {
        GateConfig gate = exitGate;
        return gate != null && gate.contains(x, y, z);
    }

    // Entry gate
    public boolean isEntryGateConfigured() { return entryGate != null; }
    public double getEntryMinX() { GateConfig g = entryGate; return g != null ? g.minX() : 0; }
    public double getEntryMinY() { GateConfig g = entryGate; return g != null ? g.minY() : 0; }
    public double getEntryMinZ() { GateConfig g = entryGate; return g != null ? g.minZ() : 0; }
    public double getEntryMaxX() { GateConfig g = entryGate; return g != null ? g.maxX() : 0; }
    public double getEntryMaxY() { GateConfig g = entryGate; return g != null ? g.maxY() : 0; }
    public double getEntryMaxZ() { GateConfig g = entryGate; return g != null ? g.maxZ() : 0; }
    public double getEntryDestX() { GateConfig g = entryGate; return g != null ? g.destX() : 0; }
    public double getEntryDestY() { GateConfig g = entryGate; return g != null ? g.destY() : 0; }
    public double getEntryDestZ() { GateConfig g = entryGate; return g != null ? g.destZ() : 0; }
    public float getEntryDestRotX() { GateConfig g = entryGate; return g != null ? g.destRotX() : 0; }
    public float getEntryDestRotY() { GateConfig g = entryGate; return g != null ? g.destRotY() : 0; }
    public float getEntryDestRotZ() { GateConfig g = entryGate; return g != null ? g.destRotZ() : 0; }

    // Exit gate
    public boolean isExitGateConfigured() { return exitGate != null; }
    public double getExitMinX() { GateConfig g = exitGate; return g != null ? g.minX() : 0; }
    public double getExitMinY() { GateConfig g = exitGate; return g != null ? g.minY() : 0; }
    public double getExitMinZ() { GateConfig g = exitGate; return g != null ? g.minZ() : 0; }
    public double getExitMaxX() { GateConfig g = exitGate; return g != null ? g.maxX() : 0; }
    public double getExitMaxY() { GateConfig g = exitGate; return g != null ? g.maxY() : 0; }
    public double getExitMaxZ() { GateConfig g = exitGate; return g != null ? g.maxZ() : 0; }
    public double getExitDestX() { GateConfig g = exitGate; return g != null ? g.destX() : 0; }
    public double getExitDestY() { GateConfig g = exitGate; return g != null ? g.destY() : 0; }
    public double getExitDestZ() { GateConfig g = exitGate; return g != null ? g.destZ() : 0; }
    public float getExitDestRotX() { GateConfig g = exitGate; return g != null ? g.destRotX() : 0; }
    public float getExitDestRotY() { GateConfig g = exitGate; return g != null ? g.destRotY() : 0; }
    public float getExitDestRotZ() { GateConfig g = exitGate; return g != null ? g.destRotZ() : 0; }

    // --- Block Prices ---

    private void loadBlockPrices(Connection conn) throws SQLException {
        blockPrices.clear();
        String sql = "SELECT mine_id, block_type_id, price_mantissa, price_exp10 FROM mine_block_prices";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String mineId = rs.getString("mine_id");
                    String blockTypeId = rs.getString("block_type_id");
                    BigNumber price = BigNumber.of(rs.getDouble("price_mantissa"), rs.getInt("price_exp10"));
                    blockPrices.computeIfAbsent(mineId, k -> new ConcurrentHashMap<>())
                        .put(blockTypeId, price);
                }
            }
        }
    }

    public void saveBlockPrice(String mineId, String blockTypeId, BigNumber price) {
        if (mineId == null || blockTypeId == null || price == null) return;

        blockPrices.computeIfAbsent(mineId, k -> new ConcurrentHashMap<>())
            .put(blockTypeId, price);

        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = """
            INSERT INTO mine_block_prices (mine_id, block_type_id, price_mantissa, price_exp10)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                price_mantissa = VALUES(price_mantissa), price_exp10 = VALUES(price_exp10)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, mineId);
                stmt.setString(2, blockTypeId);
                stmt.setDouble(3, price.getMantissa());
                stmt.setInt(4, price.getExponent());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save block price: " + e.getMessage());
        }
    }

    public void removeBlockPrice(String mineId, String blockTypeId) {
        if (mineId == null || blockTypeId == null) return;

        Map<String, BigNumber> prices = blockPrices.get(mineId);
        if (prices != null) {
            prices.remove(blockTypeId);
        }

        if (!DatabaseManager.getInstance().isInitialized()) return;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM mine_block_prices WHERE mine_id = ? AND block_type_id = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, mineId);
                stmt.setString(2, blockTypeId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to remove block price: " + e.getMessage());
        }
    }

    public BigNumber getBlockPrice(String mineId, String blockTypeId) {
        Map<String, BigNumber> prices = blockPrices.get(mineId);
        if (prices == null) return BigNumber.ONE;
        return prices.getOrDefault(blockTypeId, BigNumber.ONE);
    }

    public Map<String, BigNumber> getBlockPrices(String mineId) {
        Map<String, BigNumber> prices = blockPrices.get(mineId);
        if (prices == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(prices);
    }

    // --- Layer CRUD ---

    public void saveLayer(MineZoneLayer layer) {
        if (layer == null || layer.getId() == null) return;

        lock.writeLock().lock();
        try {
            for (Mine mine : mines.values()) {
                for (MineZone zone : mine.getZones()) {
                    if (zone.getId().equals(layer.getZoneId())) {
                        zone.getLayers().removeIf(l -> l.getId().equals(layer.getId()));
                        zone.getLayers().add(layer);
                        sortLayers(zone);
                        break;
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }

        saveLayerToDatabase(layer);
    }

    private void saveLayerToDatabase(MineZoneLayer layer) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = """
            INSERT INTO mine_zone_layers (id, zone_id, min_y, max_y, block_table_json, block_hp_json)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                min_y = VALUES(min_y), max_y = VALUES(max_y),
                block_table_json = VALUES(block_table_json),
                block_hp_json = VALUES(block_hp_json)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                int i = 1;
                stmt.setString(i++, layer.getId());
                stmt.setString(i++, layer.getZoneId());
                stmt.setInt(i++, layer.getMinY());
                stmt.setInt(i++, layer.getMaxY());
                stmt.setString(i++, GSON.toJson(layer.getBlockTable()));
                stmt.setString(i, GSON.toJson(layer.getBlockHpTable()));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save zone layer: " + e.getMessage());
        }
    }

    public boolean deleteLayer(String layerId) {
        lock.writeLock().lock();
        boolean removed = false;
        try {
            for (Mine mine : mines.values()) {
                for (MineZone zone : mine.getZones()) {
                    if (zone.getLayers().removeIf(l -> l.getId().equals(layerId))) {
                        removed = true;
                        break;
                    }
                }
                if (removed) break;
            }
        } finally {
            lock.writeLock().unlock();
        }

        if (removed) {
            deleteLayerFromDatabase(layerId);
        }
        return removed;
    }

    private void deleteLayerFromDatabase(String layerId) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM mine_zone_layers WHERE id = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, layerId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to delete zone layer: " + e.getMessage());
        }
    }

    private void sortLayers(MineZone zone) {
        zone.getLayers().sort(Comparator
            .comparingInt(MineZoneLayer::getMinY)
            .thenComparingInt(MineZoneLayer::getMaxY)
            .thenComparing(MineZoneLayer::getId));
    }

    private void invalidateSortedCache() {
        sortedCache = null;
    }
}
