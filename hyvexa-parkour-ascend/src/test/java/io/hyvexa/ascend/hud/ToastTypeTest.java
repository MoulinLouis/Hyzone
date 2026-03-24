package io.hyvexa.ascend.hud;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToastTypeTest {

    @Test
    void allToastTypesHaveNonBlankSuffix() {
        for (ToastType type : ToastType.values()) {
            assertNotNull(type.getSuffix(), type.name() + " suffix is null");
            assertFalse(type.getSuffix().isBlank(), type.name() + " suffix is blank");
        }
    }

    @Test
    void suffixesAreUniqueAcrossTypes() {
        Set<String> suffixes = new HashSet<>();
        for (ToastType type : ToastType.values()) {
            suffixes.add(type.getSuffix());
        }
        assertEquals(ToastType.values().length, suffixes.size(), "Duplicate suffixes found");
    }
}
