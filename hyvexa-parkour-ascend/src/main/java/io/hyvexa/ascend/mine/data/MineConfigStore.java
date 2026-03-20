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

    // blockTypeId -> price (global, not per-mine)
    private final Map<String, Long> blockPrices = new ConcurrentHashMap<>();

    // blockTypeId -> hp (global, not per-zone)
    private final Map<String, Integer> blockHpMap = new ConcurrentHashMap<>();

    // targetTier -> (blockTypeId -> amount) — recipes for tier upgrades
    private final Map<Integer, Map<String, Integer>> tierRecipes = new ConcurrentHashMap<>();

    // tier -> (level -> crystalCost) — enhancement costs
    private final Map<Integer, Map<Integer, Long>> enhanceCosts = new ConcurrentHashMap<>();

    // layerId -> MineZoneLayer (index built after loadLayers)
    private final Map<String, MineZoneLayer> layerById = new ConcurrentHashMap<>();

    // mineId -> [slot0, slot1, ...] ordered by slotIndex
    private final Map<String, List<MinerSlot>> minerSlots = new ConcurrentHashMap<>();

    // mineId -> { slotIndex -> [[x,y,z], ...] }  slotIndex=-1 for main line
    private final Map<String, Map<Integer, List<double[]>>> conveyorWaypoints = new ConcurrentHashMap<>();

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
                loadLayerRarityBlocks(conn);
                seedRarityBlockTables(conn);
                loadGate(conn);
                loadBlockPrices(conn);
                loadBlockHp(conn);
                loadMinerSlots(conn);
                loadConveyorWaypoints(conn);
                loadTierRecipes(conn);
                loadEnhanceCosts(conn);
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

                    zone.setRegenThreshold(rs.getDouble("regen_threshold"));
                    zone.setRegenCooldownSeconds(rs.getInt("regen_cooldown_seconds"));
                    mine.getZones().add(zone);
                }
            }
        }
    }

    private void loadLayers(Connection conn) throws SQLException {
        layerById.clear();
        String sql = "SELECT id, zone_id, min_y, max_y, block_table_json, egg_drop_chance, display_name " +
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
                    layerById.put(layer.getId(), layer);
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

    private void loadLayerRarityBlocks(Connection conn) throws SQLException {
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
                    MineZoneLayer layer = getLayerById(layerId);
                    if (layer != null) {
                        layer.getRarityBlockTables().put(rarity, table);
                    }
                }
            }
        }
    }

    /**
     * Seeds default rarity block tables for layers that don't have them yet.
     * Rare blocks = the 2 blocks with the lowest weight in the base table.
     */
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

            // Find the 2 rarest blocks (lowest weight)
            List<Map.Entry<String, Double>> sorted = new ArrayList<>(layer.getBlockTable().entrySet());
            sorted.sort(Map.Entry.comparingByValue());
            java.util.Set<String> rareBlocks = new java.util.HashSet<>();
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

                // Persist to DB
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

    /** Finds a layer by its ID. O(1) via index map. */
    public MineZoneLayer getLayerById(String layerId) {
        if (layerId == null) return null;
        return layerById.get(layerId);
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

    // --- Single-mine convenience methods ---

    /** Returns the single mine, or null if none configured. */
    public Mine getMine() {
        lock.readLock().lock();
        try {
            var it = mines.values().iterator();
            return it.hasNext() ? it.next() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Returns the single zone of the single mine, or null. */
    public MineZone getZone() {
        Mine mine = getMine();
        if (mine == null || mine.getZones().isEmpty()) return null;
        return mine.getZones().get(0);
    }

    /** Returns layers of the single zone. */
    public List<MineZoneLayer> getLayers() {
        MineZone zone = getZone();
        return zone != null ? zone.getLayers() : Collections.emptyList();
    }

    /** Miner slots for the single mine. */
    public List<MinerSlot> getMinerSlots() {
        Mine mine = getMine();
        return mine != null ? getMinerSlots(mine.getId()) : Collections.emptyList();
    }

    /** Slot waypoints for the single mine. */
    public List<double[]> getSlotWaypoints(int slotIndex) {
        Mine mine = getMine();
        return mine != null ? getSlotWaypoints(mine.getId(), slotIndex) : Collections.emptyList();
    }

    /** Main line waypoints for the single mine. */
    public List<double[]> getMainLineWaypoints() {
        Mine mine = getMine();
        return mine != null ? getMainLineWaypoints(mine.getId()) : Collections.emptyList();
    }

    /** Conveyor speed for the single mine. */
    public double getConveyorSpeed() {
        Mine mine = getMine();
        return mine != null ? getConveyorSpeed(mine.getId()) : 2.0;
    }

    /** Whether conveyor is configured for the single mine. */
    public boolean isConveyorConfigured() {
        Mine mine = getMine();
        return mine != null && isConveyorConfigured(mine.getId());
    }

    /** Returns the ID of the single mine, or null if none configured. */
    public String getMineId() {
        Mine mine = getMine();
        return mine != null ? mine.getId() : null;
    }

    /** A specific miner slot for the single mine. */
    public MinerSlot getMinerSlot(int slotIndex) {
        String id = getMineId();
        return id != null ? getMinerSlot(id, slotIndex) : null;
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
        String sql = "SELECT block_type_id, price FROM block_prices";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    blockPrices.put(rs.getString("block_type_id"), rs.getLong("price"));
                }
            }
        }
    }

    public void saveBlockPrice(String blockTypeId, long price) {
        if (blockTypeId == null) return;

        if (price <= 1) {
            blockPrices.remove(blockTypeId);
        } else {
            blockPrices.put(blockTypeId, price);
        }

        if (!DatabaseManager.getInstance().isInitialized()) return;

        if (price <= 1) {
            removeBlockPriceFromDatabase(blockTypeId);
            return;
        }

        String sql = """
            INSERT INTO block_prices (block_type_id, price)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE price = VALUES(price)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, blockTypeId);
                stmt.setLong(2, price);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save block price: " + e.getMessage());
        }
    }

    public void removeBlockPrice(String blockTypeId) {
        if (blockTypeId == null) return;
        blockPrices.remove(blockTypeId);
        removeBlockPriceFromDatabase(blockTypeId);
    }

    private void removeBlockPriceFromDatabase(String blockTypeId) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM block_prices WHERE block_type_id = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, blockTypeId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to remove block price: " + e.getMessage());
        }
    }

    public long getBlockPrice(String blockTypeId) {
        return blockPrices.getOrDefault(blockTypeId, 1L);
    }

    public Map<String, Long> getBlockPrices() {
        return Collections.unmodifiableMap(blockPrices);
    }

    // --- Block HP (global) ---

    private void loadBlockHp(Connection conn) throws SQLException {
        blockHpMap.clear();
        String sql = "SELECT block_type_id, hp FROM block_hp";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    blockHpMap.put(rs.getString("block_type_id"), rs.getInt("hp"));
                }
            }
        }
    }

    public void saveBlockHp(String blockTypeId, int hp) {
        if (blockTypeId == null) return;

        if (hp <= 1) {
            blockHpMap.remove(blockTypeId);
        } else {
            blockHpMap.put(blockTypeId, hp);
        }

        if (!DatabaseManager.getInstance().isInitialized()) return;

        if (hp <= 1) {
            removeBlockHpFromDatabase(blockTypeId);
            return;
        }

        String sql = """
            INSERT INTO block_hp (block_type_id, hp)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE hp = VALUES(hp)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, blockTypeId);
                stmt.setInt(2, hp);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save block hp: " + e.getMessage());
        }
    }

    public void removeBlockHp(String blockTypeId) {
        if (blockTypeId == null) return;
        blockHpMap.remove(blockTypeId);
        removeBlockHpFromDatabase(blockTypeId);
    }

    private void removeBlockHpFromDatabase(String blockTypeId) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM block_hp WHERE block_type_id = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, blockTypeId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to remove block hp: " + e.getMessage());
        }
    }

    public int getBlockHp(String blockTypeId) {
        return blockHpMap.getOrDefault(blockTypeId, 1);
    }

    public Map<String, Integer> getBlockHpMap() {
        return Collections.unmodifiableMap(blockHpMap);
    }

    // --- Miner Slots ---

    private void loadMinerSlots(Connection conn) throws SQLException {
        minerSlots.clear();
        String sql = "SELECT mine_id, slot_index, npc_x, npc_y, npc_z, npc_yaw, block_x, block_y, block_z, " +
            "interval_seconds, conveyor_speed FROM mine_miner_slots ORDER BY mine_id, slot_index";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String mineId = rs.getString("mine_id");
                    int slotIndex = rs.getInt("slot_index");
                    MinerSlot slot = new MinerSlot(mineId, slotIndex);
                    slot.setNpcPosition(rs.getDouble("npc_x"), rs.getDouble("npc_y"), rs.getDouble("npc_z"), rs.getFloat("npc_yaw"));
                    slot.setBlockPosition(rs.getInt("block_x"), rs.getInt("block_y"), rs.getInt("block_z"));
                    slot.setIntervalSeconds(rs.getDouble("interval_seconds"));
                    slot.setConveyorSpeed(rs.getDouble("conveyor_speed"));
                    minerSlots.computeIfAbsent(mineId, k -> new ArrayList<>()).add(slot);
                }
            }
        }
    }

    private void loadConveyorWaypoints(Connection conn) throws SQLException {
        conveyorWaypoints.clear();
        String sql = "SELECT mine_id, slot_index, waypoint_order, x, y, z " +
            "FROM mine_conveyor_waypoints ORDER BY mine_id, slot_index, waypoint_order";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String mineId = rs.getString("mine_id");
                    int slotIndex = rs.getInt("slot_index");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    conveyorWaypoints
                        .computeIfAbsent(mineId, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(slotIndex, k -> new ArrayList<>())
                        .add(new double[]{x, y, z});
                }
            }
        }
    }

    /** Get a specific slot for a mine. */
    public MinerSlot getMinerSlot(String mineId, int slotIndex) {
        List<MinerSlot> slots = minerSlots.get(mineId);
        if (slots == null) return null;
        for (MinerSlot slot : slots) {
            if (slot.getSlotIndex() == slotIndex) return slot;
        }
        return null;
    }

    /** Backward-compat: returns slot 0. */
    public MinerSlot getMinerSlot(String mineId) {
        return getMinerSlot(mineId, 0);
    }

    /** All configured slots for a mine. */
    public List<MinerSlot> getMinerSlots(String mineId) {
        List<MinerSlot> slots = minerSlots.get(mineId);
        return slots != null ? Collections.unmodifiableList(slots) : Collections.emptyList();
    }

    public void saveMinerSlot(MinerSlot slot) {
        if (slot == null || slot.getMineId() == null) return;

        List<MinerSlot> slots = minerSlots.computeIfAbsent(slot.getMineId(), k -> new ArrayList<>());
        slots.removeIf(s -> s.getSlotIndex() == slot.getSlotIndex());
        slots.add(slot);
        slots.sort(Comparator.comparingInt(MinerSlot::getSlotIndex));

        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = """
            INSERT INTO mine_miner_slots (mine_id, slot_index, npc_x, npc_y, npc_z, npc_yaw,
                block_x, block_y, block_z, interval_seconds, conveyor_speed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                npc_x = VALUES(npc_x), npc_y = VALUES(npc_y), npc_z = VALUES(npc_z), npc_yaw = VALUES(npc_yaw),
                block_x = VALUES(block_x), block_y = VALUES(block_y), block_z = VALUES(block_z),
                interval_seconds = VALUES(interval_seconds), conveyor_speed = VALUES(conveyor_speed)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                int i = 1;
                stmt.setString(i++, slot.getMineId());
                stmt.setInt(i++, slot.getSlotIndex());
                stmt.setDouble(i++, slot.getNpcX());
                stmt.setDouble(i++, slot.getNpcY());
                stmt.setDouble(i++, slot.getNpcZ());
                stmt.setFloat(i++, slot.getNpcYaw());
                stmt.setInt(i++, slot.getBlockX());
                stmt.setInt(i++, slot.getBlockY());
                stmt.setInt(i++, slot.getBlockZ());
                stmt.setDouble(i++, slot.getIntervalSeconds());
                stmt.setDouble(i, slot.getConveyorSpeed());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save miner slot: " + e.getMessage());
        }
    }

    // --- Conveyor Waypoints ---

    public List<double[]> getSlotWaypoints(String mineId, int slotIndex) {
        Map<Integer, List<double[]>> mineWps = conveyorWaypoints.get(mineId);
        if (mineWps == null) return Collections.emptyList();
        List<double[]> wps = mineWps.get(slotIndex);
        return wps != null ? Collections.unmodifiableList(wps) : Collections.emptyList();
    }

    public List<double[]> getMainLineWaypoints(String mineId) {
        return getSlotWaypoints(mineId, -1);
    }

    public double getConveyorSpeed(String mineId) {
        MinerSlot slot = getMinerSlot(mineId, 0);
        return slot != null ? slot.getConveyorSpeed() : 2.0;
    }

    /** Conveyor is configured if main line has >= 1 waypoint. */
    public boolean isConveyorConfigured(String mineId) {
        return !getMainLineWaypoints(mineId).isEmpty();
    }

    public void addConveyorWaypoint(String mineId, int slotIndex, double x, double y, double z) {
        List<double[]> wps = conveyorWaypoints
            .computeIfAbsent(mineId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(slotIndex, k -> new ArrayList<>());
        int order = wps.size();
        wps.add(new double[]{x, y, z});

        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = "INSERT INTO mine_conveyor_waypoints (mine_id, slot_index, waypoint_order, x, y, z) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, mineId);
                stmt.setInt(2, slotIndex);
                stmt.setInt(3, order);
                stmt.setDouble(4, x);
                stmt.setDouble(5, y);
                stmt.setDouble(6, z);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to add conveyor waypoint: " + e.getMessage());
        }
    }

    public void clearConveyorWaypoints(String mineId, int slotIndex) {
        Map<Integer, List<double[]>> mineWps = conveyorWaypoints.get(mineId);
        if (mineWps != null) {
            mineWps.remove(slotIndex);
        }

        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = "DELETE FROM mine_conveyor_waypoints WHERE mine_id = ? AND slot_index = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, mineId);
                stmt.setInt(2, slotIndex);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to clear conveyor waypoints: " + e.getMessage());
        }
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
            INSERT INTO mine_zone_layers (id, zone_id, min_y, max_y, block_table_json)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                min_y = VALUES(min_y), max_y = VALUES(max_y),
                block_table_json = VALUES(block_table_json)
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
                stmt.setString(i, GSON.toJson(layer.getBlockTable()));
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

    // --- Tier Recipes ---

    private void loadTierRecipes(Connection conn) throws SQLException {
        tierRecipes.clear();
        String sql = "SELECT tier, block_type_id, amount FROM pickaxe_tier_recipes";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int tier = rs.getInt("tier");
                    String blockTypeId = rs.getString("block_type_id");
                    int amount = rs.getInt("amount");
                    tierRecipes.computeIfAbsent(tier, k -> new ConcurrentHashMap<>())
                        .put(blockTypeId, amount);
                }
            }
        }
    }

    public void saveTierRecipe(int targetTier, String blockTypeId, int amount) {
        if (targetTier < 1 || blockTypeId == null || amount <= 0) return;

        tierRecipes.computeIfAbsent(targetTier, k -> new ConcurrentHashMap<>())
            .put(blockTypeId, amount);

        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = """
            INSERT INTO pickaxe_tier_recipes (tier, block_type_id, amount)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE amount = VALUES(amount)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setInt(1, targetTier);
                stmt.setString(2, blockTypeId);
                stmt.setInt(3, amount);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save tier recipe: " + e.getMessage());
        }
    }

    public void removeTierRecipe(int targetTier, String blockTypeId) {
        if (blockTypeId == null) return;

        Map<String, Integer> recipe = tierRecipes.get(targetTier);
        if (recipe != null) {
            recipe.remove(blockTypeId);
            if (recipe.isEmpty()) tierRecipes.remove(targetTier);
        }

        if (!DatabaseManager.getInstance().isInitialized()) return;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM pickaxe_tier_recipes WHERE tier = ? AND block_type_id = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setInt(1, targetTier);
                stmt.setString(2, blockTypeId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to remove tier recipe: " + e.getMessage());
        }
    }

    public Map<String, Integer> getTierRecipe(int targetTier) {
        Map<String, Integer> recipe = tierRecipes.get(targetTier);
        return recipe != null ? Collections.unmodifiableMap(recipe) : Collections.emptyMap();
    }

    // --- Enhancement Costs ---

    private void loadEnhanceCosts(Connection conn) throws SQLException {
        enhanceCosts.clear();
        String sql = "SELECT tier, level, crystal_cost FROM pickaxe_enhance_costs";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int tier = rs.getInt("tier");
                    int level = rs.getInt("level");
                    long cost = rs.getLong("crystal_cost");
                    enhanceCosts.computeIfAbsent(tier, k -> new ConcurrentHashMap<>())
                        .put(level, cost);
                }
            }
        }
    }

    public void saveEnhanceCost(int tier, int level, long cost) {
        if (tier < 0 || level < 1 || level > PickaxeTier.MAX_ENHANCEMENT) return;

        enhanceCosts.computeIfAbsent(tier, k -> new ConcurrentHashMap<>())
            .put(level, cost);

        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = """
            INSERT INTO pickaxe_enhance_costs (tier, level, crystal_cost)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE crystal_cost = VALUES(crystal_cost)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setInt(1, tier);
                stmt.setInt(2, level);
                stmt.setLong(3, cost);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save enhance cost: " + e.getMessage());
        }
    }

    public void removeEnhanceCost(int tier, int level) {
        Map<Integer, Long> costs = enhanceCosts.get(tier);
        if (costs != null) {
            costs.remove(level);
            if (costs.isEmpty()) enhanceCosts.remove(tier);
        }

        if (!DatabaseManager.getInstance().isInitialized()) return;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM pickaxe_enhance_costs WHERE tier = ? AND level = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setInt(1, tier);
                stmt.setInt(2, level);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to remove enhance cost: " + e.getMessage());
        }
    }

    public long getEnhanceCost(int tier, int level) {
        Map<Integer, Long> costs = enhanceCosts.get(tier);
        return costs != null ? costs.getOrDefault(level, 0L) : 0L;
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
