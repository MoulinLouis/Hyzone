package io.hyvexa.parkour.ghost;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class GhostRecorder {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long SAMPLE_INTERVAL_MS = 50;
    private static final int MAX_SAMPLES = 12000; // Cap at ~10 minutes of recording

    private final GhostStore ghostStore;
    private final Map<UUID, ActiveRecording> activeRecordings = new ConcurrentHashMap<>();
    private ScheduledFuture<?> samplingTask;

    public GhostRecorder(GhostStore ghostStore) {
        this.ghostStore = ghostStore;
    }

    public void start() {
        if (samplingTask != null && !samplingTask.isDone()) {
            LOGGER.atWarning().log("GhostRecorder sampling task already running");
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
        long startTime = System.currentTimeMillis();
        ActiveRecording recording = new ActiveRecording(playerId, mapId, startTime);
        activeRecordings.put(playerId, recording);
    }

    public void stopRecording(UUID playerId, long completionTimeMs, boolean isPersonalBest) {
        ActiveRecording recording = activeRecordings.remove(playerId);

        if (recording == null) {
            return;
        }

        // Thread-safe access to samples
        int sampleCount;
        synchronized (recording.samples) {
            sampleCount = recording.samples.size();
        }

        if (!isPersonalBest) {
            return;
        }

        if (sampleCount == 0) {
            LOGGER.atWarning().log("Ghost recording has no samples for player " + playerId
                + " - sampling may have failed");
            return;
        }

        try {
            // Create a copy of samples for thread safety
            List<GhostSample> samplesCopy;
            synchronized (recording.samples) {
                samplesCopy = new ArrayList<>(recording.samples);
            }

            GhostRecording ghost = new GhostRecording(samplesCopy, completionTimeMs);
            ghostStore.saveRecording(playerId, recording.mapId, ghost);
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e)
                .log("Failed to save ghost recording for player " + playerId);
        }
    }

    public void cancelRecording(UUID playerId) {
        activeRecordings.remove(playerId);
    }

    private void sampleActiveRecordings() {
        long now = System.currentTimeMillis();

        // Take a snapshot to avoid race conditions during iteration
        List<ActiveRecording> snapshot = new ArrayList<>(activeRecordings.values());
        for (ActiveRecording recording : snapshot) {
            try {
                samplePlayer(recording, now);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e)
                    .log("Failed to sample player " + recording.playerId);
            }
        }
    }

    private void samplePlayer(ActiveRecording recording, long now) {
        // Check if recording has reached sample limit
        synchronized (recording.samples) {
            if (recording.samples.size() >= MAX_SAMPLES) {
                return;
            }
        }

        // Find the player in the universe
        PlayerRef playerRef = null;
        for (PlayerRef pr : Universe.get().getPlayers()) {
            if (pr.getUuid().equals(recording.playerId)) {
                playerRef = pr;
                break;
            }
        }

        if (playerRef == null) {
            return; // Player not online
        }

        // Get entity reference from PlayerRef
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return;
        }

        // Get the world to execute on the correct thread
        com.hypixel.hytale.server.core.universe.world.World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }

        // Calculate timestamp offset from run start (before world execute)
        final long timestampMs = now - recording.startTimeMs;

        // Execute on world thread to access components
        world.execute(() -> {
            try {
                // Get player components on world thread
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) {
                    return;
                }

                // Get position
                Vector3d position = transform.getPosition();
                if (position == null) {
                    return;
                }

                // Get rotation (yaw from body rotation, not head rotation)
                // Body rotation creates smoother, more natural-looking NPC movement
                Vector3f bodyRotation = transform.getRotation();
                float yaw = 0f;
                if (bodyRotation != null) {
                    yaw = bodyRotation.getY(); // Y component is yaw in Hytale
                }

                // Add sample (synchronized since this runs on world thread)
                GhostSample sample = new GhostSample(
                    position.getX(),
                    position.getY(),
                    position.getZ(),
                    yaw,
                    timestampMs
                );
                synchronized (recording.samples) {
                    recording.samples.add(sample);
                }
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e).log("Error sampling ghost position");
            }
        });
    }

    private static class ActiveRecording {
        private final UUID playerId;
        private final String mapId;
        private final long startTimeMs;
        private final List<GhostSample> samples;

        private ActiveRecording(UUID playerId, String mapId, long startTimeMs) {
            this.playerId = playerId;
            this.mapId = mapId;
            this.startTimeMs = startTimeMs;
            this.samples = new ArrayList<>();
        }
    }
}
