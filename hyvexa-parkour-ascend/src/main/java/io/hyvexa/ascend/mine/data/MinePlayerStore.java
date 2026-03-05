package io.hyvexa.ascend.mine.data;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MinePlayerStore {
    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();

    private final Map<UUID, MinePlayerProgress> players = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dirtyVersions = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> pendingSave;

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
        players.put(playerId, progress);

        // Auto-unlock first mine for all players
        List<Mine> mines = ParkourAscendPlugin.getInstance().getMineConfigStore().listMinesSorted();
        if (!mines.isEmpty()) {
            MinePlayerProgress.MineProgress firstMineState = progress.getMineState(mines.get(0).getId());
            if (!firstMineState.isUnlocked()) {
                firstMineState.setUnlocked(true);
                markDirty(playerId);
            }
        }

        return progress;
    }

    public void markDirty(UUID playerId) {
        dirtyVersions.compute(playerId, (k, v) -> v == null ? 1L : v + 1L);
        queueSave();
    }

    public void evict(UUID playerId) {
        savePlayerSync(playerId);
        players.remove(playerId);
        dirtyVersions.remove(playerId);
    }

    public void flushAll() {
        for (UUID playerId : dirtyVersions.keySet()) {
            savePlayerSync(playerId);
        }
        dirtyVersions.clear();
    }

    private void queueSave() {
        if (pendingSave != null && !pendingSave.isDone()) return;
        pendingSave = HytaleServer.SCHEDULED_EXECUTOR.schedule(
            this::flushAll, 5, TimeUnit.SECONDS
        );
    }

    private MinePlayerProgress loadFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) return null;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return null;

            // Load player crystals + upgrades
            MinePlayerProgress progress = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT crystals, mining_speed_level, bag_capacity_level, multi_break_level, auto_sell_level FROM mine_players WHERE uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        progress = new MinePlayerProgress(playerId);
                        progress.setCrystals(rs.getLong("crystals"));
                        progress.setUpgradeLevel(MineUpgradeType.MINING_SPEED, rs.getInt("mining_speed_level"));
                        progress.setUpgradeLevel(MineUpgradeType.BAG_CAPACITY, rs.getInt("bag_capacity_level"));
                        progress.setUpgradeLevel(MineUpgradeType.MULTI_BREAK, rs.getInt("multi_break_level"));
                        progress.setUpgradeLevel(MineUpgradeType.AUTO_SELL, rs.getInt("auto_sell_level"));
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
                        progress.getInventory().put(
                            rs.getString("block_type_id"),
                            rs.getInt("amount")
                        );
                    }
                }
            }

            // Load mine states
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT mine_id, unlocked, completed_manually FROM mine_player_mines WHERE player_uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        MinePlayerProgress.MineProgress ms = progress.getMineState(rs.getString("mine_id"));
                        ms.setUnlocked(rs.getBoolean("unlocked"));
                        ms.setCompletedManually(rs.getBoolean("completed_manually"));
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
        if (!DatabaseManager.getInstance().isInitialized()) return;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
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

    private void savePlayerSync(UUID playerId) {
        MinePlayerProgress progress = players.get(playerId);
        if (progress == null) return;
        if (!DatabaseManager.getInstance().isInitialized()) return;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) return;

            // Save crystals + upgrades
            long crystals = progress.getCrystals();
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO mine_players (uuid, crystals,
                        mining_speed_level, bag_capacity_level, multi_break_level, auto_sell_level)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE crystals = VALUES(crystals),
                                            mining_speed_level = VALUES(mining_speed_level),
                                            bag_capacity_level = VALUES(bag_capacity_level),
                                            multi_break_level = VALUES(multi_break_level),
                                            auto_sell_level = VALUES(auto_sell_level)
                    """)) {
                ps.setString(1, playerId.toString());
                ps.setLong(2, crystals);
                ps.setInt(3, progress.getUpgradeLevel(MineUpgradeType.MINING_SPEED));
                ps.setInt(4, progress.getUpgradeLevel(MineUpgradeType.BAG_CAPACITY));
                ps.setInt(5, progress.getUpgradeLevel(MineUpgradeType.MULTI_BREAK));
                ps.setInt(6, progress.getUpgradeLevel(MineUpgradeType.AUTO_SELL));
                ps.executeUpdate();
            }

            // Save inventory — delete + re-insert
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM mine_player_inventory WHERE player_uuid = ?")) {
                ps.setString(1, playerId.toString());
                ps.executeUpdate();
            }

            Map<String, Integer> inventory = progress.getInventory();
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

            // Save mine states
            Map<String, MinePlayerProgress.MineProgress> mineStates = progress.getMineStates();
            if (!mineStates.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO mine_player_mines (player_uuid, mine_id, unlocked, completed_manually)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE unlocked = VALUES(unlocked),
                                                completed_manually = VALUES(completed_manually)
                        """)) {
                    for (var entry : mineStates.entrySet()) {
                        ps.setString(1, playerId.toString());
                        ps.setString(2, entry.getKey());
                        ps.setBoolean(3, entry.getValue().isUnlocked());
                        ps.setBoolean(4, entry.getValue().isCompletedManually());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save mine player %s: %s", playerId, e.getMessage());
        }
    }
}
