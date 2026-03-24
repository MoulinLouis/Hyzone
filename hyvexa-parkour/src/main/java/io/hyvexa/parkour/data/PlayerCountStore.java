package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;

import java.util.ArrayList;
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

    private final ConnectionProvider db;
    private final List<Sample> samples = new ArrayList<>();
    private final ReadWriteLock fileLock = new ReentrantReadWriteLock();
    private final long retentionMs;
    private long lastSavedAtMs;

    public PlayerCountStore(ConnectionProvider db) {
        this.db = db;
        this.retentionMs = TimeUnit.DAYS.toMillis(DEFAULT_RETENTION_DAYS);
    }

    public void syncLoad() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, PlayerCountStore will be empty");
            return;
        }

        long cutoff = System.currentTimeMillis() - retentionMs;
        String sql = "SELECT timestamp_ms, count FROM player_count_samples WHERE timestamp_ms >= ? ORDER BY timestamp_ms";

        fileLock.writeLock().lock();
        try {
            samples.clear();

            List<Sample> loaded = DatabaseManager.queryList(this.db, sql,
                    stmt -> stmt.setLong(1, cutoff),
                    rs -> new Sample(rs.getLong("timestamp_ms"), Math.max(0, rs.getInt("count"))));
            samples.addAll(loaded);

            lastSavedAtMs = System.currentTimeMillis();
            LOGGER.atInfo().log("PlayerCountStore loaded " + samples.size() + " samples from database");

        } finally {
            fileLock.writeLock().unlock();
        }

        // Prune old samples from database
        pruneOldSamplesFromDatabase();
    }

    private void pruneOldSamplesFromDatabase() {
        if (!this.db.isInitialized()) return;

        long cutoff = System.currentTimeMillis() - retentionMs;
        String sql = "DELETE FROM player_count_samples WHERE timestamp_ms < ?";
        int deleted = DatabaseManager.executeCount(this.db, sql, stmt -> stmt.setLong(1, cutoff));
        if (deleted > 0) {
            LOGGER.atInfo().log("Pruned " + deleted + " old player count samples");
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
        if (!this.db.isInitialized()) return;

        fileLock.writeLock().lock();
        try {
            samples.clear();
            lastSavedAtMs = System.currentTimeMillis();
        } finally {
            fileLock.writeLock().unlock();
        }

        String sql = "DELETE FROM player_count_samples";
        DatabaseManager.execute(this.db, sql);
    }

    private void saveSampleToDatabase(long timestampMs, int count) {
        if (!this.db.isInitialized()) return;

        String sql = "INSERT INTO player_count_samples (timestamp_ms, count) VALUES (?, ?)";
        DatabaseManager.execute(this.db, sql, stmt -> {
            stmt.setLong(1, timestampMs);
            stmt.setInt(2, count);
        });
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
