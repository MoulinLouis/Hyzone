package io.hyvexa.purge.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.purge.manager.PurgeWeaponConfigManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeWeaponUpgradeStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final PurgeWeaponUpgradeStore INSTANCE = new PurgeWeaponUpgradeStore();

    private final ConnectionProvider db;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>> cache = new ConcurrentHashMap<>();

    public enum UpgradeResult {
        SUCCESS,
        MAX_LEVEL,
        NOT_ENOUGH_SCRAP,
        NOT_OWNED
    }

    public enum PurchaseResult {
        SUCCESS,
        ALREADY_OWNED,
        NOT_ENOUGH_SCRAP
    }

    private PurgeWeaponUpgradeStore() {
        this.db = DatabaseManager.getInstance();
    }

    public static PurgeWeaponUpgradeStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, PurgeWeaponUpgradeStore will use in-memory mode");
        }
    }

    private static final int DEFAULT_LEVEL = 0;

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

    public boolean isOwned(UUID playerId, String weaponId) {
        return getLevel(playerId, weaponId) >= 1;
    }

    public Set<String> getOwnedWeaponIds(UUID playerId) {
        if (playerId == null) {
            return Set.of();
        }
        ConcurrentHashMap<String, Integer> playerWeapons = cache.get(playerId);
        if (playerWeapons == null) {
            loadFromDatabase(playerId);
            playerWeapons = cache.get(playerId);
        }
        if (playerWeapons == null) {
            return Set.of();
        }
        Set<String> owned = new HashSet<>();
        for (var entry : playerWeapons.entrySet()) {
            if (entry.getValue() >= 1) {
                owned.add(entry.getKey());
            }
        }
        return owned;
    }

    public UpgradeResult tryUpgrade(UUID playerId, String weaponId, PurgeWeaponConfigManager config) {
        int currentLevel = getLevel(playerId, weaponId);
        if (currentLevel < 1) {
            return UpgradeResult.NOT_OWNED;
        }
        if (currentLevel >= config.getMaxLevel()) {
            return UpgradeResult.MAX_LEVEL;
        }
        int nextLevel = currentLevel + 1;
        long cost = config.getCost(weaponId, nextLevel);

        if (cost <= 0) {
            setLevel(playerId, weaponId, nextLevel);
            return UpgradeResult.SUCCESS;
        }

        if (!this.db.isInitialized()) {
            long scrap = PurgeScrapStore.getInstance().getScrap(playerId);
            if (scrap < cost) {
                return UpgradeResult.NOT_ENOUGH_SCRAP;
            }
            PurgeScrapStore.getInstance().removeScrap(playerId, cost);
            setLevel(playerId, weaponId, nextLevel);
            return UpgradeResult.SUCCESS;
        }

        PurgeScrapStore scrapStore = PurgeScrapStore.getInstance();
        long[] scrapSnapshot = new long[1]; // [currentScrap]
        UpgradeResult result = this.db.withTransaction(conn -> {
            long currentScrap = scrapStore.selectScrapForUpdate(conn, playerId);
            if (currentScrap < cost) {
                return UpgradeResult.NOT_ENOUGH_SCRAP;
            }
            scrapStore.updateScrap(conn, playerId, currentScrap - cost);
            upsertWeaponLevel(conn, playerId, weaponId, nextLevel);
            scrapSnapshot[0] = currentScrap;
            return UpgradeResult.SUCCESS;
        }, UpgradeResult.NOT_ENOUGH_SCRAP);
        if (result == UpgradeResult.SUCCESS) {
            scrapStore.applyTransactionalScrapCommit(playerId, scrapSnapshot[0], scrapSnapshot[0] - cost);
            cache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(weaponId, nextLevel);
        }
        return result;
    }

    public PurchaseResult purchaseWeapon(UUID playerId, String weaponId, long cost) {
        if (isOwned(playerId, weaponId)) {
            return PurchaseResult.ALREADY_OWNED;
        }

        if (cost <= 0) {
            setLevel(playerId, weaponId, 1);
            return PurchaseResult.SUCCESS;
        }

        if (!this.db.isInitialized()) {
            long scrap = PurgeScrapStore.getInstance().getScrap(playerId);
            if (scrap < cost) {
                return PurchaseResult.NOT_ENOUGH_SCRAP;
            }
            PurgeScrapStore.getInstance().removeScrap(playerId, cost);
            setLevel(playerId, weaponId, 1);
            return PurchaseResult.SUCCESS;
        }

        PurgeScrapStore scrapStore = PurgeScrapStore.getInstance();
        long[] scrapSnapshot = new long[1]; // [currentScrap]
        PurchaseResult result = this.db.withTransaction(conn -> {
            long currentScrap = scrapStore.selectScrapForUpdate(conn, playerId);
            if (currentScrap < cost) {
                return PurchaseResult.NOT_ENOUGH_SCRAP;
            }
            scrapStore.updateScrap(conn, playerId, currentScrap - cost);
            upsertWeaponLevel(conn, playerId, weaponId, 1);
            scrapSnapshot[0] = currentScrap;
            return PurchaseResult.SUCCESS;
        }, PurchaseResult.NOT_ENOUGH_SCRAP);
        if (result == PurchaseResult.SUCCESS) {
            scrapStore.applyTransactionalScrapCommit(playerId, scrapSnapshot[0], scrapSnapshot[0] - cost);
            cache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(weaponId, 1);
        }
        return result;
    }

    public void initializeDefaults(UUID playerId, Set<String> defaultWeaponIds) {
        if (playerId == null || defaultWeaponIds == null || defaultWeaponIds.isEmpty()) {
            return;
        }
        for (String weaponId : defaultWeaponIds) {
            if (!isOwned(playerId, weaponId)) {
                setLevel(playerId, weaponId, 1);
            }
        }
    }

    public void resetPlayer(UUID playerId, Set<String> defaultWeaponIds) {
        if (playerId == null) {
            return;
        }
        cache.remove(playerId);
        if (this.db.isInitialized()) {
            String sql = "DELETE FROM purge_weapon_upgrades WHERE uuid = ?";
            try (Connection conn = this.db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to reset weapon upgrades for " + playerId);
            }
        }
        if (defaultWeaponIds != null && !defaultWeaponIds.isEmpty()) {
            initializeDefaults(playerId, defaultWeaponIds);
        }
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    private void loadFromDatabase(UUID playerId) {
        if (!this.db.isInitialized()) {
            return;
        }
        String sql = "SELECT weapon_id, level FROM purge_weapon_upgrades WHERE uuid = ?";
        try (Connection conn = this.db.getConnection();
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

    private void upsertWeaponLevel(Connection conn, UUID playerId, String weaponId, int level) throws SQLException {
        String sql = "INSERT INTO purge_weapon_upgrades (uuid, weapon_id, level) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE level = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, weaponId);
            stmt.setInt(3, level);
            stmt.setInt(4, level);
            stmt.executeUpdate();
        }
    }

    private void persistToDatabase(UUID playerId, String weaponId, int level) {
        if (!this.db.isInitialized()) {
            return;
        }
        String sql = "INSERT INTO purge_weapon_upgrades (uuid, weapon_id, level) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE level = ?";
        try (Connection conn = this.db.getConnection();
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
