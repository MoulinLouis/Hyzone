package io.hyvexa.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DamageBypassRegistryTest {

    private static final UUID PLAYER = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        DamageBypassRegistry.remove(PLAYER);
    }

    @Test
    void addThenIsBypassed() {
        DamageBypassRegistry.add(PLAYER);
        assertTrue(DamageBypassRegistry.isBypassed(PLAYER));
    }

    @Test
    void removeThenNotBypassed() {
        DamageBypassRegistry.add(PLAYER);
        DamageBypassRegistry.remove(PLAYER);
        assertFalse(DamageBypassRegistry.isBypassed(PLAYER));
    }

    @Test
    void isBypassedNullReturnsFalse() {
        assertFalse(DamageBypassRegistry.isBypassed(null));
    }

    @Test
    void addNullDoesNotThrow() {
        assertDoesNotThrow(() -> DamageBypassRegistry.add(null));
    }

    @Test
    void duplicateAddIsIdempotent() {
        DamageBypassRegistry.add(PLAYER);
        DamageBypassRegistry.add(PLAYER);
        assertTrue(DamageBypassRegistry.isBypassed(PLAYER));
        DamageBypassRegistry.remove(PLAYER);
        assertFalse(DamageBypassRegistry.isBypassed(PLAYER));
    }
}
