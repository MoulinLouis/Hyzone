package org.hyvote.plugins.votifier;

/**
 * Configuration for vote protocol support.
 *
 * <p>Controls which vote protocols are enabled:</p>
 * <ul>
 *   <li>V1 - RSA-encrypted votes over HTTP</li>
 *   <li>V2 - HMAC-SHA256 signed votes over HTTP or socket</li>
 * </ul>
 *
 * <p>If V1 is disabled, the HTTP server (both Nitrado:WebServer and fallback) will not be started,
 * as V2 can operate via the socket server instead.</p>
 *
 * @param v1Enabled Whether V1 protocol (RSA over HTTP) is enabled (default true)
 * @param v2Enabled Whether V2 protocol (HMAC over HTTP/socket) is enabled (default true)
 */
public record ProtocolConfig(Boolean v1Enabled, Boolean v2Enabled) {

    /**
     * Returns a ProtocolConfig with default values (both protocols enabled).
     *
     * @return default configuration
     */
    public static ProtocolConfig defaults() {
        return new ProtocolConfig(true, true);
    }

    /**
     * Merges this config with defaults for any unset values.
     *
     * @param defaults the default configuration
     * @return merged configuration with defaults applied for null values
     */
    public ProtocolConfig merge(ProtocolConfig defaults) {
        return new ProtocolConfig(
                this.v1Enabled != null ? this.v1Enabled : defaults.v1Enabled(),
                this.v2Enabled != null ? this.v2Enabled : defaults.v2Enabled()
        );
    }
}
