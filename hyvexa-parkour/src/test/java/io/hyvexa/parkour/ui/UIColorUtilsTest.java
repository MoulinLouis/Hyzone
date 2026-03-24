package io.hyvexa.parkour.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UIColorUtilsTest {

    @Test
    void getCategoryAccentColorReturnsCorrectColorForEasyCategory() {
        assertEquals(UIColorUtils.COLOR_EASY, UIColorUtils.getCategoryAccentColor("easy"));
        assertEquals(UIColorUtils.COLOR_EASY, UIColorUtils.getCategoryAccentColor("beginner"));
    }

    @Test
    void getCategoryAccentColorReturnsCorrectColorForMediumCategory() {
        assertEquals(UIColorUtils.COLOR_MEDIUM, UIColorUtils.getCategoryAccentColor("medium"));
        assertEquals(UIColorUtils.COLOR_MEDIUM, UIColorUtils.getCategoryAccentColor("normal"));
        assertEquals(UIColorUtils.COLOR_MEDIUM, UIColorUtils.getCategoryAccentColor("intermediate"));
    }

    @Test
    void getCategoryAccentColorReturnsCorrectColorForHardCategory() {
        assertEquals(UIColorUtils.COLOR_HARD, UIColorUtils.getCategoryAccentColor("hard"));
        assertEquals(UIColorUtils.COLOR_HARD, UIColorUtils.getCategoryAccentColor("difficult"));
    }

    @Test
    void getCategoryAccentColorReturnsCorrectColorForInsaneCategory() {
        assertEquals(UIColorUtils.COLOR_INSANE, UIColorUtils.getCategoryAccentColor("insane"));
        assertEquals(UIColorUtils.COLOR_INSANE, UIColorUtils.getCategoryAccentColor("extreme"));
        assertEquals(UIColorUtils.COLOR_INSANE, UIColorUtils.getCategoryAccentColor("expert"));
    }

    @Test
    void getCategoryAccentColorIsCaseInsensitive() {
        assertEquals(UIColorUtils.getCategoryAccentColor("easy"), UIColorUtils.getCategoryAccentColor("EASY"));
        assertEquals(UIColorUtils.getCategoryAccentColor("hard"), UIColorUtils.getCategoryAccentColor("Hard"));
    }

    @Test
    void getCategoryAccentColorTrimsWhitespace() {
        assertEquals(UIColorUtils.getCategoryAccentColor("easy"), UIColorUtils.getCategoryAccentColor("  easy  "));
    }

    @Test
    void getCategoryAccentColorReturnsDefaultForNullOrUnknown() {
        assertEquals(UIColorUtils.COLOR_DEFAULT, UIColorUtils.getCategoryAccentColor(null));
        assertEquals(UIColorUtils.COLOR_DEFAULT, UIColorUtils.getCategoryAccentColor("unknown_category"));
        assertEquals(UIColorUtils.COLOR_DEFAULT, UIColorUtils.getCategoryAccentColor(""));
    }

    @Test
    void getRankAccentColorReturnsCorrectColors() {
        assertEquals(UIColorUtils.COLOR_RANK_1, UIColorUtils.getRankAccentColor(1));
        assertEquals(UIColorUtils.COLOR_RANK_2, UIColorUtils.getRankAccentColor(2));
        assertEquals(UIColorUtils.COLOR_RANK_3, UIColorUtils.getRankAccentColor(3));
        assertEquals(UIColorUtils.COLOR_RANK_DEFAULT, UIColorUtils.getRankAccentColor(0));
        assertEquals(UIColorUtils.COLOR_RANK_DEFAULT, UIColorUtils.getRankAccentColor(4));
        assertEquals(UIColorUtils.COLOR_RANK_DEFAULT, UIColorUtils.getRankAccentColor(-1));
    }

    @Test
    void allColorConstantsAreValidHexFormat() {
        String hexPattern = "#[0-9a-fA-F]{6}";
        assertTrue(UIColorUtils.COLOR_EASY.matches(hexPattern));
        assertTrue(UIColorUtils.COLOR_MEDIUM.matches(hexPattern));
        assertTrue(UIColorUtils.COLOR_HARD.matches(hexPattern));
        assertTrue(UIColorUtils.COLOR_INSANE.matches(hexPattern));
        assertTrue(UIColorUtils.COLOR_DEFAULT.matches(hexPattern));
        assertTrue(UIColorUtils.COLOR_RANK_1.matches(hexPattern));
        assertTrue(UIColorUtils.COLOR_RANK_2.matches(hexPattern));
        assertTrue(UIColorUtils.COLOR_RANK_3.matches(hexPattern));
        assertTrue(UIColorUtils.COLOR_RANK_DEFAULT.matches(hexPattern));
        assertTrue(UIColorUtils.COLOR_GLOBAL.matches(hexPattern));
    }
}
