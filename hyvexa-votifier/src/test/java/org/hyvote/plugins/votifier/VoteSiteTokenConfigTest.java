package org.hyvote.plugins.votifier;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteSiteTokenConfigTest {

    @Test
    void defaultsProducesDisabledEmptyConfig() {
        VoteSiteTokenConfig config = VoteSiteTokenConfig.defaults();

        assertFalse(config.isV2Enabled());
        assertNull(config.getToken("any"));
        assertTrue(config.tokens().isEmpty());
    }

    @Test
    void constructorNormalizesServiceNamesToLowercase() {
        VoteSiteTokenConfig config = new VoteSiteTokenConfig(Map.of("MySite", "secret"));

        assertEquals("secret", config.getToken("mysite"));
        assertEquals("secret", config.getToken("MYSITE"));
        assertTrue(config.isV2Enabled());
    }

    @Test
    void constructorReturnsUnmodifiableTokenMap() {
        VoteSiteTokenConfig config = new VoteSiteTokenConfig(Map.of("Site", "token"));

        assertThrows(UnsupportedOperationException.class, () -> config.tokens().put("other", "value"));
    }

    @Test
    void constructorTreatsNullAndEmptyMapsAsDisabled() {
        VoteSiteTokenConfig nullConfig = new VoteSiteTokenConfig(null);
        VoteSiteTokenConfig emptyConfig = new VoteSiteTokenConfig(Map.of());

        assertFalse(nullConfig.isV2Enabled());
        assertFalse(emptyConfig.isV2Enabled());
        assertNull(nullConfig.getToken(null));
        assertNull(emptyConfig.getToken("missing"));
    }
}
