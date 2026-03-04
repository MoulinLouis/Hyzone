package org.hyvote.plugins.votifier;

/**
 * Configuration for the fallback HTTP server.
 *
 * <p>The fallback HTTP server is used when the Nitrado:WebServer plugin is not available.
 * It provides the same /vote and /status endpoints using Java's built-in HttpServer.</p>
 *
 * @param enabled Whether the fallback HTTP server is enabled (default true)
 * @param port    The port to listen on (default 8080)
 */
public record HttpServerConfig(boolean enabled, int port) {

    /**
     * Default port for the fallback HTTP server.
     */
    public static final int DEFAULT_PORT = 8080;

    /**
     * Returns an HttpServerConfig with default values.
     *
     * @return default configuration (enabled, port 8080)
     */
    public static HttpServerConfig defaults() {
        return new HttpServerConfig(true, DEFAULT_PORT);
    }

    /**
     * Merges this config with defaults for any null/missing values.
     *
     * @param defaults the default configuration
     * @return merged configuration
     */
    public HttpServerConfig merge(HttpServerConfig defaults) {
        return new HttpServerConfig(
                this.enabled,
                this.port > 0 ? this.port : defaults.port()
        );
    }
}
