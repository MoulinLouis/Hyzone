package io.hyvexa.common.skin;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.economy.CurrencyStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeSkinStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final PurgeSkinStore INSTANCE = new PurgeSkinStore();

    // Cache: playerId -> (weaponId -> list of owned skinIds)
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, List<String>>> ownedCache = new ConcurrentHashMap<>();
    // Cache: playerId -> (weaponId -> selected skinId or null)
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, String>> selectedCache = new ConcurrentHashMap<>();

    public enum PurchaseResult {
        SUCCESS,
        ALREADY_OWNED,
        NOT_ENOUGH_VEXA
    }

    private final ConnectionProvider db;
    private volatile CurrencyStore vexaStore;

    private PurgeSkinStore() {
        this.db = DatabaseManager.getInstance();
    }

    public void setVexaStore(CurrencyStore vexaStore) {
        this.vexaStore = vexaStore;
    }

    public static PurgeSkinStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, PurgeSkinStore will use in-memory mode");
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS purge_weapon_skins ("
                + "uuid VARCHAR(36) NOT NULL, "
                + "weapon_id VARCHAR(32) NOT NULL, "
                + "skin_id VARCHAR(64) NOT NULL, "
                + "selected BOOLEAN NOT NULL DEFAULT FALSE, "
                + "PRIMARY KEY (uuid, weapon_id, skin_id)"
                + ") ENGINE=InnoDB";
        try (Connection conn = this.db.getConnection();
             PreparedStatement stmt = DatabaseManager.prepare(conn, sql)) {
            stmt.executeUpdate();
            LOGGER.atInfo().log("PurgeSkinStore initialized (purge_weapon_skins table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge_weapon_skins table");
        }
    }

    public boolean ownsSkin(UUID playerId, String weaponId, String skinId) {
        if (weaponId == null || weaponId.isBlank() || skinId == null || skinId.isBlank()) {
            return false;
        }
        return getOwnedSkins(playerId, weaponId).contains(skinId);
    }

    public List<String> getOwnedSkins(UUID playerId, String weaponId) {
        if (playerId == null || weaponId == null || weaponId.isBlank()) {
            return List.of();
        }
        ConcurrentHashMap<String, List<String>> playerOwned = ownedCache.get(playerId);
        if (playerOwned != null && playerOwned.containsKey(weaponId)) {
            return playerOwned.get(weaponId);
        }
        loadFromDatabase(playerId);
        playerOwned = ownedCache.get(playerId);
        if (playerOwned != null && playerOwned.containsKey(weaponId)) {
            return playerOwned.get(weaponId);
        }
        return List.of();
    }

    public String getSelectedSkin(UUID playerId, String weaponId) {
        if (playerId == null || weaponId == null || weaponId.isBlank()) {
            return null;
        }
        ConcurrentHashMap<String, String> playerSelected = selectedCache.get(playerId);
        if (playerSelected != null) {
            return playerSelected.get(weaponId);
        }
        loadFromDatabase(playerId);
        playerSelected = selectedCache.get(playerId);
        if (playerSelected != null) {
            return playerSelected.get(weaponId);
        }
        return null;
    }

    public synchronized PurchaseResult purchaseSkin(UUID playerId, String weaponId, String skinId) {
        if (playerId == null || weaponId == null || weaponId.isBlank() || skinId == null || skinId.isBlank()) {
            return PurchaseResult.NOT_ENOUGH_VEXA;
        }
        if (ownsSkin(playerId, weaponId, skinId)) {
            return PurchaseResult.ALREADY_OWNED;
        }
        PurgeSkinDefinition def = PurgeSkinRegistry.getSkin(weaponId, skinId);
        if (def == null) {
            return PurchaseResult.NOT_ENOUGH_VEXA;
        }
        if (vexaStore == null) {
            return PurchaseResult.NOT_ENOUGH_VEXA;
        }
        long vexa = vexaStore.getBalance(playerId);
        if (vexa < def.getPrice()) {
            return PurchaseResult.NOT_ENOUGH_VEXA;
        }
        vexaStore.removeBalance(playerId, def.getPrice());
        persistPurchase(playerId, weaponId, skinId);
        // Update cache
        ownedCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(weaponId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(skinId);
        // Auto-select the newly purchased skin
        selectSkin(playerId, weaponId, skinId);
        return PurchaseResult.SUCCESS;
    }

    public void selectSkin(UUID playerId, String weaponId, String skinId) {
        if (playerId == null || weaponId == null || weaponId.isBlank() || skinId == null || skinId.isBlank()) {
            return;
        }
        // Verify the skin exists in the registry and the player owns it
        if (PurgeSkinRegistry.getSkin(weaponId, skinId) == null || !ownsSkin(playerId, weaponId, skinId)) {
            return;
        }
        persistSelection(playerId, weaponId, skinId);
        selectedCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(weaponId, skinId);
    }

    public void deselectSkin(UUID playerId, String weaponId) {
        if (playerId == null || weaponId == null || weaponId.isBlank()) {
            return;
        }
        persistDeselection(playerId, weaponId);
        ConcurrentHashMap<String, String> playerSelected = selectedCache.get(playerId);
        if (playerSelected != null) {
            playerSelected.remove(weaponId);
        }
    }

    public void resetPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        ownedCache.remove(playerId);
        selectedCache.remove(playerId);
        DatabaseManager.execute(this.db,
                "DELETE FROM purge_weapon_skins WHERE uuid = ?",
                stmt -> stmt.setString(1, playerId.toString()));
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            ownedCache.remove(playerId);
            selectedCache.remove(playerId);
        }
    }

    private record SkinRow(String weaponId, String skinId, boolean selected) {}

    private void loadFromDatabase(UUID playerId) {
        List<SkinRow> rows = DatabaseManager.queryList(this.db,
                "SELECT weapon_id, skin_id, selected FROM purge_weapon_skins WHERE uuid = ?",
                stmt -> stmt.setString(1, playerId.toString()),
                rs -> new SkinRow(rs.getString("weapon_id"), rs.getString("skin_id"), rs.getBoolean("selected")));

        ConcurrentHashMap<String, List<String>> owned = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, String> selected = new ConcurrentHashMap<>();
        for (SkinRow row : rows) {
            owned.computeIfAbsent(row.weaponId, k -> Collections.synchronizedList(new ArrayList<>())).add(row.skinId);
            if (row.selected) {
                selected.put(row.weaponId, row.skinId);
            }
        }
        ownedCache.putIfAbsent(playerId, owned);
        selectedCache.putIfAbsent(playerId, selected);
    }

    private void persistPurchase(UUID playerId, String weaponId, String skinId) {
        DatabaseManager.execute(this.db,
                "INSERT IGNORE INTO purge_weapon_skins (uuid, weapon_id, skin_id, selected) VALUES (?, ?, ?, FALSE)",
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, weaponId);
                    stmt.setString(3, skinId);
                });
    }

    private void persistSelection(UUID playerId, String weaponId, String skinId) {
        // Deselect all skins for this weapon, then select the chosen one
        DatabaseManager.execute(this.db,
                "UPDATE purge_weapon_skins SET selected = FALSE WHERE uuid = ? AND weapon_id = ?",
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, weaponId);
                });
        DatabaseManager.execute(this.db,
                "UPDATE purge_weapon_skins SET selected = TRUE WHERE uuid = ? AND weapon_id = ? AND skin_id = ?",
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, weaponId);
                    stmt.setString(3, skinId);
                });
    }

    private void persistDeselection(UUID playerId, String weaponId) {
        DatabaseManager.execute(this.db,
                "UPDATE purge_weapon_skins SET selected = FALSE WHERE uuid = ? AND weapon_id = ?",
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, weaponId);
                });
    }
}
