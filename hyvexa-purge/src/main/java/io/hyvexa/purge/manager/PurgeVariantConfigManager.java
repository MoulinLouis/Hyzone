package io.hyvexa.purge.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.purge.data.PurgeVariantConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeVariantConfigManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConnectionProvider db;
    private final ConcurrentHashMap<String, PurgeVariantConfig> variants = new ConcurrentHashMap<>();

    public PurgeVariantConfigManager(ConnectionProvider db) {
        this.db = db;
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
        return this.db.isInitialized();
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
        boolean success = DatabaseManager.execute(this.db, sql, stmt -> {
            stmt.setString(1, safeKey);
            stmt.setString(2, label);
            stmt.setInt(3, safeHp);
            stmt.setFloat(4, safeDmg);
            stmt.setDouble(5, safeSpeed);
            stmt.setString(6, safeNpcType);
            stmt.setInt(7, 10);
        });
        if (success) {
            variants.put(safeKey, new PurgeVariantConfig(safeKey, label, safeHp, safeDmg, safeSpeed, safeNpcType, 10));
        }
        return success;
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
        int rows = DatabaseManager.executeCount(this.db, sql, stmt -> stmt.setString(1, key));
        if (rows > 0) {
            variants.remove(key);
            return true;
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
        int rows = DatabaseManager.executeCount(this.db, sql, stmt -> {
            stmt.setDouble(1, newVal);
            stmt.setString(2, key);
        });
        if (rows > 0) {
            variants.put(key, new PurgeVariantConfig(config.key(), config.label(), config.baseHealth(), config.baseDamage(), newVal, config.npcType(), config.scrapReward()));
            return true;
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
        int rows = DatabaseManager.executeCount(this.db, sql, stmt -> {
            if (newVal instanceof Integer) {
                stmt.setInt(1, newVal.intValue());
            } else {
                stmt.setFloat(1, newVal.floatValue());
            }
            stmt.setString(2, key);
        });
        if (rows > 0) {
            variants.put(key, updated);
            return true;
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
        int rows = DatabaseManager.executeCount(this.db, sql, stmt -> {
            stmt.setString(1, safeType);
            stmt.setString(2, key);
        });
        if (rows > 0) {
            variants.put(key, new PurgeVariantConfig(config.key(), config.label(), config.baseHealth(),
                    config.baseDamage(), config.speedMultiplier(), safeType, config.scrapReward()));
            return true;
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
        int rows = DatabaseManager.executeCount(this.db, sql, stmt -> {
            stmt.setString(1, safeLabel);
            stmt.setString(2, key);
        });
        if (rows > 0) {
            variants.put(key, new PurgeVariantConfig(config.key(), safeLabel, config.baseHealth(),
                    config.baseDamage(), config.speedMultiplier(), config.npcType(), config.scrapReward()));
            return true;
        }
        return false;
    }

    private void loadAll() {
        if (!this.db.isInitialized()) {
            return;
        }
        String sql = "SELECT variant_key, label, base_health, base_damage, speed_multiplier, npc_type, scrap_reward FROM purge_zombie_variants ORDER BY variant_key ASC";
        List<PurgeVariantConfig> loaded = DatabaseManager.queryList(this.db, sql, rs -> {
            String key = rs.getString("variant_key");
            String npcType = rs.getString("npc_type");
            return new PurgeVariantConfig(
                    key,
                    rs.getString("label"),
                    Math.max(10, rs.getInt("base_health")),
                    Math.max(10f, rs.getFloat("base_damage")),
                    Math.max(0.1, rs.getDouble("speed_multiplier")),
                    npcType != null ? npcType : "Zombie",
                    Math.max(0, rs.getInt("scrap_reward"))
            );
        });
        for (PurgeVariantConfig config : loaded) {
            variants.put(config.key(), config);
        }
        LOGGER.atInfo().log("Loaded " + variants.size() + " zombie variant configs");
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
