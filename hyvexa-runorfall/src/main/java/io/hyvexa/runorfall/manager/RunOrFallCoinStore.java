package io.hyvexa.runorfall.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RunOrFallCoinStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final RunOrFallCoinStore INSTANCE = new RunOrFallCoinStore();

    private final ConcurrentHashMap<UUID, Long> coinCache = new ConcurrentHashMap<>();

    private RunOrFallCoinStore() {
    }

    public static RunOrFallCoinStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, RunOrFallCoinStore will use in-memory mode");
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS runorfall_player_coins ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "coins BIGINT NOT NULL DEFAULT 0"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            LOGGER.atInfo().log("RunOrFallCoinStore initialized (runorfall_player_coins table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create runorfall_player_coins table");
        }
    }

    public long getCoins(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        Long cached = coinCache.get(playerId);
        if (cached != null) {
            return cached;
        }
        long fromDb = loadFromDatabase(playerId);
        coinCache.putIfAbsent(playerId, fromDb);
        return coinCache.getOrDefault(playerId, 0L);
    }

    public long addCoins(UUID playerId, long amount) {
        if (playerId == null || amount <= 0L) {
            return getCoins(playerId);
        }
        long current = getCoins(playerId);
        long updated = current + amount;
        coinCache.put(playerId, updated);
        persistToDatabase(playerId, updated);
        return updated;
    }

    public void setCoins(UUID playerId, long coins) {
        if (playerId == null) {
            return;
        }
        long safeCoins = Math.max(0L, coins);
        coinCache.put(playerId, safeCoins);
        persistToDatabase(playerId, safeCoins);
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            coinCache.remove(playerId);
        }
    }

    private long loadFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return 0L;
        }
        String sql = "SELECT coins FROM runorfall_player_coins WHERE uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("coins");
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load RunOrFall coins for " + playerId);
        }
        return 0L;
    }

    private void persistToDatabase(UUID playerId, long coins) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT INTO runorfall_player_coins (uuid, coins) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE coins = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setLong(2, coins);
            stmt.setLong(3, coins);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist RunOrFall coins for " + playerId);
        }
    }
}
