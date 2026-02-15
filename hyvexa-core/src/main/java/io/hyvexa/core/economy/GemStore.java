package io.hyvexa.core.economy;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global gems currency store. Singleton shared across all modules.
 * Lazy-loads per-player gem counts from MySQL, evicts on disconnect.
 * Writes are immediate (no dirty tracking) since gems are rare/precious.
 */
public class GemStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final GemStore INSTANCE = new GemStore();

    private final ConcurrentHashMap<UUID, Long> cache = new ConcurrentHashMap<>();

    private GemStore() {
    }

    public static GemStore getInstance() {
        return INSTANCE;
    }

    /**
     * Create the player_gems table if it does not exist.
     * Safe to call multiple times (idempotent).
     */
    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, GemStore will use in-memory mode");
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS player_gems ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "gems BIGINT NOT NULL DEFAULT 0"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            LOGGER.atInfo().log("GemStore initialized (player_gems table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create player_gems table");
        }
    }

    /**
     * Get the gem count for a player. Lazy-loads from DB on cache miss.
     */
    public long getGems(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        Long cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }
        // Slow path: load from DB
        long fromDb = loadFromDatabase(playerId);
        cache.putIfAbsent(playerId, fromDb);
        return cache.get(playerId);
    }

    /**
     * Set the gem count for a player. Updates cache and persists immediately.
     */
    public void setGems(UUID playerId, long gems) {
        if (playerId == null) {
            return;
        }
        long safe = Math.max(0, gems);
        cache.put(playerId, safe);
        persistToDatabase(playerId, safe);
    }

    /**
     * Add gems to a player's balance. Returns new total.
     */
    public long addGems(UUID playerId, long amount) {
        if (playerId == null) {
            return 0;
        }
        long current = getGems(playerId);
        long newTotal = current + amount;
        setGems(playerId, newTotal);
        return newTotal;
    }

    /**
     * Remove gems from a player's balance, flooring at 0. Returns new total.
     */
    public long removeGems(UUID playerId, long amount) {
        if (playerId == null) {
            return 0;
        }
        long current = getGems(playerId);
        long newTotal = Math.max(0, current - amount);
        setGems(playerId, newTotal);
        return newTotal;
    }

    /**
     * Evict a player from cache. Called on disconnect.
     */
    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    private long loadFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return 0;
        }
        String sql = "SELECT gems FROM player_gems WHERE uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("gems");
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load gems for " + playerId);
        }
        return 0;
    }

    private void persistToDatabase(UUID playerId, long gems) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT INTO player_gems (uuid, gems) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE gems = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setLong(2, gems);
            stmt.setLong(3, gems);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist gems for " + playerId);
        }
    }
}
