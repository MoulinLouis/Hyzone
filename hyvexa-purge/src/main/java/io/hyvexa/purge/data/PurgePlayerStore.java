package io.hyvexa.purge.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgePlayerStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final PurgePlayerStore INSTANCE = new PurgePlayerStore();

    private final ConcurrentHashMap<UUID, PurgePlayerStats> cache = new ConcurrentHashMap<>();

    private PurgePlayerStore() {
    }

    public static PurgePlayerStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, PurgePlayerStore will use in-memory mode");
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS purge_player_stats ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "best_wave INT NOT NULL DEFAULT 0, "
                + "total_kills INT NOT NULL DEFAULT 0, "
                + "total_sessions INT NOT NULL DEFAULT 0"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            LOGGER.atInfo().log("PurgePlayerStore initialized (purge_player_stats table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge_player_stats table");
        }
    }

    public PurgePlayerStats getOrCreate(UUID playerId) {
        if (playerId == null) {
            return new PurgePlayerStats(0, 0, 0);
        }
        PurgePlayerStats cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }
        PurgePlayerStats fromDb = loadFromDatabase(playerId);
        cache.putIfAbsent(playerId, fromDb);
        return cache.get(playerId);
    }

    public void save(UUID playerId, PurgePlayerStats stats) {
        if (playerId == null || stats == null) {
            return;
        }
        cache.put(playerId, stats);
        persistToDatabase(playerId, stats);
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    private PurgePlayerStats loadFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return new PurgePlayerStats(0, 0, 0);
        }
        String sql = "SELECT best_wave, total_kills, total_sessions FROM purge_player_stats WHERE uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PurgePlayerStats(
                            rs.getInt("best_wave"),
                            rs.getInt("total_kills"),
                            rs.getInt("total_sessions")
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load stats for " + playerId);
        }
        return new PurgePlayerStats(0, 0, 0);
    }

    private void persistToDatabase(UUID playerId, PurgePlayerStats stats) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT INTO purge_player_stats (uuid, best_wave, total_kills, total_sessions) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE best_wave = ?, total_kills = ?, total_sessions = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setInt(2, stats.getBestWave());
            stmt.setInt(3, stats.getTotalKills());
            stmt.setInt(4, stats.getTotalSessions());
            stmt.setInt(5, stats.getBestWave());
            stmt.setInt(6, stats.getTotalKills());
            stmt.setInt(7, stats.getTotalSessions());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist stats for " + playerId);
        }
    }
}
