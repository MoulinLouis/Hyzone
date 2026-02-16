package io.hyvexa.core.analytics;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Event-based analytics store. Logs raw events to analytics_events,
 * computes daily aggregates into analytics_daily, and tracks player timestamps.
 * Singleton shared across all modules.
 */
public class AnalyticsStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final AnalyticsStore INSTANCE = new AnalyticsStore();

    private AnalyticsStore() {
    }

    public static AnalyticsStore getInstance() {
        return INSTANCE;
    }

    public record DailyStats(LocalDate date, int dau, int newPlayers, long avgSessionMs,
                             int totalSessions, float parkourTimePct, float ascendTimePct,
                             int peakConcurrent) {}

    /**
     * Create analytics tables and add timestamp columns to players.
     * Safe to call multiple times (idempotent).
     */
    public void initialize() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, AnalyticsStore will not persist");
            return;
        }

        String eventsTable = "CREATE TABLE IF NOT EXISTS analytics_events ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "timestamp_ms BIGINT NOT NULL, "
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "event_type VARCHAR(32) NOT NULL, "
                + "data_json TEXT NULL, "
                + "INDEX idx_timestamp (timestamp_ms), "
                + "INDEX idx_player (player_uuid), "
                + "INDEX idx_type_timestamp (event_type, timestamp_ms)"
                + ") ENGINE=InnoDB";

        String dailyTable = "CREATE TABLE IF NOT EXISTS analytics_daily ("
                + "date DATE NOT NULL PRIMARY KEY, "
                + "dau INT NOT NULL, "
                + "new_players INT NOT NULL, "
                + "avg_session_ms BIGINT NOT NULL, "
                + "total_sessions INT NOT NULL, "
                + "parkour_time_pct FLOAT NOT NULL, "
                + "ascend_time_pct FLOAT NOT NULL, "
                + "peak_concurrent INT NOT NULL, "
                + "data_json TEXT NULL"
                + ") ENGINE=InnoDB";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(eventsTable)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(dailyTable)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
            // Add timestamp columns to players table (safe if already exists)
            tryAlterColumn(conn, "ALTER TABLE players ADD COLUMN first_join_ms BIGINT NULL");
            tryAlterColumn(conn, "ALTER TABLE players ADD COLUMN last_seen_ms BIGINT NULL");
            LOGGER.atInfo().log("AnalyticsStore initialized (tables ensured)");
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to create analytics tables");
        }
    }

    private void tryAlterColumn(Connection conn, String sql) {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Column already exists — expected on subsequent startups
            if (!e.getMessage().contains("Duplicate column")) {
                LOGGER.atWarning().withCause(e).log("ALTER TABLE failed: " + sql);
            }
        }
    }

    /**
     * Log an analytics event. Fire-and-forget async INSERT.
     */
    public void logEvent(UUID playerId, String eventType, String dataJson) {
        if (playerId == null || eventType == null) {
            return;
        }
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        long timestampMs = System.currentTimeMillis();
        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> insertEvent(playerId, eventType, dataJson, timestampMs));
    }

    private void insertEvent(UUID playerId, String eventType, String dataJson, long timestampMs) {
        String sql = "INSERT INTO analytics_events (timestamp_ms, player_uuid, event_type, data_json) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setLong(1, timestampMs);
            stmt.setString(2, playerId.toString());
            stmt.setString(3, eventType);
            stmt.setString(4, dataJson);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to insert analytics event: " + eventType);
        }
    }

    /**
     * Update player timestamp columns. Sets last_seen_ms always.
     * Sets first_join_ms only if isFirstJoin and not already set.
     */
    public void updatePlayerTimestamps(UUID playerId, boolean isFirstJoin) {
        if (playerId == null || !DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
            long now = System.currentTimeMillis();
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                // Always update last_seen_ms
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE players SET last_seen_ms = ? WHERE uuid = ?")) {
                    DatabaseManager.applyQueryTimeout(stmt);
                    stmt.setLong(1, now);
                    stmt.setString(2, playerId.toString());
                    stmt.executeUpdate();
                }
                // Set first_join_ms only if not already set
                if (isFirstJoin) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE players SET first_join_ms = ? WHERE uuid = ? AND first_join_ms IS NULL")) {
                        DatabaseManager.applyQueryTimeout(stmt);
                        stmt.setLong(1, now);
                        stmt.setString(2, playerId.toString());
                        stmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to update player timestamps for " + playerId);
            }
        });
    }

    /**
     * Compute daily aggregates for the given date and upsert into analytics_daily.
     */
    public void computeDailyAggregates(LocalDate date) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        long dayStartMs = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long dayEndMs = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // DAU: distinct players with player_join events
            int dau = queryIntScalar(conn,
                    "SELECT COUNT(DISTINCT player_uuid) FROM analytics_events "
                    + "WHERE event_type = 'player_join' AND timestamp_ms >= ? AND timestamp_ms < ?",
                    dayStartMs, dayEndMs);

            // New players: player_join with is_new=true
            int newPlayers = queryIntScalar(conn,
                    "SELECT COUNT(*) FROM analytics_events "
                    + "WHERE event_type = 'player_join' AND timestamp_ms >= ? AND timestamp_ms < ? "
                    + "AND data_json LIKE '%\"is_new\":true%'",
                    dayStartMs, dayEndMs);

            // Session stats from player_leave events
            long totalSessionMs = 0;
            int totalSessions = 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT data_json FROM analytics_events "
                    + "WHERE event_type = 'player_leave' AND timestamp_ms >= ? AND timestamp_ms < ?")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setLong(1, dayStartMs);
                stmt.setLong(2, dayEndMs);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String json = rs.getString("data_json");
                        long sessionMs = extractLongFromJson(json, "session_ms");
                        if (sessionMs > 0) {
                            totalSessionMs += sessionMs;
                            totalSessions++;
                        }
                    }
                }
            }
            long avgSessionMs = totalSessions > 0 ? totalSessionMs / totalSessions : 0;

            // Mode split from mode_switch events
            int parkourSwitches = queryIntScalar(conn,
                    "SELECT COUNT(*) FROM analytics_events "
                    + "WHERE event_type = 'mode_switch' AND timestamp_ms >= ? AND timestamp_ms < ? "
                    + "AND data_json LIKE '%\"to\":\"parkour\"%'",
                    dayStartMs, dayEndMs);
            int ascendSwitches = queryIntScalar(conn,
                    "SELECT COUNT(*) FROM analytics_events "
                    + "WHERE event_type = 'mode_switch' AND timestamp_ms >= ? AND timestamp_ms < ? "
                    + "AND data_json LIKE '%\"to\":\"ascend\"%'",
                    dayStartMs, dayEndMs);
            int totalSwitches = parkourSwitches + ascendSwitches;
            float parkourPct = totalSwitches > 0 ? (float) parkourSwitches / totalSwitches * 100f : 0f;
            float ascendPct = totalSwitches > 0 ? (float) ascendSwitches / totalSwitches * 100f : 0f;

            // Peak concurrent from player_count_samples
            int peakConcurrent = queryIntScalar(conn,
                    "SELECT COALESCE(MAX(count), 0) FROM player_count_samples "
                    + "WHERE timestamp_ms >= ? AND timestamp_ms < ?",
                    dayStartMs, dayEndMs);

            // Upsert into analytics_daily
            String upsert = "INSERT INTO analytics_daily "
                    + "(date, dau, new_players, avg_session_ms, total_sessions, "
                    + "parkour_time_pct, ascend_time_pct, peak_concurrent) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "dau=VALUES(dau), new_players=VALUES(new_players), "
                    + "avg_session_ms=VALUES(avg_session_ms), total_sessions=VALUES(total_sessions), "
                    + "parkour_time_pct=VALUES(parkour_time_pct), ascend_time_pct=VALUES(ascend_time_pct), "
                    + "peak_concurrent=VALUES(peak_concurrent)";
            try (PreparedStatement stmt = conn.prepareStatement(upsert)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, date.toString());
                stmt.setInt(2, dau);
                stmt.setInt(3, newPlayers);
                stmt.setLong(4, avgSessionMs);
                stmt.setInt(5, totalSessions);
                stmt.setFloat(6, parkourPct);
                stmt.setFloat(7, ascendPct);
                stmt.setInt(8, peakConcurrent);
                stmt.executeUpdate();
            }
            LOGGER.atInfo().log("Computed daily aggregates for " + date + ": DAU=" + dau
                    + ", new=" + newPlayers + ", sessions=" + totalSessions);
        } catch (SQLException e) {
            LOGGER.atSevere().withCause(e).log("Failed to compute daily aggregates for " + date);
        }
    }

    /**
     * Get recent daily stats for the last N days.
     */
    public List<DailyStats> getRecentStats(int days) {
        List<DailyStats> results = new ArrayList<>();
        if (!DatabaseManager.getInstance().isInitialized()) {
            return results;
        }
        LocalDate cutoff = LocalDate.now().minusDays(days);
        String sql = "SELECT date, dau, new_players, avg_session_ms, total_sessions, "
                + "parkour_time_pct, ascend_time_pct, peak_concurrent "
                + "FROM analytics_daily WHERE date >= ? ORDER BY date DESC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, cutoff.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new DailyStats(
                            LocalDate.parse(rs.getString("date")),
                            rs.getInt("dau"),
                            rs.getInt("new_players"),
                            rs.getLong("avg_session_ms"),
                            rs.getInt("total_sessions"),
                            rs.getFloat("parkour_time_pct"),
                            rs.getFloat("ascend_time_pct"),
                            rs.getInt("peak_concurrent")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to get recent stats");
        }
        return results;
    }

    /**
     * Calculate retention for a cohort. Players who first joined daysAgo days ago,
     * what percentage logged in again within checkDays after their first join.
     */
    public float getRetention(int daysAgo, int checkDays) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return 0f;
        }
        LocalDate cohortDate = LocalDate.now().minusDays(daysAgo);
        long cohortStartMs = cohortDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long cohortEndMs = cohortDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long checkEndMs = cohortDate.plusDays(checkDays + 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // Count players who first joined on cohort date
            int cohortSize = queryIntScalar(conn,
                    "SELECT COUNT(*) FROM players WHERE first_join_ms >= ? AND first_join_ms < ?",
                    cohortStartMs, cohortEndMs);
            if (cohortSize == 0) {
                return 0f;
            }
            // Count how many of those players have a player_join event after the check window start
            long checkStartMs = cohortDate.plusDays(checkDays).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            int retained = queryIntScalar(conn,
                    "SELECT COUNT(DISTINCT ae.player_uuid) FROM analytics_events ae "
                    + "INNER JOIN players p ON ae.player_uuid = p.uuid "
                    + "WHERE p.first_join_ms >= ? AND p.first_join_ms < ? "
                    + "AND ae.event_type = 'player_join' "
                    + "AND ae.timestamp_ms >= ? AND ae.timestamp_ms < ?",
                    cohortStartMs, cohortEndMs, checkStartMs, checkEndMs);
            return (float) retained / cohortSize * 100f;
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to compute retention");
            return 0f;
        }
    }

    /**
     * Purge analytics events older than retentionDays.
     */
    public void purgeOldEvents(int retentionDays) {
        if (!DatabaseManager.getInstance().isInitialized()) {
            return;
        }
        long cutoffMs = LocalDate.now().minusDays(retentionDays)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
            String sql = "DELETE FROM analytics_events WHERE timestamp_ms < ?";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setLong(1, cutoffMs);
                int deleted = stmt.executeUpdate();
                if (deleted > 0) {
                    LOGGER.atInfo().log("Purged " + deleted + " analytics events older than " + retentionDays + " days");
                }
            } catch (SQLException e) {
                LOGGER.atWarning().withCause(e).log("Failed to purge old analytics events");
            }
        });
    }

    /**
     * Count events of a type in the last N days.
     */
    public int countEvents(String eventType, int days) {
        if (!DatabaseManager.getInstance().isInitialized()) return 0;
        long cutoffMs = dayStartMs(days);
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            return queryIntScalar(conn,
                    "SELECT COUNT(*) FROM analytics_events WHERE event_type = ? AND timestamp_ms >= ?",
                    eventType, cutoffMs);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("countEvents failed: " + eventType);
            return 0;
        }
    }

    /**
     * Count distinct players for an event type in the last N days.
     */
    public int countDistinctPlayers(String eventType, int days) {
        if (!DatabaseManager.getInstance().isInitialized()) return 0;
        long cutoffMs = dayStartMs(days);
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            return queryIntScalar(conn,
                    "SELECT COUNT(DISTINCT player_uuid) FROM analytics_events WHERE event_type = ? AND timestamp_ms >= ?",
                    eventType, cutoffMs);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("countDistinctPlayers failed: " + eventType);
            return 0;
        }
    }

    /**
     * Count events matching a LIKE pattern in data_json.
     */
    public int countEventsWithFilter(String eventType, int days, String jsonLikePattern) {
        if (!DatabaseManager.getInstance().isInitialized()) return 0;
        long cutoffMs = dayStartMs(days);
        String sql = "SELECT COUNT(*) FROM analytics_events "
                + "WHERE event_type = ? AND timestamp_ms >= ? AND data_json LIKE ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, eventType);
            stmt.setLong(2, cutoffMs);
            stmt.setString(3, jsonLikePattern);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("countEventsWithFilter failed: " + eventType);
        }
        return 0;
    }

    /**
     * Get top values for a JSON field from events, aggregated by count.
     * Returns sorted list of (value, count) entries, limited to top N.
     */
    public List<Map.Entry<String, Integer>> getTopJsonValues(String eventType, String jsonKey, int days, int limit) {
        List<Map.Entry<String, Integer>> result = new ArrayList<>();
        if (!DatabaseManager.getInstance().isInitialized()) return result;
        long cutoffMs = dayStartMs(days);
        String sql = "SELECT data_json FROM analytics_events WHERE event_type = ? AND timestamp_ms >= ?";
        Map<String, Integer> counts = new HashMap<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, eventType);
            stmt.setLong(2, cutoffMs);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String val = extractStringFromJson(rs.getString("data_json"), jsonKey);
                    if (val != null) {
                        counts.merge(val, 1, Integer::sum);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("getTopJsonValues failed: " + eventType);
        }
        result.addAll(counts.entrySet());
        result.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        if (result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    /**
     * Sum a numeric JSON field across events of a type in the last N days.
     */
    public long sumJsonLongField(String eventType, String jsonKey, int days) {
        if (!DatabaseManager.getInstance().isInitialized()) return 0;
        long cutoffMs = dayStartMs(days);
        String sql = "SELECT data_json FROM analytics_events WHERE event_type = ? AND timestamp_ms >= ?";
        long total = 0;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, eventType);
            stmt.setLong(2, cutoffMs);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    total += extractLongFromJson(rs.getString("data_json"), jsonKey);
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("sumJsonLongField failed: " + eventType);
        }
        return total;
    }

    private long dayStartMs(int daysAgo) {
        return LocalDate.now().minusDays(daysAgo)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private int queryIntScalar(Connection conn, String sql, String strParam, long longParam) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, strParam);
            stmt.setLong(2, longParam);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private int queryIntScalar(Connection conn, String sql, long... params) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            for (int i = 0; i < params.length; i++) {
                stmt.setLong(i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Extract a long value from a simple JSON string like {"key":123}.
     */
    private long extractLongFromJson(String json, String key) {
        if (json == null) {
            return 0;
        }
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) {
            return 0;
        }
        int start = idx + search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (end == start) {
            return 0;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Extract a string value from a simple JSON string like {"key":"value"}.
     */
    private String extractStringFromJson(String json, String key) {
        if (json == null) {
            return null;
        }
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) {
            return null;
        }
        int start = idx + search.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }
}
