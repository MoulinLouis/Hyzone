package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.AchievementType;
import io.hyvexa.ascend.AscendConstants.SkillTreeNode;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.util.MapUnlockHelper;
import io.hyvexa.core.db.DatabaseManager;

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

    private final Map<UUID, AscendPlayerProgress> players = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> saveFuture = new AtomicReference<>();

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, AscendPlayerStore will be empty");
            return;
        }

        players.clear();
        loadPlayers();
        loadMapProgress();
        loadSummitLevels();
        loadSkillNodes();
        loadAchievements();
        LOGGER.atInfo().log("AscendPlayerStore loaded " + players.size() + " players");
    }

    private void loadPlayers() {
        String sql = """
            SELECT uuid, coins, elevation_multiplier, ascension_count, skill_tree_points,
                   total_coins_earned, total_manual_runs, active_title,
                   ascension_started_at, fastest_ascension_ms
            FROM ascend_players
            """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    AscendPlayerProgress progress = new AscendPlayerProgress();
                    progress.setCoins(rs.getDouble("coins"));
                    progress.setElevationMultiplier(rs.getInt("elevation_multiplier"));
                    // Load new fields (with fallback for older schema)
                    try {
                        progress.setAscensionCount(rs.getInt("ascension_count"));
                        progress.setSkillTreePoints(rs.getInt("skill_tree_points"));
                        progress.setTotalCoinsEarned(rs.getDouble("total_coins_earned"));
                        progress.setTotalManualRuns(rs.getInt("total_manual_runs"));
                        progress.setActiveTitle(rs.getString("active_title"));
                        long ascensionStartedAt = rs.getLong("ascension_started_at");
                        if (!rs.wasNull()) {
                            progress.setAscensionStartedAt(ascensionStartedAt);
                        }
                        long fastestAscensionMs = rs.getLong("fastest_ascension_ms");
                        if (!rs.wasNull()) {
                            progress.setFastestAscensionMs(fastestAscensionMs);
                        }
                    } catch (SQLException ignored) {
                        // Columns may not exist yet
                    }
                    players.put(uuid, progress);
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load ascend players: " + e.getMessage());
        }
    }

    private void loadMapProgress() {
        String sql = """
            SELECT player_uuid, map_id, unlocked, completed_manually, has_robot,
                   robot_speed_level, robot_stars, multiplier, best_time_ms
            FROM ascend_player_maps
            """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    AscendPlayerProgress player = players.get(uuid);
                    if (player == null) {
                        continue;
                    }

                    String mapId = rs.getString("map_id");
                    AscendPlayerProgress.MapProgress mapProgress = player.getOrCreateMapProgress(mapId);
                    mapProgress.setUnlocked(rs.getBoolean("unlocked"));
                    mapProgress.setCompletedManually(rs.getBoolean("completed_manually"));
                    mapProgress.setHasRobot(rs.getBoolean("has_robot"));
                    mapProgress.setRobotSpeedLevel(rs.getInt("robot_speed_level"));
                    mapProgress.setRobotStars(rs.getInt("robot_stars"));
                    mapProgress.setMultiplier(rs.getDouble("multiplier"));
                    long bestTime = rs.getLong("best_time_ms");
                    if (!rs.wasNull()) {
                        mapProgress.setBestTimeMs(bestTime);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load ascend map progress: " + e.getMessage());
        }
    }

    private void loadSummitLevels() {
        String sql = "SELECT player_uuid, category, level FROM ascend_player_summit";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    AscendPlayerProgress player = players.get(uuid);
                    if (player == null) {
                        continue;
                    }
                    String categoryName = rs.getString("category");
                    int level = rs.getInt("level");
                    try {
                        SummitCategory category = SummitCategory.valueOf(categoryName);
                        player.setSummitLevel(category, level);
                    } catch (IllegalArgumentException ignored) {
                        // Unknown category, skip
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load summit levels: " + e.getMessage());
        }
    }

    private void loadSkillNodes() {
        String sql = "SELECT player_uuid, skill_node FROM ascend_player_skills";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    AscendPlayerProgress player = players.get(uuid);
                    if (player == null) {
                        continue;
                    }
                    String nodeName = rs.getString("skill_node");
                    try {
                        SkillTreeNode node = SkillTreeNode.valueOf(nodeName);
                        player.unlockSkillNode(node);
                    } catch (IllegalArgumentException ignored) {
                        // Unknown node, skip
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load skill nodes: " + e.getMessage());
        }
    }

    private void loadAchievements() {
        String sql = "SELECT player_uuid, achievement FROM ascend_player_achievements";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    AscendPlayerProgress player = players.get(uuid);
                    if (player == null) {
                        continue;
                    }
                    String achievementName = rs.getString("achievement");
                    try {
                        AchievementType achievement = AchievementType.valueOf(achievementName);
                        player.unlockAchievement(achievement);
                    } catch (IllegalArgumentException ignored) {
                        // Unknown achievement, skip
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load achievements: " + e.getMessage());
        }
    }

    public AscendPlayerProgress getPlayer(UUID playerId) {
        return players.get(playerId);
    }

    public Map<UUID, AscendPlayerProgress> getPlayersSnapshot() {
        return new HashMap<>(players);
    }

    public AscendPlayerProgress getOrCreatePlayer(UUID playerId) {
        AscendPlayerProgress progress = players.computeIfAbsent(playerId, k -> {
            AscendPlayerProgress newProgress = new AscendPlayerProgress();
            // Initialize ascension timer for new players
            newProgress.setAscensionStartedAt(System.currentTimeMillis());
            dirtyPlayers.add(playerId);
            queueSave();
            return newProgress;
        });
        // Ensure existing players have ascension timer initialized (migration)
        if (progress.getAscensionStartedAt() == null) {
            progress.setAscensionStartedAt(System.currentTimeMillis());
            markDirty(playerId);
        }
        return progress;
    }

    public void resetPlayerProgress(UUID playerId) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        // Reset basic progression
        progress.setCoins(0.0);
        progress.setElevationMultiplier(1);
        progress.getMapProgress().clear();

        // Reset Summit system
        progress.clearSummitLevels();
        progress.setTotalCoinsEarned(0.0);

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
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // Delete map progress
            try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM ascend_player_maps WHERE player_uuid = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerIdStr);
                stmt.executeUpdate();
            }

            // Delete summit levels
            try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM ascend_player_summit WHERE player_uuid = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerIdStr);
                stmt.executeUpdate();
            }

            // Delete skill nodes
            try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM ascend_player_skills WHERE player_uuid = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerIdStr);
                stmt.executeUpdate();
            }

            // Delete achievements
            try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM ascend_player_achievements WHERE player_uuid = ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerIdStr);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to delete player data during reset: " + e.getMessage());
        }
    }

    public void markDirty(UUID playerId) {
        dirtyPlayers.add(playerId);
        queueSave();
    }

    public double getCoins(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getCoins() : 0.0;
    }

    public void setCoins(UUID playerId, double coins) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setCoins(Math.max(0.0, coins));
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
     * @deprecated Use {@link #getElevationLevel(UUID)} for the level,
     * or {@link #getCalculatedElevationMultiplier(UUID)} for the actual multiplier.
     */
    @Deprecated
    public int getElevationMultiplier(UUID playerId) {
        return getElevationLevel(playerId);
    }

    /**
     * Get the player's elevation multiplier.
     * The stored value IS the multiplier directly (no conversion).
     */
    public double getCalculatedElevationMultiplier(UUID playerId) {
        return getElevationLevel(playerId);
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

    /**
     * @deprecated Use {@link #addElevationLevel(UUID, int)} instead.
     */
    @Deprecated
    public int addElevationMultiplier(UUID playerId, int amount) {
        return addElevationLevel(playerId, amount);
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

    public void addCoins(UUID playerId, double amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addCoins(amount);
        markDirty(playerId);
    }

    public boolean spendCoins(UUID playerId, double amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress.getCoins() < amount) {
            return false;
        }
        progress.addCoins(-amount);
        markDirty(playerId);
        return true;
    }

    public double getMapMultiplier(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 1.0;
        }
        return Math.max(1.0, mapProgress.getMultiplier());
    }

    public double addMapMultiplier(UUID playerId, String mapId, double amount) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        double value = mapProgress.addMultiplier(amount);
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

    public int addSummitLevel(UUID playerId, SummitCategory category, int amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        int newLevel = progress.addSummitLevel(category, amount);
        markDirty(playerId);
        return newLevel;
    }

    public Map<SummitCategory, Integer> getSummitLevels(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return Map.of();
        }
        return progress.getSummitLevels();
    }

    public double getSummitBonus(UUID playerId, SummitCategory category) {
        int level = getSummitLevel(playerId, category);
        return category.getBonusForLevel(level);
    }

    public double getTotalCoinsEarned(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getTotalCoinsEarned() : 0.0;
    }

    public void addTotalCoinsEarned(UUID playerId, double amount) {
        if (amount <= 0.0) {
            return;
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addTotalCoinsEarned(amount);
        markDirty(playerId);
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
        // Check prerequisite
        SkillTreeNode prereq = node.getPrerequisite();
        if (prereq != null && !progress.hasSkillNode(prereq)) {
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

    public boolean isSessionFirstRunClaimed(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.isSessionFirstRunClaimed();
    }

    public void setSessionFirstRunClaimed(UUID playerId, boolean claimed) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setSessionFirstRunClaimed(claimed);
        // Don't persist - this is session-only
    }

    public double[] getMultiplierDisplayValues(UUID playerId, List<AscendMap> maps, int slotCount) {
        int slots = Math.max(0, slotCount);
        double[] digits = new double[slots];
        for (int i = 0; i < slots; i++) {
            digits[i] = 1.0;
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
        return (long) Math.floor(getMultiplierProductDecimal(playerId, maps, slotCount));
    }

    public double getMultiplierProductDecimal(UUID playerId, List<AscendMap> maps, int slotCount) {
        double product = 1.0;
        int slots = Math.max(0, slotCount);
        if (maps == null || maps.isEmpty() || slots == 0) {
            double elevation = getCalculatedElevationMultiplier(playerId);
            return product * elevation;
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            double value = getMapMultiplier(playerId, map.getId());
            product *= Math.max(1.0, value);
            index++;
        }
        double elevation = getCalculatedElevationMultiplier(playerId);
        return product * elevation;
    }

    public double getCompletionPayout(UUID playerId, List<AscendMap> maps, int slotCount, String mapId, double bonusAmount) {
        double product = 1.0;
        int slots = Math.max(0, slotCount);
        if (maps == null || maps.isEmpty() || slots == 0) {
            double elevation = getCalculatedElevationMultiplier(playerId);
            return product * elevation;
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            double value = getMapMultiplier(playerId, map.getId());
            if (map.getId().equals(mapId)) {
                value += bonusAmount;
            }
            product *= Math.max(1.0, value);
            index++;
        }
        double elevation = getCalculatedElevationMultiplier(playerId);
        return product * elevation;
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

        Set<UUID> toSave = Set.copyOf(dirtyPlayers);
        dirtyPlayers.clear();
        if (toSave.isEmpty()) {
            return;
        }

        String playerSql = """
            INSERT INTO ascend_players (uuid, coins, elevation_multiplier, ascension_count,
                skill_tree_points, total_coins_earned, total_manual_runs, active_title,
                ascension_started_at, fastest_ascension_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                coins = VALUES(coins), elevation_multiplier = VALUES(elevation_multiplier),
                ascension_count = VALUES(ascension_count), skill_tree_points = VALUES(skill_tree_points),
                total_coins_earned = VALUES(total_coins_earned), total_manual_runs = VALUES(total_manual_runs),
                active_title = VALUES(active_title), ascension_started_at = VALUES(ascension_started_at),
                fastest_ascension_ms = VALUES(fastest_ascension_ms)
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
            INSERT INTO ascend_player_summit (player_uuid, category, level)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE level = VALUES(level)
            """;

        String skillSql = """
            INSERT IGNORE INTO ascend_player_skills (player_uuid, skill_node)
            VALUES (?, ?)
            """;

        String achievementSql = """
            INSERT IGNORE INTO ascend_player_achievements (player_uuid, achievement)
            VALUES (?, ?)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement playerStmt = conn.prepareStatement(playerSql);
             PreparedStatement mapStmt = conn.prepareStatement(mapSql);
             PreparedStatement summitStmt = conn.prepareStatement(summitSql);
             PreparedStatement skillStmt = conn.prepareStatement(skillSql);
             PreparedStatement achievementStmt = conn.prepareStatement(achievementSql)) {
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

                // Save player base data
                playerStmt.setString(1, playerId.toString());
                playerStmt.setDouble(2, progress.getCoins());
                playerStmt.setInt(3, progress.getElevationMultiplier());
                playerStmt.setInt(4, progress.getAscensionCount());
                playerStmt.setInt(5, progress.getSkillTreePoints());
                playerStmt.setDouble(6, progress.getTotalCoinsEarned());
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
                    mapStmt.setDouble(8, mapProgress.getMultiplier());
                    if (mapProgress.getBestTimeMs() != null) {
                        mapStmt.setLong(9, mapProgress.getBestTimeMs());
                    } else {
                        mapStmt.setNull(9, java.sql.Types.BIGINT);
                    }
                    mapStmt.addBatch();
                }

                // Save summit levels
                for (SummitCategory category : SummitCategory.values()) {
                    int level = progress.getSummitLevel(category);
                    summitStmt.setString(1, playerId.toString());
                    summitStmt.setString(2, category.name());
                    summitStmt.setInt(3, level);
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
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save ascend players: " + e.getMessage());
            dirtyPlayers.addAll(toSave);
        }
    }
}
