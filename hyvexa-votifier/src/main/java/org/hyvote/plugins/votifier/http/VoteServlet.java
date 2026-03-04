package org.hyvote.plugins.votifier.http;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.http.VoteProcessor.VoteResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * HTTP endpoint for receiving vote notifications.
 *
 * <p>Endpoint: POST /Hyvote/HytaleVotifier/vote</p>
 *
 * <p>Supports both Votifier V1 (RSA encryption) and V2 (HMAC-SHA256) protocols.
 * The protocol is auto-detected based on the payload format.</p>
 *
 * <p>V1 Protocol: Base64-encoded RSA-encrypted vote data</p>
 * <p>V2 Protocol: JSON with "payload" and "signature" fields</p>
 *
 * <p>Response codes:</p>
 * <ul>
 *   <li>200 OK - Vote received and processed successfully</li>
 *   <li>400 Bad Request - Empty payload, invalid format, or parse error</li>
 *   <li>401 Unauthorized - V2 signature verification failed</li>
 *   <li>413 Payload Too Large - Request body exceeds maximum size</li>
 *   <li>500 Internal Server Error - Unexpected server error</li>
 * </ul>
 */
public class VoteServlet extends HttpServlet {

    /**
     * Maximum allowed request body size (32KB).
     * Vote payloads are typically small (a few hundred bytes), so this is generous.
     */
    private static final int MAX_BODY_SIZE = 32 * 1024;

    private final HytaleVotifierPlugin plugin;

    public VoteServlet(HytaleVotifierPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");

        // Read request body
        String payload;
        try {
            payload = readRequestBody(req);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("exceeds maximum size")) {
                sendError(resp, 413, "Request body too large");
                plugin.getLogger().at(Level.WARNING).log("Rejected vote request: payload too large");
                return;
            }
            throw e;
        }

        // Process the vote using shared logic
        VoteResult result = VoteProcessor.processPayload(plugin, payload);

        // Handle result
        switch (result) {
            case VoteResult.Success success -> {
                VoteProcessor.dispatchVote(plugin, success.vote());
                if (plugin.getConfig().debug()) {
                    plugin.getLogger().at(Level.INFO).log("Received %s vote from %s: service=%s, username=%s",
                            success.protocol(), req.getRemoteAddr(), success.vote().serviceName(), success.vote().username());
                }
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().println(VoteProcessor.successJson("Vote processed for " + success.vote().username()));
            }
            case VoteResult.EmptyPayload() -> {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Empty payload");
                plugin.getLogger().at(Level.WARNING).log("Rejected vote request: empty payload");
            }
            case VoteResult.UnknownProtocol() -> {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unable to detect vote protocol");
                plugin.getLogger().at(Level.WARNING).log("Rejected vote request: unknown protocol");
            }
            case VoteResult.ParseError parseError -> {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid vote format");
                plugin.getLogger().at(Level.WARNING).log("Rejected %s vote request: %s", parseError.protocol(), parseError.message());
            }
            case VoteResult.SignatureError signatureError -> {
                sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Signature verification failed");
                plugin.getLogger().at(Level.WARNING).log("Rejected V2 vote: %s", signatureError.message());
            }
            case VoteResult.DecryptionError decryptionError -> {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid vote payload");
                plugin.getLogger().at(Level.WARNING).log("Rejected V1 vote: decryption failed - %s", decryptionError.message());
            }
            case VoteResult.InternalError internalError -> {
                sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
                plugin.getLogger().at(Level.SEVERE).withCause(internalError.cause()).log("Failed to process vote request");
            }
        }
    }

    /**
     * Reads the request body as a trimmed string.
     *
     * @throws IOException if reading fails or body exceeds {@link #MAX_BODY_SIZE}
     */
    private String readRequestBody(HttpServletRequest req) throws IOException {
        try (var inputStream = req.getInputStream()) {
            byte[] bytes = inputStream.readNBytes(MAX_BODY_SIZE + 1);
            if (bytes.length > MAX_BODY_SIZE) {
                throw new IOException("Request body exceeds maximum size of " + MAX_BODY_SIZE + " bytes");
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        }
    }

    private void sendError(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.getWriter().println(VoteProcessor.errorJson(message));
    }
}
