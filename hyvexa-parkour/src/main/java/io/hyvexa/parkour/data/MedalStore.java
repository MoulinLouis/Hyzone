package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks which medals each player has earned per map.
 * Lazy-loads per player, evicts on disconnect.
 */
public class MedalStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final MedalStore INSTANCE = new MedalStore();

    // player -> mapId -> set of earned medal names
    private final ConcurrentHashMap<UUID, java.util.Map<String, Set<Medal>>> cache = new ConcurrentHashMap<>();
    private final AtomicReference<List<MedalScoreEntry>> leaderboardSnapshot = new AtomicReference<>(List.of());

    private MedalStore() {
    }

    public static MedalStore getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
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
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(createSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
            // Migrate old AUTHOR medal values to PLATINUM
            try (PreparedStatement migStmt = conn.prepareStatement(
                    "UPDATE player_medals SET medal = 'PLATINUM' WHERE medal = 'AUTHOR'")) {
                DatabaseManager.applyQueryTimeout(migStmt);
                int migrated = migStmt.executeUpdate();
                if (migrated > 0) {
                    LOGGER.atInfo().log("Migrated " + migrated + " AUTHOR medals to PLATINUM");
                }
            }
            LOGGER.atInfo().log("MedalStore initialized (player_medals table ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create player_medals table");
        }
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
        if (!DatabaseManager.getInstance().isInitialized()) {
            return result;
        }
        String sql = "SELECT map_id, medal FROM player_medals WHERE player_uuid = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String mapId = rs.getString("map_id");
                    String medalStr = rs.getString("medal");
                    try {
                        Medal medal = Medal.valueOf(medalStr);
                        result.computeIfAbsent(mapId, k -> EnumSet.noneOf(Medal.class)).add(medal);
                    } catch (IllegalArgumentException ignored) {
                        // Skip unknown medal values
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load medals for " + playerId);
        }
        return result;
    }

    private void persistMedal(UUID playerId, String mapId, Medal medal) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "INSERT IGNORE INTO player_medals (player_uuid, map_id, medal) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, mapId);
            stmt.setString(3, medal.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to persist medal for " + playerId + " map=" + mapId);
        }
    }

    public List<MedalScoreEntry> getLeaderboardSnapshot() {
        return leaderboardSnapshot.get();
    }

    private void loadLeaderboardSnapshot() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        String sql = "SELECT player_uuid, medal, COUNT(*) as cnt FROM player_medals GROUP BY player_uuid, medal";
        java.util.Map<UUID, int[]> aggregated = new HashMap<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                    String medalStr = rs.getString("medal");
                    int cnt = rs.getInt("cnt");
                    try {
                        Medal medal = Medal.valueOf(medalStr);
                        int[] counts = aggregated.computeIfAbsent(playerId, k -> new int[4]);
                        counts[medal.ordinal()] = cnt;
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load leaderboard snapshot");
            return;
        }
        List<MedalScoreEntry> entries = new ArrayList<>(aggregated.size());
        for (java.util.Map.Entry<UUID, int[]> e : aggregated.entrySet()) {
            int[] c = e.getValue();
            entries.add(new MedalScoreEntry(e.getKey(), c[0], c[1], c[2], c[3]));
        }
        entries.sort(Comparator.comparingInt(MedalScoreEntry::getTotalScore).reversed());
        leaderboardSnapshot.set(List.copyOf(entries));
        LOGGER.atInfo().log("Leaderboard snapshot loaded: " + entries.size() + " players");
    }

    private void refreshLeaderboardEntry(UUID playerId) {
        java.util.Map<String, Set<Medal>> playerMedals = cache.get(playerId);
        int[] counts = new int[4];
        if (playerMedals != null) {
            for (Set<Medal> medals : playerMedals.values()) {
                for (Medal m : medals) {
                    counts[m.ordinal()]++;
                }
            }
        }
        MedalScoreEntry updated = new MedalScoreEntry(playerId, counts[0], counts[1], counts[2], counts[3]);
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
        private final int platinumCount;
        private final int totalScore;

        public MedalScoreEntry(UUID playerId, int bronzeCount, int silverCount, int goldCount, int platinumCount) {
            this.playerId = playerId;
            this.bronzeCount = bronzeCount;
            this.silverCount = silverCount;
            this.goldCount = goldCount;
            this.platinumCount = platinumCount;
            this.totalScore = bronzeCount * Medal.BRONZE.getPoints()
                    + silverCount * Medal.SILVER.getPoints()
                    + goldCount * Medal.GOLD.getPoints()
                    + platinumCount * Medal.PLATINUM.getPoints();
        }

        public UUID getPlayerId() { return playerId; }
        public int getBronzeCount() { return bronzeCount; }
        public int getSilverCount() { return silverCount; }
        public int getGoldCount() { return goldCount; }
        public int getPlatinumCount() { return platinumCount; }
        public int getTotalScore() { return totalScore; }
    }
}
