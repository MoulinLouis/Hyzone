package io.hyvexa.duel.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.BasePlayerStore;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

public class DuelStatsStore extends BasePlayerStore<DuelStats> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public DuelStatsStore() {
        super();
    }

    public DuelStatsStore(ConnectionProvider db) {
        super(db);
    }
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS duel_player_stats (
            player_uuid VARCHAR(36) PRIMARY KEY,
            player_name VARCHAR(64),
            wins INT DEFAULT 0,
            losses INT DEFAULT 0,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        )
        """;
    private static final String LOAD_ALL_SQL =
        "SELECT player_uuid, player_name, wins, losses FROM duel_player_stats";

    public void syncLoad() {
        if (!getConnectionProvider().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, DuelStatsStore will be empty");
            return;
        }
        ensureTable();
        loadAll(LOAD_ALL_SQL, rs -> {
            try {
                return UUID.fromString(rs.getString("player_uuid"));
            } catch (Exception e) {
                return null;
            }
        });
        LOGGER.atInfo().log("DuelStatsStore loaded via syncLoad");
    }

    private void ensureTable() {
        try (Connection conn = getConnectionProvider().getConnection();
             PreparedStatement stmt = DatabaseManager.prepare(conn, CREATE_TABLE_SQL)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to create duel_player_stats table: " + e.getMessage());
        }
    }

    @Nullable
    public DuelStats getStats(@Nonnull UUID playerId) {
        return getCached(playerId);
    }

    @Nullable
    public DuelStats getStatsByName(@Nonnull String playerName) {
        String target = playerName.trim();
        for (DuelStats stats : cacheValues()) {
            String statsName = stats.getPlayerName();
            if (statsName != null && statsName.equalsIgnoreCase(target)) {
                return stats;
            }
        }
        return null;
    }

    @Nonnull
    public java.util.List<DuelStats> listStats() {
        return new java.util.ArrayList<>(cacheValues());
    }

    public void recordWin(@Nonnull UUID playerId, @Nonnull String playerName) {
        DuelStats stats = getCached(playerId);
        if (stats == null) {
            stats = new DuelStats(playerId, playerName, 0, 0);
        }
        stats.setPlayerName(playerName);
        stats.incrementWins();
        save(playerId, stats);
    }

    public void recordLoss(@Nonnull UUID playerId, @Nonnull String playerName) {
        DuelStats stats = getCached(playerId);
        if (stats == null) {
            stats = new DuelStats(playerId, playerName, 0, 0);
        }
        stats.setPlayerName(playerName);
        stats.incrementLosses();
        save(playerId, stats);
    }

    @Override
    protected String loadSql() {
        return "SELECT player_uuid, player_name, wins, losses FROM duel_player_stats WHERE player_uuid = ?";
    }

    @Override
    protected String upsertSql() {
        return """
            INSERT INTO duel_player_stats (player_uuid, player_name, wins, losses, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name = VALUES(player_name), wins = VALUES(wins),
                losses = VALUES(losses), updated_at = VALUES(updated_at)
            """;
    }

    @Override
    protected DuelStats parseRow(ResultSet rs, UUID playerId) throws SQLException {
        return new DuelStats(playerId, rs.getString("player_name"), rs.getInt("wins"), rs.getInt("losses"));
    }

    @Override
    protected void bindUpsertParams(PreparedStatement stmt, UUID playerId, DuelStats s) throws SQLException {
        stmt.setString(1, playerId.toString());
        stmt.setString(2, s.getPlayerName());
        stmt.setInt(3, s.getWins());
        stmt.setInt(4, s.getLosses());
        stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
    }

    @Override
    protected DuelStats defaultValue() {
        return new DuelStats(null, null, 0, 0);
    }
}
