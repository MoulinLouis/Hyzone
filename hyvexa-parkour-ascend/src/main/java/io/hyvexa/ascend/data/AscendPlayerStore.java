package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.AchievementType;
import io.hyvexa.ascend.AscendConstants.SkillTreeNode;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.tutorial.TutorialTriggerService;
import io.hyvexa.ascend.util.MapUnlockHelper;
import io.hyvexa.core.db.DatabaseManager;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public class AscendPlayerStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final MathContext CALC_CTX = AscendConstants.CALC_CTX;

    private final Map<UUID, AscendPlayerProgress> players = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> resetPendingPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> saveFuture = new AtomicReference<>();

    /**
     * Initialize the store. With lazy loading, we don't load all players upfront.
     * Players are loaded on-demand when they connect.
     */
    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, AscendPlayerStore will use in-memory mode");
            return;
        }

        players.clear();
        dirtyPlayers.clear();
        resetPendingPlayers.clear();
        LOGGER.atInfo().log("AscendPlayerStore initialized with lazy loading");
    }

    public AscendPlayerProgress getPlayer(UUID playerId) {
        return players.get(playerId);
    }

    public Map<UUID, AscendPlayerProgress> getPlayersSnapshot() {
        return new HashMap<>(players);
    }

    public AscendPlayerProgress getOrCreatePlayer(UUID playerId) {
        // Check cache first
        AscendPlayerProgress progress = players.get(playerId);
        if (progress != null) {
            // Ensure existing players have ascension timer initialized (migration)
            if (progress.getAscensionStartedAt() == null) {
                progress.setAscensionStartedAt(System.currentTimeMillis());
                markDirty(playerId);
            }
            return progress;
        }

        // Lazy load from database
        progress = loadPlayerFromDatabase(playerId);
        if (progress != null) {
            players.put(playerId, progress);
            // Ensure loaded players have ascension timer initialized (migration)
            if (progress.getAscensionStartedAt() == null) {
                progress.setAscensionStartedAt(System.currentTimeMillis());
                markDirty(playerId);
            }
            return progress;
        }

        // Create new player
        AscendPlayerProgress newProgress = new AscendPlayerProgress();
        newProgress.setAscensionStartedAt(System.currentTimeMillis());
        players.put(playerId, newProgress);
        dirtyPlayers.add(playerId);
        queueSave();
        return newProgress;
    }

    /**
     * Load a single player's data from the database (lazy loading).
     * Returns null if player doesn't exist in database.
     */
    private AscendPlayerProgress loadPlayerFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return null;
        }

        AscendPlayerProgress progress = null;
        String playerIdStr = playerId.toString();

        // Load base player data
        String playerSql = """
            SELECT coins, elevation_multiplier, ascension_count, skill_tree_points,
                   total_coins_earned, total_manual_runs, active_title,
                   ascension_started_at, fastest_ascension_ms,
                   last_active_timestamp, has_unclaimed_passive,
                   summit_accumulated_coins, elevation_accumulated_coins, auto_upgrade_enabled,
                   seen_tutorials
            FROM ascend_players
            WHERE uuid = ?
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(playerSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerIdStr);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    progress = new AscendPlayerProgress();
                    progress.setCoins(rs.getBigDecimal("coins"));
                    progress.setElevationMultiplier(rs.getInt("elevation_multiplier"));

                    progress.setAscensionCount(safeGetInt(rs, "ascension_count", 0));
                    progress.setSkillTreePoints(safeGetInt(rs, "skill_tree_points", 0));
                    progress.setTotalCoinsEarned(safeGetBigDecimal(rs, "total_coins_earned", BigDecimal.ZERO));
                    progress.setTotalManualRuns(safeGetInt(rs, "total_manual_runs", 0));
                    progress.setActiveTitle(safeGetString(rs, "active_title", null));

                    Long ascensionStartedAt = safeGetLong(rs, "ascension_started_at");
                    if (ascensionStartedAt != null) {
                        progress.setAscensionStartedAt(ascensionStartedAt);
                    }

                    Long fastestAscensionMs = safeGetLong(rs, "fastest_ascension_ms");
                    if (fastestAscensionMs != null) {
                        progress.setFastestAscensionMs(fastestAscensionMs);
                    }

                    Long timestamp = safeGetLong(rs, "last_active_timestamp");
                    if (timestamp != null) {
                        progress.setLastActiveTimestamp(timestamp);
                    }

                    progress.setHasUnclaimedPassive(safeGetBoolean(rs, "has_unclaimed_passive", false));

                    BigDecimal summitAccumulated = safeGetBigDecimal(rs, "summit_accumulated_coins", null);
                    if (summitAccumulated != null) {
                        progress.setSummitAccumulatedCoins(summitAccumulated);
                    }

                    BigDecimal elevationAccumulated = safeGetBigDecimal(rs, "elevation_accumulated_coins", null);
                    if (elevationAccumulated != null) {
                        progress.setElevationAccumulatedCoins(elevationAccumulated);
                    }

                    progress.setAutoUpgradeEnabled(safeGetBoolean(rs, "auto_upgrade_enabled", false));
                    progress.setSeenTutorials(safeGetInt(rs, "seen_tutorials", 0));
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load player " + playerId + ": " + e.getMessage());
            return null;
        }

        // If player doesn't exist in database, return null
        if (progress == null) {
            return null;
        }

        // Load map progress
        loadMapProgressForPlayer(playerId, progress);

        // Load summit levels
        loadSummitLevelsForPlayer(playerId, progress);

        // Load skill nodes
        loadSkillNodesForPlayer(playerId, progress);

        // Load achievements
        loadAchievementsForPlayer(playerId, progress);

        return progress;
    }

    private void loadMapProgressForPlayer(UUID playerId, AscendPlayerProgress progress) {
        String sql = """
            SELECT map_id, unlocked, completed_manually, has_robot,
                   robot_speed_level, robot_stars, multiplier, best_time_ms
            FROM ascend_player_maps
            WHERE player_uuid = ?
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String mapId = rs.getString("map_id");
                    AscendPlayerProgress.MapProgress mapProgress = progress.getOrCreateMapProgress(mapId);
                    mapProgress.setUnlocked(rs.getBoolean("unlocked"));
                    mapProgress.setCompletedManually(rs.getBoolean("completed_manually"));
                    mapProgress.setHasRobot(rs.getBoolean("has_robot"));
                    mapProgress.setRobotSpeedLevel(rs.getInt("robot_speed_level"));
                    mapProgress.setRobotStars(rs.getInt("robot_stars"));
                    mapProgress.setMultiplier(rs.getBigDecimal("multiplier"));
                    long bestTime = rs.getLong("best_time_ms");
                    if (!rs.wasNull()) {
                        mapProgress.setBestTimeMs(bestTime);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load map progress for player " + playerId + ": " + e.getMessage());
        }
    }

    private void loadSummitLevelsForPlayer(UUID playerId, AscendPlayerProgress progress) {
        String sql = "SELECT category, xp FROM ascend_player_summit WHERE player_uuid = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String categoryName = rs.getString("category");
                    long xp = rs.getLong("xp");
                    try {
                        SummitCategory category = SummitCategory.valueOf(categoryName);
                        progress.setSummitXp(category, xp);
                    } catch (IllegalArgumentException ignored) {
                        // Unknown category
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load summit levels for player " + playerId + ": " + e.getMessage());
        }
    }

    private void loadSkillNodesForPlayer(UUID playerId, AscendPlayerProgress progress) {
        String sql = "SELECT skill_node FROM ascend_player_skills WHERE player_uuid = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String nodeName = rs.getString("skill_node");
                    try {
                        SkillTreeNode node = SkillTreeNode.valueOf(nodeName);
                        progress.unlockSkillNode(node);
                    } catch (IllegalArgumentException ignored) {
                        // Unknown node
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load skill nodes for player " + playerId + ": " + e.getMessage());
        }
    }

    private void loadAchievementsForPlayer(UUID playerId, AscendPlayerProgress progress) {
        String sql = "SELECT achievement FROM ascend_player_achievements WHERE player_uuid = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String achievementName = rs.getString("achievement");
                    try {
                        AchievementType achievement = AchievementType.valueOf(achievementName);
                        progress.unlockAchievement(achievement);
                    } catch (IllegalArgumentException ignored) {
                        // Unknown achievement
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load achievements for player " + playerId + ": " + e.getMessage());
        }
    }

    public void resetPlayerProgress(UUID playerId) {
        // Mark as reset so any concurrent/pending syncSave() will
        // DELETE child rows before re-inserting (prevents stale upserts)
        resetPendingPlayers.add(playerId);

        // Cancel any pending debounced save to prevent stale data from
        // being written after our DELETE
        ScheduledFuture<?> pending = saveFuture.getAndSet(null);
        if (pending != null) {
            pending.cancel(false);
        }
        saveQueued.set(false);

        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        // Reset basic progression
        progress.setCoins(BigDecimal.ZERO);
        progress.setElevationMultiplier(1);
        progress.getMapProgress().clear();

        // Reset Summit system
        progress.clearSummitXp();
        progress.setTotalCoinsEarned(BigDecimal.ZERO);
        progress.setSummitAccumulatedCoins(BigDecimal.ZERO);
        progress.setElevationAccumulatedCoins(BigDecimal.ZERO);

        // Reset Ascension/Skill Tree
        progress.setAscensionCount(0);
        progress.setSkillTreePoints(0);
        progress.setUnlockedSkillNodes(null);

        // Reset Achievements
        progress.setUnlockedAchievements(null);
        progress.setActiveTitle(null);

        // Reset Statistics
        progress.setTotalManualRuns(0);
        progress.setConsecutiveManualRuns(0);
        progress.setSessionFirstRunClaimed(false);

        // Delete all database entries for this player
        deletePlayerDataFromDatabase(playerId);

        markDirty(playerId);
    }

    private void deletePlayerDataFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        String playerIdStr = playerId.toString();
        String[] tables = {
            "ascend_player_maps",
            "ascend_player_summit",
            "ascend_player_skills",
            "ascend_player_achievements",
            "ascend_ghost_recordings"
        };

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (String table : tables) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM " + table + " WHERE player_uuid = ?")) {
                        DatabaseManager.applyQueryTimeout(stmt);
                        stmt.setString(1, playerIdStr);
                        stmt.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to delete player data during reset: " + e.getMessage());
        }
    }

    public void markDirty(UUID playerId) {
        dirtyPlayers.add(playerId);
        queueSave();
    }

    public BigDecimal getCoins(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getCoins() : BigDecimal.ZERO;
    }

    public void setCoins(UUID playerId, BigDecimal coins) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setCoins(coins.max(BigDecimal.ZERO));
        markDirty(playerId);
    }

    /**
     * Gets the raw elevation level (stored value).
     * For the actual multiplier value, use {@link #getCalculatedElevationMultiplier(UUID)}.
     */
    public int getElevationLevel(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getElevationMultiplier() : 0;
    }

    /**
     * Get the player's elevation multiplier.
     * Returns the level directly as a multiplier.
     */
    public double getCalculatedElevationMultiplier(UUID playerId) {
        int level = getElevationLevel(playerId);
        if (level <= 0) {
            return 1.0;
        }
        return level;
    }

    /**
     * Add elevation levels to a player.
     * @return The new total elevation level
     */
    public int addElevationLevel(UUID playerId, int amount) {
        if (amount <= 0) {
            return getElevationLevel(playerId);
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int value = progress.addElevationMultiplier(amount);
        markDirty(playerId);
        return value;
    }

    public AscendPlayerProgress.MapProgress getMapProgress(UUID playerId, String mapId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return null;
        }
        return progress.getMapProgress().get(mapId);
    }

    public AscendPlayerProgress.MapProgress getOrCreateMapProgress(UUID playerId, String mapId) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        return progress.getOrCreateMapProgress(mapId);
    }

    public boolean setMapUnlocked(UUID playerId, String mapId, boolean unlocked) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        if (mapProgress.isUnlocked() == unlocked) {
            return false;
        }
        mapProgress.setUnlocked(unlocked);
        markDirty(playerId);
        return true;
    }

    public void addCoins(UUID playerId, BigDecimal amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addCoins(amount);
        markDirty(playerId);
    }

    public boolean spendCoins(UUID playerId, BigDecimal amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress.getCoins().compareTo(amount) < 0) {
            return false;
        }
        progress.addCoins(amount.negate());
        markDirty(playerId);
        return true;
    }

    // ========================================
    // Atomic SQL Operations (Prevent Race Conditions)
    // ========================================

    /**
     * Atomically add coins to a player directly in the database.
     * Prevents race conditions with concurrent operations.
     *
     * @param playerId the player's UUID
     * @param amount the amount to add (can be negative to subtract)
     * @return true if the operation succeeded
     */
    public boolean atomicAddCoins(UUID playerId, BigDecimal amount) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            BigDecimal oldBalance = getCoins(playerId);
            addCoins(playerId, amount);
            BigDecimal newBalance = getCoins(playerId);
            checkCoinTutorialThresholds(playerId, oldBalance, newBalance);
            return true;
        }

        // Update memory FIRST so the in-memory value is never behind the DB value.
        // If the DB write fails, we revert the memory update.
        AscendPlayerProgress progress = players.get(playerId);
        BigDecimal oldBalance = progress != null ? progress.getCoins() : BigDecimal.ZERO;
        if (progress != null) {
            progress.addCoins(amount);
        }

        String sql = "UPDATE ascend_players SET coins = coins + ? WHERE uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setBigDecimal(1, amount);
            stmt.setString(2, playerId.toString());
            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated == 0 && progress != null) {
                progress.addCoins(amount.negate()); // Revert memory update
            } else if (rowsUpdated > 0) {
                BigDecimal newBalance = progress != null ? progress.getCoins() : BigDecimal.ZERO;
                checkCoinTutorialThresholds(playerId, oldBalance, newBalance);
            }

            return rowsUpdated > 0;
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to atomically add coins: " + e.getMessage());
            if (progress != null) {
                progress.addCoins(amount.negate()); // Revert memory update
            }
            return false;
        }
    }

    private void checkCoinTutorialThresholds(UUID playerId, BigDecimal oldBalance, BigDecimal newBalance) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        TutorialTriggerService triggerService = plugin.getTutorialTriggerService();
        if (triggerService == null) {
            return;
        }
        triggerService.checkCoinThresholds(playerId, oldBalance, newBalance);
    }

    /**
     * Atomically spend coins with balance check (prevents negative balance).
     * Returns false if insufficient funds.
     *
     * @param playerId the player's UUID
     * @param amount the amount to spend (must be positive)
     * @return true if the purchase succeeded (sufficient balance)
     */
    public boolean atomicSpendCoins(UUID playerId, BigDecimal amount) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            // Fallback to in-memory operation
            return spendCoins(playerId, amount);
        }

        String sql = "UPDATE ascend_players SET coins = coins - ? WHERE uuid = ? AND coins >= ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setBigDecimal(1, amount);
            stmt.setString(2, playerId.toString());
            stmt.setBigDecimal(3, amount);
            int rowsUpdated = stmt.executeUpdate();

            // Update in-memory cache if successful
            if (rowsUpdated > 0) {
                AscendPlayerProgress progress = players.get(playerId);
                if (progress != null) {
                    progress.addCoins(amount.negate());
                }
            }

            return rowsUpdated > 0;
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to atomically spend coins: " + e.getMessage());
            return false;
        }
    }

    /**
     * Atomically add to total coins earned (lifetime stat).
     *
     * @param playerId the player's UUID
     * @param amount the amount to add
     * @return true if the operation succeeded
     */
    public boolean atomicAddTotalCoinsEarned(UUID playerId, BigDecimal amount) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            addTotalCoinsEarned(playerId, amount);
            return true;
        }

        AscendPlayerProgress progress = players.get(playerId);
        if (progress != null) {
            progress.addTotalCoinsEarned(amount);
            progress.addSummitAccumulatedCoins(amount);
            progress.addElevationAccumulatedCoins(amount);
        }

        String sql = "UPDATE ascend_players SET total_coins_earned = total_coins_earned + ?, summit_accumulated_coins = summit_accumulated_coins + ?, elevation_accumulated_coins = elevation_accumulated_coins + ? WHERE uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setBigDecimal(1, amount);
            stmt.setBigDecimal(2, amount);
            stmt.setBigDecimal(3, amount);
            stmt.setString(4, playerId.toString());
            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated == 0 && progress != null) {
                progress.addTotalCoinsEarned(amount.negate());
                progress.setSummitAccumulatedCoins(progress.getSummitAccumulatedCoins().subtract(amount).max(BigDecimal.ZERO));
                progress.setElevationAccumulatedCoins(progress.getElevationAccumulatedCoins().subtract(amount).max(BigDecimal.ZERO));
            }

            return rowsUpdated > 0;
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to atomically add total coins earned: " + e.getMessage());
            if (progress != null) {
                progress.addTotalCoinsEarned(amount.negate());
                progress.setSummitAccumulatedCoins(progress.getSummitAccumulatedCoins().subtract(amount).max(BigDecimal.ZERO));
                progress.setElevationAccumulatedCoins(progress.getElevationAccumulatedCoins().subtract(amount).max(BigDecimal.ZERO));
            }
            return false;
        }
    }

    /**
     * Atomically add to map multiplier.
     *
     * @param playerId the player's UUID
     * @param mapId the map ID
     * @param amount the amount to add
     * @return true if the operation succeeded
     */
    public boolean atomicAddMapMultiplier(UUID playerId, String mapId, BigDecimal amount) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            addMapMultiplier(playerId, mapId, amount);
            return true;
        }

        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress != null) {
            mapProgress.addMultiplier(amount);
        }

        String sql = "UPDATE ascend_player_maps SET multiplier = multiplier + ? WHERE player_uuid = ? AND map_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setBigDecimal(1, amount);
            stmt.setString(2, playerId.toString());
            stmt.setString(3, mapId);
            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated == 0 && mapProgress != null) {
                mapProgress.addMultiplier(amount.negate());
            }

            return rowsUpdated > 0;
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to atomically add map multiplier: " + e.getMessage());
            if (mapProgress != null) {
                mapProgress.addMultiplier(amount.negate());
            }
            return false;
        }
    }

    /**
     * Atomically set elevation level and reset coins to 0 (for elevation purchase).
     *
     * @param playerId the player's UUID
     * @param newElevation the new elevation level
     * @return true if the operation succeeded
     */
    public boolean atomicSetElevationAndResetCoins(UUID playerId, int newElevation) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            // Fallback to in-memory operation
            AscendPlayerProgress progress = getOrCreatePlayer(playerId);
            progress.setElevationMultiplier(newElevation);
            progress.setCoins(BigDecimal.ZERO);
            progress.setSummitAccumulatedCoins(BigDecimal.ZERO);
            progress.setElevationAccumulatedCoins(BigDecimal.ZERO);
            markDirty(playerId);
            return true;
        }

        String sql = "UPDATE ascend_players SET elevation_multiplier = ?, coins = 0, summit_accumulated_coins = 0, elevation_accumulated_coins = 0 WHERE uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setInt(1, newElevation);
            stmt.setString(2, playerId.toString());
            int rowsUpdated = stmt.executeUpdate();

            // Update in-memory cache
            if (rowsUpdated > 0) {
                AscendPlayerProgress progress = getOrCreatePlayer(playerId);
                progress.setElevationMultiplier(newElevation);
                progress.setCoins(BigDecimal.ZERO);
                progress.setSummitAccumulatedCoins(BigDecimal.ZERO);
                progress.setElevationAccumulatedCoins(BigDecimal.ZERO);
            }

            return rowsUpdated > 0;
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to atomically set elevation and reset coins: " + e.getMessage());
            return false;
        }
    }

    public BigDecimal getMapMultiplier(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return BigDecimal.ONE;
        }
        return mapProgress.getMultiplier().max(BigDecimal.ONE);
    }

    public BigDecimal addMapMultiplier(UUID playerId, String mapId, BigDecimal amount) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        BigDecimal value = mapProgress.addMultiplier(amount);
        markDirty(playerId);
        return value;
    }

    public boolean hasRobot(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        return mapProgress != null && mapProgress.hasRobot();
    }

    public void setHasRobot(UUID playerId, String mapId, boolean hasRobot) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        mapProgress.setHasRobot(hasRobot);
        markDirty(playerId);
    }

    public int getRobotSpeedLevel(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 0;
        }
        return Math.max(0, mapProgress.getRobotSpeedLevel());
    }

    public int incrementRobotSpeedLevel(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        int value = mapProgress.incrementRobotSpeedLevel();
        markDirty(playerId);
        return value;
    }

    /**
     * Check if any maps should be auto-unlocked based on runner levels.
     * Called after incrementing runner speed level.
     * Returns the list of newly unlocked map IDs for notification.
     */
    public List<String> checkAndUnlockEligibleMaps(UUID playerId, AscendMapStore mapStore) {
        if (playerId == null || mapStore == null) {
            return List.of();
        }

        List<String> newlyUnlockedMapIds = new java.util.ArrayList<>();
        List<AscendMap> allMaps = new java.util.ArrayList<>(mapStore.listMapsSorted());

        // Start from index 1 (skip first map which is always unlocked)
        for (int i = 1; i < allMaps.size(); i++) {
            AscendMap map = allMaps.get(i);
            if (map == null) {
                continue;
            }

            // Check if already unlocked
            AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, map.getId());
            if (mapProgress != null && mapProgress.isUnlocked()) {
                continue;
            }

            // Check if meets unlock requirement
            if (MapUnlockHelper.meetsUnlockRequirement(playerId, map, this, mapStore)) {
                setMapUnlocked(playerId, map.getId(), true);
                newlyUnlockedMapIds.add(map.getId());
            }
        }

        return newlyUnlockedMapIds;
    }

    public int getRobotStars(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 0;
        }
        return Math.max(0, mapProgress.getRobotStars());
    }

    public int evolveRobot(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        mapProgress.setRobotSpeedLevel(0);
        int newStars = mapProgress.incrementRobotStars();
        // Evolution Power applied per star (handled in AscendConstants.getRunnerMultiplierIncrement)
        markDirty(playerId);
        return newStars;
    }

    // ========================================
    // Summit System Methods
    // ========================================

    public int getSummitLevel(UUID playerId, SummitCategory category) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return 0;
        }
        return progress.getSummitLevel(category);
    }

    public long getSummitXp(UUID playerId, SummitCategory category) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return 0L;
        }
        return progress.getSummitXp(category);
    }

    public long addSummitXp(UUID playerId, SummitCategory category, long amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress == null) {
            return 0L;
        }
        long newXp = progress.addSummitXp(category, amount);
        markDirty(playerId);
        return newXp;
    }

    public Map<SummitCategory, Integer> getSummitLevels(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return Map.of();
        }
        return progress.getSummitLevels();
    }

    public BigDecimal getSummitBonus(UUID playerId, SummitCategory category) {
        int level = getSummitLevel(playerId, category);
        // Convert double bonus to BigDecimal
        return BigDecimal.valueOf(category.getBonusForLevel(level));
    }

    public BigDecimal getTotalCoinsEarned(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getTotalCoinsEarned() : BigDecimal.ZERO;
    }

    public BigDecimal getSummitAccumulatedCoins(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getSummitAccumulatedCoins() : BigDecimal.ZERO;
    }

    public void addSummitAccumulatedCoins(UUID playerId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addSummitAccumulatedCoins(amount);
        markDirty(playerId);
    }

    public void addElevationAccumulatedCoins(UUID playerId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addElevationAccumulatedCoins(amount);
        markDirty(playerId);
    }

    public void addTotalCoinsEarned(UUID playerId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addTotalCoinsEarned(amount);
        progress.addSummitAccumulatedCoins(amount);
        progress.addElevationAccumulatedCoins(amount);
        markDirty(playerId);
    }

    public BigDecimal getElevationAccumulatedCoins(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getElevationAccumulatedCoins() : BigDecimal.ZERO;
    }

    // ========================================
    // Ascension System Methods
    // ========================================

    public int getAscensionCount(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getAscensionCount() : 0;
    }

    public int getSkillTreePoints(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getSkillTreePoints() : 0;
    }

    public int addSkillTreePoints(UUID playerId, int amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int newPoints = progress.addSkillTreePoints(amount);
        markDirty(playerId);
        return newPoints;
    }

    public int getAvailableSkillPoints(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getAvailableSkillPoints() : 0;
    }

    public boolean hasSkillNode(UUID playerId, SkillTreeNode node) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.hasSkillNode(node);
    }

    public boolean unlockSkillNode(UUID playerId, SkillTreeNode node) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress.hasSkillNode(node)) {
            return false;
        }
        if (progress.getAvailableSkillPoints() <= 0) {
            return false;
        }
        progress.unlockSkillNode(node);
        markDirty(playerId);
        return true;
    }

    public Set<SkillTreeNode> getUnlockedSkillNodes(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return EnumSet.noneOf(SkillTreeNode.class);
        }
        return progress.getUnlockedSkillNodes();
    }

    // ========================================
    // Achievement System Methods
    // ========================================

    public boolean hasAchievement(UUID playerId, AchievementType achievement) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.hasAchievement(achievement);
    }

    public boolean unlockAchievement(UUID playerId, AchievementType achievement) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress.hasAchievement(achievement)) {
            return false;
        }
        progress.unlockAchievement(achievement);
        markDirty(playerId);
        return true;
    }

    public Set<AchievementType> getUnlockedAchievements(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return EnumSet.noneOf(AchievementType.class);
        }
        return progress.getUnlockedAchievements();
    }

    public String getActiveTitle(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getActiveTitle() : null;
    }

    public void setActiveTitle(UUID playerId, String title) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setActiveTitle(title);
        markDirty(playerId);
    }

    public int getTotalManualRuns(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getTotalManualRuns() : 0;
    }

    public int incrementTotalManualRuns(UUID playerId) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int count = progress.incrementTotalManualRuns();
        markDirty(playerId);
        return count;
    }

    public int getConsecutiveManualRuns(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getConsecutiveManualRuns() : 0;
    }

    public int incrementConsecutiveManualRuns(UUID playerId) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int count = progress.incrementConsecutiveManualRuns();
        markDirty(playerId);
        return count;
    }

    public void resetConsecutiveManualRuns(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress != null) {
            progress.resetConsecutiveManualRuns();
            markDirty(playerId);
        }
    }

    /**
     * Shared reset logic for prestige operations.
     * Resets coins, map unlocks (except first), multipliers, manual completion, and runners.
     * @param clearBestTimes whether to also clear best times (elevation does, summit doesn't)
     * @return list of map IDs that had runners (for despawn handling)
     */
    private List<String> resetMapProgress(AscendPlayerProgress progress, String firstMapId, boolean clearBestTimes, UUID playerId) {
        List<String> mapsWithRunners = new java.util.ArrayList<>();

        // Check Momentum skill: keep 10% of map multipliers after reset
        boolean hasMomentum = false;
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getAscensionManager() != null) {
            hasMomentum = plugin.getAscensionManager().hasMomentum(playerId);
        }

        progress.setCoins(BigDecimal.ZERO);
        progress.setSummitAccumulatedCoins(BigDecimal.ZERO);
        progress.setElevationAccumulatedCoins(BigDecimal.ZERO);

        for (Map.Entry<String, AscendPlayerProgress.MapProgress> entry : progress.getMapProgress().entrySet()) {
            String mapId = entry.getKey();
            AscendPlayerProgress.MapProgress mapProgress = entry.getValue();

            mapProgress.setUnlocked(mapId.equals(firstMapId));

            // Momentum: keep 10% of multiplier (minimum 1.0)
            if (hasMomentum) {
                BigDecimal retained = mapProgress.getMultiplier()
                    .multiply(new BigDecimal("0.10"), AscendConstants.CALC_CTX)
                    .max(BigDecimal.ONE);
                mapProgress.setMultiplier(retained);
            } else {
                mapProgress.setMultiplier(BigDecimal.ONE);
            }

            mapProgress.setCompletedManually(false);

            if (clearBestTimes) {
                mapProgress.setBestTimeMs(null);
            }

            if (mapProgress.hasRobot()) {
                mapsWithRunners.add(mapId);
                mapProgress.setHasRobot(false);
                mapProgress.setRobotSpeedLevel(0);
                mapProgress.setRobotStars(0);
            }
        }

        if (firstMapId != null && !firstMapId.isEmpty()) {
            AscendPlayerProgress.MapProgress firstMapProgress = progress.getOrCreateMapProgress(firstMapId);
            firstMapProgress.setUnlocked(true);
        }

        return mapsWithRunners;
    }

    /**
     * Resets player progress for elevation: clears coins, map unlocks (except first map),
     * best times, multipliers, and removes all runners.
     *
     * @param playerId the player's UUID
     * @param firstMapId the ID of the first map (stays unlocked)
     * @return list of map IDs that had runners (for despawn handling)
     */
    public List<String> resetProgressForElevation(UUID playerId, String firstMapId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return List.of();
        }

        List<String> mapsWithRunners = resetMapProgress(progress, firstMapId, true, playerId);
        markDirty(playerId);
        return mapsWithRunners;
    }

    /**
     * Reset progress for Summit: coins, elevation, multipliers, runners, and map unlocks.
     * Keeps best times and summit XP.
     * @return list of map IDs that had runners (for despawn handling)
     */
    public List<String> resetProgressForSummit(UUID playerId, String firstMapId) {
        AscendPlayerProgress progress = getPlayer(playerId);
        if (progress == null) {
            return List.of();
        }

        // Summit Memory skill: keep 10% of elevation multiplier (minimum 1)
        boolean hasSummitMemory = false;
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getAscensionManager() != null) {
            hasSummitMemory = plugin.getAscensionManager().hasSummitMemory(playerId);
        }

        if (hasSummitMemory) {
            int retained = Math.max(1, (int) (progress.getElevationMultiplier() * 0.10));
            progress.setElevationMultiplier(retained);
        } else {
            progress.setElevationMultiplier(1);
        }

        List<String> mapsWithRunners = resetMapProgress(progress, firstMapId, false, playerId);
        markDirty(playerId);
        return mapsWithRunners;
    }

    public boolean isSessionFirstRunClaimed(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.isSessionFirstRunClaimed();
    }

    public void setSessionFirstRunClaimed(UUID playerId, boolean claimed) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setSessionFirstRunClaimed(claimed);
        // Don't persist - this is session-only
    }

    public BigDecimal[] getMultiplierDisplayValues(UUID playerId, List<AscendMap> maps, int slotCount) {
        int slots = Math.max(0, slotCount);
        BigDecimal[] digits = new BigDecimal[slots];
        for (int i = 0; i < slots; i++) {
            digits[i] = BigDecimal.ONE;
        }
        if (maps == null || maps.isEmpty() || slots == 0) {
            return digits;
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            digits[index] = getMapMultiplier(playerId, map.getId());
            index++;
        }
        return digits;
    }

    public long getMultiplierProduct(UUID playerId, List<AscendMap> maps, int slotCount) {
        return getMultiplierProductDecimal(playerId, maps, slotCount).longValue();
    }

    public BigDecimal getMultiplierProductDecimal(UUID playerId, List<AscendMap> maps, int slotCount) {
        BigDecimal product = BigDecimal.ONE;
        int slots = Math.max(0, slotCount);
        if (maps == null || maps.isEmpty() || slots == 0) {
            BigDecimal elevation = BigDecimal.valueOf(getCalculatedElevationMultiplier(playerId));
            return product.multiply(elevation, CALC_CTX);
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            BigDecimal value = getMapMultiplier(playerId, map.getId());
            product = product.multiply(value.max(BigDecimal.ONE), CALC_CTX);
            index++;
        }
        BigDecimal elevation = BigDecimal.valueOf(getCalculatedElevationMultiplier(playerId));
        return product.multiply(elevation, CALC_CTX);
    }

    public BigDecimal getCompletionPayout(UUID playerId, List<AscendMap> maps, int slotCount, String mapId, BigDecimal bonusAmount) {
        BigDecimal product = BigDecimal.ONE;
        int slots = Math.max(0, slotCount);
        if (maps == null || maps.isEmpty() || slots == 0) {
            BigDecimal elevation = BigDecimal.valueOf(getCalculatedElevationMultiplier(playerId));
            return product.multiply(elevation, CALC_CTX);
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            BigDecimal value = getMapMultiplier(playerId, map.getId());
            if (map.getId().equals(mapId)) {
                value = value.add(bonusAmount, CALC_CTX);
            }
            product = product.multiply(value.max(BigDecimal.ONE), CALC_CTX);
            index++;
        }
        BigDecimal elevation = BigDecimal.valueOf(getCalculatedElevationMultiplier(playerId));
        return product.multiply(elevation, CALC_CTX);
    }

    // ========================================
    // Tutorial Tracking
    // ========================================

    public boolean hasSeenTutorial(UUID playerId, int bit) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.hasSeenTutorial(bit);
    }

    public void markTutorialSeen(UUID playerId, int bit) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.markTutorialSeen(bit);
        markDirty(playerId);
    }

    /**
     * Remove a player from the cache (called on disconnect).
     * Flushes any pending changes to database before evicting.
     *
     * @param playerId the player's UUID
     */
    public void removePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }

        // Flush this player's data if dirty
        if (dirtyPlayers.contains(playerId)) {
            flushPendingSave();
        }

        // Remove from cache
        players.remove(playerId);
        dirtyPlayers.remove(playerId);
        resetPendingPlayers.remove(playerId);
    }

    public void flushPendingSave() {
        ScheduledFuture<?> pending = saveFuture.getAndSet(null);
        if (pending != null) {
            pending.cancel(false);
        }
        saveQueued.set(false);
        syncSave();
    }

    private void queueSave() {
        if (!saveQueued.compareAndSet(false, true)) {
            return;
        }
        ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                syncSave();
            } finally {
                saveQueued.set(false);
                saveFuture.set(null);
            }
        }, AscendConstants.SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        saveFuture.set(future);
    }

    private void syncSave() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        // Take a snapshot of dirty players but do NOT clear yet.
        // We only remove successfully saved IDs after the save completes,
        // so any markDirty() calls during the save window are preserved.
        Set<UUID> toSave = Set.copyOf(dirtyPlayers);
        if (toSave.isEmpty()) {
            return;
        }

        String playerSql = """
            INSERT INTO ascend_players (uuid, coins, elevation_multiplier, ascension_count,
                skill_tree_points, total_coins_earned, total_manual_runs, active_title,
                ascension_started_at, fastest_ascension_ms, last_active_timestamp, has_unclaimed_passive,
                summit_accumulated_coins, elevation_accumulated_coins, auto_upgrade_enabled, seen_tutorials)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                coins = VALUES(coins), elevation_multiplier = VALUES(elevation_multiplier),
                ascension_count = VALUES(ascension_count), skill_tree_points = VALUES(skill_tree_points),
                total_coins_earned = VALUES(total_coins_earned), total_manual_runs = VALUES(total_manual_runs),
                active_title = VALUES(active_title), ascension_started_at = VALUES(ascension_started_at),
                fastest_ascension_ms = VALUES(fastest_ascension_ms),
                last_active_timestamp = VALUES(last_active_timestamp),
                has_unclaimed_passive = VALUES(has_unclaimed_passive),
                summit_accumulated_coins = VALUES(summit_accumulated_coins),
                elevation_accumulated_coins = VALUES(elevation_accumulated_coins),
                auto_upgrade_enabled = VALUES(auto_upgrade_enabled),
                seen_tutorials = VALUES(seen_tutorials)
            """;

        String mapSql = """
            INSERT INTO ascend_player_maps (player_uuid, map_id, unlocked, completed_manually,
                has_robot, robot_speed_level, robot_stars, multiplier, best_time_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                unlocked = VALUES(unlocked), completed_manually = VALUES(completed_manually),
                has_robot = VALUES(has_robot), robot_speed_level = VALUES(robot_speed_level),
                robot_stars = VALUES(robot_stars), multiplier = VALUES(multiplier),
                best_time_ms = VALUES(best_time_ms)
            """;

        String summitSql = """
            INSERT INTO ascend_player_summit (player_uuid, category, xp)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE xp = VALUES(xp)
            """;

        String skillSql = """
            INSERT IGNORE INTO ascend_player_skills (player_uuid, skill_node)
            VALUES (?, ?)
            """;

        String achievementSql = """
            INSERT IGNORE INTO ascend_player_achievements (player_uuid, achievement)
            VALUES (?, ?)
            """;

        String deleteChildSql = "DELETE FROM %s WHERE player_uuid = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false); // Begin transaction
            try (PreparedStatement playerStmt = conn.prepareStatement(playerSql);
                 PreparedStatement mapStmt = conn.prepareStatement(mapSql);
                 PreparedStatement summitStmt = conn.prepareStatement(summitSql);
                 PreparedStatement skillStmt = conn.prepareStatement(skillSql);
                 PreparedStatement achievementStmt = conn.prepareStatement(achievementSql);
                 PreparedStatement delMaps = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_maps"));
                 PreparedStatement delSummit = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_summit"));
                 PreparedStatement delSkills = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_skills"));
                 PreparedStatement delAchievements = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_achievements"))) {
                DatabaseManager.applyQueryTimeout(playerStmt);
                DatabaseManager.applyQueryTimeout(mapStmt);
                DatabaseManager.applyQueryTimeout(summitStmt);
                DatabaseManager.applyQueryTimeout(skillStmt);
                DatabaseManager.applyQueryTimeout(achievementStmt);

            for (UUID playerId : toSave) {
                AscendPlayerProgress progress = players.get(playerId);
                if (progress == null) {
                    continue;
                }

                // If this player was recently reset, delete all child rows first
                // to prevent stale upserts from re-inserting deleted data
                if (resetPendingPlayers.remove(playerId)) {
                    String pid = playerId.toString();
                    for (PreparedStatement delStmt : new PreparedStatement[]{delMaps, delSummit, delSkills, delAchievements}) {
                        delStmt.setString(1, pid);
                        delStmt.executeUpdate();
                    }
                }

                // Save player base data
                playerStmt.setString(1, playerId.toString());
                playerStmt.setBigDecimal(2, progress.getCoins());
                playerStmt.setInt(3, progress.getElevationMultiplier());
                playerStmt.setInt(4, progress.getAscensionCount());
                playerStmt.setInt(5, progress.getSkillTreePoints());
                playerStmt.setBigDecimal(6, progress.getTotalCoinsEarned());
                playerStmt.setInt(7, progress.getTotalManualRuns());
                playerStmt.setString(8, progress.getActiveTitle());
                if (progress.getAscensionStartedAt() != null) {
                    playerStmt.setLong(9, progress.getAscensionStartedAt());
                } else {
                    playerStmt.setNull(9, java.sql.Types.BIGINT);
                }
                if (progress.getFastestAscensionMs() != null) {
                    playerStmt.setLong(10, progress.getFastestAscensionMs());
                } else {
                    playerStmt.setNull(10, java.sql.Types.BIGINT);
                }
                // Set passive earnings fields (positions 11, 12)
                if (progress.getLastActiveTimestamp() != null) {
                    playerStmt.setLong(11, progress.getLastActiveTimestamp());
                } else {
                    playerStmt.setNull(11, java.sql.Types.BIGINT);
                }
                playerStmt.setBoolean(12, progress.hasUnclaimedPassive());
                playerStmt.setBigDecimal(13, progress.getSummitAccumulatedCoins());
                playerStmt.setBigDecimal(14, progress.getElevationAccumulatedCoins());
                playerStmt.setBoolean(15, progress.isAutoUpgradeEnabled());
                playerStmt.setInt(16, progress.getSeenTutorials());
                playerStmt.addBatch();

                // Save map progress
                for (Map.Entry<String, AscendPlayerProgress.MapProgress> entry : progress.getMapProgress().entrySet()) {
                    AscendPlayerProgress.MapProgress mapProgress = entry.getValue();
                    mapStmt.setString(1, playerId.toString());
                    mapStmt.setString(2, entry.getKey());
                    mapStmt.setBoolean(3, mapProgress.isUnlocked());
                    mapStmt.setBoolean(4, mapProgress.isCompletedManually());
                    mapStmt.setBoolean(5, mapProgress.hasRobot());
                    mapStmt.setInt(6, mapProgress.getRobotSpeedLevel());
                    mapStmt.setInt(7, mapProgress.getRobotStars());
                    mapStmt.setBigDecimal(8, mapProgress.getMultiplier());
                    if (mapProgress.getBestTimeMs() != null) {
                        mapStmt.setLong(9, mapProgress.getBestTimeMs());
                    } else {
                        mapStmt.setNull(9, java.sql.Types.BIGINT);
                    }
                    mapStmt.addBatch();
                }

                // Save summit XP
                for (SummitCategory category : SummitCategory.values()) {
                    long xp = progress.getSummitXp(category);
                    summitStmt.setString(1, playerId.toString());
                    summitStmt.setString(2, category.name());
                    summitStmt.setLong(3, xp);
                    summitStmt.addBatch();
                }

                // Save skill nodes
                for (SkillTreeNode node : progress.getUnlockedSkillNodes()) {
                    skillStmt.setString(1, playerId.toString());
                    skillStmt.setString(2, node.name());
                    skillStmt.addBatch();
                }

                // Save achievements
                for (AchievementType achievement : progress.getUnlockedAchievements()) {
                    achievementStmt.setString(1, playerId.toString());
                    achievementStmt.setString(2, achievement.name());
                    achievementStmt.addBatch();
                }
            }
                playerStmt.executeBatch();
                mapStmt.executeBatch();
                summitStmt.executeBatch();
                skillStmt.executeBatch();
                achievementStmt.executeBatch();

                conn.commit(); // Commit transaction

                // Save succeeded - remove only the IDs we just saved
                dirtyPlayers.removeAll(toSave);
            } catch (SQLException e) {
                conn.rollback(); // Rollback transaction on error
                LOGGER.at(Level.SEVERE).log("Failed to save ascend players (rolled back): " + e.getMessage());
                // On failure, IDs stay in dirtyPlayers for the next save cycle
                throw e; // Re-throw to trigger outer catch
            }
        } catch (SQLException e) {
            // Outer catch for connection/transaction setup errors
            LOGGER.at(Level.SEVERE).log("Failed to initialize transaction for ascend player save: " + e.getMessage());
        }
    }

    // ========================================
    // Passive Earnings
    // ========================================

    public Long getLastActiveTimestamp(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getLastActiveTimestamp() : null;
    }

    public void setLastActiveTimestamp(UUID playerId, Long timestamp) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setLastActiveTimestamp(timestamp);
        markDirty(playerId);
    }

    public boolean hasUnclaimedPassive(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.hasUnclaimedPassive();
    }

    public void setHasUnclaimedPassive(UUID playerId, boolean hasUnclaimed) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setHasUnclaimedPassive(hasUnclaimed);
        markDirty(playerId);
    }

    public boolean isAutoUpgradeEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.isAutoUpgradeEnabled();
    }

    public void setAutoUpgradeEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setAutoUpgradeEnabled(enabled);
        markDirty(playerId);
    }

    private static int safeGetInt(ResultSet rs, String column, int defaultValue) {
        try {
            return rs.getInt(column);
        } catch (SQLException e) {
            LOGGER.atWarning().log("Column '" + column + "' not available: " + e.getMessage());
            return defaultValue;
        }
    }

    private static boolean safeGetBoolean(ResultSet rs, String column, boolean defaultValue) {
        try {
            return rs.getBoolean(column);
        } catch (SQLException e) {
            LOGGER.atWarning().log("Column '" + column + "' not available: " + e.getMessage());
            return defaultValue;
        }
    }

    private static String safeGetString(ResultSet rs, String column, String defaultValue) {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            LOGGER.atWarning().log("Column '" + column + "' not available: " + e.getMessage());
            return defaultValue;
        }
    }

    private static Long safeGetLong(ResultSet rs, String column) {
        try {
            long value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        } catch (SQLException e) {
            LOGGER.atWarning().log("Column '" + column + "' not available: " + e.getMessage());
            return null;
        }
    }

    private static BigDecimal safeGetBigDecimal(ResultSet rs, String column, BigDecimal defaultValue) {
        try {
            BigDecimal value = rs.getBigDecimal(column);
            return value != null ? value : defaultValue;
        } catch (SQLException e) {
            LOGGER.atWarning().log("Column '" + column + "' not available: " + e.getMessage());
            return defaultValue;
        }
    }
}
