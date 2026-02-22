package io.hyvexa.purge.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.economy.VexaStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    private PurgeSkinStore() {}

    public static PurgeSkinStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
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
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            LOGGER.atInfo().log("PurgeSkinStore initialized (purge_weapon_skins table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge_weapon_skins table");
        }
    }

    public boolean ownsSkin(UUID playerId, String weaponId, String skinId) {
        return getOwnedSkins(playerId, weaponId).contains(skinId);
    }

    public List<String> getOwnedSkins(UUID playerId, String weaponId) {
        if (playerId == null) {
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
        if (playerId == null) {
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
        if (ownsSkin(playerId, weaponId, skinId)) {
            return PurchaseResult.ALREADY_OWNED;
        }
        PurgeSkinDefinition def = PurgeSkinRegistry.getSkin(weaponId, skinId);
        if (def == null) {
            return PurchaseResult.NOT_ENOUGH_VEXA;
        }
        long vexa = VexaStore.getInstance().getVexa(playerId);
        if (vexa < def.getPrice()) {
            return PurchaseResult.NOT_ENOUGH_VEXA;
        }
        VexaStore.getInstance().removeVexa(playerId, def.getPrice());
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
        if (playerId == null) {
            return;
        }
        persistSelection(playerId, weaponId, skinId);
        selectedCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(weaponId, skinId);
    }

    public void deselectSkin(UUID playerId, String weaponId) {
        if (playerId == null) {
            return;
        }
        persistDeselection(playerId, weaponId);
        ConcurrentHashMap<String, String> playerSelected = selectedCache.get(playerId);
        if (playerSelected != null) {
            playerSelected.remove(weaponId);
        }
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            ownedCache.remove(playerId);
            selectedCache.remove(playerId);
        }
    }

    private void loadFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "SELECT weapon_id, skin_id, selected FROM purge_weapon_skins WHERE uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                ConcurrentHashMap<String, List<String>> owned = new ConcurrentHashMap<>();
                ConcurrentHashMap<String, String> selected = new ConcurrentHashMap<>();
                while (rs.next()) {
                    String weaponId = rs.getString("weapon_id");
                    String skinId = rs.getString("skin_id");
                    boolean isSelected = rs.getBoolean("selected");
                    owned.computeIfAbsent(weaponId, k -> Collections.synchronizedList(new ArrayList<>())).add(skinId);
                    if (isSelected) {
                        selected.put(weaponId, skinId);
                    }
                }
                ownedCache.putIfAbsent(playerId, owned);
                selectedCache.putIfAbsent(playerId, selected);
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load weapon skins for " + playerId);
        }
    }

    private void persistPurchase(UUID playerId, String weaponId, String skinId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT IGNORE INTO purge_weapon_skins (uuid, weapon_id, skin_id, selected) VALUES (?, ?, ?, FALSE)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, weaponId);
            stmt.setString(3, skinId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist skin purchase for " + playerId);
        }
    }

    private void persistSelection(UUID playerId, String weaponId, String skinId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        // Deselect all skins for this weapon, then select the chosen one
        String deselectSql = "UPDATE purge_weapon_skins SET selected = FALSE WHERE uuid = ? AND weapon_id = ?";
        String selectSql = "UPDATE purge_weapon_skins SET selected = TRUE WHERE uuid = ? AND weapon_id = ? AND skin_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(deselectSql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                stmt.setString(2, weaponId);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                stmt.setString(2, weaponId);
                stmt.setString(3, skinId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist skin selection for " + playerId);
        }
    }

    private void persistDeselection(UUID playerId, String weaponId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "UPDATE purge_weapon_skins SET selected = FALSE WHERE uuid = ? AND weapon_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, weaponId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist skin deselection for " + playerId);
        }
    }
}
