package io.hyvexa.runorfall.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.runorfall.data.RunOrFallPlayerStats;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RunOrFallStatsStore {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String UNKNOWN_NAME = "Unknown";
    private static final int MAX_NAME_LENGTH = 32;
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS runorfall_player_stats (
              player_uuid CHAR(36) NOT NULL PRIMARY KEY,
              player_name VARCHAR(32) NOT NULL,
              wins INT NOT NULL DEFAULT 0,
              losses INT NOT NULL DEFAULT 0,
              current_win_streak INT NOT NULL DEFAULT 0,
              best_win_streak INT NOT NULL DEFAULT 0,
              longest_survived_ms BIGINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB
            """;
    private static final String LOAD_SQL = """
            SELECT player_uuid, player_name, wins, losses, current_win_streak, best_win_streak, longest_survived_ms
            FROM runorfall_player_stats
            """;
    private static final String UPSERT_SQL = """
            INSERT INTO runorfall_player_stats (
                player_uuid, player_name, wins, losses, current_win_streak, best_win_streak, longest_survived_ms
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              player_name = VALUES(player_name),
              wins = VALUES(wins),
              losses = VALUES(losses),
              current_win_streak = VALUES(current_win_streak),
              best_win_streak = VALUES(best_win_streak),
              longest_survived_ms = VALUES(longest_survived_ms)
            """;

    private final Map<UUID, RunOrFallPlayerStats> cache = new ConcurrentHashMap<>();

    public RunOrFallStatsStore() {
        ensureTable();
        syncLoad();
    }

    public synchronized void syncLoad() {
        cache.clear();
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, RunOrFallStatsStore will stay in-memory only.");
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(LOAD_SQL)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID playerId;
                    try {
                        playerId = UUID.fromString(rs.getString("player_uuid"));
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    RunOrFallPlayerStats stats = new RunOrFallPlayerStats(
                            playerId, sanitizePlayerName(rs.getString("player_name")));
                    stats.setWins(rs.getInt("wins"));
                    stats.setLosses(rs.getInt("losses"));
                    stats.setCurrentWinStreak(rs.getInt("current_win_streak"));
                    stats.setBestWinStreak(rs.getInt("best_win_streak"));
                    stats.setLongestSurvivedMs(rs.getLong("longest_survived_ms"));
                    cache.put(playerId, stats);
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed loading RunOrFall player stats.");
        }
    }

    public synchronized RunOrFallPlayerStats getStats(@Nonnull UUID playerId, String playerName) {
        RunOrFallPlayerStats stats = cache.get(playerId);
        if (stats == null) {
            return new RunOrFallPlayerStats(playerId, sanitizePlayerName(playerName));
        }
        if (playerName != null && !playerName.isBlank()) {
            stats.setPlayerName(sanitizePlayerName(playerName));
        }
        return stats.copy();
    }

    public synchronized void recordWin(@Nonnull UUID playerId, String playerName, long survivedMs) {
        RunOrFallPlayerStats stats = cache.computeIfAbsent(playerId,
                ignored -> new RunOrFallPlayerStats(playerId, sanitizePlayerName(playerName)));
        stats.setPlayerName(sanitizePlayerName(playerName));
        stats.applyWin(Math.max(0L, survivedMs));
        save(stats);
    }

    public synchronized void recordLoss(@Nonnull UUID playerId, String playerName, long survivedMs) {
        RunOrFallPlayerStats stats = cache.computeIfAbsent(playerId,
                ignored -> new RunOrFallPlayerStats(playerId, sanitizePlayerName(playerName)));
        stats.setPlayerName(sanitizePlayerName(playerName));
        stats.applyLoss(Math.max(0L, survivedMs));
        save(stats);
    }

    private void ensureTable() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(CREATE_TABLE_SQL);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed creating runorfall_player_stats table.");
        }
    }

    private void save(RunOrFallPlayerStats stats) {
        if (stats == null || !DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, stats.getPlayerId().toString());
            stmt.setString(2, sanitizePlayerName(stats.getPlayerName()));
            stmt.setInt(3, stats.getWins());
            stmt.setInt(4, stats.getLosses());
            stmt.setInt(5, stats.getCurrentWinStreak());
            stmt.setInt(6, stats.getBestWinStreak());
            stmt.setLong(7, stats.getLongestSurvivedMs());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed saving RunOrFall stats for " + stats.getPlayerId() + ".");
        }
    }

    private static String sanitizePlayerName(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN_NAME;
        }
        String sanitized = value.trim();
        if (sanitized.length() > MAX_NAME_LENGTH) {
            return sanitized.substring(0, MAX_NAME_LENGTH);
        }
        return sanitized;
    }
}
