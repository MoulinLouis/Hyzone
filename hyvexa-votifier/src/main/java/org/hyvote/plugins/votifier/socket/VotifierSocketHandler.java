package org.hyvote.plugins.votifier.socket;

import com.google.gson.Gson;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.ProtocolConfig;
import org.hyvote.plugins.votifier.crypto.CryptoUtil;
import org.hyvote.plugins.votifier.crypto.VoteDecryptionException;
import org.hyvote.plugins.votifier.http.VoteProcessor;
import org.hyvote.plugins.votifier.vote.V2ChallengeException;
import org.hyvote.plugins.votifier.vote.V2SignatureException;
import org.hyvote.plugins.votifier.vote.V2VoteParser;
import org.hyvote.plugins.votifier.vote.Vote;
import org.hyvote.plugins.votifier.vote.VoteParseException;
import org.hyvote.plugins.votifier.vote.VoteParser;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;

/**
 * Handles a single Votifier socket connection, supporting both V1 and V2 protocols.
 *
 * <p>Protocol detection uses the first two bytes after the greeting:</p>
 * <ul>
 *   <li>V2: magic bytes 0x733A followed by length + JSON payload</li>
 *   <li>V1: 256 bytes of RSA-encrypted data (first 2 bytes are part of the payload)</li>
 * </ul>
 */
public class VotifierSocketHandler implements Runnable {

    /**
     * V2 protocol magic bytes (0x733A in big-endian).
     */
    private static final int V2_MAGIC = 0x733A;

    /**
     * V1 RSA-encrypted payload size (2048-bit key = 256 bytes).
     */
    private static final int V1_RSA_BLOCK_SIZE = 256;

    /**
     * Maximum message length (64KB).
     */
    private static final int MAX_MESSAGE_LENGTH = 65536;

    /**
     * Socket timeout in milliseconds (30 seconds).
     */
    private static final int SOCKET_TIMEOUT_MS = 30000;

    /**
     * Challenge length in bytes (before Base64 encoding).
     */
    private static final int CHALLENGE_BYTES = 24;

    private static final Gson GSON = new Gson();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final HytaleVotifierPlugin plugin;
    private final Socket socket;

    /**
     * Creates a new socket handler.
     *
     * @param plugin the plugin instance
     * @param socket the client socket
     */
    public VotifierSocketHandler(HytaleVotifierPlugin plugin, Socket socket) {
        this.plugin = plugin;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            handleConnection();
        } catch (SocketTimeoutException e) {
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.WARNING).log("Socket connection timed out from %s",
                        socket.getRemoteSocketAddress());
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Error handling socket connection from %s: %s: %s",
                    socket.getRemoteSocketAddress(), e.getClass().getSimpleName(), e.getMessage());
        } finally {
            closeSocket();
        }
    }

    private void handleConnection() throws IOException {
        // Generate challenge
        String challenge = generateChallenge();

        // Send greeting
        Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        writer.write("VOTIFIER 2 " + challenge + "\n");
        writer.flush();

        // Read first 2 bytes to detect protocol
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        int magic = dis.readShort() & 0xFFFF;

        if (magic == V2_MAGIC) {
            handleV2(dis, writer, challenge);
        } else {
            handleV1(dis, writer, magic);
        }
    }

    private void handleV1(DataInputStream dis, Writer writer, int firstTwoBytes) throws IOException {
        ProtocolConfig protocols = plugin.getConfig().protocols();
        if (protocols == null || !Boolean.TRUE.equals(protocols.v1Enabled())) {
            sendError(writer, "V1 protocol is disabled");
            plugin.getLogger().at(Level.WARNING).log("Rejected V1 socket vote from %s - V1 protocol disabled",
                    socket.getRemoteSocketAddress());
            return;
        }

        // First 2 bytes are already read as part of the 256-byte RSA block
        byte[] encrypted = new byte[V1_RSA_BLOCK_SIZE];
        encrypted[0] = (byte) (firstTwoBytes >> 8);
        encrypted[1] = (byte) (firstTwoBytes);
        dis.readFully(encrypted, 2, V1_RSA_BLOCK_SIZE - 2);

        Vote vote;
        try {
            byte[] decrypted = CryptoUtil.decrypt(encrypted, plugin.getKeyManager().getPrivateKey());
            vote = VoteParser.parse(decrypted);
        } catch (VoteDecryptionException e) {
            sendError(writer, "Decryption failed");
            plugin.getLogger().at(Level.WARNING).log("V1 decryption error from %s: %s",
                    socket.getRemoteSocketAddress(), e.getMessage());
            return;
        } catch (VoteParseException e) {
            sendError(writer, "Invalid vote format: " + e.getMessage());
            plugin.getLogger().at(Level.WARNING).log("V1 parse error from %s: %s",
                    socket.getRemoteSocketAddress(), e.getMessage());
            return;
        }

        processVote(vote);
        sendSuccess(writer);

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log("Received V1 socket vote from %s: service=%s, username=%s",
                    socket.getRemoteSocketAddress(), vote.serviceName(), vote.username());
        }
    }

    private void handleV2(DataInputStream dis, Writer writer, String challenge) throws IOException {
        // Read message length
        int length = dis.readShort() & 0xFFFF;
        if (length <= 0 || length > MAX_MESSAGE_LENGTH) {
            sendError(writer, "Invalid message length");
            plugin.getLogger().at(Level.WARNING).log("Invalid V2 message length from %s: %d",
                    socket.getRemoteSocketAddress(), length);
            return;
        }

        // Read JSON payload
        byte[] payload = new byte[length];
        dis.readFully(payload);
        String jsonPayload = new String(payload, StandardCharsets.UTF_8);

        // Parse and validate vote
        Vote vote;
        try {
            vote = V2VoteParser.parse(jsonPayload, plugin.getConfig().voteSites(), challenge);
        } catch (VoteParseException e) {
            sendError(writer, "Invalid vote format: " + e.getMessage());
            plugin.getLogger().at(Level.WARNING).log("V2 parse error from %s: %s",
                    socket.getRemoteSocketAddress(), e.getMessage());
            return;
        } catch (V2SignatureException e) {
            sendError(writer, "Signature verification failed");
            plugin.getLogger().at(Level.WARNING).log("V2 signature error from %s: %s",
                    socket.getRemoteSocketAddress(), e.getMessage());
            return;
        } catch (V2ChallengeException e) {
            sendError(writer, "Challenge verification failed");
            plugin.getLogger().at(Level.WARNING).log("V2 challenge error from %s: %s",
                    socket.getRemoteSocketAddress(), e.getMessage());
            return;
        }

        processVote(vote);
        sendSuccess(writer);

        if (plugin.getConfig().debug()) {
            plugin.getLogger().at(Level.INFO).log("Received V2 socket vote from %s: service=%s, username=%s",
                    socket.getRemoteSocketAddress(), vote.serviceName(), vote.username());
        }
    }

    private String generateChallenge() {
        byte[] bytes = new byte[CHALLENGE_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private void processVote(Vote vote) {
        VoteProcessor.dispatchVote(plugin, vote);
    }

    private void sendSuccess(Writer writer) throws IOException {
        V2Response response = new V2Response("ok", null, null);
        writer.write(GSON.toJson(response));
        writer.flush();
    }

    private void sendError(Writer writer, String message) throws IOException {
        V2Response response = new V2Response("error", message, message);
        writer.write(GSON.toJson(response));
        writer.flush();
    }

    private void closeSocket() {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore close errors
        }
    }

    /**
     * V2 protocol response format.
     */
    private record V2Response(String status, String cause, String errorMessage) {}
}
