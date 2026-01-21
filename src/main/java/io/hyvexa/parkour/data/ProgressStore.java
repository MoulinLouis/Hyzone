package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.HyvexaPlugin;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

public class ProgressStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long SAVE_DEBOUNCE_MS = 5000L;
    private static final int MAX_PLAYER_NAME_LENGTH = 32;

    private final java.util.Map<UUID, PlayerProgress> progress = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, String> lastKnownNames = new ConcurrentHashMap<>();
    private final java.util.Map<String, LeaderboardCache> leaderboardCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> saveFuture = new AtomicReference<>();
    private final ReadWriteLock fileLock = new ReentrantReadWriteLock();
    private volatile long cachedTotalXp = -1L;

    public ProgressStore() {
    }

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, ProgressStore will be empty");
            return;
        }

        fileLock.writeLock().lock();
        try {
            progress.clear();
            lastKnownNames.clear();
            leaderboardCache.clear();
            dirtyPlayers.clear();

            loadPlayers();
            loadCompletions();

            LOGGER.atInfo().log("ProgressStore loaded " + progress.size() + " players from database");

        } finally {
            fileLock.writeLock().unlock();
        }
    }

    private void loadPlayers() {
        String sql = "SELECT uuid, name, xp, level, welcome_shown, playtime_ms FROM players";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                PlayerProgress playerProgress = new PlayerProgress();
                playerProgress.xp = rs.getLong("xp");
                playerProgress.level = rs.getInt("level");
                playerProgress.welcomeShown = rs.getBoolean("welcome_shown");
                playerProgress.playtimeMs = rs.getLong("playtime_ms");

                progress.put(uuid, playerProgress);

                String name = rs.getString("name");
                if (name != null && !name.isBlank()) {
                    lastKnownNames.put(uuid, name);
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load players: " + e.getMessage());
        }
    }

    private void loadCompletions() {
        String sql = "SELECT player_uuid, map_id, best_time_ms FROM player_completions";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

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
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load completions: " + e.getMessage());
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
            dirtyPlayers.add(playerId);
        } finally {
            fileLock.writeLock().unlock();
        }
        queueSave();
    }

    public Long getBestTimeMs(UUID playerId, String mapId) {
        PlayerProgress playerProgress = progress.get(playerId);
        if (playerProgress == null) {
            return null;
        }
        return playerProgress.bestMapTimes.get(mapId);
    }

    public ProgressionResult recordMapCompletion(UUID playerId, String playerName, String mapId, long timeMs,
                                                 long firstCompletionXp) {
        fileLock.writeLock().lock();
        ProgressionResult result;
        boolean newBest = false;
        try {
            PlayerProgress playerProgress = progress.computeIfAbsent(playerId, ignored -> new PlayerProgress());
            storePlayerName(playerId, playerName);
            boolean firstCompletionForMap = playerProgress.completedMaps.add(mapId);
            Long best = playerProgress.bestMapTimes.get(mapId);
            boolean personalBest = best != null && timeMs < best;
            newBest = best == null || timeMs < best;
            if (newBest) {
                playerProgress.bestMapTimes.put(mapId, timeMs);
            }
            long xpAwarded = 0L;
            if (firstCompletionForMap) {
                xpAwarded = Math.max(0L, firstCompletionXp);
            }
            long oldXp = playerProgress.xp;
            int oldLevel = playerProgress.level;
            playerProgress.xp = Math.max(0L, oldXp + xpAwarded);
            playerProgress.level = calculateLevel(playerProgress.xp);

            dirtyPlayers.add(playerId);
            // Save completion immediately
            saveCompletion(playerId, mapId, timeMs);

            result = new ProgressionResult(firstCompletionForMap, newBest, personalBest, xpAwarded,
                    oldLevel, playerProgress.level);
        } finally {
            fileLock.writeLock().unlock();
        }
        if (newBest) {
            invalidateLeaderboardCache(mapId);
        }
        queueSave();
        return result;
    }

    private void saveCompletion(UUID playerId, String mapId, long timeMs) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = """
            INSERT INTO player_completions (player_uuid, map_id, best_time_ms)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE best_time_ms = LEAST(best_time_ms, VALUES(best_time_ms))
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, mapId);
            stmt.setLong(3, timeMs);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to save completion: " + e.getMessage());
        }
    }

    public java.util.Map<UUID, Integer> getMapCompletionCounts() {
        java.util.Map<UUID, Integer> counts = new ConcurrentHashMap<>();
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
        java.util.Map<UUID, Long> times = new HashMap<>(cache.entries.size());
        for (java.util.Map.Entry<UUID, Long> entry : cache.entries) {
            times.put(entry.getKey(), entry.getValue());
        }
        return times;
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
        if (percent >= 90.0) return 11;
        if (percent >= 80.0) return 10;
        if (percent >= 70.0) return 9;
        if (percent >= 60.0) return 8;
        if (percent >= 50.0) return 7;
        if (percent >= 40.0) return 6;
        if (percent >= 30.0) return 5;
        if (percent >= 20.0) return 4;
        if (percent >= 10.0) return 3;
        return 2;
    }

    public long getCompletionXpToNextRank(UUID playerId, MapStore mapStore) {
        if (mapStore == null) return 0L;
        long totalXp = getCachedTotalXp(mapStore);
        if (totalXp <= 0L) return 0L;
        long playerXp = getPlayerCompletionXp(playerId, mapStore);
        if (playerXp >= totalXp) return 0L;
        int rank = getCompletionRank(playerId, mapStore);
        double nextPercent = switch (rank) {
            case 1 -> 0.01;
            case 2 -> 10.0;
            case 3 -> 20.0;
            case 4 -> 30.0;
            case 5 -> 40.0;
            case 6 -> 50.0;
            case 7 -> 60.0;
            case 8 -> 70.0;
            case 9 -> 80.0;
            case 10 -> 90.0;
            case 11 -> 100.0;
            default -> 0.0;
        };
        if (nextPercent <= 0.0) return 0L;
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
        try {
            removed = progress.remove(playerId) != null;
            lastKnownNames.remove(playerId);
            dirtyPlayers.remove(playerId);
        } finally {
            fileLock.writeLock().unlock();
        }
        if (removed) {
            leaderboardCache.clear();
            deletePlayerFromDatabase(playerId);
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin != null) {
                plugin.invalidateRankCache(playerId);
            }
        }
        return removed;
    }

    private void deletePlayerFromDatabase(UUID playerId) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        // CASCADE will delete completions
        String sql = "DELETE FROM players WHERE uuid = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to delete player: " + e.getMessage());
        }
    }

    public MapPurgeResult purgeMapProgress(String mapId, long xpReduction) {
        if (mapId == null || mapId.isBlank()) {
            return new MapPurgeResult(0, 0L);
        }
        String trimmedId = mapId.trim();
        long reduction = Math.max(0L, xpReduction);
        List<UUID> affectedPlayers = new ArrayList<>();
        long totalXpRemoved = 0L;

        fileLock.writeLock().lock();
        try {
            for (java.util.Map.Entry<UUID, PlayerProgress> entry : progress.entrySet()) {
                PlayerProgress playerProgress = entry.getValue();
                boolean removedCompletion = playerProgress.completedMaps.remove(trimmedId);
                boolean removedBest = playerProgress.bestMapTimes.remove(trimmedId) != null;
                if (removedCompletion || removedBest) {
                    affectedPlayers.add(entry.getKey());
                    long oldXp = playerProgress.xp;
                    long newXp = Math.max(0L, oldXp - reduction);
                    totalXpRemoved += (oldXp - newXp);
                    playerProgress.xp = newXp;
                    playerProgress.level = calculateLevel(playerProgress.xp);
                    dirtyPlayers.add(entry.getKey());
                }
            }
        } finally {
            fileLock.writeLock().unlock();
        }

        if (!affectedPlayers.isEmpty()) {
            invalidateLeaderboardCache(trimmedId);
            purgeMapFromDatabase(trimmedId);
            queueSave();
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin != null) {
                for (UUID playerId : affectedPlayers) {
                    plugin.invalidateRankCache(playerId);
                }
            }
        }
        return new MapPurgeResult(affectedPlayers.size(), totalXpRemoved);
    }

    public boolean clearPlayerMapProgress(UUID playerId, String mapId) {
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
            removed = removedCompletion || removedBest;
            if (removed) {
                dirtyPlayers.add(playerId);
            }
        } finally {
            fileLock.writeLock().unlock();
        }
        if (removed) {
            deletePlayerMapCompletion(playerId, trimmedId);
            invalidateLeaderboardCache(trimmedId);
            queueSave();
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin != null) {
                plugin.invalidateRankCache(playerId);
            }
        }
        return removed;
    }

    private void deletePlayerMapCompletion(UUID playerId, String mapId) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = "DELETE FROM player_completions WHERE player_uuid = ? AND map_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, mapId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to delete player map completion: " + e.getMessage());
        }
    }

    private void purgeMapFromDatabase(String mapId) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = "DELETE FROM player_completions WHERE map_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mapId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to purge map completions: " + e.getMessage());
        }
    }

    public void addPlaytime(UUID playerId, String playerName, long deltaMs) {
        if (playerId == null || deltaMs <= 0L) return;

        fileLock.writeLock().lock();
        try {
            PlayerProgress playerProgress = progress.computeIfAbsent(playerId, ignored -> new PlayerProgress());
            storePlayerName(playerId, playerName);
            playerProgress.playtimeMs = Math.max(0L, playerProgress.playtimeMs + deltaMs);
            dirtyPlayers.add(playerId);
        } finally {
            fileLock.writeLock().unlock();
        }
        queueSave();
    }

    public long getPlaytimeMs(UUID playerId) {
        PlayerProgress playerProgress = progress.get(playerId);
        return playerProgress != null ? playerProgress.playtimeMs : 0L;
    }

    private static int calculateLevel(long xp) {
        long[] thresholds = ParkourConstants.RANK_XP_REQUIREMENTS;
        int rankCount = Math.min(ParkourConstants.RANK_NAMES.length, thresholds.length);
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

    public static String getRankNameForLevel(int level) {
        int rankCount = Math.min(ParkourConstants.RANK_NAMES.length, ParkourConstants.RANK_XP_REQUIREMENTS.length);
        if (rankCount <= 0) return "Unranked";
        int index = Math.max(1, Math.min(level, rankCount)) - 1;
        return ParkourConstants.RANK_NAMES[index];
    }

    public static String getRankNameForXp(long xp) {
        return getRankNameForLevel(calculateLevel(xp));
    }

    public static long getTotalPossibleXp(MapStore mapStore) {
        if (mapStore == null) return 0L;
        long total = 0L;
        for (Map map : mapStore.listMaps()) {
            total += getCategoryXp(map != null ? map.getCategory() : null);
        }
        return total;
    }

    private long getCachedTotalXp(MapStore mapStore) {
        long cached = cachedTotalXp;
        if (cached >= 0L) return cached;
        cached = getTotalPossibleXp(mapStore);
        cachedTotalXp = cached;
        return cached;
    }

    public void invalidateTotalXpCache() {
        cachedTotalXp = -1L;
    }

    private long getPlayerCompletionXp(UUID playerId, MapStore mapStore) {
        PlayerProgress playerProgress = progress.get(playerId);
        if (playerProgress == null) return 0L;
        long total = 0L;
        for (String mapId : playerProgress.completedMaps) {
            Map map = mapStore.getMap(mapId);
            if (map == null) continue;
            total += getCategoryXp(map.getCategory());
        }
        return total;
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
        lastKnownNames.put(playerId, trimmedName);
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
        if (!saveQueued.compareAndSet(false, true)) return;
        ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                syncSave();
            } finally {
                saveQueued.set(false);
                saveFuture.set(null);
            }
        }, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        saveFuture.set(future);
    }

    private void syncSave() {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        Set<UUID> toSave = Set.copyOf(dirtyPlayers);
        dirtyPlayers.clear();

        if (toSave.isEmpty()) return;

        String sql = """
            INSERT INTO players (uuid, name, xp, level, welcome_shown, playtime_ms)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name), xp = VALUES(xp), level = VALUES(level),
                welcome_shown = VALUES(welcome_shown), playtime_ms = VALUES(playtime_ms)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (UUID playerId : toSave) {
                PlayerProgress playerProgress = progress.get(playerId);
                if (playerProgress == null) continue;

                stmt.setString(1, playerId.toString());
                stmt.setString(2, lastKnownNames.get(playerId));
                stmt.setLong(3, playerProgress.xp);
                stmt.setInt(4, playerProgress.level);
                stmt.setBoolean(5, playerProgress.welcomeShown);
                stmt.setLong(6, playerProgress.playtimeMs);
                stmt.addBatch();
            }
            stmt.executeBatch();

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save players to database: " + e.getMessage());
            // Re-add to dirty set for retry
            dirtyPlayers.addAll(toSave);
        }
    }

    private void invalidateLeaderboardCache(String mapId) {
        if (mapId != null) {
            leaderboardCache.remove(mapId);
        }
    }

    private LeaderboardCache buildLeaderboardCache(String mapId) {
        if (mapId == null) {
            return new LeaderboardCache(List.of(), java.util.Map.of(), null);
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
        Long worldRecordMs = entries.isEmpty() ? null : entries.get(0).getValue();
        long lastTime = Long.MIN_VALUE;
        int rank = 0;
        for (int i = 0; i < entries.size(); i++) {
            long time = entries.get(i).getValue();
            if (i == 0 || time > lastTime) {
                rank = i + 1;
                lastTime = time;
            }
            positions.put(entries.get(i).getKey(), rank);
        }
        return new LeaderboardCache(List.copyOf(entries), java.util.Map.copyOf(positions), worldRecordMs);
    }

    private static class PlayerProgress {
        final Set<String> completedMaps = ConcurrentHashMap.newKeySet();
        final java.util.Map<String, Long> bestMapTimes = new ConcurrentHashMap<>();
        long xp;
        int level = 1;
        boolean welcomeShown;
        long playtimeMs;
    }

    private static class LeaderboardCache {
        final List<java.util.Map.Entry<UUID, Long>> entries;
        final java.util.Map<UUID, Integer> positions;
        final Long worldRecordMs;

        LeaderboardCache(List<java.util.Map.Entry<UUID, Long>> entries, java.util.Map<UUID, Integer> positions,
                         Long worldRecordMs) {
            this.entries = entries;
            this.positions = positions;
            this.worldRecordMs = worldRecordMs;
        }
    }

    public static class ProgressionResult {
        public final boolean firstCompletion;
        public final boolean newBest;
        public final boolean personalBest;
        public final long xpAwarded;
        public final int oldLevel;
        public final int newLevel;

        public ProgressionResult(boolean firstCompletion, boolean newBest, boolean personalBest, long xpAwarded,
                                 int oldLevel, int newLevel) {
            this.firstCompletion = firstCompletion;
            this.newBest = newBest;
            this.personalBest = personalBest;
            this.xpAwarded = xpAwarded;
            this.oldLevel = oldLevel;
            this.newLevel = newLevel;
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
