package io.hyvexa.purge.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import com.hypixel.hytale.server.core.HytaleServer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WeaponXpStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final WeaponXpStore INSTANCE = new WeaponXpStore();

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, int[]>> cache = new ConcurrentHashMap<>();

    private WeaponXpStore() {
    }

    public static WeaponXpStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, WeaponXpStore will use in-memory mode");
        }
    }

    /**
     * Returns [xp, level] for a player-weapon pair. Never null.
     */
    public int[] getXpData(UUID playerId, String weaponId) {
        if (playerId == null || weaponId == null) {
            return new int[]{0, 0};
        }
        ConcurrentHashMap<String, int[]> playerMap = cache.get(playerId);
        if (playerMap != null) {
            int[] data = playerMap.get(weaponId);
            if (data != null) {
                return data;
            }
        }
        loadFromDatabase(playerId, weaponId);
        playerMap = cache.get(playerId);
        if (playerMap != null) {
            int[] data = playerMap.get(weaponId);
            if (data != null) {
                return data;
            }
        }
        return new int[]{0, 0};
    }

    /** Sets the given xp/level in cache and persists async. */
    public int[] incrementXp(UUID playerId, String weaponId, int newXp, int newLevel) {
        if (playerId == null || weaponId == null) {
            return new int[]{0, 0};
        }
        int[] data = new int[]{newXp, newLevel};
        cache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(weaponId, data);
        // Async persist — XP increments happen on every kill, avoid blocking world thread
        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> persistToDatabase(playerId, weaponId, newXp, newLevel));
        return data;
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    private void loadFromDatabase(UUID playerId, String weaponId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "SELECT xp, level FROM purge_weapon_xp WHERE uuid = ? AND weapon_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, weaponId);
            try (ResultSet rs = stmt.executeQuery()) {
                ConcurrentHashMap<String, int[]> playerMap = cache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
                if (rs.next()) {
                    playerMap.putIfAbsent(weaponId, new int[]{rs.getInt("xp"), rs.getInt("level")});
                } else {
                    playerMap.putIfAbsent(weaponId, new int[]{0, 0});
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load weapon XP for " + playerId + "/" + weaponId);
        }
    }

    private void persistToDatabase(UUID playerId, String weaponId, int xp, int level) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT INTO purge_weapon_xp (uuid, weapon_id, xp, level) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE xp = ?, level = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, weaponId);
            stmt.setInt(3, xp);
            stmt.setInt(4, level);
            stmt.setInt(5, xp);
            stmt.setInt(6, level);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist weapon XP for " + playerId + "/" + weaponId);
        }
    }
}
