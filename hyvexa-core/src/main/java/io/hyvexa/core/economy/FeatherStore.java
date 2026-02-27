package io.hyvexa.core.economy;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feather currency store. Singleton, lazy-load, immediate writes.
 * Cache entries expire after {@link #CACHE_TTL_MS} to pick up cross-module DB writes.
 */
public class FeatherStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final FeatherStore INSTANCE = new FeatherStore();
    private static final long CACHE_TTL_MS = 5_000;

    private final ConcurrentHashMap<UUID, CachedBalance> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> refreshInFlight = new ConcurrentHashMap<>();

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

        CurrencyBridge.register("feathers", new CurrencyBridge.CurrencyProvider() {
            @Override
            public long getBalance(UUID playerId) {
                return getFeathers(playerId);
            }

            @Override
            public void deduct(UUID playerId, long amount) {
                removeFeathers(playerId, amount);
            }
        });
    }

    public long getFeathers(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        CachedBalance cached = cache.get(playerId);
        if (cached == null) {
            return loadAndCacheBalance(playerId);
        }
        if (cached.isStale()) {
            refreshFromDatabaseAsync(playerId, cached.cachedAt);
        }
        return cached.value;
    }

    public void setFeathers(UUID playerId, long feathers) {
        if (playerId == null) {
            return;
        }
        long safe = Math.max(0, feathers);
        cache.put(playerId, new CachedBalance(safe));
        persistToDatabase(playerId, safe);
    }

    public long addFeathers(UUID playerId, long amount) {
        if (playerId == null) {
            return 0;
        }
        return modifyFeathers(playerId, current -> current + amount);
    }

    public long removeFeathers(UUID playerId, long amount) {
        if (playerId == null) {
            return 0;
        }
        return modifyFeathers(playerId, current -> Math.max(0, current - amount));
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
            refreshInFlight.remove(playerId);
        }
    }

    private long loadAndCacheBalance(UUID playerId) {
        long fromDb = loadFromDatabase(playerId);
        cache.put(playerId, new CachedBalance(fromDb));
        return fromDb;
    }

    private void refreshFromDatabaseAsync(UUID playerId, long staleCachedAt) {
        if (playerId == null || refreshInFlight.putIfAbsent(playerId, Boolean.TRUE) != null) {
            return;
        }
        CompletableFuture.supplyAsync(() -> loadFromDatabase(playerId), HytaleServer.SCHEDULED_EXECUTOR)
                .handle((value, throwable) -> {
                    try {
                        if (throwable != null) {
                            LOGGER.atWarning().withCause(throwable).log("Failed to refresh feather cache for " + playerId);
                            return null;
                        }
                        cache.compute(playerId, (uuid, current) -> {
                            if (current == null) {
                                return null;
                            }
                            if (current.cachedAt > staleCachedAt) {
                                return current;
                            }
                            return new CachedBalance(value);
                        });
                        return null;
                    } finally {
                        refreshInFlight.remove(playerId);
                    }
                });
    }

    private long modifyFeathers(UUID playerId, java.util.function.LongUnaryOperator compute) {
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
