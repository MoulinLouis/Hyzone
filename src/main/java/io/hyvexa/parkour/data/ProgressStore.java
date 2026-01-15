package io.hyvexa.parkour.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.io.BlockingDiskFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import io.hyvexa.parkour.ParkourConstants;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.Locale;

public class ProgressStore extends BlockingDiskFile {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Map<UUID, PlayerProgress> progress = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKnownNames = new ConcurrentHashMap<>();
    private static final int MAX_PLAYER_NAME_LENGTH = 32;

    public ProgressStore() {
        super(Path.of("Parkour/Progress.json"));
    }

    public boolean isMapCompleted(UUID playerId, String mapId) {
        PlayerProgress playerProgress = progress.get(playerId);
        return playerProgress != null && playerProgress.completedMaps.contains(mapId);
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
        try {
            PlayerProgress playerProgress = progress.computeIfAbsent(playerId, ignored -> new PlayerProgress());
            storePlayerName(playerId, playerName);
            boolean firstCompletionForMap = playerProgress.completedMaps.add(mapId);
            Long best = playerProgress.bestMapTimes.get(mapId);
            boolean personalBest = best != null && timeMs < best;
            boolean newBest = best == null || timeMs < best;
            if (newBest) {
                playerProgress.bestMapTimes.put(mapId, timeMs);
            }
            long xpAwarded = 0L;
            if (firstCompletionForMap) {
                xpAwarded = Math.max(0L, firstCompletionXp);
            }
            if (personalBest) {
                xpAwarded += ParkourConstants.XP_BONUS_PERSONAL_BEST;
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
        this.syncSave();
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
        Map<UUID, Long> times = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, PlayerProgress> entry : progress.entrySet()) {
            Long best = entry.getValue().bestMapTimes.get(mapId);
            if (best != null) {
                times.put(entry.getKey(), best);
            }
        }
        return times;
    }

    @Override
    protected void read(BufferedReader bufferedReader) throws IOException {
        progress.clear();
        lastKnownNames.clear();
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
        int index = Math.min(rank, ParkourConstants.COMPLETION_RANK_NAMES.length) - 1;
        return ParkourConstants.COMPLETION_RANK_NAMES[index];
    }

    public int getCompletionRank(UUID playerId, MapStore mapStore) {
        if (mapStore == null) {
            return 1;
        }
        long totalXp = getTotalPossibleXp(mapStore);
        if (totalXp <= 0L) {
            return 1;
        }
        long playerXp = getPlayerCompletionXp(playerId, mapStore);
        if (playerXp >= totalXp) {
            return 6;
        }
        if (playerXp >= Math.round(totalXp * 0.8)) {
            return 5;
        }
        if (playerXp >= Math.round(totalXp * 0.6)) {
            return 4;
        }
        if (playerXp >= Math.round(totalXp * 0.4)) {
            return 3;
        }
        if (playerXp >= Math.round(totalXp * 0.2)) {
            return 2;
        }
        return 1;
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
            this.syncSave();
        }
        return removed;
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
}
