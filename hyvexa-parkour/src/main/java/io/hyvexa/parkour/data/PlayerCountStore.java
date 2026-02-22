package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** MySQL-backed storage for sampled online player counts. */
public class PlayerCountStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final long DEFAULT_SAMPLE_INTERVAL_SECONDS = 600L;
    public static final long DEFAULT_RETENTION_DAYS = 7L;
    private static final long DEFAULT_SAVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(DEFAULT_SAMPLE_INTERVAL_SECONDS);

    private final List<Sample> samples = new ArrayList<>();
    private final ReadWriteLock fileLock = new ReentrantReadWriteLock();
    private final long retentionMs;
    private long lastSavedAtMs;

    public PlayerCountStore() {
        this.retentionMs = TimeUnit.DAYS.toMillis(DEFAULT_RETENTION_DAYS);
    }

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, PlayerCountStore will be empty");
            return;
        }

        long cutoff = System.currentTimeMillis() - retentionMs;
        String sql = "SELECT timestamp_ms, count FROM player_count_samples WHERE timestamp_ms >= ? ORDER BY timestamp_ms";

        fileLock.writeLock().lock();
        try {
            samples.clear();

            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                if (conn == null) {
                    LOGGER.atWarning().log("Failed to acquire database connection");
                    return;
                }
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    DatabaseManager.applyQueryTimeout(stmt);
                    stmt.setLong(1, cutoff);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            long timestampMs = rs.getLong("timestamp_ms");
                            int count = Math.max(0, rs.getInt("count"));
                            samples.add(new Sample(timestampMs, count));
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.atSevere().log("Failed to load player count samples: " + e.getMessage());
            }

            lastSavedAtMs = System.currentTimeMillis();
            LOGGER.atInfo().log("PlayerCountStore loaded " + samples.size() + " samples from database");

        } finally {
            fileLock.writeLock().unlock();
        }

        // Prune old samples from database
        pruneOldSamplesFromDatabase();
    }

    private void pruneOldSamplesFromDatabase() {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        long cutoff = System.currentTimeMillis() - retentionMs;
        String sql = "DELETE FROM player_count_samples WHERE timestamp_ms < ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setLong(1, cutoff);
                int deleted = stmt.executeUpdate();
                if (deleted > 0) {
                    LOGGER.atInfo().log("Pruned " + deleted + " old player count samples");
                }
            }
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to prune old samples: " + e.getMessage());
        }
    }

    public void recordSample(long timestampMs, int count) {
        int safeCount = Math.max(0, count);
        boolean shouldSave = false;

        fileLock.writeLock().lock();
        try {
            samples.add(new Sample(timestampMs, safeCount));
            pruneOldSamples(timestampMs);
            if (timestampMs - lastSavedAtMs >= DEFAULT_SAVE_INTERVAL_MS) {
                lastSavedAtMs = timestampMs;
                shouldSave = true;
            }
        } finally {
            fileLock.writeLock().unlock();
        }

        if (shouldSave) {
            saveSampleToDatabase(timestampMs, safeCount);
        }
    }

    public void clearAll() {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        fileLock.writeLock().lock();
        try {
            samples.clear();
            lastSavedAtMs = System.currentTimeMillis();
        } finally {
            fileLock.writeLock().unlock();
        }

        String sql = "DELETE FROM player_count_samples";
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to clear player count samples: " + e.getMessage());
        }
    }

    private void saveSampleToDatabase(long timestampMs, int count) {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = "INSERT INTO player_count_samples (timestamp_ms, count) VALUES (?, ?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn == null) {
                LOGGER.atWarning().log("Failed to acquire database connection");
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                DatabaseManager.applyQueryTimeout(stmt);
                stmt.setLong(1, timestampMs);
                stmt.setInt(2, count);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.atWarning().log("Failed to save sample: " + e.getMessage());
        }
    }

    public List<Sample> getSamplesSince(long cutoffMs) {
        List<Sample> result = new ArrayList<>();
        fileLock.readLock().lock();
        try {
            for (Sample sample : samples) {
                if (sample.timestampMs >= cutoffMs) {
                    result.add(new Sample(sample.timestampMs, sample.count));
                }
            }
        } finally {
            fileLock.readLock().unlock();
        }
        result.sort(Comparator.comparingLong(Sample::getTimestampMs));
        return result;
    }

    public long getRetentionMs() {
        return retentionMs;
    }

    private void pruneOldSamples(long nowMs) {
        long cutoff = nowMs - retentionMs;
        samples.removeIf(sample -> sample.timestampMs < cutoff);
    }

    public static final class Sample {
        private final long timestampMs;
        private final int count;

        public Sample(long timestampMs, int count) {
            this.timestampMs = timestampMs;
            this.count = count;
        }

        public long getTimestampMs() {
            return timestampMs;
        }

        public int getCount() {
            return count;
        }
    }
}
