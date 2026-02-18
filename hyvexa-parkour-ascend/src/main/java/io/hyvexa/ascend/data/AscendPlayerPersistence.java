package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.AchievementType;
import io.hyvexa.ascend.AscendConstants.SkillTreeNode;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * Handles all database persistence and leaderboard queries for Ascend player data.
 * Package-private — accessed only through {@link AscendPlayerStore}.
 */
class AscendPlayerPersistence {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long LEADERBOARD_CACHE_TTL_MS = 30_000;

    private final Map<UUID, AscendPlayerProgress> players;
    private final Map<UUID, String> playerNames;
    private final Set<UUID> resetPendingPlayers;
    private final Set<UUID> transcendenceResetPending = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Long> dirtyPlayerVersions = new ConcurrentHashMap<>();
    private final Map<UUID, AscendPlayerProgress> detachedDirtyPlayers = new ConcurrentHashMap<>();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> saveFuture = new AtomicReference<>();
    private final ReentrantReadWriteLock saveLock = new ReentrantReadWriteLock();

    private volatile List<AscendPlayerStore.LeaderboardEntry> leaderboardCache = List.of();
    private volatile long leaderboardCacheTimestamp = 0;

    private final Map<String, List<AscendPlayerStore.MapLeaderboardEntry>> mapLeaderboardCache = new ConcurrentHashMap<>();
    private final Map<String, Long> mapLeaderboardCacheTimestamps = new ConcurrentHashMap<>();

    AscendPlayerPersistence(Map<UUID, AscendPlayerProgress> players,
                            Map<UUID, String> playerNames,
                            Set<UUID> resetPendingPlayers) {
        this.players = players;
        this.playerNames = playerNames;
        this.resetPendingPlayers = resetPendingPlayers;
    }

    // ========================================
    // Dirty Tracking
    // ========================================

    void markDirty(UUID playerId) {
        if (playerId == null) {
            return;
        }
        dirtyPlayerVersions.compute(playerId, (ignored, version) -> version == null ? 1L : version + 1L);
    }

    boolean isDirty(UUID playerId) {
        return dirtyPlayerVersions.containsKey(playerId);
    }

    void snapshotForSave(UUID playerId, AscendPlayerProgress progress) {
        detachedDirtyPlayers.put(playerId, progress);
    }

    void markTranscendenceResetPending(UUID playerId) {
        if (playerId != null) {
            transcendenceResetPending.add(playerId);
        }
    }

    /**
     * Clear all dirty tracking state (used during full reset).
     */
    void clearDirtyState() {
        dirtyPlayerVersions.clear();
        detachedDirtyPlayers.clear();
    }

    /**
     * Clear all leaderboard caches (used during full reset).
     */
    void clearLeaderboardCaches() {
        leaderboardCache = List.of();
        leaderboardCacheTimestamp = 0;
        mapLeaderboardCache.clear();
        mapLeaderboardCacheTimestamps.clear();
    }

    // ========================================
    // Save Scheduling
    // ========================================

    void queueSave() {
        queueSave(AscendConstants.SAVE_DEBOUNCE_MS);
    }

    void queueSave(long delayMs) {
        if (!saveQueued.compareAndSet(false, true)) {
            return;
        }
        ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                syncSave();
            } finally {
                saveFuture.set(null);
                saveQueued.set(false);
                if (!dirtyPlayerVersions.isEmpty()) {
                    long followUpDelay = DatabaseManager.getInstance().isInitialized()
                            ? 0L
                            : AscendConstants.SAVE_DEBOUNCE_MS;
                    queueSave(followUpDelay);
                }
            }
        }, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
        saveFuture.set(future);
    }

    void flushPendingSave() {
        ScheduledFuture<?> pending = saveFuture.getAndSet(null);
        if (pending != null) {
            pending.cancel(false);
        }
        saveQueued.set(false);
        syncSave();
    }

    void savePlayerIfDirty(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Long dirtyVersion = dirtyPlayerVersions.get(playerId);
        if (dirtyVersion == null) {
            detachedDirtyPlayers.remove(playerId);
            return;
        }
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            dirtyPlayerVersions.remove(playerId, dirtyVersion);
            detachedDirtyPlayers.remove(playerId);
            return;
        }
        detachedDirtyPlayers.put(playerId, progress);
        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> syncSave(Map.of(playerId, dirtyVersion)));
    }

    /**
     * Cancel any pending debounced save. Used by reset operations
     * to prevent stale data from being written after a DELETE.
     */
    void cancelPendingSave() {
        ScheduledFuture<?> pending = saveFuture.getAndSet(null);
        if (pending != null) {
            pending.cancel(false);
        }
        saveQueued.set(false);
    }

    /**
     * Returns true if there are no remaining dirty players after the
     * most recently scheduled async save completes. Used only
     * during removePlayer to decide whether to clean detachedDirtyPlayers.
     */
    boolean hasDirtyVersion(UUID playerId) {
        return dirtyPlayerVersions.containsKey(playerId);
    }

    // ========================================
    // Sync Save
    // ========================================

    void syncSave() {
        syncSave(Map.copyOf(dirtyPlayerVersions));
    }

    void syncSave(Map<UUID, Long> toSave) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        if (toSave == null || toSave.isEmpty()) {
            return;
        }

        saveLock.writeLock().lock();
        try {
            doSyncSave(toSave);
        } finally {
            saveLock.writeLock().unlock();
        }
    }

    private void doSyncSave(Map<UUID, Long> toSave) {
        String playerSql = """
            INSERT INTO ascend_players (uuid, player_name, vexa_mantissa, vexa_exp10, elevation_multiplier, ascension_count,
                skill_tree_points, total_vexa_earned_mantissa, total_vexa_earned_exp10, total_manual_runs, active_title,
                ascension_started_at, fastest_ascension_ms, last_active_timestamp, has_unclaimed_passive,
                summit_accumulated_vexa_mantissa, summit_accumulated_vexa_exp10,
                elevation_accumulated_vexa_mantissa, elevation_accumulated_vexa_exp10,
                auto_upgrade_enabled, auto_evolution_enabled, seen_tutorials, hide_other_runners,
                break_ascension_enabled,
                auto_elevation_enabled, auto_elevation_timer_seconds, auto_elevation_targets, auto_elevation_target_index,
                auto_summit_enabled, auto_summit_timer_seconds, auto_summit_config, auto_summit_rotation_index,
                transcendence_count, auto_ascend_enabled,
                compounded_elevation, cycle_level)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                seen_tutorials = VALUES(seen_tutorials),
                break_ascension_enabled = VALUES(break_ascension_enabled),
                auto_elevation_enabled = VALUES(auto_elevation_enabled),
                auto_elevation_timer_seconds = VALUES(auto_elevation_timer_seconds),
                auto_elevation_targets = VALUES(auto_elevation_targets),
                auto_elevation_target_index = VALUES(auto_elevation_target_index),
                auto_summit_enabled = VALUES(auto_summit_enabled),
                auto_summit_timer_seconds = VALUES(auto_summit_timer_seconds),
                auto_summit_config = VALUES(auto_summit_config),
                auto_summit_rotation_index = VALUES(auto_summit_rotation_index),
                transcendence_count = VALUES(transcendence_count),
                auto_ascend_enabled = VALUES(auto_ascend_enabled),
                compounded_elevation = VALUES(compounded_elevation),
                cycle_level = VALUES(cycle_level)
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
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            conn.setAutoCommit(false); // Begin transaction
            try (PreparedStatement playerStmt = conn.prepareStatement(playerSql);
                 PreparedStatement mapStmt = conn.prepareStatement(mapSql);
                 PreparedStatement summitStmt = conn.prepareStatement(summitSql);
                 PreparedStatement skillStmt = conn.prepareStatement(skillSql);
                 PreparedStatement achievementStmt = conn.prepareStatement(achievementSql);
                 PreparedStatement delMaps = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_maps"));
                 PreparedStatement delSummit = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_summit"));
                 PreparedStatement delSkills = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_skills"));
                 PreparedStatement delAchievements = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_achievements"));
                 PreparedStatement delChallengeRecords = conn.prepareStatement(String.format(deleteChildSql, "ascend_challenge_records"))) {
                DatabaseManager.applyQueryTimeout(playerStmt);
                DatabaseManager.applyQueryTimeout(mapStmt);
                DatabaseManager.applyQueryTimeout(summitStmt);
                DatabaseManager.applyQueryTimeout(skillStmt);
                DatabaseManager.applyQueryTimeout(achievementStmt);

                Map<UUID, Long> persistedVersions = new HashMap<>();
                for (Map.Entry<UUID, Long> dirtyEntry : toSave.entrySet()) {
                    UUID playerId = dirtyEntry.getKey();
                    AscendPlayerProgress progress = resolveProgressForSave(playerId);
                    if (progress == null) {
                        continue;
                    }
                    persistedVersions.put(playerId, dirtyEntry.getValue());

                    // If this player was recently reset, delete all child rows first
                    // to prevent stale upserts from re-inserting deleted data
                    if (resetPendingPlayers.remove(playerId)) {
                        String pid = playerId.toString();
                        for (PreparedStatement delStmt : new PreparedStatement[]{delMaps, delSummit, delSkills, delAchievements}) {
                            delStmt.setString(1, pid);
                            delStmt.executeUpdate();
                        }
                    }

                    // Transcendence reset: additionally delete challenge records
                    if (transcendenceResetPending.remove(playerId)) {
                        delChallengeRecords.setString(1, playerId.toString());
                        delChallengeRecords.executeUpdate();
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
                    playerStmt.setNull(11, java.sql.Types.VARCHAR);
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
                    playerStmt.setBoolean(24, progress.isBreakAscensionEnabled());
                    playerStmt.setBoolean(25, progress.isAutoElevationEnabled());
                    playerStmt.setInt(26, progress.getAutoElevationTimerSeconds());
                    playerStmt.setString(27, serializeTargets(progress.getAutoElevationTargets()));
                    playerStmt.setInt(28, progress.getAutoElevationTargetIndex());
                    playerStmt.setBoolean(29, progress.isAutoSummitEnabled());
                    playerStmt.setInt(30, progress.getAutoSummitTimerSeconds());
                    playerStmt.setString(31, serializeAutoSummitConfig(progress.getAutoSummitConfig()));
                    playerStmt.setInt(32, progress.getAutoSummitRotationIndex());
                    playerStmt.setInt(33, progress.getTranscendenceCount());
                    playerStmt.setBoolean(34, progress.isAutoAscendEnabled());
                    playerStmt.setDouble(35, progress.getCompoundedElevation());
                    playerStmt.setInt(36, progress.getCycleLevel());
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
                        double xp = progress.getSummitXp(category);
                        summitStmt.setString(1, playerId.toString());
                        summitStmt.setString(2, category.name());
                        summitStmt.setDouble(3, xp);
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

                if (persistedVersions.isEmpty()) {
                    conn.rollback();
                    return;
                }
                playerStmt.executeBatch();
                mapStmt.executeBatch();
                summitStmt.executeBatch();
                skillStmt.executeBatch();
                achievementStmt.executeBatch();

                conn.commit(); // Commit transaction

                // Save succeeded - remove only IDs that were not marked dirty again mid-save.
                for (Map.Entry<UUID, Long> entry : persistedVersions.entrySet()) {
                    UUID playerId = entry.getKey();
                    Long savedVersion = entry.getValue();
                    dirtyPlayerVersions.compute(playerId, (ignored, currentVersion) -> {
                        if (currentVersion == null) {
                            detachedDirtyPlayers.remove(playerId);
                            return null;
                        }
                        if (currentVersion.equals(savedVersion)) {
                            detachedDirtyPlayers.remove(playerId);
                            return null;
                        }
                        return currentVersion;
                    });
                }
            } catch (SQLException e) {
                conn.rollback(); // Rollback transaction on error
                LOGGER.atSevere().log("Failed to save ascend players (rolled back): " + e.getMessage());
                // On failure, versions remain dirty for the next save cycle
                throw e; // Re-throw to trigger outer catch
            }
        } catch (SQLException e) {
            // Outer catch for connection/transaction setup errors
            LOGGER.atSevere().log("Failed to initialize transaction for ascend player save: " + e.getMessage());
        }
    }

    private AscendPlayerProgress resolveProgressForSave(UUID playerId) {
        AscendPlayerProgress liveProgress = players.get(playerId);
        if (liveProgress != null) {
            return liveProgress;
        }
        return detachedDirtyPlayers.get(playerId);
    }

    // ========================================
    // Load from Database
    // ========================================

    /**
     * Load a single player's data from the database (lazy loading).
     * Returns null if player doesn't exist in database.
     */
    AscendPlayerProgress loadPlayerFromDatabase(UUID playerId) {
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
                   auto_upgrade_enabled, auto_evolution_enabled, seen_tutorials, hide_other_runners,
                   break_ascension_enabled,
                   auto_elevation_enabled, auto_elevation_timer_seconds, auto_elevation_targets, auto_elevation_target_index,
                   auto_summit_enabled, auto_summit_timer_seconds, auto_summit_config, auto_summit_rotation_index,
                   transcendence_count, auto_ascend_enabled,
                   compounded_elevation, cycle_level
            FROM ascend_players
            WHERE uuid = ?
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return null;
            }
            try (PreparedStatement stmt = conn.prepareStatement(playerSql)) {
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
                        progress.setBreakAscensionEnabled(safeGetBoolean(rs, "break_ascension_enabled", false));

                        progress.setAutoElevationEnabled(safeGetBoolean(rs, "auto_elevation_enabled", false));
                        progress.setAutoElevationTimerSeconds(safeGetInt(rs, "auto_elevation_timer_seconds", 0));
                        progress.setAutoElevationTargets(parseTargets(safeGetString(rs, "auto_elevation_targets", "[]")));
                        progress.setAutoElevationTargetIndex(safeGetInt(rs, "auto_elevation_target_index", 0));

                        progress.setAutoSummitEnabled(safeGetBoolean(rs, "auto_summit_enabled", false));
                        progress.setAutoSummitTimerSeconds(safeGetInt(rs, "auto_summit_timer_seconds", 0));
                        progress.setAutoSummitConfig(parseAutoSummitConfig(safeGetString(rs, "auto_summit_config", "[]")));
                        progress.setAutoSummitRotationIndex(safeGetInt(rs, "auto_summit_rotation_index", 0));

                        progress.setTranscendenceCount(safeGetInt(rs, "transcendence_count", 0));
                        progress.setAutoAscendEnabled(safeGetBoolean(rs, "auto_ascend_enabled", false));

                        double compoundedElev = safeGetDouble(rs, "compounded_elevation", 1.0);
                        if (compoundedElev > 1.0) {
                            progress.setCompoundedElevation(compoundedElev);
                        }
                        int cycleLevel = safeGetInt(rs, "cycle_level", 0);
                        if (cycleLevel > 0) {
                            progress.setCycleLevel(cycleLevel);
                        }
                    }
                }
            }

            // If player doesn't exist in database, return null
            if (progress == null) {
                return null;
            }

            // Load related data on the same connection
            loadMapProgressForPlayer(conn, playerId, progress);
            loadSummitLevelsForPlayer(conn, playerId, progress);
            loadSkillNodesForPlayer(conn, playerId, progress);
            compensateSkillTreeCostChanges(playerId, progress);
            loadAchievementsForPlayer(conn, playerId, progress);
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to load player " + playerId + ": " + e.getMessage());
            return null;
        }

        return progress;
    }

    private void loadMapProgressForPlayer(Connection conn, UUID playerId, AscendPlayerProgress progress) throws SQLException {
        String sql = """
            SELECT map_id, unlocked, completed_manually, has_robot,
                   robot_speed_level, robot_stars, multiplier_mantissa, multiplier_exp10, best_time_ms
            FROM ascend_player_maps
            WHERE player_uuid = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        }
    }

    private void loadSummitLevelsForPlayer(Connection conn, UUID playerId, AscendPlayerProgress progress) throws SQLException {
        String sql = "SELECT category, xp FROM ascend_player_summit WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String categoryName = rs.getString("category");
                    double xp = rs.getDouble("xp");
                    try {
                        SummitCategory category = SummitCategory.valueOf(categoryName);
                        progress.setSummitXp(category, xp);
                    } catch (IllegalArgumentException ignored) {
                        // Unknown category
                    }
                }
            }
        }
    }

    private void loadSkillNodesForPlayer(Connection conn, UUID playerId, AscendPlayerProgress progress) throws SQLException {
        String sql = "SELECT skill_node FROM ascend_player_skills WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String nodeName = rs.getString("skill_node");
                    // Migration: ELEVATION_REMNANT replaced by AUTO_ELEVATION
                    if ("ELEVATION_REMNANT".equals(nodeName)) {
                        nodeName = "AUTO_ELEVATION";
                    }
                    // Migration: ELEVATION_BOOST replaced by AUTO_ASCEND
                    if ("ELEVATION_BOOST".equals(nodeName)) {
                        nodeName = "AUTO_ASCEND";
                    }
                    try {
                        SkillTreeNode node = SkillTreeNode.valueOf(nodeName);
                        progress.unlockSkillNode(node);
                    } catch (IllegalArgumentException ignored) {
                        // Unknown node
                    }
                }
            }
        }
    }

    /**
     * Compensates players whose unlocked skill nodes now cost more after a tree rebalance.
     * If spent points exceed total points, grants bonus AP to cover the deficit.
     */
    private void compensateSkillTreeCostChanges(UUID playerId, AscendPlayerProgress progress) {
        int spent = progress.getSpentSkillPoints();
        int total = progress.getSkillTreePoints();
        if (spent > total) {
            int deficit = spent - total;
            progress.setSkillTreePoints(spent);
            LOGGER.atInfo().log("[SkillTree] Compensated player " + playerId
                + " with +" + deficit + " AP for tree rebalance (was " + total + ", now " + spent + ")");
        }
    }

    private void loadAchievementsForPlayer(Connection conn, UUID playerId, AscendPlayerProgress progress) throws SQLException {
        String sql = "SELECT achievement FROM ascend_player_achievements WHERE player_uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        }
    }

    // ========================================
    // Delete
    // ========================================

    void deletePlayerDataFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        String playerIdStr = playerId.toString();
        String[] tables = {
            "ascend_player_maps",
            "ascend_player_summit",
            "ascend_player_skills",
            "ascend_player_achievements",
            "ascend_ghost_recordings",
            "ascend_challenges",
            "ascend_challenge_records"
        };

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
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
            LOGGER.atSevere().log("Failed to delete player data during reset: " + e.getMessage());
        }
    }

    // ========================================
    // Leaderboard (DB-backed with cache)
    // ========================================

    List<AscendPlayerStore.LeaderboardEntry> getLeaderboardEntries() {
        long now = System.currentTimeMillis();
        if (now - leaderboardCacheTimestamp > LEADERBOARD_CACHE_TTL_MS) {
            List<AscendPlayerStore.LeaderboardEntry> dbEntries = fetchLeaderboardFromDatabase();
            if (dbEntries != null) {
                leaderboardCache = dbEntries;
                leaderboardCacheTimestamp = now;
            }
        }

        // Merge online players' fresh data on top of the DB snapshot
        Map<UUID, AscendPlayerStore.LeaderboardEntry> merged = new java.util.LinkedHashMap<>();
        for (AscendPlayerStore.LeaderboardEntry entry : leaderboardCache) {
            // Enrich null names from in-memory cache (survives disconnect)
            if (entry.playerName() == null || entry.playerName().isEmpty()) {
                String cachedName = playerNames.get(entry.playerId());
                if (cachedName != null) {
                    entry = new AscendPlayerStore.LeaderboardEntry(entry.playerId(), cachedName,
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
            merged.put(id, new AscendPlayerStore.LeaderboardEntry(
                id, name,
                p.getTotalVexaEarned().getMantissa(), p.getTotalVexaEarned().getExponent(),
                p.getAscensionCount(), p.getTotalManualRuns(), p.getFastestAscensionMs()
            ));
        }
        return new java.util.ArrayList<>(merged.values());
    }

    private List<AscendPlayerStore.LeaderboardEntry> fetchLeaderboardFromDatabase() {
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

        List<AscendPlayerStore.LeaderboardEntry> entries = new java.util.ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return null;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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

                        entries.add(new AscendPlayerStore.LeaderboardEntry(playerId, name, mantissa, exp10, ascensions, manualRuns, fastestMs));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to fetch leaderboard from database: " + e.getMessage());
            return null;
        }
        return entries;
    }

    void invalidateLeaderboardCache() {
        leaderboardCacheTimestamp = 0;
    }

    void invalidateMapLeaderboardCache(String mapId) {
        if (mapId != null) {
            mapLeaderboardCache.remove(mapId);
            mapLeaderboardCacheTimestamps.remove(mapId);
        }
    }

    // ========================================
    // Per-Map Leaderboard (DB-backed with cache)
    // ========================================

    List<AscendPlayerStore.MapLeaderboardEntry> getMapLeaderboard(String mapId) {
        if (mapId == null) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        Long cacheTs = mapLeaderboardCacheTimestamps.get(mapId);
        List<AscendPlayerStore.MapLeaderboardEntry> cached = mapLeaderboardCache.get(mapId);

        if (cacheTs == null || cached == null || (now - cacheTs) > LEADERBOARD_CACHE_TTL_MS) {
            List<AscendPlayerStore.MapLeaderboardEntry> dbEntries = fetchMapLeaderboardFromDatabase(mapId);
            if (dbEntries != null) {
                cached = dbEntries;
                mapLeaderboardCache.put(mapId, cached);
                mapLeaderboardCacheTimestamps.put(mapId, now);
            } else if (cached == null) {
                cached = List.of();
            }
        }

        // Merge online players' best times on top of the DB snapshot
        java.util.Map<String, AscendPlayerStore.MapLeaderboardEntry> merged = new java.util.LinkedHashMap<>();
        for (AscendPlayerStore.MapLeaderboardEntry entry : cached) {
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
            AscendPlayerStore.MapLeaderboardEntry existing = merged.get(key);
            if (existing == null || mapProgress.getBestTimeMs() < existing.bestTimeMs()) {
                merged.put(key, new AscendPlayerStore.MapLeaderboardEntry(name, mapProgress.getBestTimeMs()));
            }
        }

        List<AscendPlayerStore.MapLeaderboardEntry> result = new java.util.ArrayList<>(merged.values());
        result.sort((a, b) -> Long.compare(a.bestTimeMs(), b.bestTimeMs()));
        return result;
    }

    private List<AscendPlayerStore.MapLeaderboardEntry> fetchMapLeaderboardFromDatabase(String mapId) {
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

        List<AscendPlayerStore.MapLeaderboardEntry> entries = new java.util.ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return null;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
                        entries.add(new AscendPlayerStore.MapLeaderboardEntry(name, bestTimeMs));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to fetch map leaderboard for " + mapId + ": " + e.getMessage());
            return null;
        }
        return entries;
    }

    // ========================================
    // Safe ResultSet Helpers
    // ========================================

    private static double safeGetDouble(ResultSet rs, String column, double defaultValue) {
        try {
            return rs.getDouble(column);
        } catch (SQLException e) {
            LOGGER.atWarning().log("Column '" + column + "' not available: " + e.getMessage());
            return defaultValue;
        }
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

    private static String serializeTargets(java.util.List<Long> targets) {
        if (targets == null || targets.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < targets.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(targets.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    private static java.util.List<Long> parseTargets(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return java.util.Collections.emptyList();
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isBlank()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<Long> result = new java.util.ArrayList<>();
        for (String part : trimmed.split(",")) {
            try {
                result.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException e) {
                // Skip invalid entries
            }
        }
        return result;
    }

    /**
     * Serialize auto-summit config to JSON.
     * Format: [{"enabled":true,"target":10},{"enabled":false,"target":5},...]
     */
    static String serializeAutoSummitConfig(java.util.List<AscendPlayerProgress.AutoSummitCategoryConfig> config) {
        if (config == null || config.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < config.size(); i++) {
            if (i > 0) sb.append(",");
            AscendPlayerProgress.AutoSummitCategoryConfig c = config.get(i);
            sb.append("{\"enabled\":").append(c.isEnabled())
              .append(",\"target\":").append(c.getTargetLevel())
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Parse auto-summit config from JSON.
     * Expects: [{"enabled":true,"target":10},...] (also accepts legacy "increment" key)
     */
    static java.util.List<AscendPlayerProgress.AutoSummitCategoryConfig> parseAutoSummitConfig(String json) {
        java.util.List<AscendPlayerProgress.AutoSummitCategoryConfig> result = new java.util.ArrayList<>();
        if (json == null || json.isBlank() || json.equals("[]")) {
            // Return defaults for 3 categories
            for (int i = 0; i < 3; i++) {
                result.add(new AscendPlayerProgress.AutoSummitCategoryConfig(false, 0));
            }
            return result;
        }

        // Simple JSON parsing for {"enabled":bool,"target":int} objects
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);

        // Split by "},{"
        String[] parts = trimmed.split("\\},\\s*\\{");
        for (String part : parts) {
            String cleaned = part.replace("{", "").replace("}", "").trim();
            if (cleaned.isBlank()) continue;

            boolean enabled = false;
            int targetLevel = 0;

            for (String field : cleaned.split(",")) {
                String[] kv = field.split(":");
                if (kv.length != 2) continue;
                String key = kv[0].trim().replace("\"", "");
                String val = kv[1].trim().replace("\"", "");
                if ("enabled".equals(key)) {
                    enabled = "true".equalsIgnoreCase(val);
                } else if ("target".equals(key) || "increment".equals(key)) {
                    try {
                        targetLevel = Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        targetLevel = 0;
                    }
                }
            }
            result.add(new AscendPlayerProgress.AutoSummitCategoryConfig(enabled, targetLevel));
        }

        // Ensure exactly 3 entries
        while (result.size() < 3) {
            result.add(new AscendPlayerProgress.AutoSummitCategoryConfig(false, 0));
        }
        if (result.size() > 3) {
            result = new java.util.ArrayList<>(result.subList(0, 3));
        }

        return result;
    }
}
