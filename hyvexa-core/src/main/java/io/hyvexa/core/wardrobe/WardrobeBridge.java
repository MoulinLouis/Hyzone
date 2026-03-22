package io.hyvexa.core.wardrobe;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import io.hyvexa.core.analytics.PlayerAnalytics;
import io.hyvexa.core.cosmetic.CosmeticStore;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.economy.CurrencyBridge;
import io.hyvexa.core.economy.FeatherStore;
import io.hyvexa.core.economy.VexaStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bridge between Hyvexa economy and the Wardrobe mod's permission-based locking.
 * Handles purchasing wardrobe cosmetics (vexa deduction + permission grant)
 * and re-granting permissions on login.
 */
public class WardrobeBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final WardrobeBridge INSTANCE = new WardrobeBridge();

    /** All wardrobe cosmetics available for purchase. Populated by initialize(). */
    private volatile List<WardrobeCosmeticDef> cosmetics = List.of();
    private volatile PlayerAnalytics analytics;

    /** Maps fine-grained categories to broad shop groups. */
    private static final Map<String, String> CATEGORY_GROUPS = Map.ofEntries(
            Map.entry("Face", "Head"),
            Map.entry("Head", "Head"),
            Map.entry("Mask", "Head"),
            Map.entry("Horns", "Head"),
            Map.entry("Mouth", "Head"),
            Map.entry("Ears", "Head"),
            Map.entry("Overpants", "Clothes"),
            Map.entry("Overtop", "Clothes"),
            Map.entry("Pants", "Clothes"),
            Map.entry("Shoes", "Clothes"),
            Map.entry("Undertop", "Clothes"),
            Map.entry("Badge", "Accessories"),
            Map.entry("Tail", "Accessories"),
            Map.entry("Cape", "Accessories")
    );

    private static final List<String> GROUP_ORDER = List.of("Head", "Clothes", "Accessories");

    private static String getCategoryGroup(String category) {
        return CATEGORY_GROUPS.getOrDefault(category, category);
    }

    private WardrobeBridge() {
    }

    public static WardrobeBridge getInstance() {
        return INSTANCE;
    }

    public void setAnalytics(PlayerAnalytics analytics) {
        this.analytics = analytics;
    }

    public void initialize() {
        this.cosmetics = List.copyOf(CosmeticConfigLoader.load());
        LOGGER.atInfo().log("Loaded " + cosmetics.size() + " wardrobe cosmetics");
    }

    public List<WardrobeCosmeticDef> getAllCosmetics() {
        return cosmetics;
    }

    public WardrobeCosmeticDef findById(String id) {
        for (WardrobeCosmeticDef def : cosmetics) {
            if (def.id().equals(id)) return def;
        }
        return null;
    }

    /** Returns the broad group names in display order. */
    public List<String> getCategories() {
        return GROUP_ORDER;
    }

    /** Returns cosmetics filtered by broad group (null = all). */
    public List<WardrobeCosmeticDef> getCosmeticsByCategory(String group) {
        if (group == null) return cosmetics;
        List<WardrobeCosmeticDef> result = new ArrayList<>();
        for (WardrobeCosmeticDef def : cosmetics) {
            if (group.equals(getCategoryGroup(def.category()))) result.add(def);
        }
        return result;
    }

    /**
     * Purchase a wardrobe cosmetic: deduct currency + record ownership atomically in one
     * DB transaction, then grant permission and log analytics outside the transaction.
     */
    public PurchaseResult purchase(UUID playerId, String cosmeticId) {
        WardrobeCosmeticDef def = findById(cosmeticId);
        if (def == null) return error("Unknown cosmetic: " + cosmeticId);

        CosmeticShopConfigStore configStore = CosmeticShopConfigStore.getInstance();
        if (!configStore.isAvailable(cosmeticId)) {
            return error("This cosmetic is not currently available for purchase.");
        }

        if (CosmeticStore.getInstance().ownsCosmetic(playerId, cosmeticId)) {
            return error("You already own " + def.displayName() + "!");
        }

        int price = configStore.getPrice(cosmeticId);
        String currency = configStore.getCurrency(cosmeticId);
        long balance = CurrencyBridge.getBalance(currency, playerId);
        if (balance < price) {
            return error("Not enough " + currency + "! You need " + price + " but have " + balance + ".");
        }

        // Atomic: deduct currency + insert cosmetic ownership in one transaction
        Boolean purchased = DatabaseManager.getInstance().withTransaction(conn -> {
            if (!deductCurrencyRow(conn, currency, playerId, price)) {
                return false;
            }
            insertOwnedCosmetic(conn, playerId, cosmeticId);
            return true;
        }, null);
        if (purchased == null) {
            return error("Purchase failed, please try again.");
        }
        if (!purchased) {
            return error("Not enough " + currency + "! You need " + price + " but have "
                    + CurrencyBridge.getBalance(currency, playerId) + ".");
        }

        // Update in-memory caches after successful commit
        evictCurrencyCache(currency, playerId);
        CosmeticStore.getInstance().purchaseCosmetic(playerId, cosmeticId);

        grantPermission(playerId, def.permissionNode());
        if (analytics != null) {
            analytics.logPurchase(playerId, cosmeticId, price, currency, "wardrobe");
        }

        LOGGER.atInfo().log("Player " + playerId + " purchased wardrobe cosmetic: " + cosmeticId);
        return success("Purchased " + def.displayName() + " for " + price + " "
                + currency + "! Open /wardrobe to equip it.");
    }

    private boolean deductCurrencyRow(Connection conn, String currency, UUID playerId, long price) throws SQLException {
        String table;
        String column;
        switch (currency) {
            case "vexa" -> { table = "player_vexa"; column = "vexa"; }
            case "feathers" -> { table = "player_feathers"; column = "feathers"; }
            default -> throw new IllegalArgumentException("Unknown currency: " + currency);
        }
        String sql = "UPDATE " + table + " SET " + column + " = " + column + " - ? "
                + "WHERE uuid = ? AND " + column + " >= ?";
        try (PreparedStatement stmt = DatabaseManager.prepare(conn, sql)) {
            stmt.setLong(1, price);
            stmt.setString(2, playerId.toString());
            stmt.setLong(3, price);
            return stmt.executeUpdate() > 0;
        }
    }

    private void insertOwnedCosmetic(Connection conn, UUID playerId, String cosmeticId) throws SQLException {
        String sql = "INSERT IGNORE INTO player_cosmetics (player_uuid, cosmetic_id, equipped) VALUES (?, ?, FALSE)";
        try (PreparedStatement stmt = DatabaseManager.prepare(conn, sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, cosmeticId);
            stmt.executeUpdate();
        }
    }

    private void evictCurrencyCache(String currency, UUID playerId) {
        switch (currency) {
            case "vexa" -> VexaStore.getInstance().evictPlayer(playerId);
            case "feathers" -> FeatherStore.getInstance().evictPlayer(playerId);
        }
    }

    /**
     * Re-grant all wardrobe permissions for owned cosmetics. Call on player login.
     */
    public void regrantPermissions(UUID playerId) {
        for (WardrobeCosmeticDef def : cosmetics) {
            if (CosmeticStore.getInstance().ownsCosmetic(playerId, def.id())) {
                grantPermission(playerId, def.permissionNode());
            }
        }
    }

    /**
     * Reset all wardrobe cosmetics: clear DB records and revoke all permissions.
     */
    public void resetAll(UUID playerId) {
        for (WardrobeCosmeticDef def : cosmetics) {
            revokePermission(playerId, def.permissionNode());
        }
        CosmeticStore.getInstance().resetAllCosmetics(playerId);
        LOGGER.atInfo().log("Reset all wardrobe cosmetics for player " + playerId);
    }

    @SuppressWarnings("removal")
    private void revokePermission(UUID playerId, String permissionNode) {
        PermissionsModule permissions = PermissionsModule.get();
        if (permissions == null) return;
        permissions.removeUserPermission(playerId, Set.of(permissionNode));
    }

    @SuppressWarnings("removal")
    private void grantPermission(UUID playerId, String permissionNode) {
        PermissionsModule permissions = PermissionsModule.get();
        if (permissions == null) {
            LOGGER.atWarning().log("PermissionsModule not available, cannot grant: " + permissionNode);
            return;
        }
        permissions.addUserPermission(playerId, Set.of(permissionNode));
    }

    private static PurchaseResult success(String message) {
        return new PurchaseResult(true, message);
    }

    private static PurchaseResult error(String message) {
        return new PurchaseResult(false, message);
    }

    public record PurchaseResult(boolean success, String message) {
    }

    public record WardrobeCosmeticDef(String id, String displayName,
                                       String permissionNode, String category, String iconKey,
                                       String iconPath) {
    }
}
