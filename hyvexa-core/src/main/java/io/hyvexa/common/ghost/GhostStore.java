package io.hyvexa.common.ghost;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.List;

/**
 * Concrete ghost store using shared GhostSample/GhostRecording types.
 * Configured via constructor params for table name and mode label.
 */
public class GhostStore extends AbstractGhostStore<GhostSample, GhostRecording> {

    private final HytaleLogger logger;
    private final String tableName;
    private final String modeLabel;

    public static final int MAX_SAMPLES = AbstractGhostStore.MAX_SAMPLES;

    public GhostStore(String tableName, String modeLabel) {
        this.logger = HytaleLogger.forEnclosingClass();
        this.tableName = tableName;
        this.modeLabel = modeLabel;
    }

    @Override
    protected HytaleLogger logger() {
        return logger;
    }

    @Override
    protected String tableName() {
        return tableName;
    }

    @Override
    protected String modeLabel() {
        return modeLabel;
    }

    @Override
    protected List<GhostSample> getSamples(GhostRecording recording) {
        return recording.getSamples();
    }

    @Override
    protected long getCompletionTimeMs(GhostRecording recording) {
        return recording.getCompletionTimeMs();
    }

    @Override
    protected GhostRecording createRecording(List<GhostSample> samples, long completionTimeMs) {
        return new GhostRecording(samples, completionTimeMs);
    }

    @Override
    protected GhostSample createSample(double x, double y, double z, float yaw, long timestampMs) {
        return new GhostSample(x, y, z, yaw, timestampMs);
    }

    @Override
    protected double sampleX(GhostSample sample) {
        return sample.x();
    }

    @Override
    protected double sampleY(GhostSample sample) {
        return sample.y();
    }

    @Override
    protected double sampleZ(GhostSample sample) {
        return sample.z();
    }

    @Override
    protected float sampleYaw(GhostSample sample) {
        return sample.yaw();
    }

    @Override
    protected long sampleTimestampMs(GhostSample sample) {
        return sample.timestampMs();
    }
}
