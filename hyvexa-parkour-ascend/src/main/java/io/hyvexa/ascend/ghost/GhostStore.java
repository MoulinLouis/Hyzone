package io.hyvexa.ascend.ghost;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GhostStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final int MAX_SAMPLES = 12000;

    private final Map<String, GhostRecording> cache = new ConcurrentHashMap<>();

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, GhostStore will be empty");
            return;
        }

        ensureGhostTableExists();
        cache.clear();

        String sql = """
            SELECT player_uuid, map_id, recording_blob, completion_time_ms
            FROM ascend_ghost_recordings
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String playerUuid = rs.getString("player_uuid");
                    String mapId = rs.getString("map_id");
                    byte[] blob = rs.getBytes("recording_blob");
                    long completionTimeMs = rs.getLong("completion_time_ms");

                    try {
                        GhostRecording recording = deserialize(blob, completionTimeMs);
                        String key = makeKey(UUID.fromString(playerUuid), mapId);
                        cache.put(key, recording);
                    } catch (Exception e) {
                        LOGGER.at(Level.WARNING).withCause(e)
                            .log("Failed to deserialize ghost recording for " + playerUuid + "/" + mapId);
                    }
                }
            }
            LOGGER.atInfo().log("Loaded " + cache.size() + " ghost recordings");
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to load ghost recordings");
        }
    }

    public void saveRecording(UUID playerId, String mapId, GhostRecording recording) {
        // Validate sample count to prevent DoS via oversized recordings
        if (recording.getSamples().size() > MAX_SAMPLES) {
            LOGGER.atWarning().log("Rejecting ghost recording for " + playerId + "/" + mapId
                + " - sample count " + recording.getSamples().size() + " exceeds max " + MAX_SAMPLES);
            return;
        }

        String key = makeKey(playerId, mapId);
        cache.put(key, recording);

        try {
            byte[] blob = serialize(recording);
            String sql = """
                INSERT INTO ascend_ghost_recordings (player_uuid, map_id, recording_blob, completion_time_ms)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE recording_blob = VALUES(recording_blob),
                                        completion_time_ms = VALUES(completion_time_ms),
                                        recorded_at = CURRENT_TIMESTAMP
                """;

            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setString(1, playerId.toString());
                stmt.setString(2, mapId);
                stmt.setBytes(3, blob);
                stmt.setLong(4, recording.getCompletionTimeMs());
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e)
                .log("Failed to save ghost recording for " + playerId + "/" + mapId);
        }
    }

    public GhostRecording getRecording(UUID playerId, String mapId) {
        String key = makeKey(playerId, mapId);
        return cache.get(key);
    }

    public void deleteRecording(UUID playerId, String mapId) {
        String key = makeKey(playerId, mapId);
        cache.remove(key);

        String sql = "DELETE FROM ascend_ghost_recordings WHERE player_uuid = ? AND map_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, mapId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).withCause(e)
                .log("Failed to delete ghost recording for " + playerId + "/" + mapId);
        }
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
            CREATE TABLE IF NOT EXISTS ascend_ghost_recordings (
              player_uuid VARCHAR(36) NOT NULL,
              map_id VARCHAR(32) NOT NULL,
              recording_blob MEDIUMBLOB NOT NULL,
              completion_time_ms BIGINT NOT NULL,
              recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (player_uuid, map_id)
            ) ENGINE=InnoDB
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to create ascend_ghost_recordings table");
        }
    }

    private String makeKey(UUID playerId, String mapId) {
        return playerId.toString() + ":" + mapId;
    }
}
