package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.core.db.ConnectionProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Coordinator for Ascend player data. Owns the player cache, persistence wiring,
 * cross-domain reset flows, and leaderboard queries. Domain-specific operations
 * are delegated to focused facades accessible via {@link #volt()}, {@link #progression()},
 * {@link #runners()}, {@link #gameplay()}, and {@link #settings()}.
 *
 * <p>All SQL is delegated to the package-private {@link AscendPlayerPersistence}.</p>
 */
public class AscendPlayerStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<UUID, AscendPlayerProgress> players = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();
    private final Set<UUID> resetPendingPlayers = ConcurrentHashMap.newKeySet();

    private final ConnectionProvider db;
    private final AscendPlayerPersistence persistence;
    private ChallengeManager challengeManager;
    private RobotManager robotManager;

    private final AscendVoltFacade voltFacade;
    private final AscendProgressionFacade progressionFacade;
    private final AscendRunnerFacade runnerFacade;
    private final AscendGameplayFacade gameplayFacade;
    private final AscendSettingsFacade settingsFacade;

    public AscendPlayerStore(ConnectionProvider db) {
        this.db = db;
        this.persistence = new AscendPlayerPersistence(db, players, playerNames, resetPendingPlayers);
        this.voltFacade = new AscendVoltFacade(players, this);
        this.progressionFacade = new AscendProgressionFacade(players, this);
        this.runnerFacade = new AscendRunnerFacade(players, persistence, this);
        this.gameplayFacade = new AscendGameplayFacade(players, this);
        this.settingsFacade = new AscendSettingsFacade(players, this);
    }

    public void setRuntimeServices(ChallengeManager challengeManager, RobotManager robotManager) {
        this.challengeManager = challengeManager;
        this.robotManager = robotManager;
        progressionFacade.setChallengeManager(challengeManager);
        runnerFacade.setRuntimeServices(challengeManager, robotManager);
    }

    public AscendVoltFacade volt() { return voltFacade; }
    public AscendProgressionFacade progression() { return progressionFacade; }
    public AscendRunnerFacade runners() { return runnerFacade; }
    public AscendGameplayFacade gameplay() { return gameplayFacade; }
    public AscendSettingsFacade settings() { return settingsFacade; }

    public record LeaderboardEntry(UUID playerId, String playerName,
            double totalVoltEarnedMantissa, int totalVoltEarnedExp10,
            int ascensionCount, int totalManualRuns, Long fastestAscensionMs) {}

    public record MapLeaderboardEntry(UUID playerId, String playerName, long bestTimeMs) {
        public MapLeaderboardEntry(String playerName, long bestTimeMs) {
            this(null, playerName, bestTimeMs);
        }
    }

    public static final class MultiplierResult {
        public final BigNumber product;
        public final BigNumber[] values;

        MultiplierResult(BigNumber product, BigNumber[] values) {
            this.product = product;
            this.values = values;
        }
    }

    /**
     * Initialize the store. With lazy loading, we don't load all players upfront.
     * Players are loaded on-demand when they connect.
     */
    public void syncLoad() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, AscendPlayerStore will use in-memory mode");
            return;
        }

        players.clear();
        persistence.clearDirtyState();
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
        AscendPlayerProgress progress = players.get(playerId);
        if (progress != null) {
            migrateAscensionTimer(playerId, progress);
            return progress;
        }

        AscendPlayerProgress loaded = persistence.loadPlayerFromDatabase(playerId);
        if (loaded == null) {
            loaded = new AscendPlayerProgress();
            loaded.gameplay().setAscensionStartedAt(System.currentTimeMillis());
            markDirty(playerId);
        }

        AscendPlayerProgress existing = players.putIfAbsent(playerId, loaded);
        if (existing != null) {
            migrateAscensionTimer(playerId, existing);
            return existing;
        }

        migrateAscensionTimer(playerId, loaded);
        return loaded;
    }

    private void migrateAscensionTimer(UUID playerId, AscendPlayerProgress progress) {
        if (progress.gameplay().getAscensionStartedAt() == null) {
            progress.gameplay().setAscensionStartedAt(System.currentTimeMillis());
            markDirty(playerId);
        }
    }

    public void removePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }

        // Snapshot dirty data before eviction so async save can find it
        AscendPlayerProgress progress = players.get(playerId);
        if (progress != null && persistence.isDirty(playerId)) {
            persistence.snapshotForSave(playerId, progress);
        }

        // Disconnect path: queue targeted async persistence and then evict cache.
        persistence.savePlayerIfDirty(playerId);

        // Remove from cache
        players.remove(playerId);
        resetPendingPlayers.remove(playerId);
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

    public void markDirty(UUID playerId) {
        if (playerId == null) {
            return;
        }
        persistence.markDirty(playerId);
        persistence.queueSave();
    }

    public void flushPendingSave() {
        persistence.flushPendingSave();
    }

    /**
     * Synchronously save a single player's dirty data and return whether it succeeded.
     * Used for idempotency-sensitive operations where we must confirm persistence
     * before proceeding (e.g. passive earnings claim).
     */
    public boolean savePlayerSync(UUID playerId) {
        return persistence.savePlayerSync(playerId);
    }

    public void resetPlayerProgress(UUID playerId) {
        // Mark as reset so any concurrent/pending syncSave() will
        // DELETE child rows before re-inserting (prevents stale upserts)
        resetPendingPlayers.add(playerId);

        // Cancel any pending debounced save to prevent stale data from
        // being written after our DELETE
        persistence.cancelPendingSave();

        AscendPlayerProgress progress = getOrCreatePlayer(playerId);
        // Reset basic progression
        progress.economy().setVolt(BigNumber.ZERO);
        progress.economy().setElevationMultiplier(1);
        progress.gameplay().getMapProgress().clear();

        // Reset Summit system
        progress.economy().clearSummitXp();
        progress.economy().setTotalVoltEarned(BigNumber.ZERO);
        progress.economy().setSummitAccumulatedVolt(BigNumber.ZERO);
        progress.economy().setElevationAccumulatedVolt(BigNumber.ZERO);

        // Reset Ascension/Skill Tree
        progress.gameplay().setAscensionCount(0);
        progress.gameplay().setSkillTreePoints(0);
        progress.gameplay().setUnlockedSkillNodes(null);

        // Reset Achievements
        progress.gameplay().setUnlockedAchievements(null);

        // Reset Easter Egg cats
        progress.gameplay().setFoundCats(null);

        // Reset Statistics
        progress.gameplay().setTotalManualRuns(0);
        progress.gameplay().setConsecutiveManualRuns(0);
        progress.session().setSessionFirstRunClaimed(false);

        // Delete all database entries for this player
        persistence.deletePlayerDataFromDatabase(playerId);

        markDirty(playerId);
    }

    /**
     * Marks a player for full child-row deletion on the next syncSave.
     * Use before clearing in-memory collections (e.g. mapProgress.clear())
     * so stale DB rows are removed before re-insert.
     */
    public void markResetPending(UUID playerId) {
        if (playerId != null) {
            resetPendingPlayers.add(playerId);
        }
    }

    /**
     * Marks a player for challenge records deletion on the next syncSave (transcendence reset).
     * Must be combined with markResetPending for full transcendence wipe.
     */
    public void markTranscendenceResetPending(UUID playerId) {
        persistence.markTranscendenceResetPending(playerId);
    }

    /**
     * Delete ALL player data from the database and clear all in-memory caches.
     * This is a server-wide wipe for launching in a fresh state.
     */
    public void resetAllPlayersProgress() {
        // Cancel any pending debounced save to prevent stale data re-insertion
        persistence.cancelPendingSave();

        // Wipe DB FIRST — if we clear in-memory caches first, another thread
        // (passive earnings, run tracker) can call getOrCreatePlayer() which
        // re-loads old data from the not-yet-wiped DB back into memory.
        persistence.deleteAllPlayerData();

        // Now clear in-memory caches — any subsequent getOrCreatePlayer() will
        // find an empty DB and create fresh default progress
        players.clear();
        playerNames.clear();
        persistence.clearDirtyState();
        resetPendingPlayers.clear();

        // Invalidate leaderboard caches
        persistence.clearLeaderboardCaches();
    }

    /**
     * Shared reset logic for prestige operations.
     * Resets volt, map unlocks (except first), multipliers, manual completion, and runners.
     * @param clearBestTimes whether to also clear best times (elevation does, summit doesn't)
     * @return list of map IDs that had runners (for despawn handling)
     */
    private List<String> resetMapProgress(AscendPlayerProgress progress, String firstMapId, boolean clearBestTimes, UUID playerId) {
        List<String> mapsWithRunners = new java.util.ArrayList<>();

        progress.economy().setVolt(BigNumber.ZERO);
        progress.economy().setSummitAccumulatedVolt(BigNumber.ZERO);
        progress.economy().setElevationAccumulatedVolt(BigNumber.ZERO);

        for (Map.Entry<String, GameplayState.MapProgress> entry : progress.gameplay().getMapProgress().entrySet()) {
            String mapId = entry.getKey();
            GameplayState.MapProgress mapProgress = entry.getValue();

            mapProgress.setUnlocked(mapId.equals(firstMapId));
            mapProgress.setMultiplier(BigNumber.ONE);
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
            GameplayState.MapProgress firstMapProgress = progress.gameplay().getOrCreateMapProgress(firstMapId);
            firstMapProgress.setUnlocked(true);
        }

        return mapsWithRunners;
    }

    /**
     * Resets player progress for elevation: clears volt, map unlocks (except first map),
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
        notifyRobotManager(playerId);
        return mapsWithRunners;
    }

    /**
     * Reset progress for Summit: volt, elevation, multipliers, runners, and map unlocks.
     * Keeps best times and summit XP.
     * @return list of map IDs that had runners (for despawn handling)
     */
    public List<String> resetProgressForSummit(UUID playerId, String firstMapId) {
        AscendPlayerProgress progress = getPlayer(playerId);
        if (progress == null) {
            return List.of();
        }

        progress.economy().setElevationMultiplier(1);
        progress.automation().setAutoElevationTargetIndex(0);

        List<String> mapsWithRunners = resetMapProgress(progress, firstMapId, false, playerId);

        markDirty(playerId);
        notifyRobotManager(playerId);
        return mapsWithRunners;
    }

    /**
     * Reset progress for a challenge: same as summit reset.
     * Resets volt, elevation, multipliers, runners, and map unlocks.
     * Keeps best times.
     * @return list of map IDs that had runners (for despawn handling)
     */
    public List<String> resetProgressForChallenge(UUID playerId, String firstMapId) {
        AscendPlayerProgress progress = getPlayer(playerId);
        if (progress == null) {
            return List.of();
        }

        progress.economy().setElevationMultiplier(1);
        progress.automation().setAutoElevationTargetIndex(0);
        progress.economy().clearSummitXp();

        List<String> mapsWithRunners = resetMapProgress(progress, firstMapId, false, playerId);
        markDirty(playerId);
        notifyRobotManager(playerId);
        return mapsWithRunners;
    }

    private void notifyRobotManager(UUID playerId) {
        if (robotManager == null) {
            return;
        }
        robotManager.markPlayerDirty(playerId);
    }

    public List<LeaderboardEntry> getLeaderboardEntries() {
        return persistence.getLeaderboardEntries();
    }

    public void invalidateLeaderboardCache() {
        persistence.invalidateLeaderboardCache();
    }

    public void invalidateMapLeaderboardCache(String mapId) {
        persistence.invalidateMapLeaderboardCache(mapId);
    }

    public List<MapLeaderboardEntry> getMapLeaderboard(String mapId) {
        return persistence.getMapLeaderboard(mapId);
    }
}
