package io.hyvexa.parkour.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.util.io.BlockingDiskFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PlayerCountStore extends BlockingDiskFile {

    public static final long DEFAULT_SAMPLE_INTERVAL_SECONDS = 300L;
    public static final long DEFAULT_RETENTION_DAYS = 7L;
    private static final long DEFAULT_SAVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(DEFAULT_SAMPLE_INTERVAL_SECONDS);

    private final List<Sample> samples = new ArrayList<>();
    private final long retentionMs;
    private long lastSavedAtMs;

    public PlayerCountStore() {
        super(Path.of("Parkour/PlayerCounts.json"));
        this.retentionMs = TimeUnit.DAYS.toMillis(DEFAULT_RETENTION_DAYS);
    }

    public void recordSample(long timestampMs, int count) {
        int safeCount = Math.max(0, count);
        boolean shouldSave = false;
        this.fileLock.writeLock().lock();
        try {
            samples.add(new Sample(timestampMs, safeCount));
            pruneOldSamples(timestampMs);
            if (timestampMs - lastSavedAtMs >= DEFAULT_SAVE_INTERVAL_MS) {
                lastSavedAtMs = timestampMs;
                shouldSave = true;
            }
        } finally {
            this.fileLock.writeLock().unlock();
        }
        if (shouldSave) {
            this.syncSave();
        }
    }

    public List<Sample> getSamplesSince(long cutoffMs) {
        List<Sample> result = new ArrayList<>();
        this.fileLock.readLock().lock();
        try {
            for (Sample sample : samples) {
                if (sample.timestampMs >= cutoffMs) {
                    result.add(new Sample(sample.timestampMs, sample.count));
                }
            }
        } finally {
            this.fileLock.readLock().unlock();
        }
        result.sort(Comparator.comparingLong(Sample::getTimestampMs));
        return result;
    }

    public long getRetentionMs() {
        return retentionMs;
    }

    @Override
    protected void read(BufferedReader bufferedReader) throws IOException {
        JsonElement parsed = JsonParser.parseReader(bufferedReader);
        if (!parsed.isJsonObject()) {
            return;
        }
        JsonObject object = parsed.getAsJsonObject();
        samples.clear();
        if (object.has("samples") && object.get("samples").isJsonArray()) {
            JsonArray array = object.getAsJsonArray("samples");
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("timestampMs") || !entry.has("count")) {
                    continue;
                }
                long timestampMs = entry.get("timestampMs").getAsLong();
                int count = entry.get("count").getAsInt();
                samples.add(new Sample(timestampMs, Math.max(0, count)));
            }
        }
        pruneOldSamples(System.currentTimeMillis());
        lastSavedAtMs = System.currentTimeMillis();
    }

    @Override
    protected void write(BufferedWriter bufferedWriter) throws IOException {
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        this.fileLock.readLock().lock();
        try {
            for (Sample sample : samples) {
                JsonObject entry = new JsonObject();
                entry.addProperty("timestampMs", sample.timestampMs);
                entry.addProperty("count", sample.count);
                array.add(entry);
            }
        } finally {
            this.fileLock.readLock().unlock();
        }
        object.add("samples", array);
        bufferedWriter.write(object.toString());
    }

    @Override
    protected void create(BufferedWriter bufferedWriter) throws IOException {
        write(bufferedWriter);
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
