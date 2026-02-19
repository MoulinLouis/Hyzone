package io.hyvexa.purge.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeWeaponConfigManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_LEVEL = 10;

    private final ConcurrentHashMap<String, List<WeaponLevelEntry>> cache = new ConcurrentHashMap<>();

    public record WeaponLevelEntry(int level, int damage, long cost) {}

    // Default AK47 seed values: level -> (damage, cost)
    private static final int[][] AK47_DEFAULTS = {
            {0,  17, 0},
            {1,  19, 50},
            {2,  21, 100},
            {3,  23, 175},
            {4,  25, 275},
            {5,  28, 400},
            {6,  31, 550},
            {7,  34, 750},
            {8,  38, 1000},
            {9,  42, 1300},
            {10, 47, 1700},
    };

    public PurgeWeaponConfigManager() {
        createTable();
        seedDefaults();
        loadAll();
    }

    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    public int getDamage(String weaponId, int level) {
        List<WeaponLevelEntry> levels = cache.get(weaponId);
        if (levels == null) {
            return 17;
        }
        for (WeaponLevelEntry entry : levels) {
            if (entry.level() == level) {
                return entry.damage();
            }
        }
        return 17;
    }

    public long getCost(String weaponId, int level) {
        List<WeaponLevelEntry> levels = cache.get(weaponId);
        if (levels == null) {
            return 0;
        }
        for (WeaponLevelEntry entry : levels) {
            if (entry.level() == level) {
                return entry.cost();
            }
        }
        return 0;
    }

    public Set<String> getWeaponIds() {
        return Set.copyOf(cache.keySet());
    }

    public String getDisplayName(String weaponId) {
        return switch (weaponId) {
            case "AK47" -> "AK-47";
            default -> weaponId;
        };
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
        if (!"AK47".equals(weaponId) || !isPersistenceAvailable()) {
            return;
        }
        synchronized (cache) {
            String sql = "REPLACE INTO purge_weapon_levels (weapon_id, level, damage, cost) VALUES (?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                for (int[] row : AK47_DEFAULTS) {
                    stmt.setString(1, weaponId);
                    stmt.setInt(2, row[0]);
                    stmt.setInt(3, row[1]);
                    stmt.setLong(4, row[2]);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to reset weapon defaults for " + weaponId);
                return;
            }
            // Reload cache
            List<WeaponLevelEntry> levels = new ArrayList<>();
            for (int[] row : AK47_DEFAULTS) {
                levels.add(new WeaponLevelEntry(row[0], row[1], row[2]));
            }
            cache.put(weaponId, levels);
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
                cache.put(weaponId, updated);
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
        // Only seed if table is empty for AK47
        String countSql = "SELECT COUNT(*) FROM purge_weapon_levels WHERE weapon_id = 'AK47'";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(countSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return; // Already seeded
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to check weapon level count");
            return;
        }

        String insertSql = "INSERT INTO purge_weapon_levels (weapon_id, level, damage, cost) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            for (int[] row : AK47_DEFAULTS) {
                stmt.setString(1, "AK47");
                stmt.setInt(2, row[0]);
                stmt.setInt(3, row[1]);
                stmt.setLong(4, row[2]);
                stmt.addBatch();
            }
            stmt.executeBatch();
            LOGGER.atInfo().log("Seeded default AK47 weapon levels (11 rows)");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to seed default weapon levels");
        }
    }

    private void loadAll() {
        if (!isPersistenceAvailable()) {
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
                // Sort each weapon's levels
                for (List<WeaponLevelEntry> levels : temp.values()) {
                    levels.sort(Comparator.comparingInt(WeaponLevelEntry::level));
                }
                cache.putAll(temp);
            }
            int total = cache.values().stream().mapToInt(List::size).sum();
            LOGGER.atInfo().log("Loaded " + total + " weapon level definitions");
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load weapon levels");
        }
    }
}
