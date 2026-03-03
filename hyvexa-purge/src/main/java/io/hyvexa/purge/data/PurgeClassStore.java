package io.hyvexa.purge.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeClassStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final PurgeClassStore INSTANCE = new PurgeClassStore();

    private final ConcurrentHashMap<UUID, Set<PurgeClass>> unlockedCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PurgeClass> selectedCache = new ConcurrentHashMap<>();
    // Track which players we've loaded selected class for (to distinguish "loaded null" from "not loaded")
    private final ConcurrentHashMap<UUID, Boolean> selectedLoaded = new ConcurrentHashMap<>();

    public enum PurchaseResult {
        SUCCESS,
        ALREADY_UNLOCKED,
        NOT_ENOUGH_SCRAP
    }

    private PurgeClassStore() {
    }

    public static PurgeClassStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, PurgeClassStore will use in-memory mode");
            return;
        }
        String sql1 = "CREATE TABLE IF NOT EXISTS purge_player_classes ("
                + "uuid VARCHAR(36) NOT NULL, "
                + "class_id VARCHAR(32) NOT NULL, "
                + "PRIMARY KEY (uuid, class_id)"
                + ") ENGINE=InnoDB";
        String sql2 = "CREATE TABLE IF NOT EXISTS purge_player_selected_class ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "selected_class VARCHAR(32) DEFAULT NULL"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(sql1)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql2)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            LOGGER.atInfo().log("PurgeClassStore initialized (purge_player_classes + purge_player_selected_class tables ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge class tables");
        }
    }

    public Set<PurgeClass> getUnlockedClasses(UUID playerId) {
        if (playerId == null) {
            return EnumSet.noneOf(PurgeClass.class);
        }
        Set<PurgeClass> cached = unlockedCache.get(playerId);
        if (cached != null) {
            return cached;
        }
        loadUnlockedFromDatabase(playerId);
        return unlockedCache.getOrDefault(playerId, EnumSet.noneOf(PurgeClass.class));
    }

    public boolean isUnlocked(UUID playerId, PurgeClass purgeClass) {
        return getUnlockedClasses(playerId).contains(purgeClass);
    }

    public PurgeClass getSelectedClass(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        if (selectedLoaded.containsKey(playerId)) {
            return selectedCache.get(playerId);
        }
        loadSelectedFromDatabase(playerId);
        return selectedCache.get(playerId);
    }

    public void selectClass(UUID playerId, PurgeClass purgeClass) {
        if (playerId == null) {
            return;
        }
        if (purgeClass == null) {
            selectedCache.remove(playerId);
        } else {
            selectedCache.put(playerId, purgeClass);
        }
        selectedLoaded.put(playerId, Boolean.TRUE);
        persistSelected(playerId, purgeClass);
    }

    public void unlockClass(UUID playerId, PurgeClass purgeClass) {
        if (playerId == null || purgeClass == null) {
            return;
        }
        unlockedCache.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet())
                .add(purgeClass);
        persistUnlock(playerId, purgeClass);
    }

    public PurchaseResult purchaseClass(UUID playerId, PurgeClass purgeClass) {
        if (isUnlocked(playerId, purgeClass)) {
            return PurchaseResult.ALREADY_UNLOCKED;
        }
        long cost = purgeClass.getUnlockCost();

        if (!DatabaseManager.getInstance().isInitialized()) {
            // In-memory fallback (dev/testing)
            long scrap = PurgeScrapStore.getInstance().getScrap(playerId);
            if (scrap < cost) {
                return PurchaseResult.NOT_ENOUGH_SCRAP;
            }
            PurgeScrapStore.getInstance().removeScrap(playerId, cost);
            unlockClass(playerId, purgeClass);
            return PurchaseResult.SUCCESS;
        }

        PurgeScrapStore scrapStore = PurgeScrapStore.getInstance();
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long currentScrap = scrapStore.selectScrapForUpdate(conn, playerId);
                if (currentScrap < cost) {
                    conn.rollback();
                    return PurchaseResult.NOT_ENOUGH_SCRAP;
                }
                scrapStore.updateScrap(conn, playerId, currentScrap - cost);
                insertClassUnlock(conn, playerId, purgeClass);
                conn.commit();

                // Update caches only after successful commit
                scrapStore.setCachedScrap(playerId, currentScrap - cost);
                unlockedCache.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(purgeClass);
                return PurchaseResult.SUCCESS;
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.atWarning().withCause(e).log("Failed to purchase class for " + playerId);
                return PurchaseResult.NOT_ENOUGH_SCRAP;
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to get connection for class purchase");
            return PurchaseResult.NOT_ENOUGH_SCRAP;
        }
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            unlockedCache.remove(playerId);
            selectedCache.remove(playerId);
            selectedLoaded.remove(playerId);
        }
    }

    private void loadUnlockedFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "SELECT class_id FROM purge_player_classes WHERE uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                Set<PurgeClass> classes = ConcurrentHashMap.newKeySet();
                while (rs.next()) {
                    PurgeClass pc = PurgeClass.fromName(rs.getString("class_id"));
                    if (pc != null) {
                        classes.add(pc);
                    }
                }
                unlockedCache.putIfAbsent(playerId, classes);
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load unlocked classes for " + playerId);
        }
    }

    private void loadSelectedFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            selectedLoaded.put(playerId, Boolean.TRUE);
            return;
        }
        String sql = "SELECT selected_class FROM purge_player_selected_class WHERE uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String classId = rs.getString("selected_class");
                    if (classId != null) {
                        PurgeClass pc = PurgeClass.fromName(classId);
                        if (pc != null) {
                            selectedCache.put(playerId, pc);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load selected class for " + playerId);
        }
        selectedLoaded.put(playerId, Boolean.TRUE);
    }

    private void insertClassUnlock(Connection conn, UUID playerId, PurgeClass purgeClass) throws SQLException {
        String sql = "INSERT IGNORE INTO purge_player_classes (uuid, class_id) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, purgeClass.name());
            stmt.executeUpdate();
        }
    }

    private void persistUnlock(UUID playerId, PurgeClass purgeClass) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT IGNORE INTO purge_player_classes (uuid, class_id) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, purgeClass.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist class unlock for " + playerId);
        }
    }

    private void persistSelected(UUID playerId, PurgeClass purgeClass) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT INTO purge_player_selected_class (uuid, selected_class) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE selected_class = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            String classId = purgeClass != null ? purgeClass.name() : null;
            stmt.setString(2, classId);
            stmt.setString(3, classId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist selected class for " + playerId);
        }
    }
}
