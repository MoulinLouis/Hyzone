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
 * Global vexa currency store. Singleton shared across all modules.
 * Lazy-loads per-player vexa counts from MySQL, evicts on disconnect.
 * Cache entries expire after {@link #CACHE_TTL_MS} to pick up cross-module DB writes.
 */
public class VexaStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final VexaStore INSTANCE = new VexaStore();
    private static final long CACHE_TTL_MS = 5_000;

    private final ConcurrentHashMap<UUID, CachedBalance> cache = new ConcurrentHashMap<>();

    private VexaStore() {
    }

    public static VexaStore getInstance() {
        return INSTANCE;
    }

    /**
     * Create the player_vexa table if it does not exist.
     * Safe to call multiple times (idempotent).
     */
    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, VexaStore will use in-memory mode");
            return;
        }
        String createSql = "CREATE TABLE IF NOT EXISTS player_vexa ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "vexa BIGINT NOT NULL DEFAULT 0"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            migratePlayerGemsToVexa(conn);
            try (PreparedStatement stmt = conn.prepareStatement(createSql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            LOGGER.atInfo().log("VexaStore initialized (player_vexa table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create player_vexa table");
        }

        CurrencyBridge.register("vexa", new CurrencyBridge.CurrencyProvider() {
            @Override
            public long getBalance(UUID playerId) {
                return getVexa(playerId);
            }

            @Override
            public void deduct(UUID playerId, long amount) {
                removeVexa(playerId, amount);
            }
        });
    }

    /**
     * Get the vexa count for a player. Lazy-loads from DB on cache miss or stale entry.
     */
    public long getVexa(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        CachedBalance cached = cache.get(playerId);
        if (cached != null && !cached.isStale()) {
            return cached.value;
        }
        long fromDb = loadFromDatabase(playerId);
        cache.put(playerId, new CachedBalance(fromDb));
        return fromDb;
    }

    /**
     * Set the vexa count for a player. Updates cache and persists immediately.
     */
    public void setVexa(UUID playerId, long vexa) {
        if (playerId == null) {
            return;
        }
        long safe = Math.max(0, vexa);
        cache.put(playerId, new CachedBalance(safe));
        persistToDatabase(playerId, safe);
    }

    /**
     * Add vexa to a player's balance. Returns new total.
     */
    public long addVexa(UUID playerId, long amount) {
        if (playerId == null) {
            return 0;
        }
        return modifyVexa(playerId, current -> current + amount);
    }

    /**
     * Remove vexa from a player's balance, flooring at 0. Returns new total.
     */
    public long removeVexa(UUID playerId, long amount) {
        if (playerId == null) {
            return 0;
        }
        return modifyVexa(playerId, current -> Math.max(0, current - amount));
    }

    /**
     * Atomically modify a player's vexa balance. The compute function receives the current
     * balance and returns the desired new balance. Persists the result to the database.
     */
    private long modifyVexa(UUID playerId, java.util.function.LongUnaryOperator compute) {
        long[] result = new long[1];
        cache.compute(playerId, (uuid, cached) -> {
            long current = (cached != null && !cached.isStale()) ? cached.value : loadFromDatabase(uuid);
            long newTotal = Math.max(0, compute.applyAsLong(current));
            result[0] = newTotal;
            return new CachedBalance(newTotal);
        });
        persistToDatabase(playerId, result[0]);
        return result[0];
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
        String sql = "SELECT vexa FROM player_vexa WHERE uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("vexa");
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load vexa for " + playerId);
        }
        return 0;
    }

    private void persistToDatabase(UUID playerId, long vexa) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT INTO player_vexa (uuid, vexa) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE vexa = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setLong(2, vexa);
            stmt.setLong(3, vexa);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist vexa for " + playerId);
        }
    }

    private void migratePlayerGemsToVexa(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            if (tableExists(conn, "player_gems") && !tableExists(conn, "player_vexa")) {
                try (PreparedStatement stmt = conn.prepareStatement("RENAME TABLE player_gems TO player_vexa")) {
                    DatabaseManager.applyQueryTimeout(stmt);
                    stmt.executeUpdate();
                    LOGGER.atInfo().log("Renamed table player_gems -> player_vexa");
                }
            }

            if (tableExists(conn, "player_vexa")
                    && columnExists(conn, "player_vexa", "gems")
                    && !columnExists(conn, "player_vexa", "vexa")) {
                try (PreparedStatement stmt = conn.prepareStatement("ALTER TABLE player_vexa RENAME COLUMN gems TO vexa")) {
                    DatabaseManager.applyQueryTimeout(stmt);
                    stmt.executeUpdate();
                    LOGGER.atInfo().log("Renamed column player_vexa.gems -> player_vexa.vexa");
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to migrate player_gems/player_vexa schema");
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, tableName, columnName)) {
            return rs.next();
        }
    }

    private static final class CachedBalance {
        final long value;
        final long cachedAt;

        CachedBalance(long value) {
            this.value = value;
            this.cachedAt = System.currentTimeMillis();
        }

        boolean isStale() {
            return System.currentTimeMillis() - cachedAt > CACHE_TTL_MS;
        }
    }
}
