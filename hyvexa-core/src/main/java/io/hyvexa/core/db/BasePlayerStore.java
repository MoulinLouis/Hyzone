package io.hyvexa.core.db;

import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public abstract class BasePlayerStore<V> {

    private final ConcurrentHashMap<UUID, V> cache = new ConcurrentHashMap<>();
    private final ConnectionProvider connectionProvider;

    protected BasePlayerStore(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    // --- Template methods (subclasses implement) ---

    /** SELECT query with single {@code ?} for UUID. */
    protected abstract String loadSql();

    /** INSERT ... ON DUPLICATE KEY UPDATE query. */
    protected abstract String upsertSql();

    /** Parse one ResultSet row into V. Cursor is already on the row. */
    protected abstract V parseRow(ResultSet rs, UUID playerId) throws SQLException;

    /** Bind all upsert parameters onto the PreparedStatement. */
    protected abstract void bindUpsertParams(PreparedStatement stmt, UUID playerId, V value) throws SQLException;

    /** Default value when no DB row exists or DB is not initialized. */
    protected abstract V defaultValue();

    // --- Provided behavior ---

    public V getOrLoad(UUID playerId) {
        if (playerId == null) return defaultValue();
        return cache.computeIfAbsent(playerId, this::loadFromDatabase);
    }

    public void save(UUID playerId, V value) {
        if (playerId == null) return;
        cache.put(playerId, value);
        persistToDatabase(playerId, value);
    }

    public void evict(UUID playerId) {
        if (playerId == null) return;
        cache.remove(playerId);
    }

    /** Read from cache only, no DB fallback. Returns null if not cached. */
    protected V getCached(UUID playerId) {
        return playerId == null ? null : cache.get(playerId);
    }

    /** Expose cache values for subclasses that need listing (leaderboards). */
    protected Collection<V> cacheValues() {
        return cache.values();
    }

    protected final ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    /** Bulk-load all rows for subclasses that need it (leaderboards). */
    protected void loadAll(String loadAllSql, Function<ResultSet, UUID> keyExtractor) {
        if (!connectionProvider.isInitialized()) return;
        int skipped = 0;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = DatabaseManager.prepare(conn, loadAllSql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID key = keyExtractor.apply(rs);
                    V value = parseRow(rs, key);
                    if (key != null && value != null) {
                        cache.put(key, value);
                    }
                } catch (Exception e) {
                    skipped++;
                }
            }
        } catch (SQLException e) {
            logError("bulk loading", e);
        }
        if (skipped > 0) {
            HytaleLogger.forEnclosingClass().atWarning()
                .log("[%s] Skipped %d malformed rows during bulk load", getClass().getSimpleName(), skipped);
        }
    }

    /** Clear the cache. Intended for subclass syncLoad() implementations. */
    protected void clearCache() {
        cache.clear();
    }

    private V loadFromDatabase(UUID playerId) {
        if (!connectionProvider.isInitialized()) {
            return defaultValue();
        }
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = DatabaseManager.prepare(conn, loadSql())) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return parseRow(rs, playerId);
                }
            }
        } catch (SQLException e) {
            logError("loading player " + playerId, e);
        }
        return defaultValue();
    }

    private void persistToDatabase(UUID playerId, V value) {
        if (!connectionProvider.isInitialized()) {
            return;
        }
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = DatabaseManager.prepare(conn, upsertSql())) {
            bindUpsertParams(stmt, playerId, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logError("saving player " + playerId, e);
        }
    }

    private void logError(String context, SQLException e) {
        HytaleLogger.forEnclosingClass().atWarning().withCause(e)
            .log("[%s] DB error %s", getClass().getSimpleName(), context);
    }
}
