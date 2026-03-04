package org.hyvote.plugins.votifier;

/**
 * Configuration for the Votifier V2 socket server.
 *
 * @param enabled Whether the socket server is enabled (default true)
 * @param port    The port to listen on (default 8192)
 */
public record SocketConfig(boolean enabled, int port) {

    /**
     * Default port for Votifier socket protocol.
     */
    public static final int DEFAULT_PORT = 8192;

    /**
     * Returns a SocketConfig with default values.
     *
     * @return default configuration (enabled, port 8192)
     */
    public static SocketConfig defaults() {
        return new SocketConfig(true, DEFAULT_PORT);
    }

    /**
     * Merges this config with defaults for any null/missing values.
     *
     * @param defaults the default configuration
     * @return merged configuration
     */
    public SocketConfig merge(SocketConfig defaults) {
        return new SocketConfig(
                this.enabled,
                this.port > 0 ? this.port : defaults.port()
        );
    }
}
