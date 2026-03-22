package io.hyvexa.ascend.mine.data;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MinePlayerStore {
    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final ConnectionProvider db;
    private final Map<UUID, MinePlayerProgress> players = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dirtyVersions = new ConcurrentHashMap<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

    public MinePlayerStore() {
        this(DatabaseManager.getInstance());
    }

    public MinePlayerStore(ConnectionProvider db) {
        this.db = db;
    }

    public MinePlayerProgress getPlayer(UUID playerId) {
        return players.get(playerId);
    }

    public MinePlayerProgress getOrCreatePlayer(UUID playerId) {
        MinePlayerProgress progress = players.get(playerId);
        if (progress != null) return progress;
        progress = loadFromDatabase(playerId);
        if (progress == null) {
            progress = new MinePlayerProgress(playerId);
            ensurePlayerRow(playerId);
        }
        MinePlayerProgress existing = players.putIfAbsent(playerId, progress);
        return existing != null ? existing : progress;
    }

    public void markDirty(UUID playerId) {
        dirtyVersions.compute(playerId, (k, v) -> v == null ? 1L : v + 1L);
        queueSave();
    }

    public void evict(UUID playerId) {
        if (!flushPlayer(playerId)) {
            LOGGER.atSevere().log("Skipping mine player eviction for %s because state is still dirty after save", playerId);
            return;
        }
        players.remove(playerId);
        dirtyVersions.remove(playerId);
    }

    public void flushAll() {
        for (UUID playerId : new ArrayList<>(dirtyVersions.keySet())) {
            flushPlayer(playerId);
        }
    }

    private void queueSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                saveScheduled.set(false);
                flushAll();
            }, 5, TimeUnit.SECONDS);
        }
    }

    private MinePlayerProgress loadFromDatabase(UUID playerId) {
        if (!this.db.isInitialized()) return null;
        try (Connection conn = this.db.getConnection()) {
            if (conn == null) return null;

            // Load player crystals + upgrades
            MinePlayerProgress progress = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT crystals, bag_capacity_level, upgrade_momentum, upgrade_fortune, " +
                    "upgrade_jackhammer, upgrade_stomp, upgrade_blast, upgrade_haste, " +
                    "upgrade_conveyor_capacity, upgrade_cashback, " +
                    "in_mine, pickaxe_tier, pickaxe_enhancement FROM mine_players WHERE uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        progress = new MinePlayerProgress(playerId);
                        progress.setCrystals(rs.getDouble("crystals"));
                        progress.setUpgradeLevel(MineUpgradeType.BAG_CAPACITY, rs.getInt("bag_capacity_level"));
                        progress.setUpgradeLevel(MineUpgradeType.MOMENTUM, rs.getInt("upgrade_momentum"));
                        progress.setUpgradeLevel(MineUpgradeType.FORTUNE, rs.getInt("upgrade_fortune"));
                        progress.setUpgradeLevel(MineUpgradeType.JACKHAMMER, rs.getInt("upgrade_jackhammer"));
                        progress.setUpgradeLevel(MineUpgradeType.STOMP, rs.getInt("upgrade_stomp"));
                        progress.setUpgradeLevel(MineUpgradeType.BLAST, rs.getInt("upgrade_blast"));
                        progress.setUpgradeLevel(MineUpgradeType.HASTE, rs.getInt("upgrade_haste"));
                        progress.setUpgradeLevel(MineUpgradeType.CONVEYOR_CAPACITY, rs.getInt("upgrade_conveyor_capacity"));
                        progress.setUpgradeLevel(MineUpgradeType.CASHBACK, rs.getInt("upgrade_cashback"));
                        progress.setInMine(rs.getBoolean("in_mine"));
                        progress.setPickaxeTier(rs.getInt("pickaxe_tier"));
                        progress.setPickaxeEnhancement(rs.getInt("pickaxe_enhancement"));
                    }
                }
            }

            if (progress == null) return null;

            // Load inventory
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT block_type_id, amount FROM mine_player_inventory WHERE player_uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        progress.loadInventoryItem(
                            rs.getString("block_type_id"),
                            rs.getInt("amount"));
                    }
                }
            }

            // Load egg inventory
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT layer_id, count FROM mine_player_eggs WHERE player_uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        progress.loadEgg(rs.getString("layer_id"), rs.getInt("count"));
                    }
                }
            }

            // Load miner collection
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, layer_id, rarity, speed_level FROM mine_player_miners_v2 WHERE player_uuid = ? ORDER BY id")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        MinerRarity rarity = MinerRarity.fromName(rs.getString("rarity"));
                        if (rarity == null) rarity = MinerRarity.COMMON;
                        CollectedMiner miner = new CollectedMiner(
                            rs.getLong("id"),
                            rs.getString("layer_id"),
                            rarity,
                            rs.getInt("speed_level")
                        );
                        progress.addMiner(miner);
                    }
                }
            }

            // Load slot assignments
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT slot_index, miner_id FROM mine_player_slot_assignments WHERE player_uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        progress.assignMinerToSlot(rs.getInt("slot_index"), rs.getLong("miner_id"));
                    }
                }
            }

            // Load conveyor buffer
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT block_type_id, amount FROM mine_player_conveyor_buffer WHERE player_uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        progress.loadConveyorBufferItem(
                            rs.getString("block_type_id"),
                            rs.getInt("amount"));
                    }
                }
            }

            return progress;
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to load mine player %s: %s", playerId, e.getMessage());
            return null;
        }
    }

    private void ensurePlayerRow(UUID playerId) {
        if (!this.db.isInitialized()) return;
        try (Connection conn = this.db.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO mine_players (uuid) VALUES (?)")) {
                ps.setString(1, playerId.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to ensure mine player row %s: %s", playerId, e.getMessage());
        }
    }

    private boolean flushPlayer(UUID playerId) {
        Long dirtyVersion = dirtyVersions.get(playerId);
        if (dirtyVersion == null) {
            return true;
        }
        if (!savePlayerSync(playerId)) {
            return false;
        }
        return dirtyVersions.remove(playerId, dirtyVersion);
    }

    /**
     * Insert a new miner into the database and return its auto-generated ID.
     * Called immediately when opening an egg (not batched).
     */
    public long insertMiner(UUID playerId, CollectedMiner miner) {
        if (!this.db.isInitialized()) return -1;
        try (Connection conn = this.db.getConnection()) {
            if (conn == null) return -1;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO mine_player_miners_v2 (player_uuid, layer_id, rarity, speed_level) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, playerId.toString());
                ps.setString(2, miner.getLayerId());
                ps.setString(3, miner.getRarity().name());
                ps.setInt(4, miner.getSpeedLevel());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to insert miner for %s: %s", playerId, e.getMessage());
        }
        return -1;
    }

    private boolean savePlayerSync(UUID playerId) {
        MinePlayerProgress progress = players.get(playerId);
        if (progress == null) return true;
        if (!this.db.isInitialized()) return true;

        MinePlayerProgress.PlayerSaveSnapshot snapshot = progress.createSaveSnapshot();

        try (Connection conn = this.db.getConnection()) {
            if (conn == null) return false;

            conn.setAutoCommit(false);
            try {
                // Save crystals + upgrades
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO mine_players (uuid, crystals,
                            bag_capacity_level, upgrade_momentum, upgrade_fortune,
                            upgrade_jackhammer, upgrade_stomp, upgrade_blast, upgrade_haste,
                            upgrade_conveyor_capacity, upgrade_cashback,
                            in_mine, pickaxe_tier, pickaxe_enhancement)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE crystals = VALUES(crystals),
                                                bag_capacity_level = VALUES(bag_capacity_level),
                                                upgrade_momentum = VALUES(upgrade_momentum),
                                                upgrade_fortune = VALUES(upgrade_fortune),
                                                upgrade_jackhammer = VALUES(upgrade_jackhammer),
                                                upgrade_stomp = VALUES(upgrade_stomp),
                                                upgrade_blast = VALUES(upgrade_blast),
                                                upgrade_haste = VALUES(upgrade_haste),
                                                upgrade_conveyor_capacity = VALUES(upgrade_conveyor_capacity),
                                                upgrade_cashback = VALUES(upgrade_cashback),
                                                in_mine = VALUES(in_mine),
                                                pickaxe_tier = VALUES(pickaxe_tier),
                                                pickaxe_enhancement = VALUES(pickaxe_enhancement)
                        """)) {
                    ps.setString(1, playerId.toString());
                    ps.setDouble(2, snapshot.crystals());
                    ps.setInt(3, snapshot.upgradeLevels().getOrDefault(MineUpgradeType.BAG_CAPACITY, 0));
                    ps.setInt(4, snapshot.upgradeLevels().getOrDefault(MineUpgradeType.MOMENTUM, 0));
                    ps.setInt(5, snapshot.upgradeLevels().getOrDefault(MineUpgradeType.FORTUNE, 0));
                    ps.setInt(6, snapshot.upgradeLevels().getOrDefault(MineUpgradeType.JACKHAMMER, 0));
                    ps.setInt(7, snapshot.upgradeLevels().getOrDefault(MineUpgradeType.STOMP, 0));
                    ps.setInt(8, snapshot.upgradeLevels().getOrDefault(MineUpgradeType.BLAST, 0));
                    ps.setInt(9, snapshot.upgradeLevels().getOrDefault(MineUpgradeType.HASTE, 0));
                    ps.setInt(10, snapshot.upgradeLevels().getOrDefault(MineUpgradeType.CONVEYOR_CAPACITY, 0));
                    ps.setInt(11, snapshot.upgradeLevels().getOrDefault(MineUpgradeType.CASHBACK, 0));
                    ps.setBoolean(12, snapshot.inMine());
                    ps.setInt(13, snapshot.pickaxeTier());
                    ps.setInt(14, snapshot.pickaxeEnhancement());
                    ps.executeUpdate();
                }

                // Save inventory — delete + re-insert
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM mine_player_inventory WHERE player_uuid = ?")) {
                    ps.setString(1, playerId.toString());
                    ps.executeUpdate();
                }

                Map<String, Integer> inventory = snapshot.inventory();
                if (!inventory.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO mine_player_inventory (player_uuid, block_type_id, amount) VALUES (?, ?, ?)")) {
                        for (var entry : inventory.entrySet()) {
                            ps.setString(1, playerId.toString());
                            ps.setString(2, entry.getKey());
                            ps.setInt(3, entry.getValue());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                // Save conveyor buffer — delete + re-insert
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM mine_player_conveyor_buffer WHERE player_uuid = ?")) {
                    ps.setString(1, playerId.toString());
                    ps.executeUpdate();
                }

                Map<String, Integer> conveyorBuffer = snapshot.conveyorBuffer();
                if (!conveyorBuffer.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO mine_player_conveyor_buffer (player_uuid, block_type_id, amount) VALUES (?, ?, ?)")) {
                        for (var entry : conveyorBuffer.entrySet()) {
                            ps.setString(1, playerId.toString());
                            ps.setString(2, entry.getKey());
                            ps.setInt(3, entry.getValue());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                // Save egg inventory — delete + re-insert
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM mine_player_eggs WHERE player_uuid = ?")) {
                    ps.setString(1, playerId.toString());
                    ps.executeUpdate();
                }

                Map<String, Integer> eggs = snapshot.eggInventory();
                if (!eggs.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO mine_player_eggs (player_uuid, layer_id, count) VALUES (?, ?, ?)")) {
                        for (var entry : eggs.entrySet()) {
                            ps.setString(1, playerId.toString());
                            ps.setString(2, entry.getKey());
                            ps.setInt(3, entry.getValue());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                // Save miner speed levels (INSERTs happen at egg-open time via insertMiner)
                List<CollectedMiner> miners = snapshot.minerCollection();
                if (!miners.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE mine_player_miners_v2 SET speed_level = ? WHERE id = ?")) {
                        for (CollectedMiner m : miners) {
                            ps.setInt(1, m.getSpeedLevel());
                            ps.setLong(2, m.getId());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                // Save slot assignments — delete + re-insert
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM mine_player_slot_assignments WHERE player_uuid = ?")) {
                    ps.setString(1, playerId.toString());
                    ps.executeUpdate();
                }

                Map<Integer, Long> slots = snapshot.slotAssignments();
                if (!slots.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO mine_player_slot_assignments (player_uuid, slot_index, miner_id) VALUES (?, ?, ?)")) {
                        for (var entry : slots.entrySet()) {
                            ps.setString(1, playerId.toString());
                            ps.setInt(2, entry.getKey());
                            ps.setLong(3, entry.getValue());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            return true;
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save mine player %s: %s", playerId, e.getMessage());
            return false;
        }
    }
}
