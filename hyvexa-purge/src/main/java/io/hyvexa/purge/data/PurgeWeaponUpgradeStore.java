package io.hyvexa.purge.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeWeaponUpgradeStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final PurgeWeaponUpgradeStore INSTANCE = new PurgeWeaponUpgradeStore();

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>> cache = new ConcurrentHashMap<>();

    public enum UpgradeResult {
        SUCCESS,
        MAX_LEVEL,
        NOT_ENOUGH_SCRAP
    }

    private PurgeWeaponUpgradeStore() {
    }

    public static PurgeWeaponUpgradeStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, PurgeWeaponUpgradeStore will use in-memory mode");
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS purge_weapon_upgrades ("
                + "uuid VARCHAR(36) NOT NULL, "
                + "weapon_id VARCHAR(32) NOT NULL, "
                + "level INT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (uuid, weapon_id)"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            LOGGER.atInfo().log("PurgeWeaponUpgradeStore initialized (purge_weapon_upgrades table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge_weapon_upgrades table");
        }
    }

    private static final int DEFAULT_LEVEL = 1;

    public int getLevel(UUID playerId, String weaponId) {
        if (playerId == null) {
            return DEFAULT_LEVEL;
        }
        ConcurrentHashMap<String, Integer> playerWeapons = cache.get(playerId);
        if (playerWeapons != null) {
            return playerWeapons.getOrDefault(weaponId, DEFAULT_LEVEL);
        }
        loadFromDatabase(playerId);
        playerWeapons = cache.get(playerId);
        if (playerWeapons != null) {
            return playerWeapons.getOrDefault(weaponId, DEFAULT_LEVEL);
        }
        return DEFAULT_LEVEL;
    }

    public void setLevel(UUID playerId, String weaponId, int level) {
        if (playerId == null) {
            return;
        }
        cache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(weaponId, level);
        persistToDatabase(playerId, weaponId, level);
    }

    public UpgradeResult tryUpgrade(UUID playerId, String weaponId, PurgeWeaponConfigManager config) {
        int currentLevel = getLevel(playerId, weaponId);
        if (currentLevel >= config.getMaxLevel()) {
            return UpgradeResult.MAX_LEVEL;
        }
        int nextLevel = currentLevel + 1;
        long cost = config.getCost(weaponId, nextLevel);
        long scrap = PurgeScrapStore.getInstance().getScrap(playerId);
        if (scrap < cost) {
            return UpgradeResult.NOT_ENOUGH_SCRAP;
        }
        // Deduct scrap
        if (cost > 0) {
            PurgeScrapStore.getInstance().removeScrap(playerId, cost);
        }
        // Increment level
        setLevel(playerId, weaponId, nextLevel);
        return UpgradeResult.SUCCESS;
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    private void loadFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "SELECT weapon_id, level FROM purge_weapon_upgrades WHERE uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                ConcurrentHashMap<String, Integer> weapons = new ConcurrentHashMap<>();
                while (rs.next()) {
                    weapons.put(rs.getString("weapon_id"), rs.getInt("level"));
                }
                cache.putIfAbsent(playerId, weapons);
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load weapon upgrades for " + playerId);
        }
    }

    private void persistToDatabase(UUID playerId, String weaponId, int level) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT INTO purge_weapon_upgrades (uuid, weapon_id, level) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE level = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, weaponId);
            stmt.setInt(3, level);
            stmt.setInt(4, level);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist weapon upgrade for " + playerId);
        }
    }
}
