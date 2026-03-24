package io.hyvexa.ascend.mine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineBlockDisplayTest {

    @Test
    void getItemIdStripsNamespace() {
        assertEquals("Stone_Block", MineBlockDisplay.getItemId("hytale:Stone_Block"));
        assertEquals("Stone_Block", MineBlockDisplay.getItemId("Stone_Block"));
    }

    @Test
    void getItemIdReturnsEmptyForNull() {
        assertEquals("", MineBlockDisplay.getItemId(null));
    }

    @Test
    void getDisplayNameCapitalizesTokens() {
        // "hytale:dark_stone_block" -> not in registry -> strips namespace -> "dark_stone_block" -> "Dark Stone Block"
        assertEquals("Dark Stone Block", MineBlockDisplay.getDisplayName("hytale:dark_stone_block"));
        assertEquals("Stone", MineBlockDisplay.getDisplayName("hytale:stone"));
    }

    @Test
    void getDisplayNameReturnsUnknownForNullOrEmpty() {
        assertEquals("Unknown", MineBlockDisplay.getDisplayName(null));
        assertEquals("Unknown", MineBlockDisplay.getDisplayName(""));
    }

    @Test
    void getDisplayNameUsesRegisteredNameWhenAvailable() {
        // "Rock_Stone" is registered with display name "Stone" in MineBlockRegistry
        assertEquals("Stone", MineBlockDisplay.getDisplayName("Rock_Stone"));
    }
}
