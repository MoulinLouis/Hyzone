package io.hyvexa.ascend.mine.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class MinerConfigStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConnectionProvider db;
    private final Supplier<String> defaultMineId;

    // mineId -> [slot0, slot1, ...] ordered by slotIndex
    private final Map<String, List<MinerSlot>> minerSlots = new ConcurrentHashMap<>();

    // layerId -> { MinerRarity -> MinerDefinition } (admin-configured miner names/portraits)
    private final Map<String, Map<MinerRarity, MinerDefinition>> minerDefs = new ConcurrentHashMap<>();

    public MinerConfigStore(ConnectionProvider db, Supplier<String> defaultMineId) {
        this.db = db;
        this.defaultMineId = defaultMineId;
    }

    public void syncLoad(Connection conn) throws SQLException {
        loadMinerSlots(conn);
        loadMinerDefinitions(conn);
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

        if (!this.db.isInitialized()) return;

        String sql = """
            INSERT INTO mine_miner_slots (mine_id, slot_index, npc_x, npc_y, npc_z, npc_yaw,
                block_x, block_y, block_z, interval_seconds, conveyor_speed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                npc_x = VALUES(npc_x), npc_y = VALUES(npc_y), npc_z = VALUES(npc_z), npc_yaw = VALUES(npc_yaw),
                block_x = VALUES(block_x), block_y = VALUES(block_y), block_z = VALUES(block_z),
                interval_seconds = VALUES(interval_seconds), conveyor_speed = VALUES(conveyor_speed)
            """;

        DatabaseManager.execute(this.db, sql, stmt -> {
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
        });
    }

    // --- Single-mine convenience ---

    public List<MinerSlot> getMinerSlots() {
        String id = defaultMineId.get();
        return id != null ? getMinerSlots(id) : Collections.emptyList();
    }

    public MinerSlot getMinerSlot(int slotIndex) {
        String id = defaultMineId.get();
        return id != null ? getMinerSlot(id, slotIndex) : null;
    }

    // --- Miner Definitions ---

    private void loadMinerDefinitions(Connection conn) throws SQLException {
        minerDefs.clear();
        String sql = "SELECT layer_id, rarity, display_name, portrait_id FROM mine_layer_miner_defs";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String layerId = rs.getString("layer_id");
                    MinerRarity rarity = MinerRarity.fromName(rs.getString("rarity"));
                    if (rarity == null) continue;
                    MinerDefinition def = new MinerDefinition(
                        layerId, rarity,
                        rs.getString("display_name"),
                        rs.getString("portrait_id")
                    );
                    minerDefs.computeIfAbsent(layerId, k -> new ConcurrentHashMap<>())
                        .put(rarity, def);
                }
            }
        }
    }

    public MinerDefinition getMinerDefinition(String layerId, MinerRarity rarity) {
        if (layerId == null || rarity == null) return null;
        Map<MinerRarity, MinerDefinition> defs = minerDefs.get(layerId);
        return defs != null ? defs.get(rarity) : null;
    }

    public Map<MinerRarity, MinerDefinition> getMinerDefinitions(String layerId) {
        if (layerId == null) return Collections.emptyMap();
        Map<MinerRarity, MinerDefinition> defs = minerDefs.get(layerId);
        return defs != null ? Collections.unmodifiableMap(defs) : Collections.emptyMap();
    }

    public void saveMinerDefinition(MinerDefinition def) {
        if (def == null || def.layerId() == null || def.rarity() == null) return;

        minerDefs.computeIfAbsent(def.layerId(), k -> new ConcurrentHashMap<>())
            .put(def.rarity(), def);

        if (!this.db.isInitialized()) return;

        String sql = """
            INSERT INTO mine_layer_miner_defs (layer_id, rarity, display_name, portrait_id)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                display_name = VALUES(display_name), portrait_id = VALUES(portrait_id)
            """;

        DatabaseManager.execute(this.db, sql, stmt -> {
            stmt.setString(1, def.layerId());
            stmt.setString(2, def.rarity().name());
            stmt.setString(3, def.displayName());
            stmt.setString(4, def.portraitId());
        });
    }
}
