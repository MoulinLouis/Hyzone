package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores feather reward amounts per category per medal tier.
 * Loaded into memory on startup (max 4 rows: Easy, Medium, Hard, Insane).
 */
public class MedalRewardStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final MedalRewardStore INSTANCE = new MedalRewardStore();

    private final ConcurrentHashMap<String, MedalRewards> rewards = new ConcurrentHashMap<>();

    private MedalRewardStore() {
    }

    public static MedalRewardStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, MedalRewardStore will use in-memory mode");
            return;
        }
        String createSql = "CREATE TABLE IF NOT EXISTS medal_rewards ("
                + "category VARCHAR(32) NOT NULL PRIMARY KEY, "
                + "bronze_feathers INT NOT NULL DEFAULT 0, "
                + "silver_feathers INT NOT NULL DEFAULT 0, "
                + "gold_feathers INT NOT NULL DEFAULT 0, "
                + "author_feathers INT NOT NULL DEFAULT 0"
                + ") ENGINE=InnoDB";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(createSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            addColumnIfMissing(conn, "medal_rewards", "author_feathers", "INT NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create medal_rewards table");
            return;
        }
        loadAll();
        LOGGER.atInfo().log("MedalRewardStore initialized (" + rewards.size() + " categories loaded)");
    }

    public int getReward(String category, Medal medal) {
        if (category == null || medal == null) {
            return 0;
        }
        MedalRewards r = rewards.get(normalizeCategory(category));
        if (r == null) {
            return 0;
        }
        return switch (medal) {
            case BRONZE -> r.bronze;
            case SILVER -> r.silver;
            case GOLD -> r.gold;
            case AUTHOR -> r.author;
        };
    }

    public void setRewards(String category, int bronze, int silver, int gold, int author) {
        if (category == null) {
            return;
        }
        String key = normalizeCategory(category);
        rewards.put(key, new MedalRewards(Math.max(0, bronze), Math.max(0, silver), Math.max(0, gold), Math.max(0, author)));
        persistToDatabase(key, bronze, silver, gold, author);
    }

    public ConcurrentHashMap<String, MedalRewards> getAllRewards() {
        return rewards;
    }

    public MedalRewards getRewards(String category) {
        if (category == null) {
            return null;
        }
        return rewards.get(normalizeCategory(category));
    }

    private void loadAll() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "SELECT category, bronze_feathers, silver_feathers, gold_feathers, author_feathers FROM medal_rewards";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String category = rs.getString("category");
                    int bronze = rs.getInt("bronze_feathers");
                    int silver = rs.getInt("silver_feathers");
                    int gold = rs.getInt("gold_feathers");
                    int author = rs.getInt("author_feathers");
                    rewards.put(normalizeCategory(category), new MedalRewards(bronze, silver, gold, author));
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load medal rewards");
        }
    }

    private void persistToDatabase(String category, int bronze, int silver, int gold, int author) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT INTO medal_rewards (category, bronze_feathers, silver_feathers, gold_feathers, author_feathers) "
                + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "bronze_feathers = ?, silver_feathers = ?, gold_feathers = ?, author_feathers = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, category);
            stmt.setInt(2, bronze);
            stmt.setInt(3, silver);
            stmt.setInt(4, gold);
            stmt.setInt(5, author);
            stmt.setInt(6, bronze);
            stmt.setInt(7, silver);
            stmt.setInt(8, gold);
            stmt.setInt(9, author);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist medal rewards for " + category);
        }
    }

    private static String normalizeCategory(String category) {
        if (category == null) {
            return "easy";
        }
        return category.trim().toLowerCase();
    }

    private void addColumnIfMissing(Connection conn, String table, String column, String definition) {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return;
            }
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to check column " + table + "." + column + ": " + e.getMessage());
            return;
        }
        String sql = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            LOGGER.atInfo().log("Added column " + table + "." + column);
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to add column " + table + "." + column + ": " + e.getMessage());
        }
    }

    public static class MedalRewards {
        public final int bronze;
        public final int silver;
        public final int gold;
        public final int author;

        public MedalRewards(int bronze, int silver, int gold, int author) {
            this.bronze = bronze;
            this.silver = silver;
            this.gold = gold;
            this.author = author;
        }
    }
}
