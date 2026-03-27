package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks which medals each player has earned per map.
 * Lazy-loads per player, evicts on disconnect.
 */
public class MedalStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MEDAL_COUNT = Medal.values().length;

    private final ConnectionProvider connectionProvider;
    // player -> mapId -> set of earned medal names
    private final ConcurrentHashMap<UUID, java.util.Map<String, Set<Medal>>> cache = new ConcurrentHashMap<>();
    private final AtomicReference<List<MedalScoreEntry>> leaderboardSnapshot = new AtomicReference<>(List.of());

    public MedalStore(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void initialize() {
        if (!connectionProvider.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, MedalStore will use in-memory mode");
            return;
        }
        String createSql = "CREATE TABLE IF NOT EXISTS player_medals ("
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "map_id VARCHAR(64) NOT NULL, "
                + "medal VARCHAR(6) NOT NULL, "
                + "earned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (player_uuid, map_id, medal)"
                + ") ENGINE=InnoDB";
        if (!DatabaseManager.execute(connectionProvider, createSql)) {
            return;
        }
        // Widen medal column to fit EMERALD/INSANE before any data migrations
        DatabaseManager.execute(connectionProvider,
                "ALTER TABLE player_medals MODIFY COLUMN medal VARCHAR(8) NOT NULL");
        // Migrate old AUTHOR/PLATINUM medal values to EMERALD
        int migrated = DatabaseManager.executeCount(connectionProvider,
                "UPDATE player_medals SET medal = 'EMERALD' WHERE medal IN ('AUTHOR', 'PLATINUM', 'PLATIN', 'EMERAL')",
                stmt -> {});
        if (migrated > 0) {
            LOGGER.atInfo().log("Migrated " + migrated + " AUTHOR/PLATINUM medals to EMERALD");
        }
        LOGGER.atInfo().log("MedalStore initialized (player_medals table ensured)");
        loadLeaderboardSnapshot();
    }

    public Set<Medal> getEarnedMedals(UUID playerId, String mapId) {
        if (playerId == null || mapId == null) {
            return Set.of();
        }
        java.util.Map<String, Set<Medal>> playerMedals = ensurePlayerLoaded(playerId);
        Set<Medal> medals = playerMedals.get(mapId);
        return medals != null ? Collections.unmodifiableSet(medals) : Set.of();
    }

    public boolean hasEarnedMedal(UUID playerId, String mapId, Medal medal) {
        if (playerId == null || mapId == null || medal == null) {
            return false;
        }
        return getEarnedMedals(playerId, mapId).contains(medal);
    }

    public void awardMedal(UUID playerId, String mapId, Medal medal) {
        if (playerId == null || mapId == null || medal == null) {
            return;
        }
        java.util.Map<String, Set<Medal>> playerMedals = ensurePlayerLoaded(playerId);
        playerMedals.computeIfAbsent(mapId, k -> EnumSet.noneOf(Medal.class)).add(medal);
        persistMedal(playerId, mapId, medal);
        refreshLeaderboardEntry(playerId);
    }

    public void evictPlayer(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    private java.util.Map<String, Set<Medal>> ensurePlayerLoaded(UUID playerId) {
        return cache.computeIfAbsent(playerId, this::loadFromDatabase);
    }

    private java.util.Map<String, Set<Medal>> loadFromDatabase(UUID playerId) {
        java.util.Map<String, Set<Medal>> result = new ConcurrentHashMap<>();
        if (!connectionProvider.isInitialized()) {
            return result;
        }
        String sql = "SELECT map_id, medal FROM player_medals WHERE player_uuid = ?";
        List<java.util.Map.Entry<String, Medal>> rows = DatabaseManager.queryList(connectionProvider, sql,
                stmt -> stmt.setString(1, playerId.toString()),
                rs -> {
                    String mapId = rs.getString("map_id");
                    String medalStr = rs.getString("medal");
                    try {
                        return java.util.Map.entry(mapId, Medal.valueOf(medalStr));
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                });
        for (var entry : rows) {
            if (entry != null) {
                result.computeIfAbsent(entry.getKey(), k -> EnumSet.noneOf(Medal.class)).add(entry.getValue());
            }
        }
        return result;
    }

    private void persistMedal(UUID playerId, String mapId, Medal medal) {
        if (!connectionProvider.isInitialized()) {
            return;
        }
        String sql = "INSERT IGNORE INTO player_medals (player_uuid, map_id, medal) VALUES (?, ?, ?)";
        DatabaseManager.execute(connectionProvider, sql, stmt -> {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, mapId);
            stmt.setString(3, medal.name());
        });
    }

    public List<MedalScoreEntry> getLeaderboardSnapshot() {
        return leaderboardSnapshot.get();
    }

    private void loadLeaderboardSnapshot() {
        if (!connectionProvider.isInitialized()) {
            return;
        }
        String sql = "SELECT player_uuid, medal, COUNT(*) as cnt FROM player_medals GROUP BY player_uuid, medal";
        java.util.Map<UUID, int[]> aggregated = new HashMap<>();
        List<Object[]> rows = DatabaseManager.queryList(connectionProvider, sql, rs -> {
            try {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                String medalStr = rs.getString("medal");
                int cnt = rs.getInt("cnt");
                Medal medal = Medal.valueOf(medalStr);
                return new Object[]{playerId, medal, cnt};
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        });
        for (Object[] row : rows) {
            if (row != null) {
                UUID playerId = (UUID) row[0];
                Medal medal = (Medal) row[1];
                int cnt = (int) row[2];
                int[] counts = aggregated.computeIfAbsent(playerId, k -> new int[MEDAL_COUNT]);
                counts[medal.ordinal()] = cnt;
            }
        }
        List<MedalScoreEntry> entries = new ArrayList<>(aggregated.size());
        for (java.util.Map.Entry<UUID, int[]> e : aggregated.entrySet()) {
            int[] c = e.getValue();
            entries.add(new MedalScoreEntry(e.getKey(), c[0], c[1], c[2], c[3], c[4]));
        }
        entries.sort(Comparator.comparingInt(MedalScoreEntry::getTotalScore).reversed());
        leaderboardSnapshot.set(List.copyOf(entries));
        LOGGER.atInfo().log("Leaderboard snapshot loaded: " + entries.size() + " players");
    }

    private void refreshLeaderboardEntry(UUID playerId) {
        java.util.Map<String, Set<Medal>> playerMedals = cache.get(playerId);
        int[] counts = new int[MEDAL_COUNT];
        if (playerMedals != null) {
            for (Set<Medal> medals : playerMedals.values()) {
                for (Medal m : medals) {
                    counts[m.ordinal()]++;
                }
            }
        }
        MedalScoreEntry updated = new MedalScoreEntry(playerId, counts[0], counts[1], counts[2], counts[3], counts[4]);
        List<MedalScoreEntry> current = new ArrayList<>(leaderboardSnapshot.get());
        current.removeIf(e -> e.getPlayerId().equals(playerId));
        if (updated.getTotalScore() > 0) {
            current.add(updated);
        }
        current.sort(Comparator.comparingInt(MedalScoreEntry::getTotalScore).reversed());
        leaderboardSnapshot.set(List.copyOf(current));
    }

    public static class MedalScoreEntry {
        private final UUID playerId;
        private final int bronzeCount;
        private final int silverCount;
        private final int goldCount;
        private final int emeraldCount;
        private final int insaneCount;
        private final int totalScore;

        public MedalScoreEntry(UUID playerId, int bronzeCount, int silverCount, int goldCount,
                               int emeraldCount, int insaneCount) {
            this.playerId = playerId;
            this.bronzeCount = bronzeCount;
            this.silverCount = silverCount;
            this.goldCount = goldCount;
            this.emeraldCount = emeraldCount;
            this.insaneCount = insaneCount;
            this.totalScore = bronzeCount * Medal.BRONZE.getPoints()
                    + silverCount * Medal.SILVER.getPoints()
                    + goldCount * Medal.GOLD.getPoints()
                    + emeraldCount * Medal.EMERALD.getPoints()
                    + insaneCount * Medal.INSANE.getPoints();
        }

        public UUID getPlayerId() { return playerId; }
        public int getBronzeCount() { return bronzeCount; }
        public int getSilverCount() { return silverCount; }
        public int getGoldCount() { return goldCount; }
        public int getEmeraldCount() { return emeraldCount; }
        public int getInsaneCount() { return insaneCount; }
        public int getTotalScore() { return totalScore; }
    }
}
