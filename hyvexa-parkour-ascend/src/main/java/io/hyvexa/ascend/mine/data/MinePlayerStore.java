package io.hyvexa.ascend.mine.data;

import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

            // Load player crystals
            MinePlayerProgress progress = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT crystals_mantissa, crystals_exp10 FROM mine_players WHERE uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        progress = new MinePlayerProgress(playerId);
                        double mantissa = rs.getDouble("crystals_mantissa");
                        int exp10 = rs.getInt("crystals_exp10");
                        progress.setCrystals(BigNumber.of(mantissa, exp10));
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

            // Save crystals
            BigNumber crystals = progress.getCrystals();
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO mine_players (uuid, crystals_mantissa, crystals_exp10)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE crystals_mantissa = VALUES(crystals_mantissa),
                                            crystals_exp10 = VALUES(crystals_exp10)
                    """)) {
                ps.setString(1, playerId.toString());
                ps.setDouble(2, crystals.getMantissa());
                ps.setInt(3, crystals.getExponent());
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
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save mine player %s: %s", playerId, e.getMessage());
        }
    }
}
