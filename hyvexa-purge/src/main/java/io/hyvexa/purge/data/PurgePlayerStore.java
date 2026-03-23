package io.hyvexa.purge.data;

import io.hyvexa.core.db.BasePlayerStore;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PurgePlayerStore extends BasePlayerStore<PurgePlayerStats> {

    private static final PurgePlayerStore INSTANCE = new PurgePlayerStore(DatabaseManager.getInstance());

    public PurgePlayerStore(ConnectionProvider db) {
        super(db);
    }

    public static PurgePlayerStore getInstance() {
        return INSTANCE;
    }

    @Override
    protected String loadSql() {
        return "SELECT best_wave, total_kills, total_sessions FROM purge_player_stats WHERE uuid = ?";
    }

    @Override
    protected String upsertSql() {
        return "INSERT INTO purge_player_stats (uuid, best_wave, total_kills, total_sessions) VALUES (?, ?, ?, ?) "
             + "ON DUPLICATE KEY UPDATE best_wave = ?, total_kills = ?, total_sessions = ?";
    }

    @Override
    protected PurgePlayerStats parseRow(ResultSet rs, UUID playerId) throws SQLException {
        return new PurgePlayerStats(rs.getInt("best_wave"), rs.getInt("total_kills"), rs.getInt("total_sessions"));
    }

    @Override
    protected void bindUpsertParams(PreparedStatement stmt, UUID id, PurgePlayerStats s) throws SQLException {
        stmt.setString(1, id.toString());
        stmt.setInt(2, s.getBestWave());
        stmt.setInt(3, s.getTotalKills());
        stmt.setInt(4, s.getTotalSessions());
        stmt.setInt(5, s.getBestWave());
        stmt.setInt(6, s.getTotalKills());
        stmt.setInt(7, s.getTotalSessions());
    }

    @Override
    protected PurgePlayerStats defaultValue() {
        return new PurgePlayerStats(0, 0, 0);
    }
}
