package io.hyvexa.purge.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeWeaponConfigManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_LEVEL = 10;
    private static final long[] DEFAULT_COSTS = {
            0L, 50L, 100L, 175L, 275L, 400L, 550L, 750L, 1000L, 1300L, 1700L
    };

    private final ConcurrentHashMap<String, List<WeaponLevelEntry>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<Integer, WeaponLevelEntry>> cacheByLevel = new ConcurrentHashMap<>();

    public record WeaponLevelEntry(int level, int damage, long cost) {}

    private record WeaponDefaults(String displayName, List<WeaponLevelEntry> levels) {}

    private static final Map<String, WeaponDefaults> DEFAULT_WEAPONS = buildDefaultWeapons();

    public PurgeWeaponConfigManager() {
        createTable();
        seedDefaults();
        loadAll();
    }

    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    public int getDamage(String weaponId, int level) {
        Map<Integer, WeaponLevelEntry> levels = cacheByLevel.get(weaponId);
        if (levels == null) {
            return getDefaultDamage(weaponId, level);
        }
        WeaponLevelEntry entry = levels.get(level);
        return entry != null ? entry.damage() : getDefaultDamage(weaponId, level);
    }

    public long getCost(String weaponId, int level) {
        Map<Integer, WeaponLevelEntry> levels = cacheByLevel.get(weaponId);
        if (levels == null) {
            return getDefaultCost(weaponId, level);
        }
        WeaponLevelEntry entry = levels.get(level);
        return entry != null ? entry.cost() : getDefaultCost(weaponId, level);
    }

    public Set<String> getWeaponIds() {
        return Set.copyOf(cache.keySet());
    }

    public String getDisplayName(String weaponId) {
        WeaponDefaults defaults = DEFAULT_WEAPONS.get(weaponId);
        return defaults != null ? defaults.displayName() : weaponId;
    }

    public List<WeaponLevelEntry> getAllLevels(String weaponId) {
        List<WeaponLevelEntry> levels = cache.get(weaponId);
        if (levels == null) {
            return List.of();
        }
        return List.copyOf(levels);
    }

    public String getStarDisplay(int level) {
        if (level <= 0) return "0";
        if (level % 2 == 0) {
            return String.valueOf(level / 2);
        }
        return level / 2 + ".5";
    }

    public boolean setDamage(String weaponId, int level, int damage) {
        return updateField(weaponId, level, "damage", damage, -1);
    }

    public boolean setCost(String weaponId, int level, long cost) {
        return updateField(weaponId, level, "cost", -1, cost);
    }

    public boolean adjustDamage(String weaponId, int level, int delta) {
        if (delta == 0) {
            return false;
        }
        synchronized (cache) {
            int current = getDamage(weaponId, level);
            int newDamage = Math.max(1, current + delta);
            return setDamage(weaponId, level, newDamage);
        }
    }

    public boolean adjustCost(String weaponId, int level, long delta) {
        if (delta == 0) {
            return false;
        }
        synchronized (cache) {
            long current = getCost(weaponId, level);
            long newCost = Math.max(0, current + delta);
            return setCost(weaponId, level, newCost);
        }
    }

    public void resetDefaults(String weaponId) {
        WeaponDefaults defaults = DEFAULT_WEAPONS.get(weaponId);
        if (defaults == null || !isPersistenceAvailable()) {
            return;
        }
        synchronized (cache) {
            String sql = "REPLACE INTO purge_weapon_levels (weapon_id, level, damage, cost) VALUES (?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                for (WeaponLevelEntry row : defaults.levels()) {
                    stmt.setString(1, weaponId);
                    stmt.setInt(2, row.level());
                    stmt.setInt(3, row.damage());
                    stmt.setLong(4, row.cost());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to reset weapon defaults for " + weaponId);
                return;
            }
            cacheWeaponLevels(weaponId, defaults.levels());
        }
    }

    private boolean updateField(String weaponId, int level, String column, int intValue, long longValue) {
        if (!isPersistenceAvailable()) {
            return false;
        }
        synchronized (cache) {
            String sql = "UPDATE purge_weapon_levels SET " + column + " = ? WHERE weapon_id = ? AND level = ?";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                if ("damage".equals(column)) {
                    stmt.setInt(1, intValue);
                } else {
                    stmt.setLong(1, longValue);
                }
                stmt.setString(2, weaponId);
                stmt.setInt(3, level);
                int rows = stmt.executeUpdate();
                if (rows <= 0) {
                    return false;
                }
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to update " + column + " for " + weaponId + " level " + level);
                return false;
            }
            // Update cache
            List<WeaponLevelEntry> levels = cache.get(weaponId);
            if (levels != null) {
                List<WeaponLevelEntry> updated = new ArrayList<>();
                for (WeaponLevelEntry entry : levels) {
                    if (entry.level() == level) {
                        if ("damage".equals(column)) {
                            updated.add(new WeaponLevelEntry(level, intValue, entry.cost()));
                        } else {
                            updated.add(new WeaponLevelEntry(level, entry.damage(), longValue));
                        }
                    } else {
                        updated.add(entry);
                    }
                }
                cacheWeaponLevels(weaponId, updated);
            }
            return true;
        }
    }

    private boolean isPersistenceAvailable() {
        return DatabaseManager.getInstance().isInitialized();
    }

    private void createTable() {
        if (!isPersistenceAvailable()) {
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS purge_weapon_levels ("
                + "weapon_id VARCHAR(32) NOT NULL, "
                + "level INT NOT NULL, "
                + "damage INT NOT NULL, "
                + "cost BIGINT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (weapon_id, level)"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge_weapon_levels table");
        }
    }

    private void seedDefaults() {
        if (!isPersistenceAvailable()) {
            return;
        }

        // Insert only missing rows so existing admin-tuned values are preserved.
        String insertSql = "INSERT IGNORE INTO purge_weapon_levels (weapon_id, level, damage, cost) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            for (Map.Entry<String, WeaponDefaults> weapon : DEFAULT_WEAPONS.entrySet()) {
                for (WeaponLevelEntry row : weapon.getValue().levels()) {
                    stmt.setString(1, weapon.getKey());
                    stmt.setInt(2, row.level());
                    stmt.setInt(3, row.damage());
                    stmt.setLong(4, row.cost());
                    stmt.addBatch();
                }
            }
            stmt.executeBatch();
            LOGGER.atInfo().log("Ensured default weapon level seeds for " + DEFAULT_WEAPONS.size() + " Hyguns weapons");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to seed default weapon levels");
        }
    }

    private void loadAll() {
        if (!isPersistenceAvailable()) {
            loadDefaultsIntoCache();
            LOGGER.atWarning().log("Database unavailable, using in-memory default weapon levels");
            return;
        }
        String sql = "SELECT weapon_id, level, damage, cost FROM purge_weapon_levels ORDER BY weapon_id, level ASC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                ConcurrentHashMap<String, List<WeaponLevelEntry>> temp = new ConcurrentHashMap<>();
                while (rs.next()) {
                    String weaponId = rs.getString("weapon_id");
                    int level = rs.getInt("level");
                    int damage = rs.getInt("damage");
                    long cost = rs.getLong("cost");
                    temp.computeIfAbsent(weaponId, k -> new ArrayList<>())
                            .add(new WeaponLevelEntry(level, damage, cost));
                }
                if (temp.isEmpty()) {
                    loadDefaultsIntoCache();
                    LOGGER.atWarning().log("No weapon levels found in DB, using defaults in memory");
                    return;
                }
                cache.clear();
                cacheByLevel.clear();
                for (Map.Entry<String, List<WeaponLevelEntry>> entry : temp.entrySet()) {
                    cacheWeaponLevels(entry.getKey(), entry.getValue());
                }
            }
            int total = cache.values().stream().mapToInt(List::size).sum();
            LOGGER.atInfo().log("Loaded " + total + " weapon level definitions");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load weapon levels");
            loadDefaultsIntoCache();
        }
    }

    private int getDefaultDamage(String weaponId, int level) {
        WeaponLevelEntry levelEntry = getDefaultLevel(weaponId, level);
        return levelEntry != null ? levelEntry.damage() : 17;
    }

    private long getDefaultCost(String weaponId, int level) {
        WeaponLevelEntry levelEntry = getDefaultLevel(weaponId, level);
        return levelEntry != null ? levelEntry.cost() : 0L;
    }

    private WeaponLevelEntry getDefaultLevel(String weaponId, int level) {
        WeaponDefaults defaults = DEFAULT_WEAPONS.get(weaponId);
        if (defaults == null || level < 0 || level >= defaults.levels().size()) {
            return null;
        }
        return defaults.levels().get(level);
    }

    private void loadDefaultsIntoCache() {
        cache.clear();
        cacheByLevel.clear();
        for (Map.Entry<String, WeaponDefaults> entry : DEFAULT_WEAPONS.entrySet()) {
            cacheWeaponLevels(entry.getKey(), entry.getValue().levels());
        }
    }

    private static Map<String, WeaponDefaults> buildDefaultWeapons() {
        LinkedHashMap<String, WeaponDefaults> defaults = new LinkedHashMap<>();
        defaults.put("Glock18", createWeaponDefaults("Glock-18",
                6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17));
        defaults.put("ColtRevolver", createWeaponDefaults("Colt Revolver",
                24, 27, 30, 32, 35, 40, 44, 48, 54, 59, 66));
        defaults.put("DesertEagle", createWeaponDefaults("Desert Eagle",
                25, 28, 31, 34, 37, 41, 46, 50, 56, 62, 69));
        defaults.put("Mac10", createWeaponDefaults("Mac-10",
                2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12));
        defaults.put("MP9", createWeaponDefaults("MP9",
                3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13));
        defaults.put("Thompson", createWeaponDefaults("Thompson",
                3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13));
        defaults.put("AK47", createWeaponDefaults("AK-47",
                17, 19, 21, 23, 25, 28, 31, 34, 38, 42, 47));
        defaults.put("M4A1s", createWeaponDefaults("M4A1s",
                14, 16, 17, 19, 21, 23, 26, 28, 31, 35, 39));
        defaults.put("Barret50", createWeaponDefaults("Barret .50",
                60, 67, 74, 81, 88, 99, 109, 120, 134, 148, 166));
        defaults.put("DoubleBarrel", createWeaponDefaults("Double Barrel",
                3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13));
        defaults.put("Flamethrower", createWeaponDefaults("Flamethrower",
                10, 11, 12, 14, 15, 16, 18, 20, 22, 25, 28));
        return Map.copyOf(defaults);
    }

    private static WeaponDefaults createWeaponDefaults(String displayName, int... damagesByLevel) {
        if (damagesByLevel.length != MAX_LEVEL + 1 || DEFAULT_COSTS.length != damagesByLevel.length) {
            throw new IllegalArgumentException("Weapon defaults must provide exactly " + (MAX_LEVEL + 1) + " levels");
        }
        List<WeaponLevelEntry> levels = new ArrayList<>(damagesByLevel.length);
        for (int level = 0; level < damagesByLevel.length; level++) {
            levels.add(new WeaponLevelEntry(level, damagesByLevel[level], DEFAULT_COSTS[level]));
        }
        return new WeaponDefaults(displayName, List.copyOf(levels));
    }

    private void cacheWeaponLevels(String weaponId, List<WeaponLevelEntry> levels) {
        List<WeaponLevelEntry> sorted = new ArrayList<>(levels);
        sorted.sort(Comparator.comparingInt(WeaponLevelEntry::level));
        cache.put(weaponId, sorted);
        Map<Integer, WeaponLevelEntry> indexed = new HashMap<>();
        for (WeaponLevelEntry levelEntry : sorted) {
            indexed.put(levelEntry.level(), levelEntry);
        }
        cacheByLevel.put(weaponId, Map.copyOf(indexed));
    }
}
