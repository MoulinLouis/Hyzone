package io.hyvexa.ascend.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudEffectTest {

    @Test
    void constructorSetsFieldsCorrectly() {
        HudEffect effect = new HudEffect("#elem1", "#ffffff", "#ff0000");
        assertEquals("#elem1", effect.getElementId());
        assertEquals("#ffffff", effect.getOriginalColor());
        assertEquals("#ff0000", effect.getFlashColor());
    }

    @Test
    void appliedFlagStartsFalse() {
        HudEffect effect = new HudEffect("id", "#000", "#fff");
        assertFalse(effect.isApplied());
    }

    @Test
    void markAsAppliedTogglesState() {
        HudEffect effect = new HudEffect("id", "#000", "#fff");
        effect.markAsApplied();
        assertTrue(effect.isApplied());
    }
}
