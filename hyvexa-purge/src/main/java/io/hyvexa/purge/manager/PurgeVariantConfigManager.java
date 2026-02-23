package io.hyvexa.purge.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.purge.data.PurgeVariantConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeVariantConfigManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConcurrentHashMap<String, PurgeVariantConfig> variants = new ConcurrentHashMap<>();

    public PurgeVariantConfigManager() {
        createTable();
        migrateAddNpcType();
        migrateAddScrapReward();
        loadAll();
        seedDefaults();
    }

    public PurgeVariantConfig getVariant(String key) {
        return key != null ? variants.get(key) : null;
    }

    public List<PurgeVariantConfig> getAllVariants() {
        List<PurgeVariantConfig> list = new ArrayList<>(variants.values());
        list.sort(Comparator.comparing(PurgeVariantConfig::key));
        return list;
    }

    public int getVariantCount() {
        return variants.size();
    }

    public boolean isPersistenceAvailable() {
        return DatabaseManager.getInstance().isInitialized();
    }

    public boolean addVariant(String key, String label, int hp, float dmg, double speed) {
        return addVariant(key, label, hp, dmg, speed, "Zombie");
    }

    public boolean addVariant(String key, String label, int hp, float dmg, double speed, String npcType) {
        if (!isPersistenceAvailable() || key == null || key.isBlank()) {
            return false;
        }
        String safeKey = key.toUpperCase().replaceAll("[^A-Z0-9_]", "");
        if (safeKey.isEmpty() || variants.containsKey(safeKey)) {
            return false;
        }
        int safeHp = (int) Math.max(10, Math.round(hp / 10.0) * 10);
        float safeDmg = (float) Math.max(10, Math.round(dmg / 10.0) * 10);
        double safeSpeed = Math.max(0.1, speed);
        String safeNpcType = (npcType != null && !npcType.isBlank()) ? npcType.trim() : "Zombie";

        String sql = "INSERT INTO purge_zombie_variants (variant_key, label, base_health, base_damage, speed_multiplier, npc_type, scrap_reward) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, safeKey);
            stmt.setString(2, label);
            stmt.setInt(3, safeHp);
            stmt.setFloat(4, safeDmg);
            stmt.setDouble(5, safeSpeed);
            stmt.setString(6, safeNpcType);
            stmt.setInt(7, 10);
            stmt.executeUpdate();
            variants.put(safeKey, new PurgeVariantConfig(safeKey, label, safeHp, safeDmg, safeSpeed, safeNpcType, 10));
            return true;
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to add variant " + safeKey);
            return false;
        }
    }

    public boolean removeVariant(String key) {
        if (!isPersistenceAvailable() || key == null) {
            return false;
        }
        if (variants.size() <= 1) {
            return false;
        }
        if (!variants.containsKey(key)) {
            return false;
        }
        String sql = "DELETE FROM purge_zombie_variants WHERE variant_key = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, key);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                variants.remove(key);
                return true;
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove variant " + key);
        }
        return false;
    }

    public boolean adjustHealth(String key, int delta) {
        return adjustField(key, "base_health", delta, (config, d) -> {
            int newVal = (int) Math.max(10, Math.round((config.baseHealth() + d) / 10.0) * 10);
            return new PurgeVariantConfig(config.key(), config.label(), newVal, config.baseDamage(), config.speedMultiplier(), config.npcType(), config.scrapReward());
        });
    }

    public boolean adjustDamage(String key, int delta) {
        return adjustField(key, "base_damage", delta, (config, d) -> {
            float newVal = (float) Math.max(10, Math.round((config.baseDamage() + d) / 10.0) * 10);
            return new PurgeVariantConfig(config.key(), config.label(), config.baseHealth(), newVal, config.speedMultiplier(), config.npcType(), config.scrapReward());
        });
    }

    public boolean adjustScrap(String key, int delta) {
        return adjustField(key, "scrap_reward", delta, (config, d) -> {
            int newVal = (int) Math.max(0, Math.round((config.scrapReward() + d) / 10.0) * 10);
            return new PurgeVariantConfig(config.key(), config.label(), config.baseHealth(), config.baseDamage(), config.speedMultiplier(), config.npcType(), newVal);
        });
    }

    public boolean adjustSpeed(String key, double delta) {
        if (!isPersistenceAvailable() || key == null) {
            return false;
        }
        PurgeVariantConfig config = variants.get(key);
        if (config == null) {
            return false;
        }
        double newVal = Math.max(0.1, Math.round((config.speedMultiplier() + delta) * 10.0) / 10.0);
        String sql = "UPDATE purge_zombie_variants SET speed_multiplier = ? WHERE variant_key = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setDouble(1, newVal);
            stmt.setString(2, key);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                variants.put(key, new PurgeVariantConfig(config.key(), config.label(), config.baseHealth(), config.baseDamage(), newVal, config.npcType(), config.scrapReward()));
                return true;
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to adjust speed for " + key);
        }
        return false;
    }

    private interface FieldUpdater {
        PurgeVariantConfig apply(PurgeVariantConfig config, int delta);
    }

    private boolean adjustField(String key, String column, int delta, FieldUpdater updater) {
        if (!isPersistenceAvailable() || key == null || delta == 0) {
            return false;
        }
        PurgeVariantConfig config = variants.get(key);
        if (config == null) {
            return false;
        }
        PurgeVariantConfig updated = updater.apply(config, delta);
        Number newVal;
        if ("base_health".equals(column)) {
            newVal = updated.baseHealth();
        } else if ("scrap_reward".equals(column)) {
            newVal = updated.scrapReward();
        } else {
            newVal = updated.baseDamage();
        }
        String sql = "UPDATE purge_zombie_variants SET " + column + " = ? WHERE variant_key = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            if (newVal instanceof Integer) {
                stmt.setInt(1, newVal.intValue());
            } else {
                stmt.setFloat(1, newVal.floatValue());
            }
            stmt.setString(2, key);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                variants.put(key, updated);
                return true;
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to adjust " + column + " for " + key);
        }
        return false;
    }

    public boolean setNpcType(String key, String npcType) {
        if (!isPersistenceAvailable() || key == null) {
            return false;
        }
        PurgeVariantConfig config = variants.get(key);
        if (config == null) {
            return false;
        }
        String safeType = (npcType != null && !npcType.isBlank()) ? npcType.trim() : "Zombie";
        String sql = "UPDATE purge_zombie_variants SET npc_type = ? WHERE variant_key = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, safeType);
            stmt.setString(2, key);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                variants.put(key, new PurgeVariantConfig(config.key(), config.label(), config.baseHealth(),
                        config.baseDamage(), config.speedMultiplier(), safeType, config.scrapReward()));
                return true;
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to set npc_type for " + key);
        }
        return false;
    }

    public boolean setLabel(String key, String label) {
        if (!isPersistenceAvailable() || key == null) {
            return false;
        }
        PurgeVariantConfig config = variants.get(key);
        if (config == null) {
            return false;
        }
        String safeLabel = label != null ? label.trim() : "";
        if (safeLabel.isEmpty() || safeLabel.length() > 64) {
            return false;
        }
        String sql = "UPDATE purge_zombie_variants SET label = ? WHERE variant_key = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, safeLabel);
            stmt.setString(2, key);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                variants.put(key, new PurgeVariantConfig(config.key(), safeLabel, config.baseHealth(),
                        config.baseDamage(), config.speedMultiplier(), config.npcType(), config.scrapReward()));
                return true;
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to set label for " + key);
        }
        return false;
    }

    private void createTable() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS purge_zombie_variants ("
                + "variant_key VARCHAR(32) NOT NULL PRIMARY KEY, "
                + "label VARCHAR(64) NOT NULL, "
                + "base_health INT NOT NULL DEFAULT 50, "
                + "base_damage FLOAT NOT NULL DEFAULT 20, "
                + "speed_multiplier DOUBLE NOT NULL DEFAULT 1.0, "
                + "npc_type VARCHAR(64) NOT NULL DEFAULT 'Zombie', "
                + "scrap_reward INT NOT NULL DEFAULT 10"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge_zombie_variants table");
        }
    }

    private void migrateAddNpcType() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, "purge_zombie_variants", "npc_type")) {
            if (rs.next()) {
                return; // column already exists
            }
        } catch (SQLException e) {
            LOGGER.atFine().log("Could not check for npc_type column: " + e.getMessage());
            return;
        }
        String sql = "ALTER TABLE purge_zombie_variants ADD COLUMN npc_type VARCHAR(64) NOT NULL DEFAULT 'Zombie'";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            LOGGER.atInfo().log("Added npc_type column to purge_zombie_variants");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to add npc_type column");
        }
    }

    private void migrateAddScrapReward() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, "purge_zombie_variants", "scrap_reward")) {
            if (rs.next()) {
                return; // column already exists
            }
        } catch (SQLException e) {
            LOGGER.atFine().log("Could not check for scrap_reward column: " + e.getMessage());
            return;
        }
        String sql = "ALTER TABLE purge_zombie_variants ADD COLUMN scrap_reward INT NOT NULL DEFAULT 10";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            LOGGER.atInfo().log("Added scrap_reward column to purge_zombie_variants");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to add scrap_reward column");
        }
    }

    private void loadAll() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "SELECT variant_key, label, base_health, base_damage, speed_multiplier, npc_type, scrap_reward FROM purge_zombie_variants ORDER BY variant_key ASC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("variant_key");
                    String npcType = rs.getString("npc_type");
                    variants.put(key, new PurgeVariantConfig(
                            key,
                            rs.getString("label"),
                            Math.max(10, rs.getInt("base_health")),
                            Math.max(10f, rs.getFloat("base_damage")),
                            Math.max(0.1, rs.getDouble("speed_multiplier")),
                            npcType != null ? npcType : "Zombie",
                            Math.max(0, rs.getInt("scrap_reward"))
                    ));
                }
            }
            LOGGER.atInfo().log("Loaded " + variants.size() + " zombie variant configs");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load zombie variants");
        }
    }

    private void seedDefaults() {
        if (!isPersistenceAvailable() || !variants.isEmpty()) {
            return;
        }
        LOGGER.atInfo().log("Seeding default zombie variants (SLOW, NORMAL, FAST)");
        addVariant("SLOW", "Slow", 50, 20f, 0.9);
        addVariant("NORMAL", "Normal", 50, 20f, 1.0);
        addVariant("FAST", "Fast", 50, 20f, 1.2);
    }
}
