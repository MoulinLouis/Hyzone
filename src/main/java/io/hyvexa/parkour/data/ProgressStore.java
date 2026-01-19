package io.hyvexa.parkour.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.util.io.BlockingDiskFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.parkour.ParkourConstants;

import java.util.ArrayList;
import java.util.Comparator;
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
import java.util.Locale;

public class ProgressStore extends BlockingDiskFile {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long SAVE_DEBOUNCE_MS = 5000L;
    private final Map<UUID, PlayerProgress> progress = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKnownNames = new ConcurrentHashMap<>();
    private final Map<String, LeaderboardCache> leaderboardCache = new ConcurrentHashMap<>();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> saveFuture = new AtomicReference<>();
    private static final int MAX_PLAYER_NAME_LENGTH = 32;
    private volatile long cachedTotalXp = -1L;

    public ProgressStore() {
        super(Path.of("Parkour/Progress.json"));
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
        this.fileLock.writeLock().lock();
        try {
            PlayerProgress playerProgress = progress.computeIfAbsent(playerId, ignored -> new PlayerProgress());
            storePlayerName(playerId, playerName);
            playerProgress.welcomeShown = true;
        } finally {
            this.fileLock.writeLock().unlock();
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
        this.fileLock.writeLock().lock();
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

            Set<String> unlockedTitles = ConcurrentHashMap.newKeySet();
            if (playerProgress.level >= ParkourConstants.RANK_TITLE_NOVICE_LEVEL) {
                addTitle(playerProgress, ParkourConstants.TITLE_NOVICE, unlockedTitles);
            }
            if (playerProgress.level >= ParkourConstants.RANK_TITLE_PRO_LEVEL) {
                addTitle(playerProgress, ParkourConstants.TITLE_PRO, unlockedTitles);
            }
            if (playerProgress.level >= ParkourConstants.RANK_TITLE_MASTER_LEVEL) {
                addTitle(playerProgress, ParkourConstants.TITLE_MASTER, unlockedTitles);
            }
            result = new ProgressionResult(firstCompletionForMap, newBest, personalBest, xpAwarded,
                    oldLevel, playerProgress.level, unlockedTitles);
        } finally {
            this.fileLock.writeLock().unlock();
        }
        if (newBest) {
            invalidateLeaderboardCache(mapId);
        }
        queueSave();
        return result;
    }

    public Map<UUID, Integer> getMapCompletionCounts() {
        Map<UUID, Integer> counts = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, PlayerProgress> entry : progress.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().completedMaps.size());
        }
        return counts;
    }

    public Map<UUID, Long> getBestTimesForMap(String mapId) {
        if (mapId == null) {
            return Map.of();
        }
        LeaderboardCache cache = leaderboardCache.computeIfAbsent(mapId, this::buildLeaderboardCache);
        Map<UUID, Long> times = new HashMap<>(cache.entries.size());
        for (Map.Entry<UUID, Long> entry : cache.entries) {
            times.put(entry.getKey(), entry.getValue());
        }
        return times;
    }

    public List<Map.Entry<UUID, Long>> getLeaderboardEntries(String mapId) {
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

    @Override
    protected void read(BufferedReader bufferedReader) throws IOException {
        progress.clear();
        lastKnownNames.clear();
        leaderboardCache.clear();
        JsonElement parsed = JsonParser.parseReader(bufferedReader);
        if (!parsed.isJsonArray()) {
            return;
        }
        for (JsonElement element : parsed.getAsJsonArray()) {
            try {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                if (!object.has("uuid")) {
                    continue;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(object.get("uuid").getAsString());
                } catch (IllegalArgumentException e) {
                    LOGGER.at(Level.WARNING).log("Skipping invalid UUID in progress data: " + e.getMessage());
                    continue;
                }
                PlayerProgress playerProgress = new PlayerProgress();
                JsonArray completedArray = null;
                if (object.has("completedMaps") && object.get("completedMaps").isJsonArray()) {
                    completedArray = object.getAsJsonArray("completedMaps");
                } else if (object.has("completedCourses") && object.get("completedCourses").isJsonArray()) {
                    completedArray = object.getAsJsonArray("completedCourses");
                }
                if (completedArray != null) {
                    for (JsonElement completed : completedArray) {
                        if (completed.isJsonPrimitive()) {
                            playerProgress.completedMaps.add(completed.getAsString());
                        }
                    }
                }
                if (object.has("bestTimes") && object.get("bestTimes").isJsonObject()) {
                    JsonObject bestTimes = object.getAsJsonObject("bestTimes");
                    for (Map.Entry<String, JsonElement> entry : bestTimes.entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) {
                            playerProgress.bestMapTimes.put(entry.getKey(), entry.getValue().getAsLong());
                        }
                    }
                }
                if (object.has("name") && object.get("name").isJsonPrimitive()) {
                    storePlayerName(uuid, object.get("name").getAsString());
                }
                if (object.has("xp") && object.get("xp").isJsonPrimitive()) {
                    playerProgress.xp = object.get("xp").getAsLong();
                }
                playerProgress.level = calculateLevel(playerProgress.xp);
                if (object.has("titles") && object.get("titles").isJsonArray()) {
                    for (JsonElement title : object.getAsJsonArray("titles")) {
                        if (title.isJsonPrimitive()) {
                            playerProgress.titles.add(title.getAsString());
                        }
                    }
                }
                if (object.has("welcomeShown") && object.get("welcomeShown").isJsonPrimitive()) {
                    playerProgress.welcomeShown = object.get("welcomeShown").getAsBoolean();
                }
                if (object.has("playtimeMs") && object.get("playtimeMs").isJsonPrimitive()) {
                    playerProgress.playtimeMs = object.get("playtimeMs").getAsLong();
                }
                progress.put(uuid, playerProgress);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Skipping invalid entry in progress data: " + e.getMessage());
            }
        }
    }

    @Override
    protected void write(BufferedWriter bufferedWriter) throws IOException {
        JsonArray array = new JsonArray();
        for (Map.Entry<UUID, PlayerProgress> entry : progress.entrySet()) {
            JsonObject object = new JsonObject();
            object.addProperty("uuid", entry.getKey().toString());
            JsonArray completed = new JsonArray();
            for (String mapId : entry.getValue().completedMaps) {
                completed.add(mapId);
            }
            object.add("completedMaps", completed);
            JsonObject bestTimes = new JsonObject();
            for (Map.Entry<String, Long> bestEntry : entry.getValue().bestMapTimes.entrySet()) {
                bestTimes.addProperty(bestEntry.getKey(), bestEntry.getValue());
            }
            object.add("bestTimes", bestTimes);
            String playerName = lastKnownNames.get(entry.getKey());
            if (playerName != null && !playerName.isBlank()) {
                object.addProperty("name", playerName);
            }
            object.addProperty("xp", entry.getValue().xp);
            object.addProperty("level", entry.getValue().level);
            object.addProperty("welcomeShown", entry.getValue().welcomeShown);
            object.addProperty("playtimeMs", entry.getValue().playtimeMs);
            JsonArray titles = new JsonArray();
            for (String title : entry.getValue().titles) {
                titles.add(title);
            }
            object.add("titles", titles);
            array.add(object);
        }
        bufferedWriter.write(array.toString());
    }

    @Override
    protected void create(BufferedWriter bufferedWriter) throws IOException {
        bufferedWriter.write("[]");
    }

    private static class PlayerProgress {
        private final Set<String> completedMaps = ConcurrentHashMap.newKeySet();
        private final Map<String, Long> bestMapTimes = new ConcurrentHashMap<>();
        private final Set<String> titles = ConcurrentHashMap.newKeySet();
        private long xp;
        private int level = 1;
        private boolean welcomeShown;
        private long playtimeMs;
    }

    private static class LeaderboardCache {
        private final List<Map.Entry<UUID, Long>> entries;
        private final Map<UUID, Integer> positions;
        private final Long worldRecordMs;

        private LeaderboardCache(List<Map.Entry<UUID, Long>> entries, Map<UUID, Integer> positions,
                                 Long worldRecordMs) {
            this.entries = entries;
            this.positions = positions;
            this.worldRecordMs = worldRecordMs;
        }
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
        if (percent >= 90.0) {
            return 11;
        }
        if (percent >= 80.0) {
            return 10;
        }
        if (percent >= 70.0) {
            return 9;
        }
        if (percent >= 60.0) {
            return 8;
        }
        if (percent >= 50.0) {
            return 7;
        }
        if (percent >= 40.0) {
            return 6;
        }
        if (percent >= 30.0) {
            return 5;
        }
        if (percent >= 20.0) {
            return 4;
        }
        if (percent >= 10.0) {
            return 3;
        }
        return 2;
    }

    public Set<String> getTitles(UUID playerId) {
        PlayerProgress playerProgress = progress.get(playerId);
        return playerProgress != null ? Set.copyOf(playerProgress.titles) : Set.of();
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
        this.fileLock.writeLock().lock();
        boolean removed;
        try {
            removed = progress.remove(playerId) != null;
            lastKnownNames.remove(playerId);
        } finally {
            this.fileLock.writeLock().unlock();
        }
        if (removed) {
            leaderboardCache.clear();
            queueSave();
            // Invalidate rank cache so HUD updates immediately
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin != null) {
                plugin.invalidateRankCache(playerId);
            }
        }
        return removed;
    }

    public MapPurgeResult purgeMapProgress(String mapId, long xpReduction) {
        if (mapId == null || mapId.isBlank()) {
            return new MapPurgeResult(0, 0L);
        }
        String trimmedId = mapId.trim();
        long reduction = Math.max(0L, xpReduction);
        List<UUID> affectedPlayers = new ArrayList<>();
        long totalXpRemoved = 0L;
        this.fileLock.writeLock().lock();
        try {
            for (Map.Entry<UUID, PlayerProgress> entry : progress.entrySet()) {
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
                }
            }
        } finally {
            this.fileLock.writeLock().unlock();
        }
        if (!affectedPlayers.isEmpty()) {
            invalidateLeaderboardCache(trimmedId);
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

    public void addPlaytime(UUID playerId, String playerName, long deltaMs) {
        if (playerId == null || deltaMs <= 0L) {
            return;
        }
        this.fileLock.writeLock().lock();
        try {
            PlayerProgress playerProgress = progress.computeIfAbsent(playerId, ignored -> new PlayerProgress());
            storePlayerName(playerId, playerName);
            playerProgress.playtimeMs = Math.max(0L, playerProgress.playtimeMs + deltaMs);
        } finally {
            this.fileLock.writeLock().unlock();
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
        if (rankCount <= 0) {
            return 1;
        }
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
        if (rankCount <= 0) {
            return "Unranked";
        }
        int index = Math.max(1, Math.min(level, rankCount)) - 1;
        return ParkourConstants.RANK_NAMES[index];
    }

    public static String getRankNameForXp(long xp) {
        return getRankNameForLevel(calculateLevel(xp));
    }

    private static void addTitle(PlayerProgress progress, String title, Set<String> unlockedTitles) {
        if (progress.titles.add(title)) {
            unlockedTitles.add(title);
        }
    }

    public static long getTotalPossibleXp(MapStore mapStore) {
        if (mapStore == null) {
            return 0L;
        }
        long total = 0L;
        for (io.hyvexa.parkour.data.Map map : mapStore.listMaps()) {
            total += getCategoryXp(map != null ? map.getCategory() : null);
        }
        return total;
    }

    /**
     * Returns cached total XP, computing it if not cached.
     * Use invalidateTotalXpCache() when maps change.
     */
    private long getCachedTotalXp(MapStore mapStore) {
        long cached = cachedTotalXp;
        if (cached >= 0L) {
            return cached;
        }
        cached = getTotalPossibleXp(mapStore);
        cachedTotalXp = cached;
        return cached;
    }

    /**
     * Invalidates the cached total XP. Call when maps are added/removed/edited.
     */
    public void invalidateTotalXpCache() {
        cachedTotalXp = -1L;
    }

    private long getPlayerCompletionXp(UUID playerId, MapStore mapStore) {
        PlayerProgress playerProgress = progress.get(playerId);
        if (playerProgress == null) {
            return 0L;
        }
        long total = 0L;
        for (String mapId : playerProgress.completedMaps) {
            io.hyvexa.parkour.data.Map map = mapStore.getMap(mapId);
            if (map == null) {
                continue;
            }
            total += getCategoryXp(map.getCategory());
        }
        return total;
    }

    public static long getCategoryXp(String category) {
        if (category == null || category.isBlank()) {
            return ParkourConstants.MAP_XP_EASY;
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "medium" -> ParkourConstants.MAP_XP_MEDIUM;
            case "hard" -> ParkourConstants.MAP_XP_HARD;
            case "insane" -> ParkourConstants.MAP_XP_INSANE;
            default -> ParkourConstants.MAP_XP_EASY;
        };
    }

    private void storePlayerName(UUID playerId, String playerName) {
        if (playerId == null || playerName == null || playerName.isBlank()) {
            return;
        }
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
        }, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        saveFuture.set(future);
    }

    private void invalidateLeaderboardCache(String mapId) {
        if (mapId != null) {
            leaderboardCache.remove(mapId);
        }
    }

    private LeaderboardCache buildLeaderboardCache(String mapId) {
        if (mapId == null) {
            return new LeaderboardCache(List.of(), Map.of(), null);
        }
        List<Map.Entry<UUID, Long>> entries = new ArrayList<>();
        for (Map.Entry<UUID, PlayerProgress> entry : progress.entrySet()) {
            Long best = entry.getValue().bestMapTimes.get(mapId);
            if (best != null) {
                entries.add(Map.entry(entry.getKey(), best));
            }
        }
        entries.sort(Comparator.comparingLong(Map.Entry::getValue));
        Map<UUID, Integer> positions = new HashMap<>();
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
        return new LeaderboardCache(List.copyOf(entries), Map.copyOf(positions), worldRecordMs);
    }

    public static class ProgressionResult {
        public final boolean firstCompletion;
        public final boolean newBest;
        public final boolean personalBest;
        public final long xpAwarded;
        public final int oldLevel;
        public final int newLevel;
        public final Set<String> titlesUnlocked;

        public ProgressionResult(boolean firstCompletion, boolean newBest, boolean personalBest, long xpAwarded,
                                 int oldLevel, int newLevel, Set<String> titlesUnlocked) {
            this.firstCompletion = firstCompletion;
            this.newBest = newBest;
            this.personalBest = personalBest;
            this.xpAwarded = xpAwarded;
            this.oldLevel = oldLevel;
            this.newLevel = newLevel;
            this.titlesUnlocked = titlesUnlocked;
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
