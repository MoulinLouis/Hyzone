package org.hyvote.plugins.votifier.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.http.VoteProcessor.VoteResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Fallback HTTP server for when the Nitrado:WebServer plugin is not available.
 *
 * <p>Uses Java's built-in {@link HttpServer} to provide the same HTTP endpoints
 * as the Nitrado WebServer integration.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /Hyvote/HytaleVotifier/status - Health check and server status</li>
 *   <li>POST /Hyvote/HytaleVotifier/vote - Receive vote notifications</li>
 * </ul>
 */
public class FallbackHttpServer {

    private static final String CONTEXT_PATH = "/Hyvote/HytaleVotifier";

    /**
     * Maximum allowed request body size (32KB).
     * Vote payloads are typically small (a few hundred bytes), so this is generous.
     */
    private static final int MAX_BODY_SIZE = 32 * 1024;

    private final HytaleVotifierPlugin plugin;
    private final int port;
    private volatile HttpServer server;
    private volatile boolean running = false;

    public FallbackHttpServer(HytaleVotifierPlugin plugin, int port) {
        this.plugin = plugin;
        this.port = port;
    }

    /**
     * Starts the HTTP server.
     *
     * @throws IOException if the server fails to start
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(CONTEXT_PATH + "/status", this::handleStatus);
        server.createContext(CONTEXT_PATH + "/vote", this::handleVote);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        running = true;

        plugin.getLogger().at(Level.INFO).log(
                "Fallback HTTP server started on port %d - endpoints at %s/vote and %s/status",
                port, CONTEXT_PATH, CONTEXT_PATH);
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            running = false;
            plugin.getLogger().at(Level.INFO).log("Fallback HTTP server stopped");
        }
    }

    /**
     * Returns whether the server is running.
     *
     * @return true if the server is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Handles GET /status requests.
     */
    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            boolean v1Enabled = plugin.getConfig().protocols() != null
                    && Boolean.TRUE.equals(plugin.getConfig().protocols().v1Enabled());
            boolean v2Enabled = plugin.getConfig().protocols() != null
                    && Boolean.TRUE.equals(plugin.getConfig().protocols().v2Enabled())
                    && plugin.getConfig().voteSites() != null
                    && plugin.getConfig().voteSites().isV2Enabled();

            sendResponse(exchange, 200, VoteProcessor.statusJson(plugin.getPluginVersion(), v1Enabled, v2Enabled));
        } catch (Exception e) {
            plugin.getLogger().at(Level.SEVERE).withCause(e).log("Failed to process status request");
            sendError(exchange, 500, "Internal server error");
        }
    }

    /**
     * Handles POST /vote requests.
     */
    private void handleVote(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        // Read request body
        String payload;
        try {
            payload = readRequestBody(exchange);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("exceeds maximum size")) {
                sendError(exchange, 413, "Request body too large");
                plugin.getLogger().at(Level.WARNING).log("Rejected vote request: payload too large");
                return;
            }
            throw e;
        }

        // Process the vote using shared logic
        VoteResult result = VoteProcessor.processPayload(plugin, payload);

        String remoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();

        // Handle result
        switch (result) {
            case VoteResult.Success success -> {
                VoteProcessor.dispatchVote(plugin, success.vote());
                if (plugin.getConfig().debug()) {
                    plugin.getLogger().at(Level.INFO).log("Received %s vote from %s: service=%s, username=%s",
                            success.protocol(), remoteAddress, success.vote().serviceName(), success.vote().username());
                }
                sendResponse(exchange, 200, VoteProcessor.successJson("Vote processed for " + success.vote().username()));
            }
            case VoteResult.EmptyPayload() -> {
                sendError(exchange, 400, "Empty payload");
                plugin.getLogger().at(Level.WARNING).log("Rejected vote request: empty payload");
            }
            case VoteResult.UnknownProtocol() -> {
                sendError(exchange, 400, "Unable to detect vote protocol");
                plugin.getLogger().at(Level.WARNING).log("Rejected vote request: unknown protocol");
            }
            case VoteResult.ParseError parseError -> {
                sendError(exchange, 400, "Invalid vote format");
                plugin.getLogger().at(Level.WARNING).log("Rejected %s vote request: %s", parseError.protocol(), parseError.message());
            }
            case VoteResult.SignatureError signatureError -> {
                sendError(exchange, 401, "Signature verification failed");
                plugin.getLogger().at(Level.WARNING).log("Rejected V2 vote: %s", signatureError.message());
            }
            case VoteResult.DecryptionError decryptionError -> {
                sendError(exchange, 400, "Invalid vote payload");
                plugin.getLogger().at(Level.WARNING).log("Rejected V1 vote: decryption failed - %s", decryptionError.message());
            }
            case VoteResult.InternalError internalError -> {
                sendError(exchange, 500, "Internal server error");
                plugin.getLogger().at(Level.SEVERE).withCause(internalError.cause()).log("Failed to process vote request");
            }
        }
    }

    /**
     * Reads the request body as a trimmed string.
     *
     * @throws IOException if reading fails or body exceeds {@link #MAX_BODY_SIZE}
     */
    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (var inputStream = exchange.getRequestBody()) {
            byte[] bytes = inputStream.readNBytes(MAX_BODY_SIZE + 1);
            if (bytes.length > MAX_BODY_SIZE) {
                throw new IOException("Request body exceeds maximum size of " + MAX_BODY_SIZE + " bytes");
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        }
    }

    /**
     * Sends a JSON response.
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Sends an error response.
     */
    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        sendResponse(exchange, statusCode, VoteProcessor.errorJson(message));
    }
}
