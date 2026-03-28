package io.hyvexa.parkour.data;

import io.hyvexa.common.util.FormatUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory leaderboard cache built from player progress data.
 * Thread-safe: cache entries are lazily built and invalidated on best-time changes.
 */
public class ParkourLeaderboardCache {

    private final Map<UUID, ProgressStore.PlayerProgress> progress;
    private final Map<UUID, String> lastKnownNames;
    private final Map<String, LeaderboardEntry> leaderboardCache = new ConcurrentHashMap<>();
    private final Map<String, Long> leaderboardVersions = new ConcurrentHashMap<>();

    ParkourLeaderboardCache(Map<UUID, ProgressStore.PlayerProgress> progress,
                            Map<UUID, String> lastKnownNames) {
        this.progress = progress;
        this.lastKnownNames = lastKnownNames;
    }

    void invalidateLeaderboardCache(String mapId) {
        if (mapId == null) {
            return;
        }
        leaderboardVersions.merge(mapId, 1L, Long::sum);
        leaderboardCache.remove(mapId);
    }

    void clearAll() {
        leaderboardCache.clear();
        leaderboardVersions.clear();
    }

    Map<UUID, Long> getBestTimesForMap(String mapId) {
        if (mapId == null) {
            return Map.of();
        }
        LeaderboardEntry cache = leaderboardCache.computeIfAbsent(mapId, this::buildLeaderboardCache);
        return new HashMap<>(cache.timesByPlayer);
    }

    List<Map.Entry<UUID, Long>> getLeaderboardEntries(String mapId) {
        if (mapId == null) {
            return List.of();
        }
        LeaderboardEntry cache = leaderboardCache.computeIfAbsent(mapId, this::buildLeaderboardCache);
        return cache.entries;
    }

    int getLeaderboardPosition(String mapId, UUID playerId) {
        if (mapId == null || playerId == null) {
            return -1;
        }
        LeaderboardEntry cache = leaderboardCache.computeIfAbsent(mapId, this::buildLeaderboardCache);
        Integer position = cache.positions.get(playerId);
        return position != null ? position : -1;
    }

    Long getWorldRecordTimeMs(String mapId) {
        if (mapId == null) {
            return null;
        }
        LeaderboardEntry cache = leaderboardCache.computeIfAbsent(mapId, this::buildLeaderboardCache);
        return cache.worldRecordMs;
    }

    LeaderboardHudSnapshot getLeaderboardHudSnapshot(String mapId, UUID playerId) {
        if (mapId == null) {
            return LeaderboardHudSnapshot.empty();
        }
        LeaderboardEntry cache = leaderboardCache.computeIfAbsent(mapId, this::buildLeaderboardCache);
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

    private LeaderboardEntry buildLeaderboardCache(String mapId) {
        if (mapId == null) {
            return LeaderboardEntry.empty();
        }
        List<Map.Entry<UUID, Long>> entries = new ArrayList<>();
        for (Map.Entry<UUID, ProgressStore.PlayerProgress> entry : progress.entrySet()) {
            Long best = entry.getValue().bestMapTimes.get(mapId);
            if (best != null) {
                entries.add(Map.entry(entry.getKey(), best));
            }
        }
        entries.sort(Comparator.comparingLong(Map.Entry::getValue));
        Map<UUID, Integer> positions = new HashMap<>();
        Map<UUID, Integer> ordinalPositions = new HashMap<>();
        Map<UUID, Long> timesByPlayer = new HashMap<>(entries.size());
        Long worldRecordMs = entries.isEmpty() ? null : entries.get(0).getValue();
        long lastTime = Long.MIN_VALUE;
        int rank = 0;
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<UUID, Long> leaderboardEntry = entries.get(i);
            long time = toDisplayedCentiseconds(leaderboardEntry.getValue());
            if (i == 0 || time > lastTime) {
                rank = i + 1;
                lastTime = time;
            }
            UUID entryPlayerId = leaderboardEntry.getKey();
            positions.put(entryPlayerId, rank);
            ordinalPositions.put(entryPlayerId, i + 1);
            timesByPlayer.put(entryPlayerId, leaderboardEntry.getValue());
        }
        long version = leaderboardVersions.getOrDefault(mapId, 0L);
        return new LeaderboardEntry(List.copyOf(entries), Map.copyOf(positions),
                Map.copyOf(ordinalPositions), Map.copyOf(timesByPlayer),
                buildTopRows(entries), worldRecordMs, version);
    }

    private static long toDisplayedCentiseconds(long durationMs) {
        return Math.round(durationMs / 10.0);
    }

    private List<LeaderboardHudRow> buildTopRows(List<Map.Entry<UUID, Long>> entries) {
        List<LeaderboardHudRow> topRows = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            if (i < entries.size()) {
                Map.Entry<UUID, Long> entry = entries.get(i);
                topRows.add(new LeaderboardHudRow(String.valueOf(i + 1), getDisplayPlayerName(entry.getKey()),
                        FormatUtils.formatDuration(entry.getValue())));
            } else {
                topRows.add(LeaderboardHudRow.empty(i + 1));
            }
        }
        return List.copyOf(topRows);
    }

    private String getDisplayPlayerName(UUID playerId) {
        String name = lastKnownNames.get(playerId);
        if (name == null || name.isBlank()) {
            return "Player";
        }
        return FormatUtils.truncate(name, 14);
    }

    // ---- Inner data classes ----

    static final class LeaderboardEntry {
        final List<Map.Entry<UUID, Long>> entries;
        final Map<UUID, Integer> positions;
        final Map<UUID, Integer> ordinalPositions;
        final Map<UUID, Long> timesByPlayer;
        final List<LeaderboardHudRow> topRows;
        final Long worldRecordMs;
        final long version;

        LeaderboardEntry(List<Map.Entry<UUID, Long>> entries, Map<UUID, Integer> positions,
                         Map<UUID, Integer> ordinalPositions, Map<UUID, Long> timesByPlayer,
                         List<LeaderboardHudRow> topRows, Long worldRecordMs, long version) {
            this.entries = entries;
            this.positions = positions;
            this.ordinalPositions = ordinalPositions;
            this.timesByPlayer = timesByPlayer;
            this.topRows = topRows;
            this.worldRecordMs = worldRecordMs;
            this.version = version;
        }

        private static LeaderboardEntry empty() {
            return new LeaderboardEntry(List.of(), Map.of(), Map.of(), Map.of(),
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

        LeaderboardHudRow(String rank, String name, String time) {
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
}
