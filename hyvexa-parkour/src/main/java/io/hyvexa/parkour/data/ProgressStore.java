package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.analytics.PlayerAnalytics;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.parkour.ParkourConstants;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/** MySQL-backed storage for player progress, completions, and leaderboard caches. */
public class ProgressStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long SAVE_DEBOUNCE_MS = 5000L;
    private static final int MAX_PLAYER_NAME_LENGTH = 32;

    private final ConnectionProvider db;
    private final java.util.Map<UUID, PlayerProgress> progress = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, String> lastKnownNames = new ConcurrentHashMap<>();
    private final java.util.Map<String, LeaderboardCache> leaderboardCache = new ConcurrentHashMap<>();
    private final java.util.Map<String, Long> leaderboardVersions = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, Long> dirtyPlayerVersions = new ConcurrentHashMap<>();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> saveFuture = new AtomicReference<>();
    private final ReadWriteLock fileLock = new ReentrantReadWriteLock();
    private final AtomicLong cachedTotalXp = new AtomicLong(-1L);
    private volatile PlayerAnalytics analytics;
    private volatile java.util.function.Consumer<UUID> rankCacheInvalidator;

    public ProgressStore(ConnectionProvider db) {
        this.db = db;
    }

    public void setAnalytics(PlayerAnalytics analytics) {
        this.analytics = analytics;
    }

    public void setRankCacheInvalidator(java.util.function.Consumer<UUID> rankCacheInvalidator) {
        this.rankCacheInvalidator = rankCacheInvalidator;
    }

    public void syncLoad() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, ProgressStore will be empty");
            return;
        }

        fileLock.writeLock().lock();
        try {
            progress.clear();
            lastKnownNames.clear();
            leaderboardCache.clear();
            leaderboardVersions.clear();
            dirtyPlayerVersions.clear();

            long totalStart = System.nanoTime();
            try (Connection conn = this.db.getConnection()) {
                long start = System.nanoTime();
                loadPlayers(conn);
                DatabaseManager.logSlowQuery("ProgressStore.loadPlayers", start);

                start = System.nanoTime();
                loadCompletions(conn);
                DatabaseManager.logSlowQuery("ProgressStore.loadCompletions", start);

                start = System.nanoTime();
                loadCheckpointTimes(conn);
                DatabaseManager.logSlowQuery("ProgressStore.loadCheckpointTimes", start);
            } catch (SQLException e) {
                LOGGER.atSevere().withCause(e).log("Failed to acquire connection for syncLoad");
                return;
            }
            DatabaseManager.logSlowQuery("ProgressStore.syncLoad (total)", totalStart);

            LOGGER.atInfo().log("ProgressStore loaded " + progress.size() + " players from database");

        } finally {
            fileLock.writeLock().unlock();
        }
    }

    private void loadPlayers(Connection conn) {
        String sql = "SELECT uuid, name, xp, level, welcome_shown, playtime_ms, vip, founder, teleport_item_use_count, jump_count FROM players";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    PlayerProgress playerProgress = new PlayerProgress();
                    playerProgress.xp = rs.getLong("xp");
                    playerProgress.level = rs.getInt("level");
                    playerProgress.welcomeShown = rs.getBoolean("welcome_shown");
                    playerProgress.playtimeMs = rs.getLong("playtime_ms");
                    playerProgress.vip = rs.getBoolean("vip");
                    playerProgress.founder = rs.getBoolean("founder");
                    playerProgress.teleportItemUseCount = rs.getInt("teleport_item_use_count");
                    playerProgress.jumpCount = rs.getLong("jump_count");

                    progress.put(uuid, playerProgress);

                    String name = rs.getString("name");
                    if (name != null && !name.isBlank()) {
                        lastKnownNames.put(uuid, name);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to load players: " + e.getMessage());
        }
    }

    private void loadCompletions(Connection conn) {
        String sql = "SELECT player_uuid, map_id, best_time_ms FROM player_completions";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    String mapId = rs.getString("map_id");
                    long bestTime = rs.getLong("best_time_ms");

                    PlayerProgress playerProgress = progress.get(uuid);
                    if (playerProgress != null) {
                        playerProgress.completedMaps.add(mapId);
                        if (bestTime > 0) {
                            playerProgress.bestMapTimes.put(mapId, bestTime);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to load completions: " + e.getMessage());
        }
    }

    private void loadCheckpointTimes(Connection conn) {
        String sql = "SELECT player_uuid, map_id, checkpoint_index, time_ms FROM player_checkpoint_times"
                + " ORDER BY checkpoint_index";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    String mapId = rs.getString("map_id");
                    int checkpointIndex = rs.getInt("checkpoint_index");
                    long timeMs = rs.getLong("time_ms");

                    PlayerProgress playerProgress = progress.get(uuid);
                    if (playerProgress != null) {
                        List<Long> times = playerProgress.checkpointTimes
                                .computeIfAbsent(mapId, k -> new ArrayList<>());
                        while (times.size() <= checkpointIndex) {
                            times.add(0L);
                        }
                        times.set(checkpointIndex, timeMs);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to load checkpoint times: " + e.getMessage());
        }
    }

    public boolean isMapCompleted(UUID playerId, String mapId) {
        PlayerProgress playerProgress = progress.get(playerId);
        return playerProgress != null && playerProgress.completedMaps.contains(mapId);
    }

    public boolean shouldShowWelcome(UUID playerId) {
        PlayerProgress playerProgress = progress.get(playerId);
        return playerProgress == null || !playerProgress.welcomeShown;
    }

    public void markWelcomeShown(UUID playerId, String playerName) {
        fileLock.writeLock().lock();
        try {
            PlayerProgress playerProgress = progress.computeIfAbsent(playerId, ignored -> new PlayerProgress());
            storePlayerName(playerId, playerName);
            playerProgress.welcomeShown = true;
            markDirty(playerId);
        } finally {
            fileLock.writeLock().unlock();
        }
        queueSave();
    }

    public int getTeleportItemUseCount(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        fileLock.readLock().lock();
        try {
            PlayerProgress playerProgress = progress.get(playerId);
            return playerProgress != null ? playerProgress.teleportItemUseCount : 0;
        } finally {
            fileLock.readLock().unlock();
        }
    }

    public void incrementTeleportItemUseCount(UUID playerId, String playerName) {
        if (playerId == null) {
            return;
        }
        fileLock.writeLock().lock();
        try {
            PlayerProgress playerProgress = progress.computeIfAbsent(playerId, ignored -> new PlayerProgress());
            storePlayerName(playerId, playerName);
            playerProgress.teleportItemUseCount++;
            markDirty(playerId);
        } finally {
            fileLock.writeLock().unlock();
        }
        queueSave();
    }

    public boolean setPlayerRank(UUID playerId, String playerName, boolean vip, boolean founder) {
        if (playerId == null) {
            return false;
        }
        boolean changed = false;
        fileLock.writeLock().lock();
        try {
            PlayerProgress playerProgress = progress.computeIfAbsent(playerId, ignored -> new PlayerProgress());
            storePlayerName(playerId, playerName);
            if (founder) {
                vip = true;
            }
            if (playerProgress.vip != vip || playerProgress.founder != founder) {
                playerProgress.vip = vip;
                playerProgress.founder = founder;
                markDirty(playerId);
                changed = true;
            }
        } finally {
            fileLock.writeLock().unlock();
        }
        if (changed) {
            queueSave();
        }
        return changed;
    }

    public boolean isVip(UUID playerId) {
        PlayerProgress playerProgress = progress.get(playerId);
        return playerProgress != null && (playerProgress.vip || playerProgress.founder);
    }

    public boolean isFounder(UUID playerId) {
        PlayerProgress playerProgress = progress.get(playerId);
        return playerProgress != null && playerProgress.founder;
    }


    public UUID getPlayerIdByName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        String target = playerName.trim();
        for (java.util.Map.Entry<UUID, String> entry : lastKnownNames.entrySet()) {
            String name = entry.getValue();
            if (name != null && name.equalsIgnoreCase(target)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Long getBestTimeMs(UUID playerId, String mapId) {
        PlayerProgress playerProgress = progress.get(playerId);
        if (playerProgress == null) {
            return null;
        }
        return playerProgress.bestMapTimes.get(mapId);
    }

    public List<Long> getCheckpointTimes(UUID playerId, String mapId) {
        PlayerProgress playerProgress = progress.get(playerId);
        if (playerProgress == null) {
            return List.of();
        }
        List<Long> times = playerProgress.checkpointTimes.get(mapId);
        return times != null ? List.copyOf(times) : List.of();
    }

    public ProgressionResult recordMapCompletion(UUID playerId, String playerName, String mapId, long timeMs,
                                                 MapStore mapStore, List<Long> checkpointTimes) {
        return recordMapCompletion(playerId, playerName, mapId, timeMs, mapStore, checkpointTimes, null);
    }

    /**
     * Record a map completion, updating XP/level/rank and persisting asynchronously.
     * Acquires write lock. Side effects: marks player dirty, triggers async DB save.
     *
     * @param completionSavedCallback optional callback invoked with true on success, false on failure
     */
    public ProgressionResult recordMapCompletion(UUID playerId, String playerName, String mapId, long timeMs,
                                                 MapStore mapStore, List<Long> checkpointTimes,
                                                 Consumer<Boolean> completionSavedCallback) {
        fileLock.writeLock().lock();
        ProgressionResult result;
        boolean newBest = false;
        CompletionPersistenceRequest completionPersistenceRequest = null;
        try {
            PlayerProgress playerProgress = progress.computeIfAbsent(playerId, ignored -> new PlayerProgress());
            storePlayerName(playerId, playerName);
            boolean firstCompletionForMap = playerProgress.completedMaps.add(mapId);
            Long best = playerProgress.bestMapTimes.get(mapId);
            boolean personalBest = best != null && timeMs < best;
            newBest = best == null || timeMs < best;
            if (newBest) {
                playerProgress.bestMapTimes.put(mapId, timeMs);
                if (checkpointTimes != null && !checkpointTimes.isEmpty()) {
                    playerProgress.checkpointTimes.put(mapId, new ArrayList<>(checkpointTimes));
                }
            }
            long oldXp = playerProgress.xp;
            int oldLevel = playerProgress.level;
            long recalculatedXp = mapStore != null ? calculateCompletionXp(playerProgress, mapStore) : oldXp;
            playerProgress.xp = Math.max(0L, recalculatedXp);
            playerProgress.level = calculateLevel(playerProgress.xp);
            long xpAwarded = Math.max(0L, playerProgress.xp - oldXp);

            markDirty(playerId);
            List<Long> checkpointSnapshot = newBest && checkpointTimes != null && !checkpointTimes.isEmpty()
                    ? List.copyOf(checkpointTimes)
                    : List.of();
            completionPersistenceRequest = new CompletionPersistenceRequest(playerId, mapId, timeMs, checkpointSnapshot);
            boolean completionSaveQueued = this.db.isInitialized();

            result = new ProgressionResult(firstCompletionForMap, newBest, personalBest, xpAwarded,
                    oldLevel, playerProgress.level, completionSaveQueued);
        } finally {
            fileLock.writeLock().unlock();
        }
        if (newBest) {
            invalidateLeaderboardCache(mapId);
        }
        queueSave();
        persistCompletionAsync(completionPersistenceRequest, completionSavedCallback);
        if (analytics != null) {
            try {
                analytics.logEvent(playerId, "map_complete",
                        "{\"map_id\":\"" + mapId + "\",\"time_ms\":" + timeMs
                        + ",\"is_pb\":" + result.newBest
                        + ",\"first_completion\":" + result.firstCompletion + "}");
                if (result.oldLevel != result.newLevel) {
                    analytics.logEvent(playerId, "level_up",
                            "{\"old_level\":" + result.oldLevel
                            + ",\"new_level\":" + result.newLevel + "}");
                }
            } catch (Exception e) { /* silent */ }
        }
        return result;
    }

    private void persistCompletionAsync(CompletionPersistenceRequest request, Consumer<Boolean> completionSavedCallback) {
        CompletableFuture.supplyAsync(() -> persistCompletion(request), HytaleServer.SCHEDULED_EXECUTOR)
                .whenComplete((saved, throwable) -> {
                    if (throwable != null) {
                        LOGGER.atSevere().withCause(throwable)
                                .log("Unexpected error while saving completion asynchronously");
                        notifyCompletionSaveResult(completionSavedCallback, false);
                        return;
                    }
                    notifyCompletionSaveResult(completionSavedCallback, Boolean.TRUE.equals(saved));
                });
    }

    private boolean persistCompletion(CompletionPersistenceRequest request) {
        if (request == null || !this.db.isInitialized()) {
            return false;
        }

        String completionSql = """
            INSERT INTO player_completions (player_uuid, map_id, best_time_ms)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE best_time_ms = LEAST(best_time_ms, VALUES(best_time_ms))
            """;
        String deleteCheckpointSql = "DELETE FROM player_checkpoint_times WHERE player_uuid = ? AND map_id = ?";
        String insertCheckpointSql = """
            INSERT INTO player_checkpoint_times (player_uuid, map_id, checkpoint_index, time_ms)
            VALUES (?, ?, ?, ?)
            """;

        return this.db.withTransaction(conn -> {
            try (PreparedStatement completionStmt = conn.prepareStatement(completionSql)) {
                DatabaseManager.applyQueryTimeout(completionStmt);
                completionStmt.setString(1, request.playerId.toString());
                completionStmt.setString(2, request.mapId);
                completionStmt.setLong(3, request.timeMs);
                completionStmt.executeUpdate();

                if (!request.checkpointTimes.isEmpty()) {
                    try (PreparedStatement deleteStmt = conn.prepareStatement(deleteCheckpointSql);
                         PreparedStatement insertStmt = conn.prepareStatement(insertCheckpointSql)) {
                        DatabaseManager.applyQueryTimeout(deleteStmt);
                        DatabaseManager.applyQueryTimeout(insertStmt);
                        deleteStmt.setString(1, request.playerId.toString());
                        deleteStmt.setString(2, request.mapId);
                        deleteStmt.executeUpdate();

                        for (int i = 0; i < request.checkpointTimes.size(); i++) {
                            insertStmt.setString(1, request.playerId.toString());
                            insertStmt.setString(2, request.mapId);
                            insertStmt.setInt(3, i);
                            insertStmt.setLong(4, request.checkpointTimes.get(i));
                            insertStmt.addBatch();
                        }
                        insertStmt.executeBatch();
                    }
                }

                return true;
            }
        }, false);
    }

    private void notifyCompletionSaveResult(Consumer<Boolean> completionSavedCallback, boolean completionSaved) {
        if (completionSavedCallback == null) {
            return;
        }
        try {
            completionSavedCallback.accept(completionSaved);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Completion save callback failed");
        }
    }

    public java.util.Map<UUID, Integer> getMapCompletionCounts() {
        java.util.Map<UUID, Integer> counts = new HashMap<>();
        for (java.util.Map.Entry<UUID, PlayerProgress> entry : progress.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().completedMaps.size());
        }
        return counts;
    }

    public java.util.Map<UUID, Long> getBestTimesForMap(String mapId) {
        if (mapId == null) {
            return java.util.Map.of();
        }
        LeaderboardCache cache = leaderboardCache.computeIfAbsent(mapId, this::buildLeaderboardCache);
        return new HashMap<>(cache.timesByPlayer);
    }

    public List<java.util.Map.Entry<UUID, Long>> getLeaderboardEntries(String mapId) {
        if (mapId == null) {
            return List.of();
        }
        LeaderboardCache cache = leaderboardCache.computeIfAbsent(mapId, this::buildLeaderboardCache);
        return cache.entries;
    }

    public int getLeaderboardPosition(String mapId, UUID playerId) {
        if (mapId == null || playerId == null) {
            return -1;
        }
        LeaderboardCache cache = leaderboardCache.computeIfAbsent(mapId, this::buildLeaderboardCache);
        Integer position = cache.positions.get(playerId);
        return position != null ? position : -1;
    }

    public Long getWorldRecordTimeMs(String mapId) {
        if (mapId == null) {
            return null;
        }
        LeaderboardCache cache = leaderboardCache.computeIfAbsent(mapId, this::buildLeaderboardCache);
        return cache.worldRecordMs;
    }

    public LeaderboardHudSnapshot getLeaderboardHudSnapshot(String mapId, UUID playerId) {
        if (mapId == null) {
            return LeaderboardHudSnapshot.empty();
        }
        LeaderboardCache cache = leaderboardCache.computeIfAbsent(mapId, this::buildLeaderboardCache);
        LeaderboardHudRow selfRow = LeaderboardHudRow.empty();
        if (playerId != null) {
            Integer ordinalPosition = cache.ordinalPositions.get(playerId);
            Long selfTime = cache.timesByPlayer.get(playerId);
            if (ordinalPosition != null && selfTime != null) {
                selfRow = new LeaderboardHudRow(String.valueOf(ordinalPosition),
                        getDisplayPlayerName(playerId), FormatUtils.formatDuration(selfTime));
            }
        }
        return new LeaderboardHudSnapshot(cache.version, cache.topRows, selfRow);
    }

    public long getXp(UUID playerId) {
        PlayerProgress playerProgress = progress.get(playerId);
        return playerProgress != null ? playerProgress.xp : 0L;
    }

    public int getLevel(UUID playerId) {
        PlayerProgress playerProgress = progress.get(playerId);
        return playerProgress != null ? playerProgress.level : 1;
    }

    public String getRankName(UUID playerId, MapStore mapStore) {
        int rank = getCompletionRank(playerId, mapStore);
        int index = Math.max(1, Math.min(rank, ParkourConstants.COMPLETION_RANK_NAMES.length)) - 1;
        return ParkourConstants.COMPLETION_RANK_NAMES[index];
    }

    private static final double[] RANK_THRESHOLDS = {
        0.01, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0
    };

    public int getCompletionRank(UUID playerId, MapStore mapStore) {
        if (mapStore == null) {
            return 1;
        }
        long totalXp = getCachedTotalXp(mapStore);
        if (totalXp <= 0L) {
            return 1;
        }
        long playerXp = getPlayerCompletionXp(playerId, mapStore);
        if (playerXp <= 0L) {
            return 1;
        }
        if (playerXp >= totalXp) {
            return 12;
        }
        double percent = (playerXp * 100.0) / totalXp;
        if (percent < 0.01) {
            return 1;
        }
        for (int i = RANK_THRESHOLDS.length - 1; i >= 0; i--) {
            if (percent >= RANK_THRESHOLDS[i]) {
                return i + 2;
            }
        }
        return 2;
    }

    public long getCompletionXpToNextRank(UUID playerId, MapStore mapStore) {
        if (mapStore == null) return 0L;
        long totalXp = getCachedTotalXp(mapStore);
        if (totalXp <= 0L) return 0L;
        long playerXp = getPlayerCompletionXp(playerId, mapStore);
        if (playerXp >= totalXp) return 0L;
        int rank = getCompletionRank(playerId, mapStore);
        if (rank < 1 || rank > RANK_THRESHOLDS.length) return 0L;
        double nextPercent = RANK_THRESHOLDS[rank - 1];
        long requiredXp = (long) Math.ceil((totalXp * nextPercent) / 100.0);
        return Math.max(0L, requiredXp - playerXp);
    }

    public Set<String> getCompletedMaps(UUID playerId) {
        PlayerProgress playerProgress = progress.get(playerId);
        return playerProgress != null ? Set.copyOf(playerProgress.completedMaps) : Set.of();
    }

    public Set<UUID> getPlayerIds() {
        return Set.copyOf(progress.keySet());
    }

    public String getPlayerName(UUID playerId) {
        return lastKnownNames.get(playerId);
    }

    public int getCompletedMapCount(UUID playerId) {
        PlayerProgress playerProgress = progress.get(playerId);
        return playerProgress != null ? playerProgress.completedMaps.size() : 0;
    }

    public boolean clearProgress(UUID playerId) {
        fileLock.writeLock().lock();
        boolean removed;
        PlayerProgress removedProgress;
        try {
            removedProgress = progress.remove(playerId);
            removed = removedProgress != null;
            lastKnownNames.remove(playerId);
            dirtyPlayerVersions.remove(playerId);
        } finally {
            fileLock.writeLock().unlock();
        }
        if (removed) {
            for (String mapId : removedProgress.bestMapTimes.keySet()) {
                invalidateLeaderboardCache(mapId);
            }
            deletePlayerFromDatabase(playerId);
            if (rankCacheInvalidator != null) {
                rankCacheInvalidator.accept(playerId);
            }
        }
        return removed;
    }

    private void deletePlayerFromDatabase(UUID playerId) {
        if (!this.db.isInitialized()) return;

        String deleteCheckpoints = "DELETE FROM player_checkpoint_times WHERE player_uuid = ?";
        // CASCADE will delete completions
        String sql = "DELETE FROM players WHERE uuid = ?";

        try (Connection conn = this.db.getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement checkpointStmt = conn.prepareStatement(deleteCheckpoints);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(checkpointStmt);
                DatabaseManager.applyQueryTimeout(stmt);
                checkpointStmt.setString(1, playerId.toString());
                checkpointStmt.executeUpdate();
                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to delete player: " + e.getMessage());
        }
    }

    public MapPurgeResult purgeMapProgress(String mapId, MapStore mapStore) {
        if (mapId == null || mapId.isBlank()) {
            return new MapPurgeResult(0, 0L);
        }
        String trimmedId = mapId.trim();
        List<UUID> affectedPlayers = new ArrayList<>();
        long totalXpRemoved = 0L;

        fileLock.writeLock().lock();
        try {
            for (java.util.Map.Entry<UUID, PlayerProgress> entry : progress.entrySet()) {
                PlayerProgress playerProgress = entry.getValue();
                boolean removedCompletion = playerProgress.completedMaps.remove(trimmedId);
                boolean removedBest = playerProgress.bestMapTimes.remove(trimmedId) != null;
                boolean removedCheckpoints = playerProgress.checkpointTimes.remove(trimmedId) != null;
                if (removedCompletion || removedBest || removedCheckpoints) {
                    affectedPlayers.add(entry.getKey());
                    long oldXp = playerProgress.xp;
                    long newXp = mapStore != null ? calculateCompletionXp(playerProgress, mapStore) : oldXp;
                    totalXpRemoved += Math.max(0L, oldXp - newXp);
                    playerProgress.xp = Math.max(0L, newXp);
                    playerProgress.level = calculateLevel(playerProgress.xp);
                    markDirty(entry.getKey());
                }
            }
        } finally {
            fileLock.writeLock().unlock();
        }

        if (!affectedPlayers.isEmpty()) {
            invalidateLeaderboardCache(trimmedId);
            purgeMapFromDatabase(trimmedId);
            queueSave();
            if (rankCacheInvalidator != null) {
                for (UUID playerId : affectedPlayers) {
                    rankCacheInvalidator.accept(playerId);
                }
            }
        }
        return new MapPurgeResult(affectedPlayers.size(), totalXpRemoved);
    }

    public boolean clearPlayerMapProgress(UUID playerId, String mapId, MapStore mapStore) {
        if (playerId == null || mapId == null || mapId.isBlank()) {
            return false;
        }
        String trimmedId = mapId.trim();
        boolean removed;
        fileLock.writeLock().lock();
        try {
            PlayerProgress playerProgress = progress.get(playerId);
            if (playerProgress == null) {
                return false;
            }
            boolean removedCompletion = playerProgress.completedMaps.remove(trimmedId);
            boolean removedBest = playerProgress.bestMapTimes.remove(trimmedId) != null;
            boolean removedCheckpoints = playerProgress.checkpointTimes.remove(trimmedId) != null;
            removed = removedCompletion || removedBest || removedCheckpoints;
            if (removed) {
                long newXp = mapStore != null ? calculateCompletionXp(playerProgress, mapStore) : playerProgress.xp;
                playerProgress.xp = Math.max(0L, newXp);
                playerProgress.level = calculateLevel(playerProgress.xp);
                markDirty(playerId);
            }
        } finally {
            fileLock.writeLock().unlock();
        }
        if (removed) {
            deletePlayerMapCompletion(playerId, trimmedId);
            invalidateLeaderboardCache(trimmedId);
            queueSave();
            if (rankCacheInvalidator != null) {
                rankCacheInvalidator.accept(playerId);
            }
        }
        return removed;
    }

    private void deletePlayerMapCompletion(UUID playerId, String mapId) {
        if (!this.db.isInitialized()) return;

        String deleteCheckpoints = "DELETE FROM player_checkpoint_times WHERE player_uuid = ? AND map_id = ?";
        String sql = "DELETE FROM player_completions WHERE player_uuid = ? AND map_id = ?";

        try (Connection conn = this.db.getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement checkpointStmt = conn.prepareStatement(deleteCheckpoints);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(checkpointStmt);
                DatabaseManager.applyQueryTimeout(stmt);
                checkpointStmt.setString(1, playerId.toString());
                checkpointStmt.setString(2, mapId);
                checkpointStmt.executeUpdate();

                stmt.setString(1, playerId.toString());
                stmt.setString(2, mapId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to delete player map completion: " + e.getMessage());
        }
    }

    private void purgeMapFromDatabase(String mapId) {
        if (!this.db.isInitialized()) return;

        String deleteCheckpoints = "DELETE FROM player_checkpoint_times WHERE map_id = ?";
        String sql = "DELETE FROM player_completions WHERE map_id = ?";

        try (Connection conn = this.db.getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement checkpointStmt = conn.prepareStatement(deleteCheckpoints);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(checkpointStmt);
                DatabaseManager.applyQueryTimeout(stmt);
                checkpointStmt.setString(1, mapId);
                checkpointStmt.executeUpdate();

                stmt.setString(1, mapId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to purge map completions: " + e.getMessage());
        }
    }

    public void addPlaytime(UUID playerId, String playerName, long deltaMs) {
        if (playerId == null || deltaMs <= 0L) return;

        fileLock.writeLock().lock();
        try {
            PlayerProgress playerProgress = progress.computeIfAbsent(playerId, ignored -> new PlayerProgress());
            storePlayerName(playerId, playerName);
            playerProgress.playtimeMs = Math.max(0L, playerProgress.playtimeMs + deltaMs);
            markDirty(playerId);
        } finally {
            fileLock.writeLock().unlock();
        }
        queueSave();
    }

    public long getPlaytimeMs(UUID playerId) {
        PlayerProgress playerProgress = progress.get(playerId);
        return playerProgress != null ? playerProgress.playtimeMs : 0L;
    }

    public long getJumpCount(UUID playerId) {
        PlayerProgress playerProgress = progress.get(playerId);
        return playerProgress != null ? playerProgress.jumpCount : 0L;
    }

    public void addJumps(UUID playerId, String playerName, int count) {
        if (playerId == null || count <= 0) return;

        fileLock.writeLock().lock();
        try {
            PlayerProgress playerProgress = progress.computeIfAbsent(playerId, ignored -> new PlayerProgress());
            storePlayerName(playerId, playerName);
            playerProgress.jumpCount = Math.max(0L, playerProgress.jumpCount + count);
            markDirty(playerId);
        } finally {
            fileLock.writeLock().unlock();
        }
        queueSave();
    }

    private static int calculateLevel(long xp) {
        long[] thresholds = ParkourConstants.RANK_XP_REQUIREMENTS;
        int rankCount = thresholds.length;
        if (rankCount <= 0) return 1;
        int level = 1;
        for (int i = 0; i < rankCount; i++) {
            if (xp >= thresholds[i]) {
                level = i + 1;
            } else {
                break;
            }
        }
        return level;
    }

    public static long getTotalPossibleXp(MapStore mapStore) {
        if (mapStore == null) return 0L;
        long total = 0L;
        for (Map map : mapStore.listMapsReadonly()) {
            total += getMapCompletionXp(map);
        }
        return total;
    }

    private long getCachedTotalXp(MapStore mapStore) {
        long cached = cachedTotalXp.get();
        if (cached >= 0L) {
            return cached;
        }
        long computed = getTotalPossibleXp(mapStore);
        cachedTotalXp.compareAndSet(-1L, computed);
        return cachedTotalXp.get();
    }

    public void invalidateTotalXpCache() {
        cachedTotalXp.set(-1L);
    }

    private long getPlayerCompletionXp(UUID playerId, MapStore mapStore) {
        PlayerProgress playerProgress = progress.get(playerId);
        if (playerProgress == null) return 0L;
        return calculateCompletionXp(playerProgress, mapStore);
    }

    public long getCalculatedCompletionXp(UUID playerId, MapStore mapStore) {
        if (playerId == null || mapStore == null) return 0L;
        return getPlayerCompletionXp(playerId, mapStore);
    }

    private static long calculateCompletionXp(PlayerProgress playerProgress, MapStore mapStore) {
        if (playerProgress == null || mapStore == null) return 0L;
        long total = 0L;
        for (String mapId : playerProgress.completedMaps) {
            Map map = mapStore.getMapReadonly(mapId);
            total += getMapCompletionXp(map);
        }
        return total;
    }

    private static long getMapCompletionXp(Map map) {
        if (map == null) return 0L;
        return Math.max(0L, map.getFirstCompletionXp());
    }

    public static long getCategoryXp(String category) {
        if (category == null || category.isBlank()) return ParkourConstants.MAP_XP_EASY;
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "medium" -> ParkourConstants.MAP_XP_MEDIUM;
            case "hard" -> ParkourConstants.MAP_XP_HARD;
            case "insane" -> ParkourConstants.MAP_XP_INSANE;
            default -> ParkourConstants.MAP_XP_EASY;
        };
    }

    private void storePlayerName(UUID playerId, String playerName) {
        if (playerId == null || playerName == null || playerName.isBlank()) return;
        String trimmedName = playerName.trim();
        if (trimmedName.length() > MAX_PLAYER_NAME_LENGTH) {
            trimmedName = trimmedName.substring(0, MAX_PLAYER_NAME_LENGTH);
        }
        String previousName = lastKnownNames.put(playerId, trimmedName);
        if (previousName == null || previousName.equals(trimmedName)) {
            return;
        }
        PlayerProgress playerProgress = progress.get(playerId);
        if (playerProgress == null) {
            return;
        }
        for (String mapId : playerProgress.bestMapTimes.keySet()) {
            invalidateLeaderboardCache(mapId);
        }
    }

    private void markDirty(UUID playerId) {
        if (playerId == null) {
            return;
        }
        dirtyPlayerVersions.compute(playerId, (ignored, version) -> version == null ? 1L : version + 1L);
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
        queueSave(SAVE_DEBOUNCE_MS);
    }

    private void queueSave(long delayMs) {
        if (!saveQueued.compareAndSet(false, true)) return;
        ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                syncSave();
            } finally {
                saveFuture.set(null);
                saveQueued.set(false);
                if (!dirtyPlayerVersions.isEmpty()) {
                    long followUpDelay = db.isInitialized() ? 0L : SAVE_DEBOUNCE_MS;
                    queueSave(followUpDelay);
                }
            }
        }, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
        saveFuture.set(future);
    }

    private void syncSave() {
        java.util.Map<UUID, Long> toSave = java.util.Map.copyOf(dirtyPlayerVersions);
        if (toSave.isEmpty()) return;
        if (!this.db.isInitialized()) return;
        java.util.Map<UUID, Long> skippedIds = new HashMap<>();
        List<UUID> batchedIds = new ArrayList<>();

        String sql = """
            INSERT INTO players (uuid, name, xp, level, welcome_shown, playtime_ms, vip, founder, teleport_item_use_count, jump_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name), xp = VALUES(xp), level = VALUES(level),
                welcome_shown = VALUES(welcome_shown), playtime_ms = VALUES(playtime_ms),
                vip = VALUES(vip), founder = VALUES(founder), teleport_item_use_count = VALUES(teleport_item_use_count),
                jump_count = VALUES(jump_count)
            """;

        try (Connection conn = this.db.getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);

                for (java.util.Map.Entry<UUID, Long> dirtyEntry : toSave.entrySet()) {
                    UUID playerId = dirtyEntry.getKey();
                    PlayerProgress playerProgress = progress.get(playerId);
                    if (playerProgress == null) {
                        skippedIds.put(playerId, dirtyEntry.getValue());
                        continue;
                    }

                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, lastKnownNames.get(playerId));
                    stmt.setLong(3, playerProgress.xp);
                    stmt.setInt(4, playerProgress.level);
                    stmt.setBoolean(5, playerProgress.welcomeShown);
                    stmt.setLong(6, playerProgress.playtimeMs);
                    stmt.setBoolean(7, playerProgress.vip);
                    stmt.setBoolean(8, playerProgress.founder);
                    stmt.setInt(9, playerProgress.teleportItemUseCount);
                    stmt.setLong(10, playerProgress.jumpCount);
                    stmt.addBatch();
                    batchedIds.add(playerId);
                }
                if (batchedIds.isEmpty()) {
                    clearSavedVersions(skippedIds);
                    return;
                }
                stmt.executeBatch();
                clearSavedVersions(toSave);
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to save players to database: " + e.getMessage());
            // Keep failed writes dirty for retry, but clear stale IDs with no in-memory state.
            clearSavedVersions(skippedIds);
        }
    }

    private void clearSavedVersions(java.util.Map<UUID, Long> snapshot) {
        for (java.util.Map.Entry<UUID, Long> entry : snapshot.entrySet()) {
            UUID playerId = entry.getKey();
            Long savedVersion = entry.getValue();
            dirtyPlayerVersions.compute(playerId, (ignored, currentVersion) -> {
                if (currentVersion == null) {
                    return null;
                }
                return currentVersion.equals(savedVersion) ? null : currentVersion;
            });
        }
    }

    private void invalidateLeaderboardCache(String mapId) {
        if (mapId == null) {
            return;
        }
        leaderboardVersions.merge(mapId, 1L, Long::sum);
        leaderboardCache.remove(mapId);
    }

    private LeaderboardCache buildLeaderboardCache(String mapId) {
        if (mapId == null) {
            return LeaderboardCache.empty();
        }
        List<java.util.Map.Entry<UUID, Long>> entries = new ArrayList<>();
        for (java.util.Map.Entry<UUID, PlayerProgress> entry : progress.entrySet()) {
            Long best = entry.getValue().bestMapTimes.get(mapId);
            if (best != null) {
                entries.add(java.util.Map.entry(entry.getKey(), best));
            }
        }
        entries.sort(Comparator.comparingLong(java.util.Map.Entry::getValue));
        java.util.Map<UUID, Integer> positions = new HashMap<>();
        java.util.Map<UUID, Integer> ordinalPositions = new HashMap<>();
        java.util.Map<UUID, Long> timesByPlayer = new HashMap<>(entries.size());
        Long worldRecordMs = entries.isEmpty() ? null : entries.get(0).getValue();
        long lastTime = Long.MIN_VALUE;
        int rank = 0;
        for (int i = 0; i < entries.size(); i++) {
            java.util.Map.Entry<UUID, Long> leaderboardEntry = entries.get(i);
            long time = toDisplayedCentiseconds(leaderboardEntry.getValue());
            if (i == 0 || time > lastTime) {
                rank = i + 1;
                lastTime = time;
            }
            UUID playerId = leaderboardEntry.getKey();
            positions.put(playerId, rank);
            ordinalPositions.put(playerId, i + 1);
            timesByPlayer.put(playerId, leaderboardEntry.getValue());
        }
        long version = leaderboardVersions.getOrDefault(mapId, 0L);
        return new LeaderboardCache(List.copyOf(entries), java.util.Map.copyOf(positions),
                java.util.Map.copyOf(ordinalPositions), java.util.Map.copyOf(timesByPlayer),
                buildTopRows(entries), worldRecordMs, version);
    }

    private static long toDisplayedCentiseconds(long durationMs) {
        return Math.round(durationMs / 10.0);
    }

    private List<LeaderboardHudRow> buildTopRows(List<java.util.Map.Entry<UUID, Long>> entries) {
        List<LeaderboardHudRow> topRows = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            if (i < entries.size()) {
                java.util.Map.Entry<UUID, Long> entry = entries.get(i);
                topRows.add(new LeaderboardHudRow(String.valueOf(i + 1), getDisplayPlayerName(entry.getKey()),
                        FormatUtils.formatDuration(entry.getValue())));
            } else {
                topRows.add(LeaderboardHudRow.empty(i + 1));
            }
        }
        return List.copyOf(topRows);
    }

    private String getDisplayPlayerName(UUID playerId) {
        String name = getPlayerName(playerId);
        if (name == null || name.isBlank()) {
            return "Player";
        }
        return FormatUtils.truncate(name, 14);
    }

    private static class PlayerProgress {
        final Set<String> completedMaps = new java.util.HashSet<>();
        final java.util.Map<String, Long> bestMapTimes = new HashMap<>();
        final java.util.Map<String, List<Long>> checkpointTimes = new HashMap<>();
        long xp;
        int level = 1;
        boolean welcomeShown;
        long playtimeMs;
        boolean vip;
        boolean founder;
        int teleportItemUseCount = 0;
        long jumpCount = 0L;
    }

    private static class LeaderboardCache {
        final List<java.util.Map.Entry<UUID, Long>> entries;
        final java.util.Map<UUID, Integer> positions;
        final java.util.Map<UUID, Integer> ordinalPositions;
        final java.util.Map<UUID, Long> timesByPlayer;
        final List<LeaderboardHudRow> topRows;
        final Long worldRecordMs;
        final long version;

        LeaderboardCache(List<java.util.Map.Entry<UUID, Long>> entries, java.util.Map<UUID, Integer> positions,
                         java.util.Map<UUID, Integer> ordinalPositions, java.util.Map<UUID, Long> timesByPlayer,
                         List<LeaderboardHudRow> topRows, Long worldRecordMs, long version) {
            this.entries = entries;
            this.positions = positions;
            this.ordinalPositions = ordinalPositions;
            this.timesByPlayer = timesByPlayer;
            this.topRows = topRows;
            this.worldRecordMs = worldRecordMs;
            this.version = version;
        }

        private static LeaderboardCache empty() {
            return new LeaderboardCache(List.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
                    List.of(
                            LeaderboardHudRow.empty(1),
                            LeaderboardHudRow.empty(2),
                            LeaderboardHudRow.empty(3),
                            LeaderboardHudRow.empty(4),
                            LeaderboardHudRow.empty(5)
                    ), null, 0L);
        }
    }

    public static final class LeaderboardHudSnapshot {
        private static final LeaderboardHudSnapshot EMPTY =
                new LeaderboardHudSnapshot(0L, List.of(), LeaderboardHudRow.empty());

        private final long version;
        private final List<LeaderboardHudRow> topRows;
        private final LeaderboardHudRow selfRow;

        private LeaderboardHudSnapshot(long version, List<LeaderboardHudRow> topRows, LeaderboardHudRow selfRow) {
            this.version = version;
            this.topRows = topRows;
            this.selfRow = selfRow;
        }

        public static LeaderboardHudSnapshot empty() {
            return EMPTY;
        }

        public long getVersion() {
            return version;
        }

        public List<LeaderboardHudRow> getTopRows() {
            return topRows;
        }

        public LeaderboardHudRow getSelfRow() {
            return selfRow;
        }
    }

    public static final class LeaderboardHudRow {
        private static final LeaderboardHudRow EMPTY_SELF = new LeaderboardHudRow("", "", "");
        private final String rank;
        private final String name;
        private final String time;

        private LeaderboardHudRow(String rank, String name, String time) {
            this.rank = rank;
            this.name = name;
            this.time = time;
        }

        public static LeaderboardHudRow empty(int rank) {
            return new LeaderboardHudRow(String.valueOf(rank), "", "");
        }

        public static LeaderboardHudRow empty() {
            return EMPTY_SELF;
        }

        public String getRank() {
            return rank;
        }

        public String getName() {
            return name;
        }

        public String getTime() {
            return time;
        }
    }

    private static final class CompletionPersistenceRequest {
        private final UUID playerId;
        private final String mapId;
        private final long timeMs;
        private final List<Long> checkpointTimes;

        private CompletionPersistenceRequest(UUID playerId, String mapId, long timeMs, List<Long> checkpointTimes) {
            this.playerId = playerId;
            this.mapId = mapId;
            this.timeMs = timeMs;
            this.checkpointTimes = checkpointTimes != null ? checkpointTimes : List.of();
        }
    }

    public static class ProgressionResult {
        public final boolean firstCompletion;
        public final boolean newBest;
        public final boolean personalBest;
        public final long xpAwarded;
        public final int oldLevel;
        public final int newLevel;
        /**
         * True when completion persistence was queued/attempted.
         * This is not a confirmation of durable DB success.
         */
        public final boolean completionSaved;

        public ProgressionResult(boolean firstCompletion, boolean newBest, boolean personalBest, long xpAwarded,
                                 int oldLevel, int newLevel, boolean completionSaved) {
            this.firstCompletion = firstCompletion;
            this.newBest = newBest;
            this.personalBest = personalBest;
            this.xpAwarded = xpAwarded;
            this.oldLevel = oldLevel;
            this.newLevel = newLevel;
            this.completionSaved = completionSaved;
        }
    }

    public static class MapPurgeResult {
        public final int playersUpdated;
        public final long totalXpRemoved;

        public MapPurgeResult(int playersUpdated, long totalXpRemoved) {
            this.playersUpdated = playersUpdated;
            this.totalXpRemoved = totalXpRemoved;
        }
    }
}
