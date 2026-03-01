package io.hyvexa.core.cosmetic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticDefinitionTest {

    @Test
    void fromIdKnown() {
        CosmeticDefinition def = CosmeticDefinition.fromId("GOLD_GLOW");
        assertSame(CosmeticDefinition.GOLD_GLOW, def);
    }

    @Test
    void fromIdNull() {
        assertNull(CosmeticDefinition.fromId(null));
    }

    @Test
    void fromIdNonexistent() {
        assertNull(CosmeticDefinition.fromId("NONEXISTENT"));
    }

    @Test
    void glowTypeEffectId() {
        CosmeticDefinition glow = CosmeticDefinition.GOLD_GLOW;
        assertEquals(glow.getVisualId(), glow.getEffectId());
    }

    @Test
    void glowTypeParticleIdIsNull() {
        assertNull(CosmeticDefinition.GOLD_GLOW.getParticleId());
    }

    @Test
    void trailTypeEffectIdIsNull() {
        assertNull(CosmeticDefinition.TRAIL_GOLD.getEffectId());
    }

    @Test
    void trailTypeParticleId() {
        CosmeticDefinition trail = CosmeticDefinition.TRAIL_GOLD;
        assertEquals(trail.getVisualId(), trail.getParticleId());
    }

    @Test
    void idMatchesEnumName() {
        for (CosmeticDefinition def : CosmeticDefinition.values()) {
            assertEquals(def.name(), def.getId());
        }
    }

    @Test
    void allGlowsAreEntityEffect() {
        for (CosmeticDefinition def : CosmeticDefinition.values()) {
            if (def.getKind() == CosmeticDefinition.Kind.GLOW) {
                assertEquals(CosmeticDefinition.Type.ENTITY_EFFECT, def.getType());
            }
        }
    }
}
