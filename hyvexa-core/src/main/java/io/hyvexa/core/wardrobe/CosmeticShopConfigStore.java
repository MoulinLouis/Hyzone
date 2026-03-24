package io.hyvexa.core.wardrobe;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
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

    private final ConnectionProvider db;
    private final ConcurrentHashMap<String, CosmeticConfig> configs = new ConcurrentHashMap<>();

    private CosmeticShopConfigStore() {
        this.db = DatabaseManager.getInstance();
    }

    public static CosmeticShopConfigStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, CosmeticShopConfigStore will use in-memory mode");
            return;
        }
        String createSql = "CREATE TABLE IF NOT EXISTS cosmetic_shop_config ("
                + "cosmetic_id VARCHAR(64) NOT NULL PRIMARY KEY, "
                + "available BOOLEAN NOT NULL DEFAULT FALSE, "
                + "price INT NOT NULL DEFAULT 0, "
                + "currency VARCHAR(16) NOT NULL DEFAULT 'vexa'"
                + ") ENGINE=InnoDB";
        try (Connection conn = this.db.getConnection();
             PreparedStatement stmt = DatabaseManager.prepare(conn,createSql)) {
            stmt.executeUpdate();
            LOGGER.atInfo().log("CosmeticShopConfigStore initialized (cosmetic_shop_config table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create cosmetic_shop_config table");
        }
        loadAll();
    }

    private void loadAll() {
        // Clear stale entries so removed DB rows don't linger in cache
        configs.clear();
        List<CosmeticConfig> loaded = DatabaseManager.queryList(this.db,
                "SELECT cosmetic_id, available, price, currency FROM cosmetic_shop_config",
                rs -> new CosmeticConfig(
                        rs.getString("cosmetic_id"),
                        rs.getBoolean("available"),
                        rs.getInt("price"),
                        rs.getString("currency")));
        for (CosmeticConfig cfg : loaded) {
            configs.put(cfg.cosmeticId, cfg);
        }
        LOGGER.atInfo().log("Loaded " + configs.size() + " cosmetic shop configs");
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
        DatabaseManager.execute(this.db,
                "INSERT INTO cosmetic_shop_config (cosmetic_id, available, price, currency) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE available = ?, price = ?, currency = ?",
                stmt -> {
                    stmt.setString(1, cfg.cosmeticId);
                    stmt.setBoolean(2, cfg.available);
                    stmt.setInt(3, cfg.price);
                    stmt.setString(4, cfg.currency);
                    stmt.setBoolean(5, cfg.available);
                    stmt.setInt(6, cfg.price);
                    stmt.setString(7, cfg.currency);
                });
    }

    public record CosmeticConfig(String cosmeticId, boolean available, int price, String currency) {}
}
