package io.hyvexa.common.ghost;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * MySQL + in-memory cache for ghost recordings using GhostSample/GhostRecording types.
 * Configured via constructor params for table name and mode label.
 */
public class GhostStore {

    public static final int MAX_SAMPLES = 12000;

    private final HytaleLogger logger;
    private final String tableName;
    private final String modeLabel;
    private final ConnectionProvider db;
    private final Map<String, GhostRecording> cache = new ConcurrentHashMap<>();

    public GhostStore(String tableName, String modeLabel, ConnectionProvider db) {
        this.logger = HytaleLogger.forEnclosingClass();
        this.tableName = tableName;
        this.modeLabel = modeLabel;
        this.db = db;
        validateTableName();
    }

    private void validateTableName() {
        if (tableName == null || !tableName.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalStateException("Invalid table name: " + tableName);
        }
    }

    private record GhostRow(String playerUuid, String mapId, byte[] blob, long completionTimeMs) {}

    public void syncLoad() {
        if (!this.db.isInitialized()) {
            logger.atWarning().log("Database not initialized, " + modeLabel + " GhostStore will be empty");
            return;
        }

        ensureGhostTableExists();
        cache.clear();

        List<GhostRow> rows = DatabaseManager.queryList(this.db,
                "SELECT player_uuid, map_id, recording_blob, completion_time_ms FROM " + tableName,
                rs -> new GhostRow(
                        rs.getString("player_uuid"),
                        rs.getString("map_id"),
                        rs.getBytes("recording_blob"),
                        rs.getLong("completion_time_ms")));

        for (GhostRow row : rows) {
            try {
                GhostRecording recording = deserialize(row.blob, row.completionTimeMs);
                String key = makeKey(UUID.fromString(row.playerUuid), row.mapId);
                cache.put(key, recording);
            } catch (Exception e) {
                logger.atWarning().withCause(e)
                        .log("Failed to deserialize " + modeLabel
                                + " ghost recording for " + row.playerUuid + "/" + row.mapId);
            }
        }
        logger.atInfo().log("Loaded " + cache.size() + " " + modeLabel + " ghost recordings");
    }

    public void saveRecording(UUID playerId, String mapId, GhostRecording recording) {
        List<GhostSample> samples = recording.getSamples();
        if (samples.size() > MAX_SAMPLES) {
            logger.atWarning().log("Rejecting " + modeLabel + " ghost recording for " + playerId + "/" + mapId
                    + " - sample count " + samples.size() + " exceeds max " + MAX_SAMPLES);
            return;
        }

        String key = makeKey(playerId, mapId);
        cache.put(key, recording);

        try {
            byte[] blob = serialize(recording);
            DatabaseManager.execute(this.db,
                    """
                    INSERT INTO %s (player_uuid, map_id, recording_blob, completion_time_ms)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE recording_blob = VALUES(recording_blob),
                                            completion_time_ms = VALUES(completion_time_ms),
                                            recorded_at = CURRENT_TIMESTAMP
                    """.formatted(tableName),
                    stmt -> {
                        stmt.setString(1, playerId.toString());
                        stmt.setString(2, mapId);
                        stmt.setBytes(3, blob);
                        stmt.setLong(4, recording.getCompletionTimeMs());
                    });
        } catch (Exception e) {
            logger.atSevere().withCause(e)
                    .log("Failed to save " + modeLabel + " ghost recording for " + playerId + "/" + mapId);
        }
    }

    public GhostRecording getRecording(UUID playerId, String mapId) {
        return cache.get(makeKey(playerId, mapId));
    }

    public void deleteRecording(UUID playerId, String mapId) {
        cache.remove(makeKey(playerId, mapId));
        DatabaseManager.execute(this.db,
                "DELETE FROM " + tableName + " WHERE player_uuid = ? AND map_id = ?",
                stmt -> {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, mapId);
                });
    }

    private byte[] serialize(GhostRecording recording) throws Exception {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
             DataOutputStream dataOut = new DataOutputStream(gzipOut)) {

            List<GhostSample> samples = recording.getSamples();
            dataOut.writeInt(samples.size());

            for (GhostSample sample : samples) {
                dataOut.writeDouble(sample.x());
                dataOut.writeDouble(sample.y());
                dataOut.writeDouble(sample.z());
                dataOut.writeFloat(sample.yaw());
                dataOut.writeLong(sample.timestampMs());
            }
        }
        return byteOut.toByteArray();
    }

    private GhostRecording deserialize(byte[] blob, long completionTimeMs) throws Exception {
        ByteArrayInputStream byteIn = new ByteArrayInputStream(blob);
        try (GZIPInputStream gzipIn = new GZIPInputStream(byteIn);
             DataInputStream dataIn = new DataInputStream(gzipIn)) {

            int sampleCount = dataIn.readInt();
            if (sampleCount < 0 || sampleCount > MAX_SAMPLES) {
                throw new IllegalArgumentException(
                        "Invalid sample count: " + sampleCount + " (max " + MAX_SAMPLES + ")");
            }
            List<GhostSample> samples = new ArrayList<>(sampleCount);

            for (int i = 0; i < sampleCount; i++) {
                double x = dataIn.readDouble();
                double y = dataIn.readDouble();
                double z = dataIn.readDouble();
                float yaw = dataIn.readFloat();
                long timestampMs = dataIn.readLong();
                samples.add(new GhostSample(x, y, z, yaw, timestampMs));
            }

            return new GhostRecording(samples, completionTimeMs);
        }
    }

    private void ensureGhostTableExists() {
        String sql = """
                CREATE TABLE IF NOT EXISTS %s (
                  player_uuid VARCHAR(36) NOT NULL,
                  map_id VARCHAR(32) NOT NULL,
                  recording_blob MEDIUMBLOB NOT NULL,
                  completion_time_ms BIGINT NOT NULL,
                  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (player_uuid, map_id)
                ) ENGINE=InnoDB
                """.formatted(tableName);

        try (Connection conn = this.db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            logger.atSevere().withCause(e)
                    .log("Failed to create " + modeLabel + " ghost table " + tableName);
        }
    }

    private String makeKey(UUID playerId, String mapId) {
        return playerId.toString() + ":" + mapId;
    }
}
