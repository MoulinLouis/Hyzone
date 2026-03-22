package io.hyvexa.purge.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class PurgeScrapStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final PurgeScrapStore INSTANCE = new PurgeScrapStore();
    private static final long FLUSH_INTERVAL_MS = 2_000L;
    private static final long SLOW_FLUSH_WARNING_MS = 1_000L;

    private final ConnectionProvider db;
    private final ConcurrentHashMap<UUID, ScrapBalance> balanceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ScrapBalance> dirtyBalances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private final AtomicLong lastFlushLatencyMs = new AtomicLong();
    private final AtomicLong lastFlushAtMs = new AtomicLong();

    private volatile ScheduledExecutorService flushExecutor;

    private PurgeScrapStore() {
        this.db = DatabaseManager.getInstance();
    }

    public static PurgeScrapStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, PurgeScrapStore will use in-memory mode");
            return;
        }
        startFlushLoop();
    }

    public long getScrap(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return getOrLoadBalance(playerId).scrap();
    }

    public long getLifetimeScrap(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return getOrLoadBalance(playerId).lifetime();
    }

    public void addScrap(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) {
            return;
        }
        synchronized (lockFor(playerId)) {
            ScrapBalance current = getOrLoadBalanceLocked(playerId);
            setCachedBalanceLocked(playerId, new ScrapBalance(
                    current.scrap() + amount,
                    current.lifetime() + amount
            ), true);
        }
    }

    public void removeScrap(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) {
            return;
        }
        synchronized (lockFor(playerId)) {
            ScrapBalance current = getOrLoadBalanceLocked(playerId);
            setCachedBalanceLocked(playerId, new ScrapBalance(
                    Math.max(0, current.scrap() - amount),
                    current.lifetime()
            ), true);
        }
    }

    public void resetPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        synchronized (lockFor(playerId)) {
            setCachedBalanceLocked(playerId, ScrapBalance.ZERO, true);
        }
        flushPlayerAsync(playerId);
    }

    public void evictPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        boolean evict = true;
        synchronized (lockFor(playerId)) {
            if (dirtyBalances.containsKey(playerId) && !flushPlayerLocked(playerId)) {
                evict = false;
            }
            if (evict) {
                balanceCache.remove(playerId);
                dirtyBalances.remove(playerId);
            }
        }
        if (evict) {
            playerLocks.remove(playerId);
        }
    }

    /**
     * Update the cached scrap value directly. Used after transactional purchases
     * where the DB write already succeeded.
     */
    public void setCachedScrap(UUID playerId, long scrap) {
        if (playerId == null) {
            return;
        }
        synchronized (lockFor(playerId)) {
            ScrapBalance current = getOrLoadBalanceLocked(playerId);
            setCachedBalanceLocked(playerId, new ScrapBalance(Math.max(0, scrap), current.lifetime()), false);
        }
    }

    /**
     * Reconcile cache state after a transactional DB write that changed scrap by a known delta.
     */
    public void applyTransactionalScrapCommit(UUID playerId, long previousScrap, long committedScrap) {
        if (playerId == null) {
            return;
        }
        synchronized (lockFor(playerId)) {
            ScrapBalance current = getOrLoadBalanceLocked(playerId);
            long delta = committedScrap - previousScrap;
            long adjustedScrap = Math.max(0, current.scrap() + delta);
            boolean keepDirty = current.scrap() != previousScrap || dirtyBalances.containsKey(playerId);
            setCachedBalanceLocked(playerId,
                    new ScrapBalance(adjustedScrap, current.lifetime()),
                    keepDirty || adjustedScrap != committedScrap);
        }
    }

    /**
     * Lock and read the current scrap balance within a transaction.
     * Caller must have called {@code conn.setAutoCommit(false)} first.
     */
    long selectScrapForUpdate(Connection conn, UUID playerId) throws SQLException {
        synchronized (lockFor(playerId)) {
            ScrapBalance cached = balanceCache.get(playerId);
            ScrapBalance locked = loadFromDatabase(conn, playerId, true);
            if (cached == null) {
                balanceCache.put(playerId, locked);
                return locked.scrap();
            }
            ScrapBalance dirty = dirtyBalances.get(playerId);
            if (dirty != null) {
                persistToDatabase(conn, playerId, dirty);
                dirtyBalances.remove(playerId, dirty);
                return dirty.scrap();
            }
            if (locked.scrap() != cached.scrap() || locked.lifetime() != cached.lifetime()) {
                balanceCache.put(playerId, locked);
                return locked.scrap();
            }
            return cached.scrap();
        }
    }

    /**
     * Update the scrap value within an existing transaction (does not touch lifetime).
     */
    void updateScrap(Connection conn, UUID playerId, long newScrap) throws SQLException {
        String sql = "INSERT INTO purge_player_scrap (uuid, scrap, lifetime_scrap_earned) VALUES (?, ?, 0) "
                + "ON DUPLICATE KEY UPDATE scrap = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setLong(2, newScrap);
            stmt.setLong(3, newScrap);
            stmt.executeUpdate();
        }
    }

    public void flushPlayerAsync(UUID playerId) {
        if (playerId == null || !this.db.isInitialized()) {
            return;
        }
        ScheduledExecutorService executor = flushExecutor;
        if (executor == null) {
            flushPlayer(playerId);
            return;
        }
        try {
            executor.execute(() -> flushPlayer(playerId));
        } catch (RejectedExecutionException e) {
            flushPlayer(playerId);
        }
    }

    public int getDirtyPlayerCount() {
        return dirtyBalances.size();
    }

    public long getLastFlushLatencyMs() {
        return lastFlushLatencyMs.get();
    }

    public long getLastFlushAtMs() {
        return lastFlushAtMs.get();
    }

    public void shutdown() {
        ScheduledExecutorService executor = flushExecutor;
        flushExecutor = null;
        if (executor != null) {
            executor.shutdown();
        }
        flushDirtyPlayers();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void startFlushLoop() {
        if (!this.db.isInitialized()) {
            return;
        }
        synchronized (this) {
            if (flushExecutor != null) {
                return;
            }
            flushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PurgeScrapFlush");
                t.setDaemon(true);
                return t;
            });
            flushExecutor.scheduleWithFixedDelay(this::flushDirtySafely,
                    FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void flushDirtySafely() {
        try {
            flushDirtyPlayers();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to flush purge scrap cache");
        }
    }

    private void flushDirtyPlayers() {
        if (!this.db.isInitialized() || dirtyBalances.isEmpty()) {
            return;
        }
        long startedAt = System.currentTimeMillis();
        int flushedPlayers = 0;
        for (Map.Entry<UUID, ScrapBalance> entry : new ArrayList<>(dirtyBalances.entrySet())) {
            if (flushPlayer(entry.getKey())) {
                flushedPlayers++;
            }
        }
        long latency = System.currentTimeMillis() - startedAt;
        lastFlushLatencyMs.set(latency);
        lastFlushAtMs.set(System.currentTimeMillis());
        if (latency >= SLOW_FLUSH_WARNING_MS) {
            LOGGER.atWarning().log("Purge scrap flush was slow: players=" + flushedPlayers
                    + ", dirtyRemaining=" + dirtyBalances.size()
                    + ", latencyMs=" + latency);
        } else if (flushedPlayers > 0) {
            LOGGER.atFine().log("Flushed purge scrap cache: players=" + flushedPlayers
                    + ", dirtyRemaining=" + dirtyBalances.size()
                    + ", latencyMs=" + latency);
        }
    }

    private boolean flushPlayer(UUID playerId) {
        if (playerId == null || !this.db.isInitialized()) {
            return false;
        }
        synchronized (lockFor(playerId)) {
            return flushPlayerLocked(playerId);
        }
    }

    private boolean flushPlayerLocked(UUID playerId) {
        ScrapBalance dirty = dirtyBalances.get(playerId);
        if (dirty == null) {
            return true;
        }
        try (Connection conn = this.db.getConnection()) {
            persistToDatabase(conn, playerId, dirty);
            dirtyBalances.remove(playerId, dirty);
            return true;
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist scrap for " + playerId);
            return false;
        }
    }

    private ScrapBalance getOrLoadBalance(UUID playerId) {
        ScrapBalance cached = balanceCache.get(playerId);
        if (cached != null) {
            return cached;
        }
        synchronized (lockFor(playerId)) {
            return getOrLoadBalanceLocked(playerId);
        }
    }

    private ScrapBalance getOrLoadBalanceLocked(UUID playerId) {
        ScrapBalance cached = balanceCache.get(playerId);
        if (cached != null) {
            return cached;
        }
        ScrapBalance loaded = loadFromDatabase(playerId);
        balanceCache.put(playerId, loaded);
        return loaded;
    }

    private ScrapBalance loadFromDatabase(UUID playerId) {
        if (!this.db.isInitialized()) {
            return ScrapBalance.ZERO;
        }
        try (Connection conn = this.db.getConnection()) {
            return loadFromDatabase(conn, playerId, false);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load scrap for " + playerId);
            return ScrapBalance.ZERO;
        }
    }

    private ScrapBalance loadFromDatabase(Connection conn, UUID playerId, boolean forUpdate) throws SQLException {
        String sql = "SELECT scrap, lifetime_scrap_earned FROM purge_player_scrap WHERE uuid = ?"
                + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ScrapBalance(rs.getLong("scrap"), rs.getLong("lifetime_scrap_earned"));
                }
            }
        }
        return ScrapBalance.ZERO;
    }

    private void setCachedBalanceLocked(UUID playerId, ScrapBalance balance, boolean dirty) {
        balanceCache.put(playerId, balance);
        if (dirty) {
            dirtyBalances.put(playerId, balance);
        } else {
            dirtyBalances.remove(playerId);
        }
    }

    private Object lockFor(UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, ignored -> new Object());
    }

    private void persistToDatabase(Connection conn, UUID playerId, ScrapBalance balance) throws SQLException {
        if (!this.db.isInitialized()) {
            return;
        }
        String sql = "INSERT INTO purge_player_scrap (uuid, scrap, lifetime_scrap_earned) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE scrap = ?, lifetime_scrap_earned = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setLong(2, balance.scrap());
            stmt.setLong(3, balance.lifetime());
            stmt.setLong(4, balance.scrap());
            stmt.setLong(5, balance.lifetime());
            stmt.executeUpdate();
        }
    }

    private record ScrapBalance(long scrap, long lifetime) {
        private static final ScrapBalance ZERO = new ScrapBalance(0, 0);
    }
}
