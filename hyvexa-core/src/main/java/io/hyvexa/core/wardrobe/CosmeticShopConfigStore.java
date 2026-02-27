package io.hyvexa.core.wardrobe;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global config store for wardrobe cosmetic availability and pricing.
 * Admins toggle which cosmetics appear in the shop and set prices/currency.
 */
public class CosmeticShopConfigStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final CosmeticShopConfigStore INSTANCE = new CosmeticShopConfigStore();

    private final ConcurrentHashMap<String, CosmeticConfig> configs = new ConcurrentHashMap<>();

    private CosmeticShopConfigStore() {}

    public static CosmeticShopConfigStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, CosmeticShopConfigStore will use in-memory mode");
            return;
        }
        String createSql = "CREATE TABLE IF NOT EXISTS cosmetic_shop_config ("
                + "cosmetic_id VARCHAR(64) NOT NULL PRIMARY KEY, "
                + "available BOOLEAN NOT NULL DEFAULT FALSE, "
                + "price INT NOT NULL DEFAULT 0, "
                + "currency VARCHAR(16) NOT NULL DEFAULT 'vexa'"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(createSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            LOGGER.atInfo().log("CosmeticShopConfigStore initialized (cosmetic_shop_config table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create cosmetic_shop_config table");
        }
        loadAll();
    }

    private void loadAll() {
        if (!DatabaseManager.getInstance().isInitialized()) return;
        // Clear stale entries so removed DB rows don't linger in cache
        configs.clear();
        String sql = "SELECT cosmetic_id, available, price, currency FROM cosmetic_shop_config";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("cosmetic_id");
                    boolean available = rs.getBoolean("available");
                    int price = rs.getInt("price");
                    String currency = rs.getString("currency");
                    configs.put(id, new CosmeticConfig(id, available, price, currency));
                }
            }
            LOGGER.atInfo().log("Loaded " + configs.size() + " cosmetic shop configs");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load cosmetic shop configs");
        }
    }

    public boolean isAvailable(String cosmeticId) {
        CosmeticConfig cfg = configs.get(cosmeticId);
        return cfg != null && cfg.available;
    }

    public int getPrice(String cosmeticId) {
        CosmeticConfig cfg = configs.get(cosmeticId);
        return cfg != null ? cfg.price : 0;
    }

    public String getCurrency(String cosmeticId) {
        CosmeticConfig cfg = configs.get(cosmeticId);
        return cfg != null ? cfg.currency : "vexa";
    }

    public CosmeticConfig getConfig(String cosmeticId) {
        return configs.get(cosmeticId);
    }

    public List<CosmeticConfig> getAllConfigs() {
        return new ArrayList<>(configs.values());
    }

    public void setConfig(String cosmeticId, boolean available, int price, String currency) {
        CosmeticConfig cfg = new CosmeticConfig(cosmeticId, available, Math.max(0, price), currency);
        configs.put(cosmeticId, cfg);
        persistConfig(cfg);
    }

    public void toggleAvailable(String cosmeticId) {
        CosmeticConfig cfg = configs.get(cosmeticId);
        if (cfg == null) {
            setConfig(cosmeticId, true, 0, "vexa");
        } else {
            setConfig(cosmeticId, !cfg.available, cfg.price, cfg.currency);
        }
    }

    public void adjustPrice(String cosmeticId, int delta) {
        CosmeticConfig cfg = configs.get(cosmeticId);
        if (cfg == null) {
            setConfig(cosmeticId, false, Math.max(0, delta), "vexa");
        } else {
            setConfig(cosmeticId, cfg.available, Math.max(0, cfg.price + delta), cfg.currency);
        }
    }

    public void toggleCurrency(String cosmeticId) {
        CosmeticConfig cfg = configs.get(cosmeticId);
        if (cfg == null) {
            setConfig(cosmeticId, false, 0, "feathers");
        } else {
            String newCurrency = "vexa".equals(cfg.currency) ? "feathers" : "vexa";
            setConfig(cosmeticId, cfg.available, cfg.price, newCurrency);
        }
    }

    private void persistConfig(CosmeticConfig cfg) {
        if (!DatabaseManager.getInstance().isInitialized()) return;
        String sql = "INSERT INTO cosmetic_shop_config (cosmetic_id, available, price, currency) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE available = ?, price = ?, currency = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, cfg.cosmeticId);
            stmt.setBoolean(2, cfg.available);
            stmt.setInt(3, cfg.price);
            stmt.setString(4, cfg.currency);
            stmt.setBoolean(5, cfg.available);
            stmt.setInt(6, cfg.price);
            stmt.setString(7, cfg.currency);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist cosmetic config for " + cfg.cosmeticId);
        }
    }

    public record CosmeticConfig(String cosmeticId, boolean available, int price, String currency) {}
}
