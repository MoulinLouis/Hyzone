package io.hyvexa.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssetPathUtilsTest {

    @Test
    void nullReturnsNull() {
        assertNull(AssetPathUtils.normalizeIconAssetPath(null));
    }

    @Test
    void blankReturnsNull() {
        assertNull(AssetPathUtils.normalizeIconAssetPath(""));
        assertNull(AssetPathUtils.normalizeIconAssetPath("   "));
    }

    @Test
    void backslashesConvertedToForwardSlashes() {
        String result = AssetPathUtils.normalizeIconAssetPath("UI\\Icons\\star.png");
        assertEquals("UI/Icons/star.png", result);
    }

    @Test
    void parentDirPrefixesStripped() {
        String result = AssetPathUtils.normalizeIconAssetPath("../../Textures/foo.png");
        assertEquals("UI/Custom/Textures/foo.png", result);
    }

    @Test
    void commonPrefixRemoved() {
        String result = AssetPathUtils.normalizeIconAssetPath("Common/Textures/bar.png");
        assertEquals("UI/Custom/Textures/bar.png", result);
    }

    @Test
    void texturesPrefixWrapped() {
        String result = AssetPathUtils.normalizeIconAssetPath("Textures/foo.png");
        assertEquals("UI/Custom/Textures/foo.png", result);
    }

    @Test
    void nonMatchingPathPassesThrough() {
        String result = AssetPathUtils.normalizeIconAssetPath("UI/Icons/star.png");
        assertEquals("UI/Icons/star.png", result);
    }

    @Test
    void combinedParentAndCommonAndTextures() {
        String result = AssetPathUtils.normalizeIconAssetPath("../Common/Textures/icon.png");
        assertEquals("UI/Custom/Textures/icon.png", result);
    }
}
