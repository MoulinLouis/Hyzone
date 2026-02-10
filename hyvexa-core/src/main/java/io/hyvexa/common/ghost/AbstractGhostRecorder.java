package io.hyvexa.common.ghost;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Shared sampling pipeline for ghost recording. */
public abstract class AbstractGhostRecorder<TSample, TRecording> {

    private static final long SAMPLE_INTERVAL_MS = 50L;
    private static final int MAX_SAMPLES = 12000;

    @FunctionalInterface
    public interface RecordingWriter<TRecording> {
        void saveRecording(UUID playerId, String mapId, TRecording recording);
    }

    private final RecordingWriter<TRecording> recordingWriter;
    private final Map<UUID, ActiveRecording<TSample>> activeRecordings = new ConcurrentHashMap<>();
    private ScheduledFuture<?> samplingTask;

    protected AbstractGhostRecorder(RecordingWriter<TRecording> recordingWriter) {
        this.recordingWriter = recordingWriter;
    }

    protected abstract HytaleLogger logger();

    protected abstract PlayerRef resolvePlayerRef(UUID playerId);

    protected abstract TRecording createRecording(List<TSample> samples, long completionTimeMs);

    protected abstract TSample createSample(Vector3d position, float yaw, long timestampMs);

    public void start() {
        if (samplingTask != null && !samplingTask.isDone()) {
            logger().atWarning().log("GhostRecorder sampling task already running");
            return;
        }

        samplingTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                this::sampleActiveRecordings,
                SAMPLE_INTERVAL_MS,
                SAMPLE_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        if (samplingTask != null) {
            samplingTask.cancel(false);
            samplingTask = null;
        }
        activeRecordings.clear();
    }

    public void startRecording(UUID playerId, String mapId) {
        activeRecordings.put(playerId, new ActiveRecording<>(playerId, mapId, System.currentTimeMillis()));
    }

    public void stopRecording(UUID playerId, long completionTimeMs, boolean isPersonalBest) {
        ActiveRecording<TSample> recording = activeRecordings.remove(playerId);
        if (recording == null || !isPersonalBest) {
            return;
        }

        int sampleCount;
        synchronized (recording.samples) {
            sampleCount = recording.samples.size();
        }

        if (sampleCount == 0) {
            logger().atWarning().log("Ghost recording has no samples for player " + playerId
                    + " - sampling may have failed");
            return;
        }

        try {
            List<TSample> samplesCopy;
            synchronized (recording.samples) {
                samplesCopy = new ArrayList<>(recording.samples);
            }
            TRecording ghost = createRecording(samplesCopy, completionTimeMs);
            recordingWriter.saveRecording(playerId, recording.mapId, ghost);
        } catch (Exception e) {
            logger().at(Level.SEVERE).withCause(e)
                    .log("Failed to save ghost recording for player " + playerId);
        }
    }

    public void cancelRecording(UUID playerId) {
        activeRecordings.remove(playerId);
    }

    private void sampleActiveRecordings() {
        long now = System.currentTimeMillis();
        List<ActiveRecording<TSample>> snapshot = new ArrayList<>(activeRecordings.values());
        for (ActiveRecording<TSample> recording : snapshot) {
            try {
                samplePlayer(recording, now);
            } catch (Exception e) {
                logger().at(Level.WARNING).withCause(e)
                        .log("Failed to sample player " + recording.playerId);
            }
        }
    }

    private void samplePlayer(ActiveRecording<TSample> recording, long now) {
        synchronized (recording.samples) {
            if (recording.samples.size() >= MAX_SAMPLES) {
                return;
            }
        }

        PlayerRef playerRef = resolvePlayerRef(recording.playerId);
        if (playerRef == null) {
            return;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return;
        }

        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }

        long timestampMs = now - recording.startTimeMs;

        world.execute(() -> {
            try {
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) {
                    return;
                }

                Vector3d position = transform.getPosition();
                if (position == null) {
                    return;
                }

                Vector3f bodyRotation = transform.getRotation();
                float yaw = bodyRotation != null ? bodyRotation.getY() : 0f;

                TSample sample = createSample(position, yaw, timestampMs);
                synchronized (recording.samples) {
                    recording.samples.add(sample);
                }
            } catch (Exception e) {
                logger().at(Level.WARNING).withCause(e).log("Error sampling ghost position");
            }
        });
    }

    private static final class ActiveRecording<TSample> {
        private final UUID playerId;
        private final String mapId;
        private final long startTimeMs;
        private final List<TSample> samples = new ArrayList<>();

        private ActiveRecording(UUID playerId, String mapId, long startTimeMs) {
            this.playerId = playerId;
            this.mapId = mapId;
            this.startTimeMs = startTimeMs;
        }
    }
}
