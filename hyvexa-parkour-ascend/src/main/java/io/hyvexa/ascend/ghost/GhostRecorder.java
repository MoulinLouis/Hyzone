package io.hyvexa.ascend.ghost;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.common.ghost.AbstractGhostRecorder;
import io.hyvexa.common.ghost.GhostRecording;
import io.hyvexa.common.ghost.GhostSample;
import io.hyvexa.common.ghost.GhostStore;

import java.util.List;
import java.util.UUID;

public class GhostRecorder extends AbstractGhostRecorder<GhostSample, GhostRecording> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public GhostRecorder(GhostStore ghostStore) {
        super(ghostStore::saveRecording);
    }

    @Override
    protected HytaleLogger logger() {
        return LOGGER;
    }

    @Override
    protected PlayerRef resolvePlayerRef(UUID playerId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return null;
        }
        return plugin.getPlayerRef(playerId);
    }

    @Override
    protected GhostRecording createRecording(List<GhostSample> samples, long completionTimeMs) {
        return new GhostRecording(samples, completionTimeMs);
    }

    @Override
    protected GhostSample createSample(Vector3d position, float yaw, long timestampMs) {
        return new GhostSample(
                position.getX(),
                position.getY(),
                position.getZ(),
                yaw,
                timestampMs
        );
    }
}
