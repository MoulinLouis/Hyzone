package io.hyvexa.purge.data;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PurgeWaveDefinitionTest {

    @Test
    void totalCountSumsAllVariants() {
        PurgeWaveDefinition def = new PurgeWaveDefinition(1,
                Map.of("zombie", 5, "skeleton", 3), 1000, 2);
        assertEquals(8, def.totalCount());
    }

    @Test
    void totalCountIgnoresNegatives() {
        PurgeWaveDefinition def = new PurgeWaveDefinition(1,
                Map.of("zombie", 5, "ghost", -2), 1000, 2);
        assertEquals(5, def.totalCount());
    }

    @Test
    void totalCountEmptyMap() {
        PurgeWaveDefinition def = new PurgeWaveDefinition(1,
                Map.of(), 1000, 2);
        assertEquals(0, def.totalCount());
    }

    @Test
    void getCountKnownVariant() {
        PurgeWaveDefinition def = new PurgeWaveDefinition(1,
                Map.of("zombie", 5), 1000, 2);
        assertEquals(5, def.getCount("zombie"));
    }

    @Test
    void getCountUnknownReturnsZero() {
        PurgeWaveDefinition def = new PurgeWaveDefinition(1,
                Map.of("zombie", 5), 1000, 2);
        assertEquals(0, def.getCount("unknown"));
    }

    @Test
    void getVariantKeysIsUnmodifiable() {
        PurgeWaveDefinition def = new PurgeWaveDefinition(1,
                Map.of("zombie", 5, "skeleton", 3), 1000, 2);
        Set<String> keys = def.getVariantKeys();
        assertTrue(keys.contains("zombie"));
        assertTrue(keys.contains("skeleton"));
        assertThrows(UnsupportedOperationException.class, () -> keys.add("ghost"));
    }

    @Test
    void recordAccessors() {
        PurgeWaveDefinition def = new PurgeWaveDefinition(3,
                Map.of("zombie", 10), 500, 4);
        assertEquals(3, def.waveNumber());
        assertEquals(500, def.spawnDelayMs());
        assertEquals(4, def.spawnBatchSize());
    }
}
