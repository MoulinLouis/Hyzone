package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores feather reward amounts per category per medal tier.
 * Loaded into memory on startup (max 4 rows: Easy, Medium, Hard, Insane).
 */
public class MedalRewardStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConnectionProvider connectionProvider;
    private final ConcurrentHashMap<String, MedalRewards> rewards = new ConcurrentHashMap<>();

    public MedalRewardStore(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void initialize() {
        if (!connectionProvider.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, MedalRewardStore will use in-memory mode");
            return;
        }
        String createSql = "CREATE TABLE IF NOT EXISTS medal_rewards ("
                + "category VARCHAR(32) NOT NULL PRIMARY KEY, "
                + "bronze_feathers INT NOT NULL DEFAULT 0, "
                + "silver_feathers INT NOT NULL DEFAULT 0, "
                + "gold_feathers INT NOT NULL DEFAULT 0, "
                + "emerald_feathers INT NOT NULL DEFAULT 0"
                + ") ENGINE=InnoDB";
        if (!DatabaseManager.execute(connectionProvider, createSql)) {
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
            case EMERALD -> r.emerald;
            case INSANE -> r.insane;
        };
    }

    public void setRewards(String category, int bronze, int silver, int gold, int emerald) {
        if (category == null) {
            return;
        }
        String key = normalizeCategory(category);
        MedalRewards existing = rewards.get(key);
        int insane = existing != null ? existing.insane : 0;
        setRewards(category, bronze, silver, gold, emerald, insane);
    }

    public void setRewards(String category, int bronze, int silver, int gold, int emerald, int insane) {
        if (category == null) {
            return;
        }
        String key = normalizeCategory(category);
        int clampedBronze = Math.max(0, bronze);
        int clampedSilver = Math.max(0, silver);
        int clampedGold = Math.max(0, gold);
        int clampedEmerald = Math.max(0, emerald);
        int clampedInsane = Math.max(0, insane);
        rewards.put(key, new MedalRewards(clampedBronze, clampedSilver, clampedGold, clampedEmerald, clampedInsane));
        persistToDatabase(key, clampedBronze, clampedSilver, clampedGold, clampedEmerald, clampedInsane);
    }

    public java.util.Map<String, MedalRewards> getAllRewards() {
        return Collections.unmodifiableMap(rewards);
    }

    public MedalRewards getRewards(String category) {
        if (category == null) {
            return null;
        }
        return rewards.get(normalizeCategory(category));
    }

    private void loadAll() {
        if (!connectionProvider.isInitialized()) {
            return;
        }
        String sql = "SELECT category, bronze_feathers, silver_feathers, gold_feathers, emerald_feathers, insane_feathers FROM medal_rewards";
        List<java.util.Map.Entry<String, MedalRewards>> loaded = DatabaseManager.queryList(connectionProvider, sql, rs -> {
            String category = rs.getString("category");
            MedalRewards r = new MedalRewards(
                    rs.getInt("bronze_feathers"), rs.getInt("silver_feathers"),
                    rs.getInt("gold_feathers"), rs.getInt("emerald_feathers"),
                    rs.getInt("insane_feathers"));
            return java.util.Map.entry(normalizeCategory(category), r);
        });
        for (var entry : loaded) {
            rewards.put(entry.getKey(), entry.getValue());
        }
    }

    private void persistToDatabase(String category, int bronze, int silver, int gold, int emerald, int insane) {
        if (!connectionProvider.isInitialized()) {
            return;
        }
        String sql = "INSERT INTO medal_rewards (category, bronze_feathers, silver_feathers, gold_feathers, emerald_feathers, insane_feathers) "
                + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "bronze_feathers = ?, silver_feathers = ?, gold_feathers = ?, emerald_feathers = ?, insane_feathers = ?";
        DatabaseManager.execute(connectionProvider, sql, stmt -> {
            stmt.setString(1, category);
            stmt.setInt(2, bronze);
            stmt.setInt(3, silver);
            stmt.setInt(4, gold);
            stmt.setInt(5, emerald);
            stmt.setInt(6, insane);
            stmt.setInt(7, bronze);
            stmt.setInt(8, silver);
            stmt.setInt(9, gold);
            stmt.setInt(10, emerald);
            stmt.setInt(11, insane);
        });
    }

    private static String normalizeCategory(String category) {
        if (category == null) {
            return "easy";
        }
        return category.trim().toLowerCase();
    }

    public static class MedalRewards {
        public final int bronze;
        public final int silver;
        public final int gold;
        public final int emerald;
        public final int insane;

        public MedalRewards(int bronze, int silver, int gold, int emerald, int insane) {
            this.bronze = bronze;
            this.silver = silver;
            this.gold = gold;
            this.emerald = emerald;
            this.insane = insane;
        }
    }
}
