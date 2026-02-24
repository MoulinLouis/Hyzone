package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parkour feather currency store. Singleton, lazy-load, immediate writes.
 * Follows VexaStore pattern exactly.
 */
public class FeatherStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final FeatherStore INSTANCE = new FeatherStore();

    private final ConcurrentHashMap<UUID, Long> cache = new ConcurrentHashMap<>();

    private FeatherStore() {
    }

    public static FeatherStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, FeatherStore will use in-memory mode");
            return;
        }
        String createSql = "CREATE TABLE IF NOT EXISTS player_feathers ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "feathers BIGINT NOT NULL DEFAULT 0"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(createSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            LOGGER.atInfo().log("FeatherStore initialized (player_feathers table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create player_feathers table");
        }
    }

    public long getFeathers(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        Long cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }
        long fromDb = loadFromDatabase(playerId);
        cache.putIfAbsent(playerId, fromDb);
        return cache.get(playerId);
    }

    public void setFeathers(UUID playerId, long feathers) {
        if (playerId == null) {
            return;
        }
        long safe = Math.max(0, feathers);
        cache.put(playerId, safe);
        persistToDatabase(playerId, safe);
    }

    public long addFeathers(UUID playerId, long amount) {
        if (playerId == null) {
            return 0;
        }
        long current = getFeathers(playerId);
        long newTotal = current + amount;
        setFeathers(playerId, newTotal);
        return newTotal;
    }

    public long removeFeathers(UUID playerId, long amount) {
        if (playerId == null) {
            return 0;
        }
        long current = getFeathers(playerId);
        long newTotal = Math.max(0, current - amount);
        setFeathers(playerId, newTotal);
        return newTotal;
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    private long loadFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return 0;
        }
        String sql = "SELECT feathers FROM player_feathers WHERE uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("feathers");
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load feathers for " + playerId);
        }
        return 0;
    }

    private void persistToDatabase(UUID playerId, long feathers) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT INTO player_feathers (uuid, feathers) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE feathers = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setLong(2, feathers);
            stmt.setLong(3, feathers);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist feathers for " + playerId);
        }
    }
}
