package io.hyvexa.core.cosmetic;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.analytics.PlayerAnalytics;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.economy.CurrencyBridge;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persistence layer for player cosmetics. Singleton shared across all modules.
 * Follows the same lazy-load + immediate-write pattern as VexaStore.
 */
public class CosmeticStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile CosmeticStore instance;

    /** Per-player cache: list of owned cosmetic IDs. */
    private final ConcurrentHashMap<UUID, List<String>> ownedCache = new ConcurrentHashMap<>();
    /** Per-player cache: currently equipped cosmetic ID (null = none). */
    private final ConcurrentHashMap<UUID, String> equippedCache = new ConcurrentHashMap<>();
    /** Sentinel value meaning no cosmetic is equipped. Empty string rather than null for safe map operations. */
    private static final String NONE_EQUIPPED = "";
    /** Per-player locks for transactional operations (purchase). */
    private final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private final ConnectionProvider db;
    private volatile PlayerAnalytics analytics;

    private CosmeticStore(ConnectionProvider db) {
        this.db = db;
    }

    public void setAnalytics(PlayerAnalytics analytics) {
        this.analytics = analytics;
    }

    public static CosmeticStore createAndRegister(ConnectionProvider db) {
        if (instance != null) {
            throw new IllegalStateException("CosmeticStore already initialized");
        }
        instance = new CosmeticStore(db);
        instance.initialize();
        return instance;
    }

    public static CosmeticStore get() {
        CosmeticStore ref = instance;
        if (ref == null) {
            throw new IllegalStateException("CosmeticStore not yet initialized — check plugin load order");
        }
        return ref;
    }

    public static void destroy() {
        instance = null;
    }

    public record PurchaseResult(boolean success, String message) {
    }

    public void initialize() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, CosmeticStore will use in-memory mode");
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS player_cosmetics ("
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "cosmetic_id VARCHAR(64) NOT NULL, "
                + "equipped BOOLEAN NOT NULL DEFAULT FALSE, "
                + "PRIMARY KEY (player_uuid, cosmetic_id)"
                + ") ENGINE=InnoDB";
        try (Connection conn = this.db.getConnection();
             PreparedStatement stmt = DatabaseManager.prepare(conn, sql)) {
            stmt.executeUpdate();
            LOGGER.atInfo().log("CosmeticStore initialized (player_cosmetics table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create player_cosmetics table");
        }
    }

    // ── Queries ──────────────────────────────────────────────────────────

    public boolean ownsCosmetic(UUID playerId, String cosmeticId) {
        if (playerId == null || cosmeticId == null) return false;
        return getOwnedCosmetics(playerId).contains(cosmeticId);
    }

    public List<String> getOwnedCosmetics(UUID playerId) {
        if (playerId == null) return Collections.emptyList();
        List<String> cached = ownedCache.get(playerId);
        if (cached != null) return cached;
        // Lazy-load from DB
        loadPlayer(playerId);
        cached = ownedCache.get(playerId);
        return cached != null ? cached : Collections.emptyList();
    }

    /**
     * Returns the equipped cosmetic ID, or null if none equipped.
     */
    public String getEquippedCosmeticId(UUID playerId) {
        if (playerId == null) return null;
        String cached = equippedCache.get(playerId);
        if (cached == null) {
            loadPlayer(playerId);
            cached = equippedCache.get(playerId);
        }
        if (cached == null || NONE_EQUIPPED.equals(cached)) return null;
        return cached;
    }

    // ── Mutations ────────────────────────────────────────────────────────

    public void purchaseCosmetic(UUID playerId, String cosmeticId) {
        if (playerId == null || cosmeticId == null) return;
        // Update cache
        getOwnedCosmetics(playerId); // ensure loaded
        ownedCache.computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>());
        List<String> owned = ownedCache.get(playerId);
        if (!owned.contains(cosmeticId)) {
            owned.add(cosmeticId);
        }
        // Persist
        persistPurchase(playerId, cosmeticId);
    }

    public PurchaseResult purchaseShopCosmetic(UUID playerId, CosmeticDefinition def) {
        if (playerId == null || def == null) {
            return new PurchaseResult(false, "Unknown cosmetic.");
        }

        Object lock = playerLocks.computeIfAbsent(playerId, k -> new Object());
        synchronized (lock) {
            String cosmeticId = def.getId();
            if (ownsCosmetic(playerId, cosmeticId)) {
                return new PurchaseResult(false, "You already own " + def.getDisplayName() + "!");
            }

            String currency = def.getKind() == CosmeticDefinition.Kind.TRAIL ? "feathers" : "vexa";
            int price = def.getPrice();
            long balance = CurrencyBridge.getBalance(currency, playerId);
            if (balance < price) {
                return new PurchaseResult(false, "Not enough " + currency + "! You need " + price
                        + " but have " + balance + ".");
            }

            if (!CurrencyBridge.deduct(currency, playerId, price)) {
                long currentBalance = CurrencyBridge.getBalance(currency, playerId);
                return new PurchaseResult(false, "Not enough " + currency + "! You need " + price
                        + " but have " + currentBalance + ".");
            }

            purchaseCosmetic(playerId, cosmeticId);
            if (analytics != null) {
                analytics.logPurchase(playerId, cosmeticId, price, currency, "effects");
            }
            return new PurchaseResult(true, "Purchased " + def.getDisplayName() + "!");
        }
    }

    public void equipCosmetic(UUID playerId, String cosmeticId) {
        if (playerId == null || cosmeticId == null) return;
        // Guard: player must own the cosmetic before equipping
        if (!ownsCosmetic(playerId, cosmeticId)) {
            LOGGER.atWarning().log("Player " + playerId + " tried to equip unowned cosmetic: " + cosmeticId);
            return;
        }
        // Unequip previous in DB
        String prev = getEquippedCosmeticId(playerId);
        if (prev != null) {
            persistEquipped(playerId, prev, false);
        }
        // Equip new
        equippedCache.put(playerId, cosmeticId);
        persistEquipped(playerId, cosmeticId, true);
    }

    public void unequipCosmetic(UUID playerId) {
        if (playerId == null) return;
        String prev = getEquippedCosmeticId(playerId);
        if (prev != null) {
            persistEquipped(playerId, prev, false);
        }
        equippedCache.put(playerId, NONE_EQUIPPED);
    }

    /**
     * Delete all cosmetics for a player from DB and cache.
     */
    public void resetAllCosmetics(UUID playerId) {
        if (playerId == null) return;
        ownedCache.put(playerId, new CopyOnWriteArrayList<>());
        equippedCache.put(playerId, NONE_EQUIPPED);
        DatabaseManager.execute(this.db,
                "DELETE FROM player_cosmetics WHERE player_uuid = ?",
                stmt -> stmt.setString(1, playerId.toString()));
    }

    public void evictPlayer(UUID playerId) {
        if (playerId == null) return;
        ownedCache.remove(playerId);
        equippedCache.remove(playerId);
        playerLocks.remove(playerId);
    }

    // ── DB operations ────────────────────────────────────────────────────

    private record CosmeticRow(String cosmeticId, boolean equipped) {}

    private void loadPlayer(UUID playerId) {
        List<CosmeticRow> rows = DatabaseManager.queryList(this.db,
                "SELECT cosmetic_id, equipped FROM player_cosmetics WHERE player_uuid = ?",
                stmt -> stmt.setString(1, playerId.toString()),
                rs -> new CosmeticRow(rs.getString("cosmetic_id"), rs.getBoolean("equipped")));

        List<String> owned = new CopyOnWriteArrayList<>();
        String equipped = NONE_EQUIPPED;
        for (CosmeticRow row : rows) {
            owned.add(row.cosmeticId);
            if (row.equipped) {
                equipped = row.cosmeticId;
            }
        }

        ownedCache.putIfAbsent(playerId, owned);
        equippedCache.putIfAbsent(playerId, equipped);
    }

    private void persistPurchase(UUID playerId, String cosmeticId) {
        DatabaseManager.execute(this.db,
                "INSERT IGNORE INTO player_cosmetics (player_uuid, cosmetic_id, equipped) VALUES (?, ?, FALSE)",
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, cosmeticId);
                });
    }

    private void persistEquipped(UUID playerId, String cosmeticId, boolean equipped) {
        DatabaseManager.execute(this.db,
                "UPDATE player_cosmetics SET equipped = ? WHERE player_uuid = ? AND cosmetic_id = ?",
                stmt -> {
                    stmt.setBoolean(1, equipped);
                    stmt.setString(2, playerId.toString());
                    stmt.setString(3, cosmeticId);
                });
    }
}
