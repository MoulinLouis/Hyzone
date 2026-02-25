package io.hyvexa.core.wardrobe;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import io.hyvexa.core.cosmetic.CosmeticStore;
import io.hyvexa.core.economy.CurrencyBridge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    /** All wardrobe cosmetics available for purchase. */
    private static final List<WardrobeCosmeticDef> COSMETICS = List.of(
            new WardrobeCosmeticDef("WD_Badge_Hyvexa", "Hyvexa Badge", 100,
                    "hyvexa.cosmetic.badge_hyvexa", "Badge", "BadgeHyvexa")
    );

    private WardrobeBridge() {
    }

    public static WardrobeBridge getInstance() {
        return INSTANCE;
    }

    public List<WardrobeCosmeticDef> getAllCosmetics() {
        return COSMETICS;
    }

    public WardrobeCosmeticDef findById(String id) {
        for (WardrobeCosmeticDef def : COSMETICS) {
            if (def.id().equals(id)) return def;
        }
        return null;
    }

    /** Returns distinct ordered category list. */
    public List<String> getCategories() {
        LinkedHashSet<String> cats = new LinkedHashSet<>();
        for (WardrobeCosmeticDef def : COSMETICS) {
            cats.add(def.category());
        }
        return new ArrayList<>(cats);
    }

    /** Returns cosmetics filtered by category (null = all). */
    public List<WardrobeCosmeticDef> getCosmeticsByCategory(String category) {
        if (category == null) return COSMETICS;
        List<WardrobeCosmeticDef> result = new ArrayList<>();
        for (WardrobeCosmeticDef def : COSMETICS) {
            if (def.category().equals(category)) result.add(def);
        }
        return result;
    }

    /**
     * Purchase a wardrobe cosmetic: deduct currency, record ownership, grant permission.
     * Uses CosmeticShopConfigStore for price/currency and CurrencyBridge for deduction.
     * Returns a result message for the player.
     */
    public String purchase(UUID playerId, String cosmeticId) {
        WardrobeCosmeticDef def = findById(cosmeticId);
        if (def == null) return "Unknown cosmetic: " + cosmeticId;

        CosmeticShopConfigStore configStore = CosmeticShopConfigStore.getInstance();
        if (!configStore.isAvailable(cosmeticId)) {
            return "This cosmetic is not currently available for purchase.";
        }

        if (CosmeticStore.getInstance().ownsCosmetic(playerId, cosmeticId)) {
            return "You already own " + def.displayName() + "!";
        }

        int price = configStore.getPrice(cosmeticId);
        String currency = configStore.getCurrency(cosmeticId);
        long balance = CurrencyBridge.getBalance(currency, playerId);
        if (balance < price) {
            return "Not enough " + currency + "! You need " + price + " but have " + balance + ".";
        }

        CurrencyBridge.deduct(currency, playerId, price);
        CosmeticStore.getInstance().purchaseCosmetic(playerId, cosmeticId);
        grantPermission(playerId, def.permissionNode());

        LOGGER.atInfo().log("Player " + playerId + " purchased wardrobe cosmetic: " + cosmeticId);
        return "Purchased " + def.displayName() + " for " + price + " " + currency + "! Open /wardrobe to equip it.";
    }

    /**
     * Re-grant all wardrobe permissions for owned cosmetics. Call on player login.
     */
    public void regrantPermissions(UUID playerId) {
        for (WardrobeCosmeticDef def : COSMETICS) {
            if (CosmeticStore.getInstance().ownsCosmetic(playerId, def.id())) {
                grantPermission(playerId, def.permissionNode());
            }
        }
    }

    /**
     * Reset all wardrobe cosmetics: clear DB records and revoke all permissions.
     */
    public void resetAll(UUID playerId) {
        for (WardrobeCosmeticDef def : COSMETICS) {
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

    public record WardrobeCosmeticDef(String id, String displayName, int price,
                                       String permissionNode, String category, String iconKey) {
    }
}
