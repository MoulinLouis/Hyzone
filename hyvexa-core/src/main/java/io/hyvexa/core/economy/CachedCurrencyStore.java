package io.hyvexa.core.economy;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongUnaryOperator;

/**
 * Base class for currency stores with in-memory cache, TTL expiration, and async refresh.
 * Subclasses provide the SQL table/column name and logger.
 */
abstract class CachedCurrencyStore implements CurrencyStore {

    private static final long CACHE_TTL_MS = 30 * 60 * 1_000L;
    private static final AtomicInteger REFRESH_THREAD_ID = new AtomicInteger(1);
    private static final ExecutorService DB_REFRESH_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread t = new Thread(runnable, "CurrencyRefresh-" + REFRESH_THREAD_ID.getAndIncrement());
        t.setDaemon(true);
        return t;
    });

    protected final ConnectionProvider db;
    private final ConcurrentHashMap<UUID, CachedBalance> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> refreshInFlight = new ConcurrentHashMap<>();

    protected CachedCurrencyStore(ConnectionProvider db) {
        this.db = db;
    }

    protected CachedCurrencyStore() {
        this(DatabaseManager.getInstance());
    }

    protected abstract HytaleLogger logger();

    /** SQL table name (e.g. "player_vexa"). */
    protected abstract String tableName();

    /** SQL column name for the balance (e.g. "vexa"). */
    protected abstract String columnName();

    /** Label for log messages (e.g. "vexa"). */
    protected abstract String currencyLabel();

    /**
     * Called during {@link #initialize()} to run any pre-table-create migrations.
     * Default implementation does nothing.
     */
    protected void preMigrate(Connection conn) throws SQLException {
    }

    /**
     * Called after table creation to register with CurrencyBridge.
     */
    protected abstract void registerBridge();

    // ── Public API ───────────────────────────────────────────────────────

    public void initialize() {
        if (!this.db.isInitialized()) {
            logger().atWarning().log("Database not initialized, " + currencyLabel() + " store will use in-memory mode");
            return;
        }
        String createSql = "CREATE TABLE IF NOT EXISTS " + tableName() + " ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                + columnName() + " BIGINT NOT NULL DEFAULT 0"
                + ") ENGINE=InnoDB";
        try (Connection conn = this.db.getConnection()) {
            preMigrate(conn);
            try (PreparedStatement stmt = DatabaseManager.prepare(conn, createSql)) {
                stmt.executeUpdate();
            }
            logger().atInfo().log(currencyLabel() + " store initialized (" + tableName() + " table ensured)");
        } catch (SQLException e) {
            logger().atSevere().withCause(e).log("Failed to create " + tableName() + " table");
        }
        registerBridge();
    }

    public long getBalance(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        long value = getCachedBalance(playerId);
        CachedBalance cached = cache.get(playerId);
        if (cached != null && cached.isStale()) {
            refreshFromDatabaseAsync(playerId, cached.cachedAt);
        }
        return value;
    }

    public void setBalance(UUID playerId, long amount) {
        if (playerId == null) {
            return;
        }
        long safe = Math.max(0, amount);
        CachedBalance previous = cache.get(playerId);
        cache.put(playerId, new CachedBalance(safe));
        if (!persistToDatabase(playerId, safe)) {
            if (previous == null) {
                cache.remove(playerId);
            } else {
                cache.put(playerId, previous);
            }
        }
    }

    public long addBalance(UUID playerId, long amount) {
        if (playerId == null) {
            return 0;
        }
        return modifyBalance(playerId, current -> current + amount);
    }

    public long removeBalance(UUID playerId, long amount) {
        if (playerId == null) {
            return 0;
        }
        return modifyBalance(playerId, current -> Math.max(0, current - amount));
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
            refreshInFlight.remove(playerId);
        }
    }

    protected long getCachedBalance(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        CachedBalance cached = cache.get(playerId);
        if (cached != null) {
            return cached.value;
        }
        return loadAndCacheBalance(playerId);
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private long loadAndCacheBalance(UUID playerId) {
        long fromDb = loadFromDatabase(playerId);
        cache.put(playerId, new CachedBalance(fromDb));
        return fromDb;
    }

    private void refreshFromDatabaseAsync(UUID playerId, long staleCachedAt) {
        if (playerId == null || refreshInFlight.putIfAbsent(playerId, Boolean.TRUE) != null) {
            return;
        }
        CompletableFuture.supplyAsync(() -> loadFromDatabase(playerId), DB_REFRESH_EXECUTOR)
                .handle((value, throwable) -> {
                    try {
                        if (throwable != null) {
                            logger().atWarning().withCause(throwable).log(
                                    "Failed to refresh " + currencyLabel() + " cache for " + playerId);
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

    private long modifyBalance(UUID playerId, LongUnaryOperator compute) {
        long[] result = new long[1];
        CachedBalance previous = cache.get(playerId);
        cache.compute(playerId, (uuid, cached) -> {
            long current = (cached != null && !cached.isStale()) ? cached.value : loadFromDatabase(uuid);
            long newTotal = Math.max(0, compute.applyAsLong(current));
            result[0] = newTotal;
            return new CachedBalance(newTotal);
        });
        if (!persistToDatabase(playerId, result[0])) {
            if (previous == null) {
                cache.remove(playerId);
            } else {
                cache.put(playerId, previous);
            }
            return previous != null ? previous.value : 0;
        }
        return result[0];
    }

    long loadFromDatabase(UUID playerId) {
        return DatabaseManager.queryOne(this.db,
                "SELECT " + columnName() + " FROM " + tableName() + " WHERE uuid = ?",
                stmt -> stmt.setString(1, playerId.toString()),
                rs -> rs.getLong(columnName()),
                0L);
    }

    private boolean persistToDatabase(UUID playerId, long amount) {
        return DatabaseManager.execute(this.db,
                "INSERT INTO " + tableName() + " (uuid, " + columnName() + ") VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE " + columnName() + " = ?",
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setLong(2, amount);
                    stmt.setLong(3, amount);
                });
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
