package io.hyvexa.ascend.mine.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PickaxeTierTest {

    @Test
    void fromTierRoundTripsKnownTiers() {
        for (PickaxeTier tier : PickaxeTier.values()) {
            assertEquals(tier, PickaxeTier.fromTier(tier.getTier()));
        }
    }

    @Test
    void fromTierFallsBackToWoodForUnknownTier() {
        assertEquals(PickaxeTier.WOOD, PickaxeTier.fromTier(99));
    }

    @Test
    void nextReturnsTheFollowingTierUntilTheEnd() {
        assertEquals(PickaxeTier.STONE, PickaxeTier.WOOD.next());
        assertEquals(PickaxeTier.PRISMATIC, PickaxeTier.VOID.next());
        assertNull(PickaxeTier.PRISMATIC.next());
    }
}
