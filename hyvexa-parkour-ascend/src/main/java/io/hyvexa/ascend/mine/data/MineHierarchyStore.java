package io.hyvexa.ascend.mine.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.hyvexa.common.math.BigNumber;

public class MineHierarchyStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();

    private final ConnectionProvider db;

    private final Map<String, Mine> mines = new LinkedHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile List<Mine> sortedCache;

    // layerId -> MineZoneLayer (index built after loadLayers)
    // Volatile reference: rebuilt atomically during syncLoad, individual put/remove for CRUD ops
    private volatile Map<String, MineZoneLayer> layerById = new ConcurrentHashMap<>();

    public MineHierarchyStore(ConnectionProvider db) {
        this.db = db;
    }

    public void syncLoad(Connection conn) throws SQLException {
        lock.writeLock().lock();
        try {
            mines.clear();
            invalidateSortedCache();

            Map<String, MineZoneLayer> newLayerById = new ConcurrentHashMap<>();

            loadMines(conn);
            loadZones(conn);
            loadLayers(conn, newLayerById);
            loadLayerRarityBlocks(conn, newLayerById);
            seedRarityBlockTables(conn);

            layerById = newLayerById;

            LOGGER.atInfo().log("MineHierarchyStore loaded " + mines.size() + " mines");
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
            "block_table_json, regen_cooldown_seconds FROM mine_zones";
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

                    zone.setRegenIntervalSeconds(rs.getInt("regen_cooldown_seconds"));
                    mine.getZones().add(zone);
                }
            }
        }
    }

    private void loadLayers(Connection conn, Map<String, MineZoneLayer> layerIndex) throws SQLException {
        String sql = "SELECT id, zone_id, min_y, max_y, block_table_json, egg_drop_chance, display_name, egg_item_id " +
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
                    try {
                        layer.setEggDropChance(rs.getDouble("egg_drop_chance"));
                    } catch (SQLException ignored) {}
                    try {
                        layer.setDisplayName(rs.getString("display_name"));
                    } catch (SQLException ignored) {}
                    try {
                        layer.setEggItemId(rs.getString("egg_item_id"));
                    } catch (SQLException ignored) {}
                    layerIndex.put(layer.getId(), layer);
                    for (Mine mine : mines.values()) {
                        boolean attached = false;
                        for (MineZone zone : mine.getZones()) {
                            if (zone.getId().equals(zoneId)) {
                                zone.getLayers().add(layer);
                                attached = true;
                                break;
                            }
                        }
                        if (attached) break;
                    }
                }
            }
        }

        // Sort layers once after all are loaded (SQL already orders them, but this ensures consistency)
        for (Mine mine : mines.values()) {
            for (MineZone zone : mine.getZones()) {
                if (!zone.getLayers().isEmpty()) {
                    sortLayers(zone);
                }
            }
        }
    }

    private void loadLayerRarityBlocks(Connection conn, Map<String, MineZoneLayer> layerIndex) throws SQLException {
        String sql = "SELECT layer_id, rarity, block_table_json FROM mine_layer_rarity_blocks";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String layerId = rs.getString("layer_id");
                    MinerRarity rarity = MinerRarity.fromName(rs.getString("rarity"));
                    if (rarity == null) continue;
                    String json = rs.getString("block_table_json");
                    if (json == null || json.isEmpty()) continue;
                    Map<String, Double> table = GSON.fromJson(json,
                        new TypeToken<Map<String, Double>>(){}.getType());
                    if (table == null || table.isEmpty()) continue;
                    MineZoneLayer layer = layerIndex.get(layerId);
                    if (layer != null) {
                        layer.getRarityBlockTables().put(rarity, table);
                    }
                }
            }
        }
    }

    private void seedRarityBlockTables(Connection conn) throws SQLException {
        List<MineZoneLayer> allLayers = new ArrayList<>();
        for (Mine mine : mines.values()) {
            for (MineZone zone : mine.getZones()) {
                allLayers.addAll(zone.getLayers());
            }
        }

        for (MineZoneLayer layer : allLayers) {
            if (layer.getBlockTable().isEmpty()) continue;
            if (layer.getRarityBlockTables().size() >= MinerRarity.values().length) continue;

            List<Map.Entry<String, Double>> sorted = new ArrayList<>(layer.getBlockTable().entrySet());
            sorted.sort(Map.Entry.comparingByValue());
            Set<String> rareBlocks = new HashSet<>();
            for (int i = 0; i < Math.min(2, sorted.size()); i++) {
                rareBlocks.add(sorted.get(i).getKey());
            }

            double[][] multipliers = {
                {1.0, 1.0},     // COMMON: no change
                {1.25, 0.90},   // UNCOMMON
                {1.5, 0.75},    // RARE
                {2.0, 0.50},    // EPIC
                {3.0, 0.25},    // LEGENDARY
            };

            for (MinerRarity rarity : MinerRarity.values()) {
                if (layer.getRarityBlockTables().containsKey(rarity)) continue;

                double rareMult = multipliers[rarity.ordinal()][0];
                double commonMult = multipliers[rarity.ordinal()][1];

                Map<String, Double> table = new HashMap<>();
                for (var entry : layer.getBlockTable().entrySet()) {
                    double mult = rareBlocks.contains(entry.getKey()) ? rareMult : commonMult;
                    table.put(entry.getKey(), entry.getValue() * mult);
                }
                layer.getRarityBlockTables().put(rarity, table);

                String json = GSON.toJson(table);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT IGNORE INTO mine_layer_rarity_blocks (layer_id, rarity, block_table_json) VALUES (?, ?, ?)")) {
                    ps.setString(1, layer.getId());
                    ps.setString(2, rarity.name());
                    ps.setString(3, json);
                    ps.executeUpdate();
                }
            }
        }
    }

    // --- Single-mine convenience methods ---

    public Mine getMine() {
        lock.readLock().lock();
        try {
            var it = mines.values().iterator();
            return it.hasNext() ? it.next() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public MineZone getZone() {
        Mine mine = getMine();
        if (mine == null || mine.getZones().isEmpty()) return null;
        return mine.getZones().get(0);
    }

    public List<MineZoneLayer> getLayers() {
        MineZone zone = getZone();
        return zone != null ? zone.getLayers() : Collections.emptyList();
    }

    public String getMineId() {
        Mine mine = getMine();
        return mine != null ? mine.getId() : null;
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

        lock.writeLock().lock();
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
            lock.writeLock().unlock();
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

        DatabaseManager.execute(this.db, sql, stmt -> {
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
        });
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
        DatabaseManager.execute(this.db, "DELETE FROM mine_definitions WHERE id = ?",
            stmt -> stmt.setString(1, id));
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
        String sql = """
            INSERT INTO mine_zones (id, mine_id, min_x, min_y, min_z, max_x, max_y, max_z,
                block_table_json, regen_cooldown_seconds)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                min_x = VALUES(min_x), min_y = VALUES(min_y), min_z = VALUES(min_z),
                max_x = VALUES(max_x), max_y = VALUES(max_y), max_z = VALUES(max_z),
                block_table_json = VALUES(block_table_json),
                regen_cooldown_seconds = VALUES(regen_cooldown_seconds)
            """;

        DatabaseManager.execute(this.db, sql, stmt -> {
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
            stmt.setInt(i, zone.getRegenIntervalSeconds());
        });
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
        DatabaseManager.execute(this.db, "DELETE FROM mine_zones WHERE id = ?",
            stmt -> stmt.setString(1, zoneId));
    }

    // --- Layer CRUD ---

    public MineZoneLayer getLayerById(String layerId) {
        if (layerId == null) return null;
        return layerById.get(layerId);
    }

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

        layerById.put(layer.getId(), layer);
        saveLayerToDatabase(layer);
    }

    private void saveLayerToDatabase(MineZoneLayer layer) {
        String sql = """
            INSERT INTO mine_zone_layers (id, zone_id, min_y, max_y, block_table_json, egg_drop_chance, display_name, egg_item_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                min_y = VALUES(min_y), max_y = VALUES(max_y),
                block_table_json = VALUES(block_table_json),
                egg_drop_chance = VALUES(egg_drop_chance),
                display_name = VALUES(display_name),
                egg_item_id = VALUES(egg_item_id)
            """;

        DatabaseManager.execute(this.db, sql, stmt -> {
            int i = 1;
            stmt.setString(i++, layer.getId());
            stmt.setString(i++, layer.getZoneId());
            stmt.setInt(i++, layer.getMinY());
            stmt.setInt(i++, layer.getMaxY());
            stmt.setString(i++, GSON.toJson(layer.getBlockTable()));
            stmt.setDouble(i++, layer.getEggDropChance());
            stmt.setString(i++, layer.getDisplayName());
            stmt.setString(i, layer.getEggItemId());
        });
    }

    public void saveLayerEggItemId(String layerId, String eggItemId) {
        MineZoneLayer layer = getLayerById(layerId);
        if (layer == null) return;
        layer.setEggItemId(eggItemId);
        DatabaseManager.execute(this.db,
            "UPDATE mine_zone_layers SET egg_item_id = ? WHERE id = ?",
            stmt -> {
                stmt.setString(1, eggItemId);
                stmt.setString(2, layerId);
            });
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
            layerById.remove(layerId);
            deleteLayerFromDatabase(layerId);
        }
        return removed;
    }

    private void deleteLayerFromDatabase(String layerId) {
        DatabaseManager.execute(this.db, "DELETE FROM mine_zone_layers WHERE id = ?",
            stmt -> stmt.setString(1, layerId));
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
