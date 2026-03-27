package io.hyvexa.ascend.mine.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TierConfigStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConnectionProvider db;

    // targetTier -> (blockTypeId -> amount) — recipes for tier upgrades
    private final Map<Integer, Map<String, Integer>> tierRecipes = new ConcurrentHashMap<>();

    // tier -> (level -> crystalCost) — enhancement costs
    private final Map<Integer, Map<Integer, Long>> enhanceCosts = new ConcurrentHashMap<>();

    public TierConfigStore(ConnectionProvider db) {
        this.db = db;
    }

    public void syncLoad(Connection conn) throws SQLException {
        loadTierRecipes(conn);
        loadEnhanceCosts(conn);
    }

    // --- Tier Recipes ---

    private void loadTierRecipes(Connection conn) throws SQLException {
        tierRecipes.clear();
        String sql = "SELECT tier, block_type_id, amount FROM pickaxe_tier_recipes";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int tier = rs.getInt("tier");
                    String blockTypeId = rs.getString("block_type_id");
                    int amount = rs.getInt("amount");
                    tierRecipes.computeIfAbsent(tier, k -> new ConcurrentHashMap<>())
                        .put(blockTypeId, amount);
                }
            }
        }
    }

    public void saveTierRecipe(int targetTier, String blockTypeId, int amount) {
        if (targetTier < 1 || blockTypeId == null || amount <= 0) return;

        tierRecipes.computeIfAbsent(targetTier, k -> new ConcurrentHashMap<>())
            .put(blockTypeId, amount);

        if (!this.db.isInitialized()) return;

        DatabaseManager.execute(this.db, """
            INSERT INTO pickaxe_tier_recipes (tier, block_type_id, amount)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE amount = VALUES(amount)
            """,
            stmt -> {
                stmt.setInt(1, targetTier);
                stmt.setString(2, blockTypeId);
                stmt.setInt(3, amount);
            });
    }

    public void removeTierRecipe(int targetTier, String blockTypeId) {
        if (blockTypeId == null) return;

        Map<String, Integer> recipe = tierRecipes.get(targetTier);
        if (recipe != null) {
            recipe.remove(blockTypeId);
            if (recipe.isEmpty()) tierRecipes.remove(targetTier);
        }

        if (!this.db.isInitialized()) return;

        DatabaseManager.execute(this.db,
            "DELETE FROM pickaxe_tier_recipes WHERE tier = ? AND block_type_id = ?",
            stmt -> {
                stmt.setInt(1, targetTier);
                stmt.setString(2, blockTypeId);
            });
    }

    public Map<String, Integer> getTierRecipe(int targetTier) {
        Map<String, Integer> recipe = tierRecipes.get(targetTier);
        return recipe != null ? Collections.unmodifiableMap(recipe) : Collections.emptyMap();
    }

    // --- Enhancement Costs ---

    private void loadEnhanceCosts(Connection conn) throws SQLException {
        enhanceCosts.clear();
        String sql = "SELECT tier, level, crystal_cost FROM pickaxe_enhance_costs";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int tier = rs.getInt("tier");
                    int level = rs.getInt("level");
                    long cost = rs.getLong("crystal_cost");
                    enhanceCosts.computeIfAbsent(tier, k -> new ConcurrentHashMap<>())
                        .put(level, cost);
                }
            }
        }
    }

    public void saveEnhanceCost(int tier, int level, long cost) {
        if (tier < 0 || level < 1 || level > PickaxeTier.MAX_ENHANCEMENT) return;

        enhanceCosts.computeIfAbsent(tier, k -> new ConcurrentHashMap<>())
            .put(level, cost);

        if (!this.db.isInitialized()) return;

        DatabaseManager.execute(this.db, """
            INSERT INTO pickaxe_enhance_costs (tier, level, crystal_cost)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE crystal_cost = VALUES(crystal_cost)
            """,
            stmt -> {
                stmt.setInt(1, tier);
                stmt.setInt(2, level);
                stmt.setLong(3, cost);
            });
    }

    public void removeEnhanceCost(int tier, int level) {
        Map<Integer, Long> costs = enhanceCosts.get(tier);
        if (costs != null) {
            costs.remove(level);
            if (costs.isEmpty()) enhanceCosts.remove(tier);
        }

        if (!this.db.isInitialized()) return;

        DatabaseManager.execute(this.db,
            "DELETE FROM pickaxe_enhance_costs WHERE tier = ? AND level = ?",
            stmt -> {
                stmt.setInt(1, tier);
                stmt.setInt(2, level);
            });
    }

    public long getEnhanceCost(int tier, int level) {
        Map<Integer, Long> costs = enhanceCosts.get(tier);
        return costs != null ? costs.getOrDefault(level, 0L) : 0L;
    }
}
