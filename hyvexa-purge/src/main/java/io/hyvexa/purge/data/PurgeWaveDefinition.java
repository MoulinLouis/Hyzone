package io.hyvexa.purge.data;

public record PurgeWaveDefinition(int waveNumber, int slowCount, int normalCount, int fastCount,
                                   int spawnDelayMs, int spawnBatchSize) {

    public int getCount(PurgeZombieVariant variant) {
        return switch (variant) {
            case SLOW -> slowCount;
            case NORMAL -> normalCount;
            case FAST -> fastCount;
        };
    }

    public int totalCount() {
        return Math.max(0, slowCount) + Math.max(0, normalCount) + Math.max(0, fastCount);
    }
}
