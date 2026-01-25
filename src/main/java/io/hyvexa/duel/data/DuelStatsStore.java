package io.hyvexa.duel.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.parkour.data.DatabaseManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class DuelStatsStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS duel_player_stats (
            player_uuid VARCHAR(36) PRIMARY KEY,
            player_name VARCHAR(64),
            wins INT DEFAULT 0,
            losses INT DEFAULT 0,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        )
        """;

    private final ConcurrentHashMap<UUID, DuelStats> cache = new ConcurrentHashMap<>();

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, DuelStatsStore will be empty");
            return;
        }
        ensureTable();
        String sql = "SELECT player_uuid, player_name, wins, losses FROM duel_player_stats";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                String playerName = rs.getString("player_name");
                int wins = rs.getInt("wins");
                int losses = rs.getInt("losses");
                cache.put(playerId, new DuelStats(playerId, playerName, wins, losses));
            }
            LOGGER.atInfo().log("DuelStatsStore loaded " + cache.size() + " player stats");
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load DuelStatsStore: " + e.getMessage());
        }
    }

    private void ensureTable() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE_SQL)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to create duel_player_stats table: " + e.getMessage());
        }
    }

    @Nullable
    public DuelStats getStats(@Nonnull UUID playerId) {
        return cache.get(playerId);
    }

    @Nullable
    public DuelStats getStatsByName(@Nonnull String playerName) {
        String target = playerName.toLowerCase();
        for (DuelStats stats : cache.values()) {
            if (stats.getPlayerName() != null && stats.getPlayerName().toLowerCase().equals(target)) {
                return stats;
            }
        }
        return null;
    }

    @Nonnull
    public java.util.List<DuelStats> listStats() {
        return new java.util.ArrayList<>(cache.values());
    }

    public void recordWin(@Nonnull UUID playerId, @Nonnull String playerName) {
        DuelStats stats = cache.computeIfAbsent(playerId, id -> new DuelStats(id, playerName, 0, 0));
        stats.setPlayerName(playerName);
        stats.incrementWins();
        saveToDatabase(stats);
    }

    public void recordLoss(@Nonnull UUID playerId, @Nonnull String playerName) {
        DuelStats stats = cache.computeIfAbsent(playerId, id -> new DuelStats(id, playerName, 0, 0));
        stats.setPlayerName(playerName);
        stats.incrementLosses();
        saveToDatabase(stats);
    }

    private void saveToDatabase(@Nonnull DuelStats stats) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = """
            INSERT INTO duel_player_stats (player_uuid, player_name, wins, losses, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name = VALUES(player_name), wins = VALUES(wins),
                losses = VALUES(losses), updated_at = VALUES(updated_at)
            """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, stats.getPlayerId().toString());
            stmt.setString(2, stats.getPlayerName());
            stmt.setInt(3, stats.getWins());
            stmt.setInt(4, stats.getLosses());
            stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save duel stats: " + e.getMessage());
        }
    }
}
