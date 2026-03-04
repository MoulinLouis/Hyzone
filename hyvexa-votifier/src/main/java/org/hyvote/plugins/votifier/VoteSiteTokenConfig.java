package org.hyvote.plugins.votifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Configuration for Votifier V2 protocol service tokens.
 * Maps vote site service names to their shared secret tokens for HMAC-SHA256 verification.
 * Service name lookups are case-insensitive.
 *
 * @param tokens Map of service names (lowercase) to their authentication tokens
 */
public record VoteSiteTokenConfig(Map<String, String> tokens) {

    /**
     * Returns a VoteSiteTokenConfig with default values (empty map).
     *
     * @return default configuration with no services configured
     */
    public static VoteSiteTokenConfig defaults() {
        return new VoteSiteTokenConfig(Collections.emptyMap());
    }

    /**
     * Compact constructor that normalizes service names to lowercase.
     */
    public VoteSiteTokenConfig {
        if (tokens == null || tokens.isEmpty()) {
            tokens = Collections.emptyMap();
        } else {
            Map<String, String> normalized = new HashMap<>();
            tokens.forEach((k, v) -> normalized.put(k.toLowerCase(Locale.ROOT), v));
            tokens = Collections.unmodifiableMap(normalized);
        }
    }

    /**
     * Gets the token for a given service name (case-insensitive lookup).
     *
     * @param serviceName the service name to look up
     * @return the token, or null if not configured
     */
    public String getToken(String serviceName) {
        return serviceName == null ? null : tokens.get(serviceName.toLowerCase(Locale.ROOT));
    }

    /**
     * Checks if V2 protocol is enabled (at least one service configured).
     *
     * @return true if at least one vote site token is configured
     */
    public boolean isV2Enabled() {
        return !tokens.isEmpty();
    }
}
