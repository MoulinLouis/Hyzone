package io.hyvexa.ascend.ascension;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.ChallengeType;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Manages the Ascension Challenge system.
 * Players can start time-limited challenges that snapshot their progress,
 * reset everything, and apply malus effects. On completion, progress is restored
 * and rewards are given. On quit, progress is restored with no reward.
 */
public class ChallengeManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();

    private final AscendPlayerStore playerStore;
    private final AscendMapStore mapStore;
    private final AscendRunTracker runTracker;

    // In-memory cache of active challenges
    private final ConcurrentHashMap<UUID, ActiveChallenge> activeChallenges = new ConcurrentHashMap<>();

    public ChallengeManager(AscendPlayerStore playerStore, AscendMapStore mapStore, AscendRunTracker runTracker) {
        this.playerStore = playerStore;
        this.mapStore = mapStore;
        this.runTracker = runTracker;
    }

    /**
     * Start a challenge for a player.
     * Snapshots progress, resets everything, applies malus.
     * @return list of map IDs that had runners (for robot despawn), or null on failure
     */
    public List<String> startChallenge(UUID playerId, ChallengeType challengeType) {
        if (playerId == null || challengeType == null) {
            return null;
        }
        if (isInChallenge(playerId)) {
            return null; // already in a challenge
        }
        if (!isChallengeUnlocked(playerId, challengeType)) {
            return null; // previous challenges not completed
        }

        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return null;
        }

        // Cancel any active run
        if (runTracker != null) {
            runTracker.cancelRun(playerId);
        }

        // Snapshot current progress
        ChallengeSnapshot snapshot = ChallengeSnapshot.capture(progress);
        long startedAtMs = System.currentTimeMillis();

        // Persist to DB for crash recovery
        String snapshotJson = GSON.toJson(snapshot);
        persistActiveChallenge(playerId, challengeType, startedAtMs, snapshotJson);

        // Cache in memory
        activeChallenges.put(playerId, new ActiveChallenge(challengeType, startedAtMs, snapshot));

        // Set challenge state on progress
        progress.setActiveChallenge(challengeType);
        progress.setChallengeStartedAtMs(startedAtMs);

        // Get first map ID for reset
        String firstMapId = getFirstMapId();

        // Reset progress (like ascension but keeps ascension count/skills/achievements)
        List<String> mapsWithRunners = playerStore.resetProgressForChallenge(playerId, firstMapId);

        playerStore.flushPendingSave();

        LOGGER.atInfo().log("[Challenge] Player " + playerId + " started challenge: " + challengeType.name());
        try {
            io.hyvexa.core.analytics.AnalyticsStore.getInstance().logEvent(playerId, "ascend_challenge_start",
                    "{\"type\":\"" + challengeType.name() + "\"}");
        } catch (Exception e) { /* silent */ }
        return mapsWithRunners;
    }

    /**
     * Complete a challenge. Records time, restores snapshot, applies reward.
     * @return completion time in ms, or -1 on failure
     */
    public long completeChallenge(UUID playerId) {
        ActiveChallenge active = activeChallenges.get(playerId);
        if (active == null) {
            return -1;
        }

        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return -1;
        }

        long elapsedMs = System.currentTimeMillis() - active.startedAtMs();

        // Cancel any active run
        if (runTracker != null) {
            runTracker.cancelRun(playerId);
        }

        // Record completion
        recordChallengeCompletion(playerId, active.challengeType(), elapsedMs);

        // Restore snapshot
        active.snapshot().restore(progress);

        // Apply permanent reward
        progress.addChallengeReward(active.challengeType());

        // Clean up
        activeChallenges.remove(playerId);
        deleteActiveChallenge(playerId);
        playerStore.markDirty(playerId);
        playerStore.flushPendingSave();

        LOGGER.atInfo().log("[Challenge] Player " + playerId + " completed " + active.challengeType().name()
            + " in " + elapsedMs + "ms");
        try {
            io.hyvexa.core.analytics.AnalyticsStore.getInstance().logEvent(playerId, "ascend_challenge_complete",
                    "{\"type\":\"" + active.challengeType().name() + "\",\"time_ms\":" + elapsedMs + "}");
        } catch (Exception e) { /* silent */ }
        return elapsedMs;
    }

    /**
     * Quit a challenge. Restores snapshot, no reward.
     */
    public void quitChallenge(UUID playerId) {
        ActiveChallenge active = activeChallenges.get(playerId);
        if (active == null) {
            return;
        }

        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            activeChallenges.remove(playerId);
            deleteActiveChallenge(playerId);
            return;
        }

        // Cancel any active run
        if (runTracker != null) {
            runTracker.cancelRun(playerId);
        }

        // Restore snapshot (no reward)
        active.snapshot().restore(progress);

        // Clean up
        activeChallenges.remove(playerId);
        deleteActiveChallenge(playerId);
        playerStore.markDirty(playerId);
        playerStore.flushPendingSave();

        LOGGER.atInfo().log("[Challenge] Player " + playerId + " quit challenge: " + active.challengeType().name());
    }

    public boolean isInChallenge(UUID playerId) {
        return activeChallenges.containsKey(playerId);
    }

    public ChallengeType getActiveChallenge(UUID playerId) {
        ActiveChallenge active = activeChallenges.get(playerId);
        return active != null ? active.challengeType() : null;
    }

    public long getChallengeStartedAtMs(UUID playerId) {
        ActiveChallenge active = activeChallenges.get(playerId);
        return active != null ? active.startedAtMs() : 0;
    }

    /**
     * Check if a challenge is unlocked for a player.
     * Challenge 1 is always unlocked. Challenge N requires completing all challenges 1..N-1.
     */
    public boolean isChallengeUnlocked(UUID playerId, ChallengeType challengeType) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return challengeType == ChallengeType.CHALLENGE_1;
        }
        for (ChallengeType prev : ChallengeType.values()) {
            if (prev.getId() >= challengeType.getId()) {
                break;
            }
            if (!progress.hasChallengeReward(prev)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a summit category is blocked by the player's active challenge.
     */
    public boolean isSummitBlocked(UUID playerId, SummitCategory category) {
        ActiveChallenge active = activeChallenges.get(playerId);
        if (active == null) {
            return false;
        }
        return active.challengeType().getBlockedSummitCategories().contains(category);
    }

    /**
     * Check if elevation is blocked by the player's active challenge.
     */
    public boolean isElevationBlocked(UUID playerId) {
        ActiveChallenge active = activeChallenges.get(playerId);
        if (active == null) {
            return false;
        }
        return active.challengeType().blocksElevation();
    }

    /**
     * Check if a map is blocked by the player's active challenge (by displayOrder).
     */
    public boolean isMapBlocked(UUID playerId, int displayOrder) {
        ActiveChallenge active = activeChallenges.get(playerId);
        if (active == null) {
            return false;
        }
        return active.challengeType().getBlockedMapDisplayOrders().contains(displayOrder);
    }

    /**
     * Get the speed divisor for the player's active challenge.
     * Returns 1.0 if no challenge or no speed nerf.
     */
    public double getSpeedDivisor(UUID playerId) {
        ActiveChallenge active = activeChallenges.get(playerId);
        if (active == null) {
            return 1.0;
        }
        return active.challengeType().getSpeedDivisor();
    }

    /**
     * Get the multiplier gain divisor for the player's active challenge.
     * Returns 1.0 if no challenge or no multiplier gain nerf.
     */
    public double getMultiplierGainDivisor(UUID playerId) {
        ActiveChallenge active = activeChallenges.get(playerId);
        if (active == null) {
            return 1.0;
        }
        return active.challengeType().getMultiplierGainDivisor();
    }

    /**
     * Get the evolution power divisor for the player's active challenge.
     * Returns 1.0 if no challenge or no evolution power nerf.
     */
    public double getEvolutionPowerDivisor(UUID playerId) {
        ActiveChallenge active = activeChallenges.get(playerId);
        if (active == null) {
            return 1.0;
        }
        return active.challengeType().getEvolutionPowerDivisor();
    }

    // ========================================
    // Challenge Reward Bonuses (permanent, from completed challenges)
    // ========================================

    /**
     * Get cumulative runner speed multiplier from challenge rewards.
     * C2: x1.10, C5: x1.05, C6: x1.05 — multiplicative stacking.
     */
    public double getChallengeSpeedMultiplier(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) return 1.0;
        double mult = 1.0;
        if (progress.hasChallengeReward(ChallengeType.CHALLENGE_2)) mult *= 1.10;
        if (progress.hasChallengeReward(ChallengeType.CHALLENGE_5)) mult *= 1.05;
        if (progress.hasChallengeReward(ChallengeType.CHALLENGE_6)) mult *= 1.05;
        return mult;
    }

    /**
     * Get cumulative multiplier gain bonus from challenge rewards.
     * C3: x1.10, C5: x1.05, C6: x1.05 — multiplicative stacking.
     */
    public double getChallengeMultiplierGainBonus(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) return 1.0;
        double mult = 1.0;
        if (progress.hasChallengeReward(ChallengeType.CHALLENGE_3)) mult *= 1.10;
        if (progress.hasChallengeReward(ChallengeType.CHALLENGE_5)) mult *= 1.05;
        if (progress.hasChallengeReward(ChallengeType.CHALLENGE_6)) mult *= 1.05;
        return mult;
    }

    /**
     * Get cumulative evolution power bonus from challenge rewards.
     * C4: +0.50, C6: +0.25 — additive stacking.
     */
    public double getChallengeEvolutionPowerBonus(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) return 0.0;
        double bonus = 0.0;
        if (progress.hasChallengeReward(ChallengeType.CHALLENGE_4)) bonus += 0.50;
        if (progress.hasChallengeReward(ChallengeType.CHALLENGE_6)) bonus += 0.25;
        return bonus;
    }

    /**
     * Get map base multiplier bonus from challenge rewards for a given display order.
     * C1: map 5 (displayOrder 4) gets x1.5. C7: map 4+5 (displayOrder 3,4) get x1.5.
     * Multiplicative stacking: map 5 with both = x2.25.
     */
    public double getChallengeMapBaseMultiplier(UUID playerId, int displayOrder) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) return 1.0;
        double mult = 1.0;
        // C1: map 5 (displayOrder 4)
        if (displayOrder == 4 && progress.hasChallengeReward(ChallengeType.CHALLENGE_1)) {
            mult *= 1.5;
        }
        // C7: map 4 (displayOrder 3) and map 5 (displayOrder 4)
        if ((displayOrder == 3 || displayOrder == 4) && progress.hasChallengeReward(ChallengeType.CHALLENGE_7)) {
            mult *= 1.5;
        }
        return mult;
    }

    /**
     * Load active challenge from DB on player connect (crash recovery).
     * Also loads permanent challenge rewards from ascend_challenge_records.
     */
    public void onPlayerConnect(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        // Load active challenge (crash recovery)
        String sql = "SELECT challenge_type_id, started_at_ms, snapshot_json FROM ascend_challenges WHERE player_uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int typeId = rs.getInt("challenge_type_id");
                        long startedAt = rs.getLong("started_at_ms");
                        String json = rs.getString("snapshot_json");

                        ChallengeType type = ChallengeType.fromId(typeId);
                        if (type == null) {
                            LOGGER.atWarning().log("[Challenge] Unknown challenge type ID " + typeId + " for player " + playerId);
                            deleteActiveChallenge(playerId);
                        } else {
                            ChallengeSnapshot snapshot;
                            try {
                                snapshot = GSON.fromJson(json, ChallengeSnapshot.class);
                            } catch (Exception e) {
                                LOGGER.atWarning().withCause(e)
                                        .log("[Challenge] Failed to deserialize snapshot for " + playerId + ", clearing challenge");
                                deleteActiveChallenge(playerId);
                                snapshot = null;
                            }
                            if (snapshot != null) {
                                activeChallenges.put(playerId, new ActiveChallenge(type, startedAt, snapshot));

                                // Restore challenge state on progress
                                AscendPlayerProgress progress = playerStore.getPlayer(playerId);
                                if (progress != null) {
                                    progress.setActiveChallenge(type);
                                    progress.setChallengeStartedAtMs(startedAt);
                                }

                                LOGGER.atInfo().log("[Challenge] Restored active challenge for " + playerId + ": " + type.name());
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("[Challenge] Failed to load challenge for " + playerId + ": " + e.getMessage());
        }

        // Load permanent challenge rewards (any challenge with completions > 0)
        loadChallengeRewards(playerId);
    }

    /**
     * Load permanent challenge rewards from ascend_challenge_records.
     * Any challenge with completions > 0 is considered completed.
     */
    private void loadChallengeRewards(UUID playerId) {
        AscendPlayerProgress progress = playerStore.getPlayer(playerId);
        if (progress == null) {
            return;
        }

        String sql = "SELECT challenge_type_id FROM ascend_challenge_records WHERE player_uuid = ? AND completions > 0";
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int typeId = rs.getInt("challenge_type_id");
                        ChallengeType type = ChallengeType.fromId(typeId);
                        if (type != null) {
                            progress.addChallengeReward(type);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("[Challenge] Failed to load challenge rewards for " + playerId + ": " + e.getMessage());
        }
    }

    /**
     * Evict player from in-memory cache on disconnect.
     */
    public void onPlayerDisconnect(UUID playerId) {
        activeChallenges.remove(playerId);
    }

    /**
     * Get challenge record (best time + completions) for a player and challenge type.
     */
    public ChallengeRecord getChallengeRecord(UUID playerId, ChallengeType challengeType) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return new ChallengeRecord(null, 0);
        }

        String sql = "SELECT best_time_ms, completions FROM ascend_challenge_records WHERE player_uuid = ? AND challenge_type_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return new ChallengeRecord(null, 0);
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                stmt.setInt(2, challengeType.getId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        long bestTime = rs.getLong("best_time_ms");
                        Long bestTimeMs = rs.wasNull() ? null : bestTime;
                        int completions = rs.getInt("completions");
                        return new ChallengeRecord(bestTimeMs, completions);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("[Challenge] Failed to load record for " + playerId + ": " + e.getMessage());
        }
        return new ChallengeRecord(null, 0);
    }

    // ========================================
    // DB persistence helpers
    // ========================================

    private void persistActiveChallenge(UUID playerId, ChallengeType type, long startedAtMs, String snapshotJson) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        String sql = """
            INSERT INTO ascend_challenges (player_uuid, challenge_type_id, started_at_ms, snapshot_json)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE challenge_type_id = VALUES(challenge_type_id),
                started_at_ms = VALUES(started_at_ms), snapshot_json = VALUES(snapshot_json)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                stmt.setInt(2, type.getId());
                stmt.setLong(3, startedAtMs);
                stmt.setString(4, snapshotJson);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("[Challenge] Failed to persist challenge for " + playerId + ": " + e.getMessage());
        }
    }

    private void deleteActiveChallenge(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        String sql = "DELETE FROM ascend_challenges WHERE player_uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("[Challenge] Failed to delete challenge for " + playerId + ": " + e.getMessage());
        }
    }

    private void recordChallengeCompletion(UUID playerId, ChallengeType type, long elapsedMs) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }

        String sql = """
            INSERT INTO ascend_challenge_records (player_uuid, challenge_type_id, best_time_ms, completions)
            VALUES (?, ?, ?, 1)
            ON DUPLICATE KEY UPDATE
                best_time_ms = CASE WHEN best_time_ms IS NULL OR VALUES(best_time_ms) < best_time_ms
                    THEN VALUES(best_time_ms) ELSE best_time_ms END,
                completions = completions + 1
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                stmt.setInt(2, type.getId());
                stmt.setLong(3, elapsedMs);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("[Challenge] Failed to record completion for " + playerId + ": " + e.getMessage());
        }
    }

    private String getFirstMapId() {
        if (mapStore == null) {
            return null;
        }
        List<AscendMap> maps = mapStore.listMapsSorted();
        return maps.isEmpty() ? null : maps.get(0).getId();
    }

    // ========================================
    // Records
    // ========================================

    public record ActiveChallenge(ChallengeType challengeType, long startedAtMs, ChallengeSnapshot snapshot) {}

    public record ChallengeRecord(Long bestTimeMs, int completions) {}

    public record ChallengeLeaderboardEntry(String playerName, long bestTimeMs, int completions) {}

    /**
     * Get the leaderboard for a specific challenge type.
     * Returns all players who have a best time, sorted fastest first.
     */
    public List<ChallengeLeaderboardEntry> getChallengeLeaderboard(ChallengeType challengeType) {
        List<ChallengeLeaderboardEntry> entries = new ArrayList<>();
        if (!DatabaseManager.getInstance().isInitialized()) {
            return entries;
        }

        String sql = """
            SELECT cr.best_time_ms, cr.completions, ap.player_name
            FROM ascend_challenge_records cr
            LEFT JOIN ascend_players ap ON cr.player_uuid = ap.uuid
            WHERE cr.challenge_type_id = ? AND cr.best_time_ms IS NOT NULL
            ORDER BY cr.best_time_ms ASC
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return entries;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setInt(1, challengeType.getId());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String playerName = rs.getString("player_name");
                        long bestTimeMs = rs.getLong("best_time_ms");
                        int completions = rs.getInt("completions");
                        entries.add(new ChallengeLeaderboardEntry(playerName, bestTimeMs, completions));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("[Challenge] Failed to load leaderboard for " + challengeType.name() + ": " + e.getMessage());
        }
        return entries;
    }
}
