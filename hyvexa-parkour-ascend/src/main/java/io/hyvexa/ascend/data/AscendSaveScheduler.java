package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscensionConstants.AchievementType;
import io.hyvexa.ascend.AscensionConstants.SkillTreeNode;
import io.hyvexa.ascend.SummitConstants.SummitCategory;
import io.hyvexa.ascend.data.GameplayState.MapProgress;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
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
 * Dirty tracking, debounced save scheduling, and sync-save execution for Ascend player data.
 * Package-private — accessed only through {@link AscendPlayerPersistence}.
 */
class AscendSaveScheduler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Child tables deleted during per-player reset. Order does not matter (no FK constraints). */
    static final String[] CHILD_TABLES = {
        "ascend_player_maps",
        "ascend_player_summit",
        "ascend_player_skills",
        "ascend_player_achievements",
        "ascend_player_cats",
        "ascend_ghost_recordings",
        "ascend_challenges",
        "ascend_challenge_records"
    };

    private final ConnectionProvider db;
    private final Map<UUID, AscendPlayerProgress> players;
    private final Map<UUID, String> playerNames;
    private final Set<UUID> resetPendingPlayers;
    private final Set<UUID> transcendenceResetPending = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Long> dirtyPlayerVersions = new ConcurrentHashMap<>();
    private final Map<UUID, AscendPlayerProgress> detachedDirtyPlayers = new ConcurrentHashMap<>();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> saveFuture = new AtomicReference<>();
    private final ReentrantReadWriteLock saveLock = new ReentrantReadWriteLock();

    AscendSaveScheduler(ConnectionProvider db,
                        Map<UUID, AscendPlayerProgress> players,
                        Map<UUID, String> playerNames,
                        Set<UUID> resetPendingPlayers) {
        this.db = db;
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
     * Returns the detached dirty snapshot for a player if one exists and the player
     * is still marked dirty (e.g. disconnect save in-flight). Used by load to avoid
     * reloading a stale DB row.
     */
    AscendPlayerProgress getDetachedIfDirty(UUID playerId) {
        AscendPlayerProgress detached = detachedDirtyPlayers.get(playerId);
        if (detached != null && dirtyPlayerVersions.containsKey(playerId)) {
            return detached;
        }
        return null;
    }

    /**
     * Clear all dirty tracking state (used during full reset).
     */
    void clearDirtyState() {
        dirtyPlayerVersions.clear();
        detachedDirtyPlayers.clear();
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
                    long followUpDelay = this.db.isInitialized()
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
        AscendPlayerProgress progress = detachedDirtyPlayers.get(playerId);
        if (progress == null) {
            progress = players.get(playerId);
        }
        if (progress == null) {
            LOGGER.atWarning().log("savePlayerIfDirty: missing progress snapshot for dirty player " + playerId);
            dirtyPlayerVersions.remove(playerId, dirtyVersion);
            detachedDirtyPlayers.remove(playerId);
            return;
        }
        detachedDirtyPlayers.put(playerId, progress);
        AscendPlayerProgress snapshot = progress;
        HytaleServer.SCHEDULED_EXECUTOR.execute(
            () -> syncSave(Map.of(playerId, dirtyVersion), Map.of(playerId, snapshot))
        );
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

    /**
     * Synchronously save a single player's data and return whether it succeeded.
     * Used for idempotency-sensitive operations (e.g. passive earnings claim)
     * where we need confirmation before proceeding.
     */
    boolean savePlayerSync(UUID playerId) {
        if (playerId == null || !this.db.isInitialized()) {
            return false;
        }
        Long dirtyVersion = dirtyPlayerVersions.get(playerId);
        if (dirtyVersion == null) {
            return true; // Nothing to save — already clean
        }
        saveLock.writeLock().lock();
        try {
            doSyncSave(Map.of(playerId, dirtyVersion), Map.of());
            // If the version was cleared by doSyncSave, the save succeeded
            return !dirtyPlayerVersions.containsKey(playerId)
                    || !dirtyVersion.equals(dirtyPlayerVersions.get(playerId));
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("savePlayerSync failed for " + playerId);
            return false;
        } finally {
            saveLock.writeLock().unlock();
        }
    }

    void syncSave() {
        syncSave(Map.copyOf(dirtyPlayerVersions), Map.of());
    }

    void syncSave(Map<UUID, Long> toSave) {
        syncSave(toSave, Map.of());
    }

    private void syncSave(Map<UUID, Long> toSave, Map<UUID, AscendPlayerProgress> progressOverrides) {
        if (!this.db.isInitialized()) {
            return;
        }
        if (toSave == null || toSave.isEmpty()) {
            return;
        }

        saveLock.writeLock().lock();
        try {
            doSyncSave(toSave, progressOverrides);
        } finally {
            saveLock.writeLock().unlock();
        }
    }

    private void doSyncSave(Map<UUID, Long> toSave, Map<UUID, AscendPlayerProgress> progressOverrides) {
        String playerSql = """
            INSERT INTO ascend_players (uuid, player_name, volt_mantissa, volt_exp10, elevation_multiplier, ascension_count,
                skill_tree_points, total_volt_earned_mantissa, total_volt_earned_exp10, total_manual_runs, active_title,
                ascension_started_at, fastest_ascension_ms,
                summit_accumulated_volt_mantissa, summit_accumulated_volt_exp10,
                elevation_accumulated_volt_mantissa, elevation_accumulated_volt_exp10,
                auto_upgrade_enabled, auto_evolution_enabled, seen_tutorials, hide_other_runners,
                break_ascension_enabled,
                auto_elevation_enabled, auto_elevation_timer_seconds, auto_elevation_targets, auto_elevation_target_index,
                auto_summit_enabled, auto_summit_timer_seconds, auto_summit_config, auto_summit_rotation_index,
                transcendence_count, auto_ascend_enabled, hud_hidden, players_hidden)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name = VALUES(player_name),
                volt_mantissa = VALUES(volt_mantissa), volt_exp10 = VALUES(volt_exp10),
                elevation_multiplier = VALUES(elevation_multiplier),
                ascension_count = VALUES(ascension_count), skill_tree_points = VALUES(skill_tree_points),
                total_volt_earned_mantissa = VALUES(total_volt_earned_mantissa),
                total_volt_earned_exp10 = VALUES(total_volt_earned_exp10),
                total_manual_runs = VALUES(total_manual_runs),
                active_title = VALUES(active_title), ascension_started_at = VALUES(ascension_started_at),
                fastest_ascension_ms = VALUES(fastest_ascension_ms),
                summit_accumulated_volt_mantissa = VALUES(summit_accumulated_volt_mantissa),
                summit_accumulated_volt_exp10 = VALUES(summit_accumulated_volt_exp10),
                elevation_accumulated_volt_mantissa = VALUES(elevation_accumulated_volt_mantissa),
                elevation_accumulated_volt_exp10 = VALUES(elevation_accumulated_volt_exp10),
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
                hud_hidden = VALUES(hud_hidden),
                players_hidden = VALUES(players_hidden)
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
                best_time_ms = CASE
                    WHEN VALUES(best_time_ms) IS NULL THEN best_time_ms
                    WHEN best_time_ms IS NULL OR VALUES(best_time_ms) < best_time_ms THEN VALUES(best_time_ms)
                    ELSE best_time_ms
                END
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

        String catSql = """
            INSERT IGNORE INTO ascend_player_cats (player_uuid, cat_token)
            VALUES (?, ?)
            """;

        String deleteChildSql = "DELETE FROM %s WHERE player_uuid = ?";

        SaveResult result = this.db.withTransaction(conn -> {
            try (PreparedStatement playerStmt = conn.prepareStatement(playerSql);
                 PreparedStatement mapStmt = conn.prepareStatement(mapSql);
                 PreparedStatement summitStmt = conn.prepareStatement(summitSql);
                 PreparedStatement skillStmt = conn.prepareStatement(skillSql);
                 PreparedStatement achievementStmt = conn.prepareStatement(achievementSql);
                 PreparedStatement catStmt = conn.prepareStatement(catSql)) {
                DatabaseManager.applyQueryTimeout(playerStmt);
                DatabaseManager.applyQueryTimeout(mapStmt);
                DatabaseManager.applyQueryTimeout(summitStmt);
                DatabaseManager.applyQueryTimeout(skillStmt);
                DatabaseManager.applyQueryTimeout(achievementStmt);
                DatabaseManager.applyQueryTimeout(catStmt);

                // Delete statements are only needed for reset-pending players;
                // created lazily to avoid unnecessary PreparedStatement allocation.
                PreparedStatement delMaps = null;
                PreparedStatement delSummit = null;
                PreparedStatement delSkills = null;
                PreparedStatement delAchievements = null;
                PreparedStatement delCats = null;
                PreparedStatement delChallengeRecords = null;

                Map<UUID, Long> persistedVersions = new HashMap<>();
                List<UUID> committedResets = new ArrayList<>();
                List<UUID> committedChallengeResets = new ArrayList<>();
                try {
                for (Map.Entry<UUID, Long> dirtyEntry : toSave.entrySet()) {
                    UUID playerId = dirtyEntry.getKey();
                    AscendPlayerProgress progress = resolveProgressForSave(playerId, progressOverrides);
                    if (progress == null) {
                        continue;
                    }
                    persistedVersions.put(playerId, dirtyEntry.getValue());

                    // If this player was recently reset, delete all child rows first
                    // to prevent stale upserts from re-inserting deleted data.
                    // Only remove flags after successful commit to avoid losing reset intent on rollback.
                    if (resetPendingPlayers.contains(playerId)) {
                        if (delMaps == null) {
                            delMaps = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_maps"));
                            delSummit = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_summit"));
                            delSkills = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_skills"));
                            delAchievements = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_achievements"));
                            delCats = conn.prepareStatement(String.format(deleteChildSql, "ascend_player_cats"));
                        }
                        String pid = playerId.toString();
                        for (PreparedStatement delStmt : new PreparedStatement[]{delMaps, delSummit, delSkills, delAchievements, delCats}) {
                            delStmt.setString(1, pid);
                            delStmt.executeUpdate();
                        }
                        committedResets.add(playerId);
                    }

                    // Transcendence reset: additionally delete challenge records
                    if (transcendenceResetPending.contains(playerId)) {
                        if (delChallengeRecords == null) {
                            delChallengeRecords = conn.prepareStatement(String.format(deleteChildSql, "ascend_challenge_records"));
                        }
                        delChallengeRecords.setString(1, playerId.toString());
                        delChallengeRecords.executeUpdate();
                        committedChallengeResets.add(playerId);
                    }

                    // Save player base data
                    playerStmt.setString(1, playerId.toString());
                    playerStmt.setString(2, playerNames.get(playerId));
                    playerStmt.setDouble(3, progress.economy().getVolt().getMantissa());
                    playerStmt.setInt(4, progress.economy().getVolt().getExponent());
                    playerStmt.setInt(5, progress.economy().getElevationMultiplier());
                    playerStmt.setInt(6, progress.gameplay().getAscensionCount());
                    playerStmt.setInt(7, progress.gameplay().getSkillTreePoints());
                    playerStmt.setDouble(8, progress.economy().getTotalVoltEarned().getMantissa());
                    playerStmt.setInt(9, progress.economy().getTotalVoltEarned().getExponent());
                    playerStmt.setInt(10, progress.gameplay().getTotalManualRuns());
                    playerStmt.setNull(11, java.sql.Types.VARCHAR);
                    if (progress.gameplay().getAscensionStartedAt() != null) {
                        playerStmt.setLong(12, progress.gameplay().getAscensionStartedAt());
                    } else {
                        playerStmt.setNull(12, java.sql.Types.BIGINT);
                    }
                    if (progress.gameplay().getFastestAscensionMs() != null) {
                        playerStmt.setLong(13, progress.gameplay().getFastestAscensionMs());
                    } else {
                        playerStmt.setNull(13, java.sql.Types.BIGINT);
                    }
                    playerStmt.setDouble(14, progress.economy().getSummitAccumulatedVolt().getMantissa());
                    playerStmt.setInt(15, progress.economy().getSummitAccumulatedVolt().getExponent());
                    playerStmt.setDouble(16, progress.economy().getElevationAccumulatedVolt().getMantissa());
                    playerStmt.setInt(17, progress.economy().getElevationAccumulatedVolt().getExponent());
                    playerStmt.setBoolean(18, progress.automation().isAutoUpgradeEnabled());
                    playerStmt.setBoolean(19, progress.automation().isAutoEvolutionEnabled());
                    playerStmt.setInt(20, progress.gameplay().getSeenTutorials());
                    playerStmt.setBoolean(21, progress.automation().isHideOtherRunners());
                    playerStmt.setBoolean(22, progress.automation().isBreakAscensionEnabled());
                    playerStmt.setBoolean(23, progress.automation().isAutoElevationEnabled());
                    playerStmt.setInt(24, progress.automation().getAutoElevationTimerSeconds());
                    playerStmt.setString(25, AscendPlayerPersistence.serializeTargets(progress.automation().getAutoElevationTargets()));
                    playerStmt.setInt(26, progress.automation().getAutoElevationTargetIndex());
                    playerStmt.setBoolean(27, progress.automation().isAutoSummitEnabled());
                    playerStmt.setInt(28, progress.automation().getAutoSummitTimerSeconds());
                    playerStmt.setString(29, AscendPlayerPersistence.serializeAutoSummitConfig(progress.automation().getAutoSummitConfig()));
                    playerStmt.setInt(30, progress.automation().getAutoSummitRotationIndex());
                    playerStmt.setInt(31, progress.gameplay().getTranscendenceCount());
                    playerStmt.setBoolean(32, progress.automation().isAutoAscendEnabled());
                    playerStmt.setBoolean(33, progress.session().isHudHidden());
                    playerStmt.setBoolean(34, progress.session().isPlayersHidden());
                    playerStmt.addBatch();

                    // Save map progress
                    for (Map.Entry<String, MapProgress> entry : progress.gameplay().getMapProgress().entrySet()) {
                        MapProgress mapProgress = entry.getValue();
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
                        double xp = progress.economy().getSummitXp(category);
                        summitStmt.setString(1, playerId.toString());
                        summitStmt.setString(2, category.name());
                        summitStmt.setDouble(3, xp);
                        summitStmt.addBatch();
                    }

                    // Save skill nodes
                    for (SkillTreeNode node : progress.gameplay().getUnlockedSkillNodes()) {
                        skillStmt.setString(1, playerId.toString());
                        skillStmt.setString(2, node.name());
                        skillStmt.addBatch();
                    }

                    // Save achievements
                    for (AchievementType achievement : progress.gameplay().getUnlockedAchievements()) {
                        achievementStmt.setString(1, playerId.toString());
                        achievementStmt.setString(2, achievement.name());
                        achievementStmt.addBatch();
                    }

                    // Save found cats
                    for (String catToken : progress.gameplay().getFoundCats()) {
                        catStmt.setString(1, playerId.toString());
                        catStmt.setString(2, catToken);
                        catStmt.addBatch();
                    }
                }

                if (persistedVersions.isEmpty()) {
                    return null;
                }
                playerStmt.executeBatch();
                mapStmt.executeBatch();
                summitStmt.executeBatch();
                skillStmt.executeBatch();
                achievementStmt.executeBatch();
                catStmt.executeBatch();

                return new SaveResult(persistedVersions, committedResets, committedChallengeResets);
                } finally {
                    // Close lazily-created delete statements
                    closeQuietly(delMaps);
                    closeQuietly(delSummit);
                    closeQuietly(delSkills);
                    closeQuietly(delAchievements);
                    closeQuietly(delCats);
                    closeQuietly(delChallengeRecords);
                }
            }
        }, null);

        if (result == null) {
            return;
        }

        // Commit succeeded — now safe to clear reset flags
        result.committedResets.forEach(resetPendingPlayers::remove);
        result.committedChallengeResets.forEach(transcendenceResetPending::remove);

        // Save succeeded - remove only IDs that were not marked dirty again mid-save.
        for (Map.Entry<UUID, Long> entry : result.persistedVersions.entrySet()) {
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
    }

    private static void closeQuietly(PreparedStatement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private AscendPlayerProgress resolveProgressForSave(UUID playerId, Map<UUID, AscendPlayerProgress> progressOverrides) {
        if (progressOverrides != null) {
            AscendPlayerProgress overridden = progressOverrides.get(playerId);
            if (overridden != null) {
                return overridden;
            }
        }
        AscendPlayerProgress liveProgress = players.get(playerId);
        if (liveProgress != null) {
            return liveProgress;
        }
        return detachedDirtyPlayers.get(playerId);
    }

    private record SaveResult(Map<UUID, Long> persistedVersions, List<UUID> committedResets,
                               List<UUID> committedChallengeResets) {
    }
}
