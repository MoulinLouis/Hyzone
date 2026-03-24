package io.hyvexa.core.analytics;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Event-based analytics store. Logs raw events to analytics_events,
 * computes daily aggregates into analytics_daily, and tracks player timestamps.
 * Singleton shared across all modules.
 */
public class AnalyticsStore implements PlayerAnalytics {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final AnalyticsStore INSTANCE = new AnalyticsStore();
    private static final Gson GSON = new Gson();
    private final ConnectionProvider db;

    private AnalyticsStore() {
        this.db = DatabaseManager.getInstance();
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
        if (!this.db.isInitialized()) {
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

        try (Connection conn = this.db.getConnection()) {
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
        if (!this.db.isInitialized()) {
            return;
        }
        long timestampMs = System.currentTimeMillis();
        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> insertEvent(playerId, eventType, dataJson, timestampMs));
    }

    public void logPurchase(UUID playerId, String itemId, long amount, String currency, String source) {
        JsonObject data = new JsonObject();
        data.addProperty("amount", amount);
        if (itemId != null) {
            data.addProperty("item", itemId);
        }
        if (currency != null) {
            data.addProperty("currency", currency);
        }
        if (source != null) {
            data.addProperty("source", source);
        }
        logEvent(playerId, "purchase", GSON.toJson(data));
    }

    private void insertEvent(UUID playerId, String eventType, String dataJson, long timestampMs) {
        DatabaseManager.execute(this.db,
                "INSERT INTO analytics_events (timestamp_ms, player_uuid, event_type, data_json) "
                + "VALUES (?, ?, ?, ?)",
                stmt -> {
                    stmt.setLong(1, timestampMs);
                    stmt.setString(2, playerId.toString());
                    stmt.setString(3, eventType);
                    stmt.setString(4, dataJson);
                });
    }

    /**
     * Update player timestamp columns. Sets last_seen_ms always.
     * Sets first_join_ms only if isFirstJoin and not already set.
     */
    public void updatePlayerTimestamps(UUID playerId, boolean isFirstJoin) {
        if (playerId == null) {
            return;
        }
        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
            long now = System.currentTimeMillis();
            // Always update last_seen_ms
            DatabaseManager.execute(this.db,
                    "UPDATE players SET last_seen_ms = ? WHERE uuid = ?",
                    stmt -> {
                        stmt.setLong(1, now);
                        stmt.setString(2, playerId.toString());
                    });
            // Set first_join_ms only if not already set
            if (isFirstJoin) {
                DatabaseManager.execute(this.db,
                        "UPDATE players SET first_join_ms = ? WHERE uuid = ? AND first_join_ms IS NULL",
                        stmt -> {
                            stmt.setLong(1, now);
                            stmt.setString(2, playerId.toString());
                        });
            }
        });
    }

    /**
     * Compute daily aggregates for the given date and upsert into analytics_daily.
     */
    public void computeDailyAggregates(LocalDate date) {
        if (!this.db.isInitialized()) {
            return;
        }
        long dayStartMs = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long dayEndMs = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        try (Connection conn = this.db.getConnection()) {
            // DAU: distinct players with player_join events
            int dau = queryIntScalar(conn,
                    "SELECT COUNT(DISTINCT player_uuid) FROM analytics_events "
                    + "WHERE event_type = 'player_join' AND timestamp_ms >= ? AND timestamp_ms < ?",
                    dayStartMs, dayEndMs);

            // New players: player_join with is_new=true (aggregated in SQL via LIKE)
            int newPlayers = queryIntScalar(conn,
                    "SELECT COUNT(*) FROM analytics_events "
                    + "WHERE event_type = 'player_join' AND timestamp_ms >= ? AND timestamp_ms < ? "
                    + "AND data_json LIKE '%\"is_new\":true%'",
                    dayStartMs, dayEndMs);

            // Session stats from player_leave events (aggregated in SQL)
            long totalSessionMs = 0;
            int totalSessions = 0;
            long avgSessionMs = 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) AS cnt, COALESCE(SUM(JSON_EXTRACT(data_json, '$.session_ms')), 0) AS total_ms "
                    + "FROM analytics_events "
                    + "WHERE event_type = 'player_leave' AND timestamp_ms >= ? AND timestamp_ms < ? "
                    + "AND JSON_EXTRACT(data_json, '$.session_ms') > 0")) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setLong(1, dayStartMs);
                stmt.setLong(2, dayEndMs);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalSessions = rs.getInt("cnt");
                        totalSessionMs = rs.getLong("total_ms");
                    }
                }
            }
            if (totalSessions > 0) {
                avgSessionMs = totalSessionMs / totalSessions;
            }

            // Mode split from mode_switch events (aggregated in SQL via LIKE)
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
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(days);
        return DatabaseManager.queryList(this.db,
                "SELECT date, dau, new_players, avg_session_ms, total_sessions, "
                + "parkour_time_pct, ascend_time_pct, peak_concurrent "
                + "FROM analytics_daily WHERE date >= ? ORDER BY date DESC",
                stmt -> stmt.setString(1, cutoff.toString()),
                rs -> new DailyStats(
                        LocalDate.parse(rs.getString("date")),
                        rs.getInt("dau"),
                        rs.getInt("new_players"),
                        rs.getLong("avg_session_ms"),
                        rs.getInt("total_sessions"),
                        rs.getFloat("parkour_time_pct"),
                        rs.getFloat("ascend_time_pct"),
                        rs.getInt("peak_concurrent")));
    }

    /**
     * Calculate retention for a cohort. Players who first joined daysAgo days ago,
     * what percentage logged in again within checkDays after their first join.
     */
    public float getRetention(int daysAgo, int checkDays) {
        if (!this.db.isInitialized()) {
            return 0f;
        }
        LocalDate cohortDate = LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo);
        long cohortStartMs = cohortDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long cohortEndMs = cohortDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long checkEndMs = cohortDate.plusDays(checkDays + 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        try (Connection conn = this.db.getConnection()) {
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
        if (!this.db.isInitialized()) {
            return;
        }
        long cutoffMs = LocalDate.now(ZoneOffset.UTC).minusDays(retentionDays)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
            int deleted = DatabaseManager.executeCount(this.db,
                    "DELETE FROM analytics_events WHERE timestamp_ms < ?",
                    stmt -> stmt.setLong(1, cutoffMs));
            if (deleted > 0) {
                LOGGER.atInfo().log("Purged " + deleted + " analytics events older than " + retentionDays + " days");
            }
        });
    }

    /**
     * Count events of a type in the last N days.
     */
    public int countEvents(String eventType, int days) {
        long cutoffMs = dayStartMs(days);
        return DatabaseManager.queryOne(this.db,
                "SELECT COUNT(*) FROM analytics_events WHERE event_type = ? AND timestamp_ms >= ?",
                stmt -> {
                    stmt.setString(1, eventType);
                    stmt.setLong(2, cutoffMs);
                },
                rs -> rs.getInt(1),
                0);
    }

    /**
     * Count distinct players for an event type in the last N days.
     */
    public int countDistinctPlayers(String eventType, int days) {
        long cutoffMs = dayStartMs(days);
        return DatabaseManager.queryOne(this.db,
                "SELECT COUNT(DISTINCT player_uuid) FROM analytics_events WHERE event_type = ? AND timestamp_ms >= ?",
                stmt -> {
                    stmt.setString(1, eventType);
                    stmt.setLong(2, cutoffMs);
                },
                rs -> rs.getInt(1),
                0);
    }

    /**
     * Count events matching a LIKE pattern in data_json.
     */
    public int countEventsWithFilter(String eventType, int days, String jsonLikePattern) {
        long cutoffMs = dayStartMs(days);
        return DatabaseManager.queryOne(this.db,
                "SELECT COUNT(*) FROM analytics_events "
                + "WHERE event_type = ? AND timestamp_ms >= ? AND data_json LIKE ?",
                stmt -> {
                    stmt.setString(1, eventType);
                    stmt.setLong(2, cutoffMs);
                    stmt.setString(3, jsonLikePattern);
                },
                rs -> rs.getInt(1),
                0);
    }

    /**
     * Get top values for a JSON field from events, aggregated by count.
     * Returns sorted list of (value, count) entries, limited to top N.
     */
    public List<Map.Entry<String, Integer>> getTopJsonValues(String eventType, String jsonKey, int days, int limit) {
        long cutoffMs = dayStartMs(days);
        String jsonPath = "$." + jsonKey;
        List<Map.Entry<String, Integer>> results = DatabaseManager.queryList(this.db,
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(data_json, ?)) AS val, COUNT(*) AS cnt "
                + "FROM analytics_events "
                + "WHERE event_type = ? AND timestamp_ms >= ? "
                + "AND JSON_EXTRACT(data_json, ?) IS NOT NULL "
                + "GROUP BY val ORDER BY cnt DESC LIMIT ?",
                stmt -> {
                    stmt.setString(1, jsonPath);
                    stmt.setString(2, eventType);
                    stmt.setLong(3, cutoffMs);
                    stmt.setString(4, jsonPath);
                    stmt.setInt(5, limit);
                },
                rs -> Map.entry(rs.getString("val"), rs.getInt("cnt")));
        // The SQL already filters NULL via IS NOT NULL, but guard against DB nulls
        results.removeIf(e -> e.getKey() == null);
        return results;
    }

    /**
     * Sum a numeric JSON field across events of a type in the last N days.
     */
    public long sumJsonLongField(String eventType, String jsonKey, int days) {
        long cutoffMs = dayStartMs(days);
        String jsonPath = "$." + jsonKey;
        return DatabaseManager.queryOne(this.db,
                "SELECT COALESCE(SUM(JSON_EXTRACT(data_json, ?)), 0) AS total "
                + "FROM analytics_events WHERE event_type = ? AND timestamp_ms >= ?",
                stmt -> {
                    stmt.setString(1, jsonPath);
                    stmt.setString(2, eventType);
                    stmt.setLong(3, cutoffMs);
                },
                rs -> rs.getLong("total"),
                0L);
    }

    private long dayStartMs(int daysAgo) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo)
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

}
