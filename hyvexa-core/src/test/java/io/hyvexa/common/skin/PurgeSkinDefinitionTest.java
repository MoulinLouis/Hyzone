package io.hyvexa.common.skin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PurgeSkinDefinitionTest {

    @Test
    void constructorSetsItemId() {
        PurgeSkinDefinition def = new PurgeSkinDefinition("AK47", "Asimov", "Asimov", 100);
        assertEquals("AK47_Asimov", def.getItemId());
    }

    @Test
    void constructorSetsFields() {
        PurgeSkinDefinition def = new PurgeSkinDefinition("Shotgun", "Gold", "Gold", 200);
        assertEquals("Shotgun", def.getWeaponId());
        assertEquals("Gold", def.getSkinId());
        assertEquals("Gold", def.getDisplayName());
        assertEquals(200, def.getPrice());
    }

    @Test
    void setPriceNegativeClampsToZero() {
        PurgeSkinDefinition def = new PurgeSkinDefinition("AK47", "Test", "Test", 100);
        def.setPrice(-5);
        assertEquals(0, def.getPrice());
    }

    @Test
    void setPriceNormal() {
        PurgeSkinDefinition def = new PurgeSkinDefinition("AK47", "Test", "Test", 100);
        def.setPrice(50);
        assertEquals(50, def.getPrice());
    }
}
