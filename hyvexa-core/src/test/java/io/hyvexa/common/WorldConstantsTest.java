package io.hyvexa.common;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorldConstantsTest {

    @Test
    void allWorldIdsAreNonBlank() {
        assertFalse(WorldConstants.WORLD_HUB.isBlank());
        assertFalse(WorldConstants.WORLD_PARKOUR.isBlank());
        assertFalse(WorldConstants.WORLD_ASCEND.isBlank());
        assertFalse(WorldConstants.WORLD_PURGE.isBlank());
        assertFalse(WorldConstants.WORLD_RUN_OR_FALL.isBlank());
    }

    @Test
    void allWorldIdsAreUnique() {
        Set<String> ids = Set.of(
                WorldConstants.WORLD_HUB,
                WorldConstants.WORLD_PARKOUR,
                WorldConstants.WORLD_ASCEND,
                WorldConstants.WORLD_PURGE,
                WorldConstants.WORLD_RUN_OR_FALL
        );
        assertEquals(5, ids.size());
    }

    @Test
    void allItemIdsAreNonBlank() {
        assertFalse(WorldConstants.ITEM_SERVER_SELECTOR.isBlank());
        assertFalse(WorldConstants.ITEM_SHOP.isBlank());
    }
}
