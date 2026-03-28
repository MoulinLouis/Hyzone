package io.hyvexa.ascend.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.ascend.data.GameplayState.MapProgress;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Database-backed leaderboard queries with TTL caching for Ascend.
 * Package-private — accessed only through {@link AscendPlayerPersistence}.
 */
class AscendLeaderboardQueries {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long LEADERBOARD_CACHE_TTL_MS = 30_000;

    private final ConnectionProvider db;
    private final Map<UUID, AscendPlayerProgress> players;
    private final Map<UUID, String> playerNames;

    private volatile List<AscendPlayerStore.LeaderboardEntry> leaderboardCache = List.of();
    private volatile long leaderboardCacheTimestamp = 0;

    private final Map<String, List<AscendPlayerStore.MapLeaderboardEntry>> mapLeaderboardCache = new ConcurrentHashMap<>();
    private final Map<String, Long> mapLeaderboardCacheTimestamps = new ConcurrentHashMap<>();

    AscendLeaderboardQueries(ConnectionProvider db,
                             Map<UUID, AscendPlayerProgress> players,
                             Map<UUID, String> playerNames) {
        this.db = db;
        this.players = players;
        this.playerNames = playerNames;
    }

    // ========================================
    // Global Leaderboard
    // ========================================

    List<AscendPlayerStore.LeaderboardEntry> getLeaderboardEntries() {
        long now = System.currentTimeMillis();
        if (now - leaderboardCacheTimestamp > LEADERBOARD_CACHE_TTL_MS) {
            List<AscendPlayerStore.LeaderboardEntry> dbEntries = fetchLeaderboardFromDatabase();
            if (dbEntries != null) {
                leaderboardCache = dbEntries;
                leaderboardCacheTimestamp = now;
            }
        }

        // Merge online players' fresh data on top of the DB snapshot
        Map<UUID, AscendPlayerStore.LeaderboardEntry> merged = new LinkedHashMap<>();
        for (AscendPlayerStore.LeaderboardEntry entry : leaderboardCache) {
            // Enrich null names from in-memory cache (survives disconnect)
            if (entry.playerName() == null || entry.playerName().isEmpty()) {
                String cachedName = playerNames.get(entry.playerId());
                if (cachedName != null) {
                    entry = new AscendPlayerStore.LeaderboardEntry(entry.playerId(), cachedName,
                            entry.totalVoltEarnedMantissa(), entry.totalVoltEarnedExp10(),
                            entry.ascensionCount(), entry.totalManualRuns(), entry.fastestAscensionMs());
                }
            }
            merged.put(entry.playerId(), entry);
        }
        for (Map.Entry<UUID, AscendPlayerProgress> e : players.entrySet()) {
            UUID id = e.getKey();
            AscendPlayerProgress p = e.getValue();
            String name = playerNames.get(id);
            merged.put(id, new AscendPlayerStore.LeaderboardEntry(
                id, name,
                p.economy().getTotalVoltEarned().getMantissa(), p.economy().getTotalVoltEarned().getExponent(),
                p.gameplay().getAscensionCount(), p.gameplay().getTotalManualRuns(), p.gameplay().getFastestAscensionMs()
            ));
        }
        return new ArrayList<>(merged.values());
    }

    private List<AscendPlayerStore.LeaderboardEntry> fetchLeaderboardFromDatabase() {
        if (!this.db.isInitialized()) {
            return List.of();
        }

        String sql = """
            SELECT uuid, player_name, total_volt_earned_mantissa, total_volt_earned_exp10,
                   ascension_count, total_manual_runs, fastest_ascension_ms
            FROM ascend_players
            WHERE total_volt_earned_exp10 > 0 OR total_volt_earned_mantissa > 0
               OR ascension_count > 0 OR total_manual_runs > 0 OR fastest_ascension_ms IS NOT NULL
            ORDER BY total_volt_earned_exp10 DESC, total_volt_earned_mantissa DESC
            LIMIT 200
            """;

        List<AscendPlayerStore.LeaderboardEntry> entries = new ArrayList<>();
        try (Connection conn = this.db.getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return null;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID playerId = UUID.fromString(rs.getString("uuid"));
                        String name = rs.getString("player_name");
                        double mantissa = rs.getDouble("total_volt_earned_mantissa");
                        int exp10 = rs.getInt("total_volt_earned_exp10");
                        int ascensions = rs.getInt("ascension_count");
                        int manualRuns = rs.getInt("total_manual_runs");
                        long fastest = rs.getLong("fastest_ascension_ms");
                        Long fastestMs = rs.wasNull() ? null : fastest;

                        entries.add(new AscendPlayerStore.LeaderboardEntry(playerId, name, mantissa, exp10, ascensions, manualRuns, fastestMs));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to fetch leaderboard from database: " + e.getMessage());
            return null;
        }
        return entries;
    }

    void invalidateLeaderboardCache() {
        leaderboardCacheTimestamp = 0;
    }

    void invalidateMapLeaderboardCache(String mapId) {
        if (mapId != null) {
            mapLeaderboardCache.remove(mapId);
            mapLeaderboardCacheTimestamps.remove(mapId);
        }
    }

    /**
     * Clear all leaderboard caches (used during full reset).
     */
    void clearLeaderboardCaches() {
        leaderboardCache = List.of();
        leaderboardCacheTimestamp = 0;
        mapLeaderboardCache.clear();
        mapLeaderboardCacheTimestamps.clear();
    }

    // ========================================
    // Per-Map Leaderboard
    // ========================================

    List<AscendPlayerStore.MapLeaderboardEntry> getMapLeaderboard(String mapId) {
        if (mapId == null) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        Long cacheTs = mapLeaderboardCacheTimestamps.get(mapId);
        List<AscendPlayerStore.MapLeaderboardEntry> cached = mapLeaderboardCache.get(mapId);

        if (cacheTs == null || cached == null || (now - cacheTs) > LEADERBOARD_CACHE_TTL_MS) {
            List<AscendPlayerStore.MapLeaderboardEntry> dbEntries = fetchMapLeaderboardFromDatabase(mapId);
            if (dbEntries != null) {
                cached = dbEntries;
                mapLeaderboardCache.put(mapId, cached);
                mapLeaderboardCacheTimestamps.put(mapId, now);
            } else if (cached == null) {
                cached = List.of();
            }
        }

        // Merge online players' best times on top of the DB snapshot
        Map<String, AscendPlayerStore.MapLeaderboardEntry> merged = new LinkedHashMap<>();
        for (AscendPlayerStore.MapLeaderboardEntry entry : cached) {
            merged.put(buildMapLeaderboardMergeKey(entry.playerId(), entry.playerName()), entry);
        }
        for (Map.Entry<UUID, AscendPlayerProgress> e : players.entrySet()) {
            UUID id = e.getKey();
            AscendPlayerProgress p = e.getValue();
            MapProgress mapProgress = p.gameplay().getMapProgress().get(mapId);
            if (mapProgress == null || mapProgress.getBestTimeMs() == null) {
                continue;
            }
            String name = playerNames.get(id);
            if (name == null) {
                name = id.toString().substring(0, 8) + "...";
            }
            String key = buildMapLeaderboardMergeKey(id, name);
            AscendPlayerStore.MapLeaderboardEntry existing = merged.get(key);
            if (existing == null || mapProgress.getBestTimeMs() < existing.bestTimeMs()) {
                merged.put(key, new AscendPlayerStore.MapLeaderboardEntry(id, name, mapProgress.getBestTimeMs()));
            }
        }

        List<AscendPlayerStore.MapLeaderboardEntry> result = new ArrayList<>(merged.values());
        result.sort((a, b) -> Long.compare(a.bestTimeMs(), b.bestTimeMs()));
        return result;
    }

    private List<AscendPlayerStore.MapLeaderboardEntry> fetchMapLeaderboardFromDatabase(String mapId) {
        if (!this.db.isInitialized()) {
            return List.of();
        }

        String sql = """
            SELECT p.uuid AS player_uuid, p.player_name, m.best_time_ms
            FROM ascend_player_maps m
            JOIN ascend_players p ON p.uuid = m.player_uuid
            WHERE m.map_id = ? AND m.best_time_ms IS NOT NULL
            ORDER BY m.best_time_ms ASC
            LIMIT 200
            """;

        List<AscendPlayerStore.MapLeaderboardEntry> entries = new ArrayList<>();
        try (Connection conn = this.db.getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return null;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, mapId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID playerId = null;
                        try {
                            String playerIdRaw = rs.getString("player_uuid");
                            if (playerIdRaw != null && !playerIdRaw.isBlank()) {
                                playerId = UUID.fromString(playerIdRaw);
                            }
                        } catch (IllegalArgumentException ignored) {
                            // Keep null playerId and continue with name fallback.
                        }
                        String name = rs.getString("player_name");
                        long bestTimeMs = rs.getLong("best_time_ms");
                        // Enrich null names from in-memory cache
                        if (name == null || name.isEmpty()) {
                            if (playerId != null) {
                                name = playerNames.get(playerId);
                            }
                        }
                        entries.add(new AscendPlayerStore.MapLeaderboardEntry(playerId, name, bestTimeMs));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atSevere().log("Failed to fetch map leaderboard for " + mapId + ": " + e.getMessage());
            return null;
        }
        return entries;
    }

    private static String buildMapLeaderboardMergeKey(UUID playerId, String playerName) {
        if (playerId != null) {
            return playerId.toString();
        }
        if (playerName == null) {
            return "";
        }
        return playerName.toLowerCase(Locale.ROOT);
    }
}
