package io.hyvexa.purge.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeClassStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile PurgeClassStore instance;

    private final ConnectionProvider db;
    private final PurgeScrapStore scrapStore;
    private final ConcurrentHashMap<UUID, Set<PurgeClass>> unlockedCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PurgeClass> selectedCache = new ConcurrentHashMap<>();
    // Track which players we've loaded selected class for (to distinguish "loaded null" from "not loaded")
    private final ConcurrentHashMap<UUID, Boolean> selectedLoaded = new ConcurrentHashMap<>();

    public enum PurchaseResult {
        SUCCESS,
        ALREADY_UNLOCKED,
        NOT_ENOUGH_SCRAP
    }

    private PurgeClassStore(ConnectionProvider db, PurgeScrapStore scrapStore) {
        this.db = db;
        this.scrapStore = scrapStore;
    }

    public static PurgeClassStore createAndRegister(ConnectionProvider db, PurgeScrapStore scrapStore) {
        if (instance != null) throw new IllegalStateException("PurgeClassStore already initialized");
        instance = new PurgeClassStore(db, scrapStore);
        return instance;
    }

    public static PurgeClassStore get() {
        PurgeClassStore ref = instance;
        if (ref == null) throw new IllegalStateException("PurgeClassStore not yet initialized");
        return ref;
    }

    public static void destroy() { instance = null; }

    public void initialize() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, PurgeClassStore will use in-memory mode");
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

        if (!this.db.isInitialized()) {
            // In-memory fallback (dev/testing)
            long scrap = this.scrapStore.getScrap(playerId);
            if (scrap < cost) {
                return PurchaseResult.NOT_ENOUGH_SCRAP;
            }
            this.scrapStore.removeScrap(playerId, cost);
            unlockClass(playerId, purgeClass);
            return PurchaseResult.SUCCESS;
        }

        long[] scrapSnapshot = new long[1]; // [currentScrap]
        PurchaseResult result = this.db.withTransaction(conn -> {
            long currentScrap = this.scrapStore.selectScrapForUpdate(conn, playerId);
            if (currentScrap < cost) {
                return PurchaseResult.NOT_ENOUGH_SCRAP;
            }
            this.scrapStore.updateScrap(conn, playerId, currentScrap - cost);
            insertClassUnlock(conn, playerId, purgeClass);
            scrapSnapshot[0] = currentScrap;
            return PurchaseResult.SUCCESS;
        }, PurchaseResult.NOT_ENOUGH_SCRAP);
        if (result == PurchaseResult.SUCCESS) {
            this.scrapStore.applyTransactionalScrapCommit(playerId, scrapSnapshot[0], scrapSnapshot[0] - cost);
            unlockedCache.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(purgeClass);
        }
        return result;
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            unlockedCache.remove(playerId);
            selectedCache.remove(playerId);
            selectedLoaded.remove(playerId);
        }
    }

    private void loadUnlockedFromDatabase(UUID playerId) {
        if (!this.db.isInitialized()) {
            return;
        }
        String sql = "SELECT class_id FROM purge_player_classes WHERE uuid = ?";
        List<PurgeClass> loaded = DatabaseManager.queryList(this.db, sql,
                stmt -> stmt.setString(1, playerId.toString()),
                rs -> PurgeClass.fromName(rs.getString("class_id")));
        Set<PurgeClass> classes = ConcurrentHashMap.newKeySet();
        for (PurgeClass pc : loaded) {
            if (pc != null) {
                classes.add(pc);
            }
        }
        unlockedCache.putIfAbsent(playerId, classes);
    }

    private void loadSelectedFromDatabase(UUID playerId) {
        if (!this.db.isInitialized()) {
            selectedLoaded.put(playerId, Boolean.TRUE);
            return;
        }
        String sql = "SELECT selected_class FROM purge_player_selected_class WHERE uuid = ?";
        PurgeClass pc = DatabaseManager.queryOne(this.db, sql,
                stmt -> stmt.setString(1, playerId.toString()),
                rs -> {
                    String classId = rs.getString("selected_class");
                    return classId != null ? PurgeClass.fromName(classId) : null;
                }, null);
        if (pc != null) {
            selectedCache.put(playerId, pc);
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
        String sql = "INSERT IGNORE INTO purge_player_classes (uuid, class_id) VALUES (?, ?)";
        DatabaseManager.execute(this.db, sql, stmt -> {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, purgeClass.name());
        });
    }

    private void persistSelected(UUID playerId, PurgeClass purgeClass) {
        String sql = "INSERT INTO purge_player_selected_class (uuid, selected_class) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE selected_class = ?";
        String classId = purgeClass != null ? purgeClass.name() : null;
        DatabaseManager.execute(this.db, sql, stmt -> {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, classId);
            stmt.setString(3, classId);
        });
    }
}
