package io.hyvexa.purge.data;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public record PurgeWaveDefinition(int waveNumber, Map<String, Integer> variantCounts,
                                   int spawnDelayMs, int spawnBatchSize) {

    public int getCount(String variantKey) {
        return variantCounts.getOrDefault(variantKey, 0);
    }

    public int totalCount() {
        int total = 0;
        for (int count : variantCounts.values()) {
            total += Math.max(0, count);
        }
        return total;
    }

    public Set<String> getVariantKeys() {
        return Collections.unmodifiableSet(variantCounts.keySet());
    }
}
