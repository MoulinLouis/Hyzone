package io.hyvexa.purge.data;

import java.util.List;

public record PurgeMapInstance(
        String instanceId,
        PurgeLocation startPoint,
        PurgeLocation exitPoint,
        List<PurgeSpawnPoint> spawnPoints
) {}
