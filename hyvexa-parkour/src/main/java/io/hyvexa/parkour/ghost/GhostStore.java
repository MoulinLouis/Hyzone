package io.hyvexa.parkour.ghost;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.common.ghost.AbstractGhostStore;

import java.util.List;

public class GhostStore extends AbstractGhostStore<GhostSample, GhostRecording> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final int MAX_SAMPLES = AbstractGhostStore.MAX_SAMPLES;

    @Override
    protected HytaleLogger logger() {
        return LOGGER;
    }

    @Override
    protected String tableName() {
        return "parkour_ghost_recordings";
    }

    @Override
    protected String modeLabel() {
        return "parkour";
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
