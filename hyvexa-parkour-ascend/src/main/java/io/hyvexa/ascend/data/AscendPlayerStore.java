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
import io.hyvexa.common.math.BigNumber;
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
    private static final long LEADERBOARD_CACHE_TTL_MS = 30_000;

    private final Map<UUID, AscendPlayerProgress> players = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> resetPendingPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> saveFuture = new AtomicReference<>();

    private volatile List<LeaderboardEntry> leaderboardCache = List.of();
    private volatile long leaderboardCacheTimestamp = 0;

    // Per-map leaderboard cache
    private final Map<String, List<MapLeaderboardEntry>> mapLeaderboardCache = new ConcurrentHashMap<>();
    private final Map<String, Long> mapLeaderboardCacheTimestamps = new ConcurrentHashMap<>();

    public record LeaderboardEntry(UUID playerId, String playerName,
            double totalVexaEarnedMantissa, int totalVexaEarnedExp10,
            int ascensionCount, int totalManualRuns, Long fastestAscensionMs) {}

    public record MapLeaderboardEntry(String playerName, long bestTimeMs) {}

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
            // Migration: start ascension timer for players who don't have one yet
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
            // Migration: start ascension timer for players who don't have one yet
            if (progress.getAscensionStartedAt() == null) {
                progress.setAscensionStartedAt(System.currentTimeMillis());
                markDirty(playerId);
            }
            return progress;
        }

        // Create new player — timer starts now (first run toward first ascension)
        AscendPlayerProgress newProgress = new AscendPlayerProgress();
        newProgress.setAscensionStartedAt(System.currentTimeMillis());
        players.put(playerId, newProgress);
        dirtyPlayers.add(playerId);
        queueSave();
        return newProgress;
    }

    public void storePlayerName(UUID playerId, String name) {
        if (playerId == null || name == null) {
            return;
        }
        String trimmed = name.length() > 32 ? name.substring(0, 32) : name;
        playerNames.put(playerId, trimmed);
    }

    public String getPlayerName(UUID playerId) {
        return playerNames.get(playerId);
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
            SELECT player_name, vexa_mantissa, vexa_exp10, elevation_multiplier, ascension_count, skill_tree_points,
                   total_vexa_earned_mantissa, total_vexa_earned_exp10, total_manual_runs, active_title,
                   ascension_started_at, fastest_ascension_ms,
                   last_active_timestamp, has_unclaimed_passive,
                   summit_accumulated_vexa_mantissa, summit_accumulated_vexa_exp10,
                   elevation_accumulated_vexa_mantissa, elevation_accumulated_vexa_exp10,
                   auto_upgrade_enabled, auto_evolution_enabled, seen_tutorials, hide_other_runners
            FROM ascend_players
            WHERE uuid = ?
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(playerSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerIdStr);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String dbName = safeGetString(rs, "player_name", null);
                    if (dbName != null) {
                        playerNames.put(playerId, dbName);
                    }
                    progress = new AscendPlayerProgress();
                    progress.setVexa(BigNumber.of(rs.getDouble("vexa_mantissa"), rs.getInt("vexa_exp10")));
                    progress.setElevationMultiplier(rs.getInt("elevation_multiplier"));

                    progress.setAscensionCount(safeGetInt(rs, "ascension_count", 0));
                    progress.setSkillTreePoints(safeGetInt(rs, "skill_tree_points", 0));
                    progress.setTotalVexaEarned(safeGetBigNumber(rs, "total_vexa_earned_mantissa", "total_vexa_earned_exp10"));
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

                    BigNumber summitAccumulated = safeGetBigNumber(rs, "summit_accumulated_vexa_mantissa", "summit_accumulated_vexa_exp10");
                    if (!summitAccumulated.isZero()) {
                        progress.setSummitAccumulatedVexa(summitAccumulated);
                    }

                    BigNumber elevationAccumulated = safeGetBigNumber(rs, "elevation_accumulated_vexa_mantissa", "elevation_accumulated_vexa_exp10");
                    if (!elevationAccumulated.isZero()) {
                        progress.setElevationAccumulatedVexa(elevationAccumulated);
                    }

                    progress.setAutoUpgradeEnabled(safeGetBoolean(rs, "auto_upgrade_enabled", false));
                    progress.setAutoEvolutionEnabled(safeGetBoolean(rs, "auto_evolution_enabled", false));
                    progress.setSeenTutorials(safeGetInt(rs, "seen_tutorials", 0));
                    progress.setHideOtherRunners(safeGetBoolean(rs, "hide_other_runners", false));
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
                   robot_speed_level, robot_stars, multiplier_mantissa, multiplier_exp10, best_time_ms
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
                    mapProgress.setMultiplier(BigNumber.of(rs.getDouble("multiplier_mantissa"), rs.getInt("multiplier_exp10")));
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
        progress.setVexa(BigNumber.ZERO);
        progress.setElevationMultiplier(1);
        progress.getMapProgress().clear();

        // Reset Summit system
        progress.clearSummitXp();
        progress.setTotalVexaEarned(BigNumber.ZERO);
        progress.setSummitAccumulatedVexa(BigNumber.ZERO);
        progress.setElevationAccumulatedVexa(BigNumber.ZERO);

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

    public BigNumber getVexa(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getVexa() : BigNumber.ZERO;
    }

    public void setVexa(UUID playerId, BigNumber vexa) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setVexa(vexa.max(BigNumber.ZERO));
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

    public void addVexa(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addVexa(amount);
        markDirty(playerId);
    }

    public boolean spendVexa(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        if (progress.getVexa().lt(amount)) {
            return false;
        }
        progress.addVexa(amount.negate());
        markDirty(playerId);
        return true;
    }

    // ========================================
    // Vexa Operations (In-memory CAS + debounced save)
    // ========================================

    /**
     * Add vexa to a player. Updates in-memory state atomically
     * and marks dirty for debounced DB save.
     *
     * @param playerId the player's UUID
     * @param amount the amount to add (can be negative to subtract)
     * @return true if the operation succeeded
     */
    public boolean atomicAddVexa(UUID playerId, BigNumber amount) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        BigNumber oldBalance = progress.getVexa();
        progress.addVexa(amount);
        BigNumber newBalance = progress.getVexa();
        markDirty(playerId);
        checkVexaTutorialThresholds(playerId, oldBalance, newBalance);
        return true;
    }

    private void checkVexaTutorialThresholds(UUID playerId, BigNumber oldBalance, BigNumber newBalance) {
        boolean crossedAscension = oldBalance.lt(AscendConstants.ASCENSION_VEXA_THRESHOLD)
                && newBalance.gte(AscendConstants.ASCENSION_VEXA_THRESHOLD);

        // Mark the ascension tutorial as seen BEFORE the tutorial check,
        // so the tutorial popup is suppressed in favor of the cinematic
        if (crossedAscension) {
            markTutorialSeen(playerId, TutorialTriggerService.ASCENSION);
        }

        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        TutorialTriggerService triggerService = plugin.getTutorialTriggerService();
        if (triggerService != null) {
            triggerService.checkVexaThresholds(playerId, oldBalance, newBalance);
        }

        // Trigger ascension cinematic every time the threshold is crossed
        if (crossedAscension) {
            triggerAscensionCinematic(playerId);
        }
    }

    private void triggerAscensionCinematic(UUID playerId) {
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin == null) return;

            com.hypixel.hytale.server.core.universe.PlayerRef playerRef = plugin.getPlayerRef(playerId);
            if (playerRef == null) return;

            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref =
                playerRef.getReference();
            if (ref == null || !ref.isValid()) return;

            com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store =
                ref.getStore();
            com.hypixel.hytale.server.core.universe.world.World world = store.getExternalData().getWorld();
            if (world == null) return;

            java.util.concurrent.CompletableFuture.runAsync(() -> {
                if (!ref.isValid()) return;
                com.hypixel.hytale.server.core.entity.entities.Player player =
                    store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
                com.hypixel.hytale.server.core.io.PacketHandler ph = playerRef.getPacketHandler();
                if (player == null || ph == null) return;

                io.hyvexa.ascend.ascension.AscensionCinematic.play(player, ph, playerRef, store, ref, world);
            }, world);
        }, 1500, TimeUnit.MILLISECONDS);
    }

    /**
     * Spend vexa with balance check (prevents negative balance).
     * Uses in-memory CAS loop. Returns false if insufficient funds.
     *
     * @param playerId the player's UUID
     * @param amount the amount to spend (must be positive)
     * @return true if the purchase succeeded (sufficient balance)
     */
    public boolean atomicSpendVexa(UUID playerId, BigNumber amount) {
        return spendVexa(playerId, amount);
    }

    /**
     * Add to total vexa earned (lifetime stat) + accumulated vexa trackers.
     * In-memory update + debounced save.
     *
     * @param playerId the player's UUID
     * @param amount the amount to add
     * @return true if the operation succeeded
     */
    public boolean atomicAddTotalVexaEarned(UUID playerId, BigNumber amount) {
        addTotalVexaEarned(playerId, amount);
        return true;
    }

    /**
     * Add to map multiplier. In-memory update + debounced save.
     *
     * @param playerId the player's UUID
     * @param mapId the map ID
     * @param amount the amount to add
     * @return true if the operation succeeded
     */
    public boolean atomicAddMapMultiplier(UUID playerId, String mapId, BigNumber amount) {
        addMapMultiplier(playerId, mapId, amount);
        return true;
    }

    /**
     * Set elevation level and reset vexa to 0 (for elevation purchase).
     * In-memory update + debounced save.
     *
     * @param playerId the player's UUID
     * @param newElevation the new elevation level
     * @return true if the operation succeeded
     */
    public boolean atomicSetElevationAndResetVexa(UUID playerId, int newElevation) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setElevationMultiplier(newElevation);
        progress.setVexa(BigNumber.ZERO);
        progress.setSummitAccumulatedVexa(BigNumber.ZERO);
        progress.setElevationAccumulatedVexa(BigNumber.ZERO);
        markDirty(playerId);
        return true;
    }

    public BigNumber getMapMultiplier(UUID playerId, String mapId) {
        AscendPlayerProgress.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return BigNumber.ONE;
        }
        return mapProgress.getMultiplier().max(BigNumber.ONE);
    }

    public BigNumber addMapMultiplier(UUID playerId, String mapId, BigNumber amount) {
        AscendPlayerProgress.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        BigNumber value = mapProgress.addMultiplier(amount);
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

    public double getSummitBonusDouble(UUID playerId, SummitCategory category) {
        int level = getSummitLevel(playerId, category);
        return category.getBonusForLevel(level);
    }

    public BigNumber getTotalVexaEarned(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getTotalVexaEarned() : BigNumber.ZERO;
    }

    public BigNumber getSummitAccumulatedVexa(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getSummitAccumulatedVexa() : BigNumber.ZERO;
    }

    public void addSummitAccumulatedVexa(UUID playerId, BigNumber amount) {
        if (amount.lte(BigNumber.ZERO)) {
            return;
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addSummitAccumulatedVexa(amount);
        markDirty(playerId);
    }

    public void addElevationAccumulatedVexa(UUID playerId, BigNumber amount) {
        if (amount.lte(BigNumber.ZERO)) {
            return;
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addElevationAccumulatedVexa(amount);
        markDirty(playerId);
    }

    public void addTotalVexaEarned(UUID playerId, BigNumber amount) {
        if (amount.lte(BigNumber.ZERO)) {
            return;
        }
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.addTotalVexaEarned(amount);
        progress.addSummitAccumulatedVexa(amount);
        progress.addElevationAccumulatedVexa(amount);
        markDirty(playerId);
    }

    public BigNumber getElevationAccumulatedVexa(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null ? progress.getElevationAccumulatedVexa() : BigNumber.ZERO;
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
     * Resets vexa, map unlocks (except first), multipliers, manual completion, and runners.
     * @param clearBestTimes whether to also clear best times (elevation does, summit doesn't)
     * @return list of map IDs that had runners (for despawn handling)
     */
    private List<String> resetMapProgress(AscendPlayerProgress progress, String firstMapId, boolean clearBestTimes, UUID playerId) {
        List<String> mapsWithRunners = new java.util.ArrayList<>();

        // Check Persistence skill: keep 10% of map multipliers after reset
        boolean hasPersistence = false;
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getAscensionManager() != null) {
            hasPersistence = plugin.getAscensionManager().hasPersistence(playerId);
        }

        progress.setVexa(BigNumber.ZERO);
        progress.setSummitAccumulatedVexa(BigNumber.ZERO);
        progress.setElevationAccumulatedVexa(BigNumber.ZERO);

        for (Map.Entry<String, AscendPlayerProgress.MapProgress> entry : progress.getMapProgress().entrySet()) {
            String mapId = entry.getKey();
            AscendPlayerProgress.MapProgress mapProgress = entry.getValue();

            mapProgress.setUnlocked(mapId.equals(firstMapId));

            // Persistence: keep 10% of multiplier (minimum 1.0)
            if (hasPersistence) {
                BigNumber retained = mapProgress.getMultiplier()
                    .multiply(BigNumber.fromDouble(0.10))
                    .max(BigNumber.ONE);
                mapProgress.setMultiplier(retained);
            } else {
                mapProgress.setMultiplier(BigNumber.ONE);
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
     * Resets player progress for elevation: clears vexa, map unlocks (except first map),
     * multipliers, and removes all runners. Best times are preserved.
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

        List<String> mapsWithRunners = resetMapProgress(progress, firstMapId, false, playerId);
        markDirty(playerId);
        return mapsWithRunners;
    }

    /**
     * Reset progress for Summit: vexa, elevation, multipliers, runners, and map unlocks.
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

    public BigNumber[] getMultiplierDisplayValues(UUID playerId, List<AscendMap> maps, int slotCount) {
        int slots = Math.max(0, slotCount);
        BigNumber[] digits = new BigNumber[slots];
        for (int i = 0; i < slots; i++) {
            digits[i] = BigNumber.ONE;
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

    public BigNumber getMultiplierProduct(UUID playerId, List<AscendMap> maps, int slotCount) {
        BigNumber product = BigNumber.ONE;
        int slots = Math.max(0, slotCount);
        if (maps == null || maps.isEmpty() || slots == 0) {
            BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
            return product.multiply(elevation);
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            BigNumber value = getMapMultiplier(playerId, map.getId());
            product = product.multiply(value.max(BigNumber.ONE));
            index++;
        }
        BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
        return product.multiply(elevation);
    }

    public BigNumber getCompletionPayout(UUID playerId, List<AscendMap> maps, int slotCount, String mapId, BigNumber bonusAmount) {
        BigNumber product = BigNumber.ONE;
        int slots = Math.max(0, slotCount);
        if (maps == null || maps.isEmpty() || slots == 0) {
            BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
            return product.multiply(elevation);
        }
        int index = 0;
        for (AscendMap map : maps) {
            if (index >= slots) {
                break;
            }
            if (map == null || map.getId() == null) {
                continue;
            }
            BigNumber value = getMapMultiplier(playerId, map.getId());
            if (map.getId().equals(mapId)) {
                value = value.add(bonusAmount);
            }
            product = product.multiply(value.max(BigNumber.ONE));
            index++;
        }
        BigNumber elevation = BigNumber.fromDouble(getCalculatedElevationMultiplier(playerId));
        return product.multiply(elevation);
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
     * Delete ALL player data from the database and clear all in-memory caches.
     * This is a server-wide wipe for launching in a fresh state.
     */
    public void resetAllPlayersProgress() {
        // Cancel any pending debounced save to prevent stale data re-insertion
        ScheduledFuture<?> pending = saveFuture.getAndSet(null);
        if (pending != null) {
            pending.cancel(false);
        }
        saveQueued.set(false);

        // Wipe DB FIRST — if we clear in-memory caches first, another thread
        // (passive earnings, run tracker) can call getOrCreatePlayer() which
        // re-loads old data from the not-yet-wiped DB back into memory.
        if (DatabaseManager.getInstance().isInitialized()) {
            String[] tables = {
                "ascend_player_maps",
                "ascend_player_summit",
                "ascend_player_skills",
                "ascend_player_achievements",
                "ascend_ghost_recordings",
                "ascend_players"
            };

            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                conn.setAutoCommit(false);
                try {
                    for (String table : tables) {
                        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table)) {
                            DatabaseManager.applyQueryTimeout(stmt);
                            stmt.executeUpdate();
                        }
                    }
                    conn.commit();
                    LOGGER.atInfo().log("All player progress wiped from database (%d tables cleared)", tables.length);
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to wipe all player data: " + e.getMessage());
            }
        }

        // Now clear in-memory caches — any subsequent getOrCreatePlayer() will
        // find an empty DB and create fresh default progress
        players.clear();
        playerNames.clear();
        dirtyPlayers.clear();
        resetPendingPlayers.clear();

        // Invalidate leaderboard caches
        leaderboardCache = List.of();
        leaderboardCacheTimestamp = 0;
        mapLeaderboardCache.clear();
        mapLeaderboardCacheTimestamps.clear();
    }

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
            INSERT INTO ascend_players (uuid, player_name, vexa_mantissa, vexa_exp10, elevation_multiplier, ascension_count,
                skill_tree_points, total_vexa_earned_mantissa, total_vexa_earned_exp10, total_manual_runs, active_title,
                ascension_started_at, fastest_ascension_ms, last_active_timestamp, has_unclaimed_passive,
                summit_accumulated_vexa_mantissa, summit_accumulated_vexa_exp10,
                elevation_accumulated_vexa_mantissa, elevation_accumulated_vexa_exp10,
                auto_upgrade_enabled, auto_evolution_enabled, seen_tutorials, hide_other_runners)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name = VALUES(player_name),
                vexa_mantissa = VALUES(vexa_mantissa), vexa_exp10 = VALUES(vexa_exp10),
                elevation_multiplier = VALUES(elevation_multiplier),
                ascension_count = VALUES(ascension_count), skill_tree_points = VALUES(skill_tree_points),
                total_vexa_earned_mantissa = VALUES(total_vexa_earned_mantissa),
                total_vexa_earned_exp10 = VALUES(total_vexa_earned_exp10),
                total_manual_runs = VALUES(total_manual_runs),
                active_title = VALUES(active_title), ascension_started_at = VALUES(ascension_started_at),
                fastest_ascension_ms = VALUES(fastest_ascension_ms),
                last_active_timestamp = VALUES(last_active_timestamp),
                has_unclaimed_passive = VALUES(has_unclaimed_passive),
                summit_accumulated_vexa_mantissa = VALUES(summit_accumulated_vexa_mantissa),
                summit_accumulated_vexa_exp10 = VALUES(summit_accumulated_vexa_exp10),
                elevation_accumulated_vexa_mantissa = VALUES(elevation_accumulated_vexa_mantissa),
                elevation_accumulated_vexa_exp10 = VALUES(elevation_accumulated_vexa_exp10),
                auto_upgrade_enabled = VALUES(auto_upgrade_enabled),
                auto_evolution_enabled = VALUES(auto_evolution_enabled),
                hide_other_runners = VALUES(hide_other_runners),
                seen_tutorials = VALUES(seen_tutorials)
            """;

        String mapSql = """
            INSERT INTO ascend_player_maps (player_uuid, map_id, unlocked, completed_manually,
                has_robot, robot_speed_level, robot_stars, multiplier_mantissa, multiplier_exp10, best_time_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                unlocked = VALUES(unlocked), completed_manually = VALUES(completed_manually),
                has_robot = VALUES(has_robot), robot_speed_level = VALUES(robot_speed_level),
                robot_stars = VALUES(robot_stars), multiplier_mantissa = VALUES(multiplier_mantissa),
                multiplier_exp10 = VALUES(multiplier_exp10),
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
                playerStmt.setString(2, playerNames.get(playerId));
                playerStmt.setDouble(3, progress.getVexa().getMantissa());
                playerStmt.setInt(4, progress.getVexa().getExponent());
                playerStmt.setInt(5, progress.getElevationMultiplier());
                playerStmt.setInt(6, progress.getAscensionCount());
                playerStmt.setInt(7, progress.getSkillTreePoints());
                playerStmt.setDouble(8, progress.getTotalVexaEarned().getMantissa());
                playerStmt.setInt(9, progress.getTotalVexaEarned().getExponent());
                playerStmt.setInt(10, progress.getTotalManualRuns());
                playerStmt.setString(11, progress.getActiveTitle());
                if (progress.getAscensionStartedAt() != null) {
                    playerStmt.setLong(12, progress.getAscensionStartedAt());
                } else {
                    playerStmt.setNull(12, java.sql.Types.BIGINT);
                }
                if (progress.getFastestAscensionMs() != null) {
                    playerStmt.setLong(13, progress.getFastestAscensionMs());
                } else {
                    playerStmt.setNull(13, java.sql.Types.BIGINT);
                }
                if (progress.getLastActiveTimestamp() != null) {
                    playerStmt.setLong(14, progress.getLastActiveTimestamp());
                } else {
                    playerStmt.setNull(14, java.sql.Types.BIGINT);
                }
                playerStmt.setBoolean(15, progress.hasUnclaimedPassive());
                playerStmt.setDouble(16, progress.getSummitAccumulatedVexa().getMantissa());
                playerStmt.setInt(17, progress.getSummitAccumulatedVexa().getExponent());
                playerStmt.setDouble(18, progress.getElevationAccumulatedVexa().getMantissa());
                playerStmt.setInt(19, progress.getElevationAccumulatedVexa().getExponent());
                playerStmt.setBoolean(20, progress.isAutoUpgradeEnabled());
                playerStmt.setBoolean(21, progress.isAutoEvolutionEnabled());
                playerStmt.setInt(22, progress.getSeenTutorials());
                playerStmt.setBoolean(23, progress.isHideOtherRunners());
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
                    mapStmt.setDouble(8, mapProgress.getMultiplier().getMantissa());
                    mapStmt.setInt(9, mapProgress.getMultiplier().getExponent());
                    if (mapProgress.getBestTimeMs() != null) {
                        mapStmt.setLong(10, mapProgress.getBestTimeMs());
                    } else {
                        mapStmt.setNull(10, java.sql.Types.BIGINT);
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
    // Leaderboard (DB-backed with cache)
    // ========================================

    public List<LeaderboardEntry> getLeaderboardEntries() {
        long now = System.currentTimeMillis();
        if (now - leaderboardCacheTimestamp > LEADERBOARD_CACHE_TTL_MS) {
            List<LeaderboardEntry> dbEntries = fetchLeaderboardFromDatabase();
            if (dbEntries != null) {
                leaderboardCache = dbEntries;
                leaderboardCacheTimestamp = now;
            }
        }

        // Merge online players' fresh data on top of the DB snapshot
        Map<UUID, LeaderboardEntry> merged = new java.util.LinkedHashMap<>();
        for (LeaderboardEntry entry : leaderboardCache) {
            // Enrich null names from in-memory cache (survives disconnect)
            if (entry.playerName() == null || entry.playerName().isEmpty()) {
                String cachedName = playerNames.get(entry.playerId());
                if (cachedName != null) {
                    entry = new LeaderboardEntry(entry.playerId(), cachedName,
                            entry.totalVexaEarnedMantissa(), entry.totalVexaEarnedExp10(),
                            entry.ascensionCount(), entry.totalManualRuns(), entry.fastestAscensionMs());
                }
            }
            merged.put(entry.playerId(), entry);
        }
        for (Map.Entry<UUID, AscendPlayerProgress> e : players.entrySet()) {
            UUID id = e.getKey();
            AscendPlayerProgress p = e.getValue();
            String name = playerNames.get(id);
            merged.put(id, new LeaderboardEntry(
                id, name,
                p.getTotalVexaEarned().getMantissa(), p.getTotalVexaEarned().getExponent(),
                p.getAscensionCount(), p.getTotalManualRuns(), p.getFastestAscensionMs()
            ));
        }
        return new java.util.ArrayList<>(merged.values());
    }

    private List<LeaderboardEntry> fetchLeaderboardFromDatabase() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return List.of();
        }

        String sql = """
            SELECT uuid, player_name, total_vexa_earned_mantissa, total_vexa_earned_exp10,
                   ascension_count, total_manual_runs, fastest_ascension_ms
            FROM ascend_players
            ORDER BY total_vexa_earned_exp10 DESC, total_vexa_earned_mantissa DESC
            LIMIT 100
            """;

        List<LeaderboardEntry> entries = new java.util.ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("uuid"));
                    String name = rs.getString("player_name");
                    double mantissa = rs.getDouble("total_vexa_earned_mantissa");
                    int exp10 = rs.getInt("total_vexa_earned_exp10");
                    int ascensions = rs.getInt("ascension_count");
                    int manualRuns = rs.getInt("total_manual_runs");
                    long fastest = rs.getLong("fastest_ascension_ms");
                    Long fastestMs = rs.wasNull() ? null : fastest;

                    entries.add(new LeaderboardEntry(playerId, name, mantissa, exp10, ascensions, manualRuns, fastestMs));
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to fetch leaderboard from database: " + e.getMessage());
            return null;
        }
        return entries;
    }

    public void invalidateLeaderboardCache() {
        leaderboardCacheTimestamp = 0;
    }

    // ========================================
    // Per-Map Leaderboard (DB-backed with cache)
    // ========================================

    public List<MapLeaderboardEntry> getMapLeaderboard(String mapId) {
        if (mapId == null) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        Long cacheTs = mapLeaderboardCacheTimestamps.get(mapId);
        List<MapLeaderboardEntry> cached = mapLeaderboardCache.get(mapId);

        if (cacheTs == null || cached == null || (now - cacheTs) > LEADERBOARD_CACHE_TTL_MS) {
            List<MapLeaderboardEntry> dbEntries = fetchMapLeaderboardFromDatabase(mapId);
            if (dbEntries != null) {
                cached = dbEntries;
                mapLeaderboardCache.put(mapId, cached);
                mapLeaderboardCacheTimestamps.put(mapId, now);
            } else if (cached == null) {
                cached = List.of();
            }
        }

        // Merge online players' best times on top of the DB snapshot
        java.util.Map<String, MapLeaderboardEntry> merged = new java.util.LinkedHashMap<>();
        for (MapLeaderboardEntry entry : cached) {
            merged.put(entry.playerName() != null ? entry.playerName().toLowerCase() : "", entry);
        }
        for (java.util.Map.Entry<UUID, AscendPlayerProgress> e : players.entrySet()) {
            UUID id = e.getKey();
            AscendPlayerProgress p = e.getValue();
            AscendPlayerProgress.MapProgress mapProgress = p.getMapProgress().get(mapId);
            if (mapProgress == null || mapProgress.getBestTimeMs() == null) {
                continue;
            }
            String name = playerNames.get(id);
            if (name == null) {
                name = id.toString().substring(0, 8) + "...";
            }
            String key = name.toLowerCase();
            MapLeaderboardEntry existing = merged.get(key);
            if (existing == null || mapProgress.getBestTimeMs() < existing.bestTimeMs()) {
                merged.put(key, new MapLeaderboardEntry(name, mapProgress.getBestTimeMs()));
            }
        }

        List<MapLeaderboardEntry> result = new java.util.ArrayList<>(merged.values());
        result.sort((a, b) -> Long.compare(a.bestTimeMs(), b.bestTimeMs()));
        return result;
    }

    private List<MapLeaderboardEntry> fetchMapLeaderboardFromDatabase(String mapId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return List.of();
        }

        String sql = """
            SELECT p.uuid AS player_uuid, p.player_name, m.best_time_ms
            FROM ascend_player_maps m
            JOIN ascend_players p ON p.uuid = m.player_uuid
            WHERE m.map_id = ? AND m.best_time_ms IS NOT NULL
            ORDER BY m.best_time_ms ASC
            LIMIT 50
            """;

        List<MapLeaderboardEntry> entries = new java.util.ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, mapId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("player_name");
                    long bestTimeMs = rs.getLong("best_time_ms");
                    // Enrich null names from in-memory cache
                    if (name == null || name.isEmpty()) {
                        try {
                            UUID pid = UUID.fromString(rs.getString("player_uuid"));
                            name = playerNames.get(pid);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    entries.add(new MapLeaderboardEntry(name, bestTimeMs));
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to fetch map leaderboard for " + mapId + ": " + e.getMessage());
            return null;
        }
        return entries;
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

    public boolean isAutoEvolutionEnabled(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.isAutoEvolutionEnabled();
    }

    public void setAutoEvolutionEnabled(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setAutoEvolutionEnabled(enabled);
        markDirty(playerId);
    }

    public boolean isHideOtherRunners(UUID playerId) {
        AscendPlayerProgress progress = players.get(playerId);
        return progress != null && progress.isHideOtherRunners();
    }

    public void setHideOtherRunners(UUID playerId, boolean enabled) {
        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        progress.setHideOtherRunners(enabled);
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

    private static BigNumber safeGetBigNumber(ResultSet rs, String mantissaCol, String exp10Col) {
        try {
            double m = rs.getDouble(mantissaCol);
            int e = rs.getInt(exp10Col);
            return BigNumber.of(m, e);
        } catch (SQLException ex) {
            LOGGER.atWarning().log("Columns '" + mantissaCol + "'/'" + exp10Col + "' not available: " + ex.getMessage());
            return BigNumber.ZERO;
        }
    }
}
