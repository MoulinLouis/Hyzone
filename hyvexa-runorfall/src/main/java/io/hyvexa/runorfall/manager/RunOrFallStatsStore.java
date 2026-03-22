package io.hyvexa.runorfall.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.BasePlayerStore;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.runorfall.data.RunOrFallPlayerStats;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

public class RunOrFallStatsStore extends BasePlayerStore<RunOrFallPlayerStats> {

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
              longest_survived_ms BIGINT NOT NULL DEFAULT 0,
              total_blocks_broken BIGINT NOT NULL DEFAULT 0,
              total_blinks_used BIGINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB
            """;
    private static final String LOAD_ALL_SQL = """
            SELECT player_uuid, player_name, wins, losses, current_win_streak, best_win_streak, longest_survived_ms,
                   total_blocks_broken, total_blinks_used
            FROM runorfall_player_stats
            """;

    public RunOrFallStatsStore() {
        this(DatabaseManager.getInstance());
    }

    public RunOrFallStatsStore(ConnectionProvider connectionProvider) {
        super(connectionProvider);
        ensureTable();
        syncLoad();
    }

    public void syncLoad() {
        clearCache();
        ConnectionProvider connectionProvider = getConnectionProvider();
        if (!connectionProvider.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, RunOrFallStatsStore will stay in-memory only.");
            return;
        }
        loadAll(LOAD_ALL_SQL, rs -> {
            try {
                return UUID.fromString(rs.getString("player_uuid"));
            } catch (Exception e) {
                return null;
            }
        });
        LOGGER.atInfo().log("RunOrFall stats loaded via syncLoad");
    }

    public RunOrFallPlayerStats getOrLoadWithName(@Nonnull UUID playerId, String playerName) {
        RunOrFallPlayerStats stats = getCached(playerId);
        if (stats == null) {
            return new RunOrFallPlayerStats(playerId, sanitizePlayerName(playerName));
        }
        RunOrFallPlayerStats copy = stats.copy();
        if (playerName != null && !playerName.isBlank()) {
            copy.setPlayerName(sanitizePlayerName(playerName));
        }
        return copy;
    }

    public void recordWin(@Nonnull UUID playerId, String playerName, long survivedMs) {
        recordWin(playerId, playerName, survivedMs, 0, 0);
    }

    public void recordWin(@Nonnull UUID playerId, String playerName, long survivedMs,
                           int blocksBroken, int blinksUsed) {
        recordResult(playerId, playerName, survivedMs, blocksBroken, blinksUsed,
                (stats, ms) -> stats.applyWin(ms));
    }

    public void recordLoss(@Nonnull UUID playerId, String playerName, long survivedMs) {
        recordLoss(playerId, playerName, survivedMs, 0, 0);
    }

    public void recordLoss(@Nonnull UUID playerId, String playerName, long survivedMs,
                            int blocksBroken, int blinksUsed) {
        recordResult(playerId, playerName, survivedMs, blocksBroken, blinksUsed,
                (stats, ms) -> stats.applyLoss(ms));
    }

    public List<RunOrFallPlayerStats> listStats() {
        return cacheValues().stream()
                .map(RunOrFallPlayerStats::copy)
                .toList();
    }

    private void recordResult(@Nonnull UUID playerId, String playerName, long survivedMs,
                               int blocksBroken, int blinksUsed,
                               java.util.function.BiConsumer<RunOrFallPlayerStats, Long> mutation) {
        RunOrFallPlayerStats current = getCached(playerId);
        if (current == null) {
            current = new RunOrFallPlayerStats(playerId, sanitizePlayerName(playerName));
        }
        RunOrFallPlayerStats next = current.copy();
        next.setPlayerName(sanitizePlayerName(playerName));
        mutation.accept(next, Math.max(0L, survivedMs));
        next.addBlocksBroken(Math.max(0, blocksBroken));
        next.addBlinksUsed(Math.max(0, blinksUsed));
        save(playerId, next);
    }

    private void ensureTable() {
        ConnectionProvider connectionProvider = getConnectionProvider();
        if (!connectionProvider.isInitialized()) {
            return;
        }
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(CREATE_TABLE_SQL);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed creating runorfall_player_stats table.");
        }
    }

    @Override
    protected String loadSql() {
        return """
            SELECT player_uuid, player_name, wins, losses, current_win_streak, best_win_streak,
                   longest_survived_ms, total_blocks_broken, total_blinks_used
            FROM runorfall_player_stats WHERE player_uuid = ?
            """;
    }

    @Override
    protected String upsertSql() {
        return """
            INSERT INTO runorfall_player_stats (
                player_uuid, player_name, wins, losses, current_win_streak, best_win_streak, longest_survived_ms,
                total_blocks_broken, total_blinks_used
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              player_name = VALUES(player_name),
              wins = VALUES(wins),
              losses = VALUES(losses),
              current_win_streak = VALUES(current_win_streak),
              best_win_streak = VALUES(best_win_streak),
              longest_survived_ms = VALUES(longest_survived_ms),
              total_blocks_broken = VALUES(total_blocks_broken),
              total_blinks_used = VALUES(total_blinks_used)
            """;
    }

    @Override
    protected RunOrFallPlayerStats parseRow(ResultSet rs, UUID playerId) throws SQLException {
        RunOrFallPlayerStats stats = new RunOrFallPlayerStats(playerId, sanitizePlayerName(rs.getString("player_name")));
        stats.setWins(rs.getInt("wins"));
        stats.setLosses(rs.getInt("losses"));
        stats.setCurrentWinStreak(rs.getInt("current_win_streak"));
        stats.setBestWinStreak(rs.getInt("best_win_streak"));
        stats.setLongestSurvivedMs(rs.getLong("longest_survived_ms"));
        stats.setTotalBlocksBroken(rs.getLong("total_blocks_broken"));
        stats.setTotalBlinksUsed(rs.getLong("total_blinks_used"));
        return stats;
    }

    @Override
    protected void bindUpsertParams(PreparedStatement stmt, UUID playerId, RunOrFallPlayerStats s) throws SQLException {
        stmt.setString(1, playerId.toString());
        stmt.setString(2, sanitizePlayerName(s.getPlayerName()));
        stmt.setInt(3, s.getWins());
        stmt.setInt(4, s.getLosses());
        stmt.setInt(5, s.getCurrentWinStreak());
        stmt.setInt(6, s.getBestWinStreak());
        stmt.setLong(7, s.getLongestSurvivedMs());
        stmt.setLong(8, s.getTotalBlocksBroken());
        stmt.setLong(9, s.getTotalBlinksUsed());
    }

    @Override
    protected RunOrFallPlayerStats defaultValue() {
        return new RunOrFallPlayerStats(null, UNKNOWN_NAME);
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
