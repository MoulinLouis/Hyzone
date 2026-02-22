package io.hyvexa.core.cosmetic;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence layer for player cosmetics. Singleton shared across all modules.
 * Follows the same lazy-load + immediate-write pattern as VexaStore.
 */
public class CosmeticStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final CosmeticStore INSTANCE = new CosmeticStore();

    /** Per-player cache: list of owned cosmetic IDs. */
    private final ConcurrentHashMap<UUID, List<String>> ownedCache = new ConcurrentHashMap<>();
    /** Per-player cache: currently equipped cosmetic ID (null = none). */
    private final ConcurrentHashMap<UUID, String> equippedCache = new ConcurrentHashMap<>();
    /** Sentinel value for "loaded but nothing equipped". */
    private static final String NONE_EQUIPPED = "";

    private CosmeticStore() {
    }

    public static CosmeticStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, CosmeticStore will use in-memory mode");
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS player_cosmetics ("
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "cosmetic_id VARCHAR(64) NOT NULL, "
                + "equipped BOOLEAN NOT NULL DEFAULT FALSE, "
                + "PRIMARY KEY (player_uuid, cosmetic_id)"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
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
        ownedCache.computeIfAbsent(playerId, k -> Collections.synchronizedList(new ArrayList<>()));
        List<String> owned = ownedCache.get(playerId);
        if (!owned.contains(cosmeticId)) {
            owned.add(cosmeticId);
        }
        // Persist
        persistPurchase(playerId, cosmeticId);
    }

    public void equipCosmetic(UUID playerId, String cosmeticId) {
        if (playerId == null || cosmeticId == null) return;
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

    public void evictPlayer(UUID playerId) {
        if (playerId == null) return;
        ownedCache.remove(playerId);
        equippedCache.remove(playerId);
    }

    // ── DB operations ────────────────────────────────────────────────────

    private void loadPlayer(UUID playerId) {
        List<String> owned = Collections.synchronizedList(new ArrayList<>());
        String equipped = NONE_EQUIPPED;

        if (DatabaseManager.getInstance().isInitialized()) {
            String sql = "SELECT cosmetic_id, equipped FROM player_cosmetics WHERE player_uuid = ?";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("cosmetic_id");
                        owned.add(id);
                        if (rs.getBoolean("equipped")) {
                            equipped = id;
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to load cosmetics for " + playerId);
            }
        }

        ownedCache.putIfAbsent(playerId, owned);
        equippedCache.putIfAbsent(playerId, equipped);
    }

    private void persistPurchase(UUID playerId, String cosmeticId) {
        if (!DatabaseManager.getInstance().isInitialized()) return;
        String sql = "INSERT IGNORE INTO player_cosmetics (player_uuid, cosmetic_id, equipped) VALUES (?, ?, FALSE)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, cosmeticId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist cosmetic purchase for " + playerId);
        }
    }

    private void persistEquipped(UUID playerId, String cosmeticId, boolean equipped) {
        if (!DatabaseManager.getInstance().isInitialized()) return;
        String sql = "UPDATE player_cosmetics SET equipped = ? WHERE player_uuid = ? AND cosmetic_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setBoolean(1, equipped);
            stmt.setString(2, playerId.toString());
            stmt.setString(3, cosmeticId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist equipped state for " + playerId);
        }
    }
}
