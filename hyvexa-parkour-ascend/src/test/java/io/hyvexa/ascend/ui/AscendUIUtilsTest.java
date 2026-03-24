package io.hyvexa.ascend.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AscendUIUtilsTest {

    @Test
    void getRankAccentColorReturnsCorrectColorsForKnownRanks() {
        assertEquals(AscendUIUtils.COLOR_RANK_1, AscendUIUtils.getRankAccentColor(1));
        assertEquals(AscendUIUtils.COLOR_RANK_2, AscendUIUtils.getRankAccentColor(2));
        assertEquals(AscendUIUtils.COLOR_RANK_3, AscendUIUtils.getRankAccentColor(3));
    }

    @Test
    void getRankAccentColorReturnsDefaultForOtherRanks() {
        assertEquals(AscendUIUtils.COLOR_RANK_DEFAULT, AscendUIUtils.getRankAccentColor(0));
        assertEquals(AscendUIUtils.COLOR_RANK_DEFAULT, AscendUIUtils.getRankAccentColor(4));
        assertEquals(AscendUIUtils.COLOR_RANK_DEFAULT, AscendUIUtils.getRankAccentColor(-1));
        assertEquals(AscendUIUtils.COLOR_RANK_DEFAULT, AscendUIUtils.getRankAccentColor(100));
    }

    @Test
    void allColorConstantsAreValidHexFormat() {
        String[] colors = {
            AscendUIUtils.COLOR_RANK_1,
            AscendUIUtils.COLOR_RANK_2,
            AscendUIUtils.COLOR_RANK_3,
            AscendUIUtils.COLOR_RANK_DEFAULT
        };
        for (String color : colors) {
            assertNotNull(color);
            assertTrue(color.startsWith("#"), "Color should start with #: " + color);
            assertEquals(7, color.length(), "Color should be 7 chars (#RRGGBB): " + color);
            // Validate hex digits after #
            assertTrue(color.substring(1).matches("[0-9a-fA-F]{6}"),
                "Color should contain valid hex digits: " + color);
        }
    }
}
