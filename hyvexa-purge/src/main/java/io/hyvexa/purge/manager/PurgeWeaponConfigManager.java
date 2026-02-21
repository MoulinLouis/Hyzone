package io.hyvexa.purge.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

    // Weapon defaults (ownership config)
    private final Set<String> defaultUnlocked = Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentHashMap<String, Long> unlockCosts = new ConcurrentHashMap<>();
    private volatile String sessionWeaponId = "AK47";

    // Lootbox settings
    private volatile int lootboxDropPercent = 5;

    public record WeaponLevelEntry(int level, int damage, long cost) {}

    private record WeaponDefaults(String displayName, List<WeaponLevelEntry> levels) {}

    private static final Map<String, WeaponDefaults> DEFAULT_WEAPONS = buildDefaultWeapons();

    public PurgeWeaponConfigManager() {
        createTable();
        createDefaultsTable();
        createSettingsTable();
        seedDefaults();
        seedWeaponDefaults();
        loadAll();
        loadWeaponDefaults();
        loadSettings();
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

    // --- Weapon ownership config methods ---

    public boolean isDefaultUnlocked(String weaponId) {
        return defaultUnlocked.contains(weaponId);
    }

    public void setDefaultUnlocked(String weaponId, boolean val) {
        if (val) {
            defaultUnlocked.add(weaponId);
        } else {
            defaultUnlocked.remove(weaponId);
        }
        if (isPersistenceAvailable()) {
            String sql = "UPDATE purge_weapon_defaults SET default_unlocked = ? WHERE weapon_id = ?";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setBoolean(1, val);
                stmt.setString(2, weaponId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to update default_unlocked for " + weaponId);
            }
        }
    }

    public long getUnlockCost(String weaponId) {
        return unlockCosts.getOrDefault(weaponId, 500L);
    }

    public void adjustUnlockCost(String weaponId, long delta) {
        if (delta == 0) {
            return;
        }
        long current = getUnlockCost(weaponId);
        long newCost = Math.max(0, current + delta);
        unlockCosts.put(weaponId, newCost);
        if (isPersistenceAvailable()) {
            String sql = "UPDATE purge_weapon_defaults SET unlock_cost = ? WHERE weapon_id = ?";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setLong(1, newCost);
                stmt.setString(2, weaponId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to update unlock_cost for " + weaponId);
            }
        }
    }

    public String getSessionWeaponId() {
        return sessionWeaponId;
    }

    public void setSessionWeapon(String weaponId) {
        String previousId = this.sessionWeaponId;
        this.sessionWeaponId = weaponId;
        if (isPersistenceAvailable()) {
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                // Unset previous
                if (previousId != null && !previousId.equals(weaponId)) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE purge_weapon_defaults SET session_weapon = FALSE WHERE weapon_id = ?")) {
                        DatabaseManager.applyQueryTimeout(stmt);
                        stmt.setString(1, previousId);
                        stmt.executeUpdate();
                    }
                }
                // Set new
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE purge_weapon_defaults SET session_weapon = TRUE WHERE weapon_id = ?")) {
                    DatabaseManager.applyQueryTimeout(stmt);
                    stmt.setString(1, weaponId);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to update session weapon to " + weaponId);
            }
        }
    }

    public Set<String> getDefaultWeaponIds() {
        return Set.copyOf(defaultUnlocked);
    }

    public boolean isSessionWeapon(String weaponId) {
        return weaponId != null && weaponId.equals(sessionWeaponId);
    }

    public int getLootboxDropPercent() {
        return lootboxDropPercent;
    }

    public double getLootboxDropChance() {
        return lootboxDropPercent / 100.0;
    }

    public void setLootboxDropPercent(int percent) {
        this.lootboxDropPercent = Math.max(0, Math.min(100, percent));
        persistSetting("lootbox_drop_percent", String.valueOf(this.lootboxDropPercent));
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

    private void createDefaultsTable() {
        if (!isPersistenceAvailable()) {
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS purge_weapon_defaults ("
                + "weapon_id VARCHAR(32) NOT NULL PRIMARY KEY, "
                + "default_unlocked BOOLEAN NOT NULL DEFAULT FALSE, "
                + "unlock_cost BIGINT NOT NULL DEFAULT 500, "
                + "session_weapon BOOLEAN NOT NULL DEFAULT FALSE"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge_weapon_defaults table");
        }
    }

    private void seedWeaponDefaults() {
        if (!isPersistenceAvailable()) {
            return;
        }
        // Seed all weapons with defaults, then ensure AK47 is default+session
        String insertSql = "INSERT IGNORE INTO purge_weapon_defaults (weapon_id, default_unlocked, unlock_cost, session_weapon) VALUES (?, FALSE, 500, FALSE)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            for (String weaponId : DEFAULT_WEAPONS.keySet()) {
                stmt.setString(1, weaponId);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to seed weapon defaults");
            return;
        }
        // Ensure AK47 is default unlocked and session weapon (only if no session weapon set)
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // Check if any session weapon is already set
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT COUNT(*) FROM purge_weapon_defaults WHERE session_weapon = TRUE")) {
                DatabaseManager.applyQueryTimeout(check);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        // No session weapon set, default AK47
                        try (PreparedStatement update = conn.prepareStatement(
                                "UPDATE purge_weapon_defaults SET default_unlocked = TRUE, session_weapon = TRUE WHERE weapon_id = 'AK47'")) {
                            DatabaseManager.applyQueryTimeout(update);
                            update.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to set AK47 as default session weapon");
        }
    }

    private void loadWeaponDefaults() {
        defaultUnlocked.clear();
        unlockCosts.clear();
        sessionWeaponId = "AK47";

        if (!isPersistenceAvailable()) {
            // Fallback: AK47 default unlocked
            defaultUnlocked.add("AK47");
            for (String weaponId : DEFAULT_WEAPONS.keySet()) {
                unlockCosts.put(weaponId, 500L);
            }
            return;
        }

        String sql = "SELECT weapon_id, default_unlocked, unlock_cost, session_weapon FROM purge_weapon_defaults";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String weaponId = rs.getString("weapon_id");
                    if (rs.getBoolean("default_unlocked")) {
                        defaultUnlocked.add(weaponId);
                    }
                    unlockCosts.put(weaponId, rs.getLong("unlock_cost"));
                    if (rs.getBoolean("session_weapon")) {
                        sessionWeaponId = weaponId;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load weapon defaults");
            defaultUnlocked.add("AK47");
        }
        LOGGER.atInfo().log("Loaded weapon defaults: " + defaultUnlocked.size() + " default unlocked, session weapon: " + sessionWeaponId);
    }

    private void createSettingsTable() {
        if (!isPersistenceAvailable()) {
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS purge_settings ("
                + "setting_key VARCHAR(64) NOT NULL PRIMARY KEY, "
                + "setting_value VARCHAR(255) NOT NULL"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create purge_settings table");
        }
    }

    private void loadSettings() {
        if (!isPersistenceAvailable()) {
            return;
        }
        String sql = "SELECT setting_key, setting_value FROM purge_settings";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("setting_key");
                    String value = rs.getString("setting_value");
                    if ("lootbox_drop_percent".equals(key)) {
                        try {
                            lootboxDropPercent = Math.max(0, Math.min(100, Integer.parseInt(value)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load purge settings");
        }
        LOGGER.atInfo().log("Purge settings loaded: lootbox_drop_percent=" + lootboxDropPercent);
    }

    private void persistSetting(String key, String value) {
        if (!isPersistenceAvailable()) {
            return;
        }
        String sql = "INSERT INTO purge_settings (setting_key, setting_value) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE setting_value = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.setString(3, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist setting " + key);
        }
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
