package io.hyvexa.parkour.data;

import java.util.List;
import java.util.UUID;

/** Immutable request object for async persistence of a map completion. */
final class CompletionPersistenceRequest {
    private final UUID playerId;
    private final String mapId;
    private final long timeMs;
    private final List<Long> checkpointTimes;

    CompletionPersistenceRequest(UUID playerId, String mapId, long timeMs, List<Long> checkpointTimes) {
        this.playerId = playerId;
        this.mapId = mapId;
        this.timeMs = timeMs;
        this.checkpointTimes = checkpointTimes != null ? checkpointTimes : List.of();
    }

    UUID getPlayerId() { return playerId; }
    String getMapId() { return mapId; }
    long getTimeMs() { return timeMs; }
    List<Long> getCheckpointTimes() { return checkpointTimes; }
}
