package io.hyvexa.parkour.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MedalTest {

    @Test
    void pointsAreStrictlyIncreasingByMedalRank() {
        assertTrue(Medal.BRONZE.getPoints() < Medal.SILVER.getPoints());
        assertTrue(Medal.SILVER.getPoints() < Medal.GOLD.getPoints());
        assertTrue(Medal.GOLD.getPoints() < Medal.EMERALD.getPoints());
        assertTrue(Medal.EMERALD.getPoints() < Medal.INSANE.getPoints());

        assertEquals(1, Medal.BRONZE.getPoints());
        assertEquals(2, Medal.SILVER.getPoints());
        assertEquals(3, Medal.GOLD.getPoints());
        assertEquals(4, Medal.EMERALD.getPoints());
        assertEquals(5, Medal.INSANE.getPoints());
    }

    @Test
    void allMedalsHaveNonBlankColor() {
        for (Medal medal : Medal.values()) {
            assertNotNull(medal.getColor());
            assertTrue(medal.getColor().startsWith("#"), medal.name() + " color should start with #");
            assertEquals(7, medal.getColor().length(), medal.name() + " color should be 7 chars (#RRGGBB)");
        }
    }

    @Test
    void allMedalsHaveNonBlankEffectId() {
        for (Medal medal : Medal.values()) {
            assertNotNull(medal.getEffectId(), medal.name() + " effectId should not be null");
            assertFalse(medal.getEffectId().isEmpty(), medal.name() + " effectId should not be empty");
        }
    }

    @Test
    void medalValuesAreInCorrectOrder() {
        Medal[] values = Medal.values();
        assertEquals(Medal.BRONZE, values[0]);
        assertEquals(Medal.SILVER, values[1]);
        assertEquals(Medal.GOLD, values[2]);
        assertEquals(Medal.EMERALD, values[3]);
        assertEquals(Medal.INSANE, values[4]);
    }

    @Test
    void medalEnumHasExactlyFiveValues() {
        assertEquals(5, Medal.values().length);
    }
}
