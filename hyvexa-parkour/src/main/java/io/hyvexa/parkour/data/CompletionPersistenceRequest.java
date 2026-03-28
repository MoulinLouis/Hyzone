package io.hyvexa.parkour.data;

import java.util.List;
import java.util.UUID;

/** Immutable request object for async persistence of a map completion. */
final class CompletionPersistenceRequest {
    final UUID playerId;
    final String mapId;
    final long timeMs;
    final List<Long> checkpointTimes;

    CompletionPersistenceRequest(UUID playerId, String mapId, long timeMs, List<Long> checkpointTimes) {
        this.playerId = playerId;
        this.mapId = mapId;
        this.timeMs = timeMs;
        this.checkpointTimes = checkpointTimes != null ? checkpointTimes : List.of();
    }
}
