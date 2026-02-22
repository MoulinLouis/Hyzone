package io.hyvexa.purge.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeScrapStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final PurgeScrapStore INSTANCE = new PurgeScrapStore();

    private final ConcurrentHashMap<UUID, Long> scrapCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lifetimeCache = new ConcurrentHashMap<>();

    private PurgeScrapStore() {
    }

    public static PurgeScrapStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, PurgeScrapStore will use in-memory mode");
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS purge_player_scrap ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "scrap BIGINT NOT NULL DEFAULT 0, "
                + "lifetime_scrap_earned BIGINT NOT NULL DEFAULT 0"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            LOGGER.atInfo().log("PurgeScrapStore initialized (purge_player_scrap table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge_player_scrap table");
        }
    }

    public long getScrap(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        Long cached = scrapCache.get(playerId);
        if (cached != null) {
            return cached;
        }
        loadFromDatabase(playerId);
        return scrapCache.getOrDefault(playerId, 0L);
    }

    public long getLifetimeScrap(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        Long cached = lifetimeCache.get(playerId);
        if (cached != null) {
            return cached;
        }
        loadFromDatabase(playerId);
        return lifetimeCache.getOrDefault(playerId, 0L);
    }

    public void addScrap(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) {
            return;
        }
        long currentScrap = getScrap(playerId);
        long currentLifetime = getLifetimeScrap(playerId);
        long newScrap = currentScrap + amount;
        long newLifetime = currentLifetime + amount;
        scrapCache.put(playerId, newScrap);
        lifetimeCache.put(playerId, newLifetime);
        persistToDatabase(playerId, newScrap, newLifetime);
    }

    public void removeScrap(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) {
            return;
        }
        long currentScrap = getScrap(playerId);
        long newScrap = Math.max(0, currentScrap - amount);
        scrapCache.put(playerId, newScrap);
        long currentLifetime = getLifetimeScrap(playerId);
        persistToDatabase(playerId, newScrap, currentLifetime);
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            scrapCache.remove(playerId);
            lifetimeCache.remove(playerId);
        }
    }

    private void loadFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "SELECT scrap, lifetime_scrap_earned FROM purge_player_scrap WHERE uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    scrapCache.putIfAbsent(playerId, rs.getLong("scrap"));
                    lifetimeCache.putIfAbsent(playerId, rs.getLong("lifetime_scrap_earned"));
                } else {
                    scrapCache.putIfAbsent(playerId, 0L);
                    lifetimeCache.putIfAbsent(playerId, 0L);
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load scrap for " + playerId);
        }
    }

    private void persistToDatabase(UUID playerId, long scrap, long lifetime) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT INTO purge_player_scrap (uuid, scrap, lifetime_scrap_earned) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE scrap = ?, lifetime_scrap_earned = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setLong(2, scrap);
            stmt.setLong(3, lifetime);
            stmt.setLong(4, scrap);
            stmt.setLong(5, lifetime);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist scrap for " + playerId);
        }
    }
}
