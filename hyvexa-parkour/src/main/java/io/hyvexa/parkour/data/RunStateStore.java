package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persists in-progress run state to MySQL so players can resume after disconnect or server restart.
 * No in-memory cache — state is loaded only on reconnect.
 */
public class RunStateStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final ConnectionProvider db;

    public RunStateStore(ConnectionProvider db) {
        this.db = db;
    }

    public void ensureTable() {
        if (!this.db.isInitialized()) {
            return;
        }
        try (Connection conn = this.db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS saved_run_state (
                    player_uuid         CHAR(36)    NOT NULL PRIMARY KEY,
                    map_id              VARCHAR(64) NOT NULL,
                    elapsed_ms          BIGINT      NOT NULL,
                    last_checkpoint     INT         NOT NULL DEFAULT -1,
                    touched_checkpoints TEXT        NOT NULL,
                    checkpoint_times    TEXT        NOT NULL,
                    map_updated_at      BIGINT      NOT NULL,
                    saved_at            BIGINT      NOT NULL,
                    CONSTRAINT fk_saved_run_map FOREIGN KEY (map_id) REFERENCES maps(id) ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);
        } catch (SQLException e) {
            LOGGER.atWarning().withCause(e).log("Failed to ensure saved_run_state table");
        }
    }

    public void saveAsync(UUID playerId, String mapId, long elapsedMs, int lastCheckpointIndex,
                          Set<Integer> touchedCheckpoints, java.util.Map<Integer, Long> checkpointTouchTimes,
                          long mapUpdatedAt) {
        CompletableFuture.runAsync(() ->
                saveSync(playerId, mapId, elapsedMs, lastCheckpointIndex, touchedCheckpoints,
                        checkpointTouchTimes, mapUpdatedAt),
                HytaleServer.SCHEDULED_EXECUTOR
        ).exceptionally(ex -> {
            LOGGER.atWarning().withCause(ex).log("Failed to save run state for " + playerId);
            return null;
        });
    }

    public void saveSync(UUID playerId, String mapId, long elapsedMs, int lastCheckpointIndex,
                         Set<Integer> touchedCheckpoints, java.util.Map<Integer, Long> checkpointTouchTimes,
                         long mapUpdatedAt) {
        String sql = """
            REPLACE INTO saved_run_state
                (player_uuid, map_id, elapsed_ms, last_checkpoint, touched_checkpoints,
                 checkpoint_times, map_updated_at, saved_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        DatabaseManager.execute(this.db, sql, stmt -> {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, mapId);
            stmt.setLong(3, elapsedMs);
            stmt.setInt(4, lastCheckpointIndex);
            stmt.setString(5, encodeCheckpoints(touchedCheckpoints));
            stmt.setString(6, encodeCheckpointTimes(checkpointTouchTimes));
            stmt.setLong(7, mapUpdatedAt);
            stmt.setLong(8, System.currentTimeMillis());
        });
    }

    public CompletableFuture<SavedRunState> loadAsync(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> loadSync(playerId), HytaleServer.SCHEDULED_EXECUTOR);
    }

    private SavedRunState loadSync(UUID playerId) {
        String sql = "SELECT map_id, elapsed_ms, last_checkpoint, touched_checkpoints, " +
                "checkpoint_times, map_updated_at, saved_at FROM saved_run_state WHERE player_uuid = ?";
        return DatabaseManager.queryOne(this.db, sql,
                stmt -> stmt.setString(1, playerId.toString()),
                rs -> new SavedRunState(
                        rs.getString("map_id"),
                        rs.getLong("elapsed_ms"),
                        rs.getInt("last_checkpoint"),
                        decodeCheckpoints(rs.getString("touched_checkpoints")),
                        decodeCheckpointTimes(rs.getString("checkpoint_times")),
                        rs.getLong("map_updated_at"),
                        rs.getLong("saved_at")
                ), null);
    }

    public void deleteAsync(UUID playerId) {
        CompletableFuture.runAsync(() -> deleteSync(playerId), HytaleServer.SCHEDULED_EXECUTOR)
                .exceptionally(ex -> {
                    LOGGER.atWarning().withCause(ex).log("Failed to delete run state for " + playerId);
                    return null;
                });
    }

    private void deleteSync(UUID playerId) {
        String sql = "DELETE FROM saved_run_state WHERE player_uuid = ?";
        DatabaseManager.execute(this.db, sql, stmt -> stmt.setString(1, playerId.toString()));
    }

    // --- Encoding/decoding ---

    private static String encodeCheckpoints(Set<Integer> checkpoints) {
        if (checkpoints == null || checkpoints.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int cp : checkpoints) {
            if (sb.length() > 0) sb.append(',');
            sb.append(cp);
        }
        return sb.toString();
    }

    private static Set<Integer> decodeCheckpoints(String encoded) {
        Set<Integer> result = new HashSet<>();
        if (encoded == null || encoded.isEmpty()) {
            return result;
        }
        for (String part : encoded.split(",")) {
            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static String encodeCheckpointTimes(java.util.Map<Integer, Long> times) {
        if (times == null || times.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : times.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return sb.toString();
    }

    private static java.util.Map<Integer, Long> decodeCheckpointTimes(String encoded) {
        java.util.Map<Integer, Long> result = new HashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return result;
        }
        for (String part : encoded.split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2) {
                try {
                    result.put(Integer.parseInt(kv[0].trim()), Long.parseLong(kv[1].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    // --- Saved state record ---

    public record SavedRunState(
            String mapId,
            long elapsedMs,
            int lastCheckpointIndex,
            Set<Integer> touchedCheckpoints,
            java.util.Map<Integer, Long> checkpointTouchTimes,
            long mapUpdatedAt,
            long savedAt
    ) {}
}
