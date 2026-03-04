package org.hyvote.plugins.votifier.http;

import com.google.gson.Gson;
import com.hypixel.hytale.server.core.HytaleServer;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.crypto.CryptoUtil;
import org.hyvote.plugins.votifier.crypto.VoteDecryptionException;
import org.hyvote.plugins.votifier.event.VoteEvent;
import org.hyvote.plugins.votifier.reminder.VoteReminderService;
import org.hyvote.plugins.votifier.util.BroadcastUtil;
import org.hyvote.plugins.votifier.util.RewardCommandUtil;
import org.hyvote.plugins.votifier.util.VoteNotificationUtil;
import org.hyvote.plugins.votifier.vote.ProtocolDetector;
import org.hyvote.plugins.votifier.vote.ProtocolDetector.Protocol;
import org.hyvote.plugins.votifier.vote.V2SignatureException;
import org.hyvote.plugins.votifier.vote.V2VoteParser;
import org.hyvote.plugins.votifier.vote.Vote;
import org.hyvote.plugins.votifier.vote.VoteParseException;
import org.hyvote.plugins.votifier.vote.VoteParser;

import java.util.Base64;

/**
 * Shared vote processing logic used by both VoteServlet and FallbackHttpServer.
 *
 * <p>This utility class handles:</p>
 * <ul>
 *   <li>Protocol detection (V1 RSA vs V2 JSON)</li>
 *   <li>Vote parsing and decryption</li>
 *   <li>Vote event firing and reward processing</li>
 *   <li>JSON response building</li>
 * </ul>
 */
public final class VoteProcessor {

    private static final Gson GSON = new Gson();

    private VoteProcessor() {
        // Utility class
    }

    // ==================== JSON Response Records ====================

    /**
     * Standard success response.
     */
    public record SuccessResponse(String status, String message) {
        public SuccessResponse(String message) {
            this("ok", message);
        }
    }

    /**
     * Standard error response.
     */
    public record ErrorResponse(String status, String message) {
        public ErrorResponse(String message) {
            this("error", message);
        }
    }

    /**
     * Protocol status information.
     */
    public record ProtocolStatus(boolean v1, boolean v2) {}

    /**
     * Server status response.
     */
    public record StatusResponse(String status, String version, String serverType, ProtocolStatus protocols) {
        public StatusResponse(String version, boolean v1Enabled, boolean v2Enabled) {
            this("ok", version, "HytaleVotifier", new ProtocolStatus(v1Enabled, v2Enabled));
        }
    }

    // ==================== JSON Response Builders ====================

    /**
     * Builds a success JSON response.
     *
     * @param message the success message
     * @return JSON string
     */
    public static String successJson(String message) {
        return GSON.toJson(new SuccessResponse(message));
    }

    /**
     * Builds an error JSON response.
     *
     * @param message the error message
     * @return JSON string
     */
    public static String errorJson(String message) {
        return GSON.toJson(new ErrorResponse(message));
    }

    /**
     * Builds a status JSON response.
     *
     * @param version   the plugin version
     * @param v1Enabled whether V1 protocol is enabled
     * @param v2Enabled whether V2 protocol is enabled
     * @return JSON string
     */
    public static String statusJson(String version, boolean v1Enabled, boolean v2Enabled) {
        return GSON.toJson(new StatusResponse(version, v1Enabled, v2Enabled));
    }

    /**
     * Result of vote processing, containing either a successful vote or an error.
     */
    public sealed interface VoteResult {
        record Success(Vote vote, Protocol protocol) implements VoteResult {}
        record EmptyPayload() implements VoteResult {}
        record UnknownProtocol() implements VoteResult {}
        record ParseError(Protocol protocol, String message) implements VoteResult {}
        record SignatureError(String message) implements VoteResult {}
        record DecryptionError(String message) implements VoteResult {}
        record InternalError(Exception cause) implements VoteResult {}
    }

    /**
     * Processes a vote payload and returns the result.
     *
     * <p>This method handles protocol detection, parsing, and decryption but does NOT
     * fire events or process rewards. Call {@link #dispatchVote} after successful processing.</p>
     *
     * @param plugin  the plugin instance
     * @param payload the raw vote payload
     * @return the processing result
     */
    public static VoteResult processPayload(HytaleVotifierPlugin plugin, String payload) {
        // Validate payload is not empty
        if (payload == null || payload.isEmpty()) {
            return new VoteResult.EmptyPayload();
        }

        // Detect protocol
        Protocol protocol = ProtocolDetector.detect(payload);
        if (protocol == Protocol.UNKNOWN) {
            return new VoteResult.UnknownProtocol();
        }

        // Process vote based on detected protocol
        try {
            Vote vote = switch (protocol) {
                case V2_JSON -> processV2Vote(payload, plugin);
                case V1_RSA -> processV1Vote(payload, plugin);
                case UNKNOWN -> throw new VoteParseException("Unable to detect vote protocol");
            };
            return new VoteResult.Success(vote, protocol);
        } catch (VoteParseException e) {
            return new VoteResult.ParseError(protocol, e.getMessage());
        } catch (V2SignatureException e) {
            return new VoteResult.SignatureError(e.getMessage());
        } catch (VoteDecryptionException e) {
            return new VoteResult.DecryptionError(e.getMessage());
        } catch (Exception e) {
            return new VoteResult.InternalError(e);
        }
    }

    /**
     * Dispatches a vote by firing events and processing rewards/notifications.
     *
     * @param plugin the plugin instance
     * @param vote   the vote to dispatch
     */
    public static void dispatchVote(HytaleVotifierPlugin plugin, Vote vote) {
        // Fire vote event for other plugins to handle rewards
        VoteEvent voteEvent = new VoteEvent(plugin, vote);
        HytaleServer.get().getEventBus().dispatchFor(VoteEvent.class, plugin.getClass()).dispatch(voteEvent);

        // Record vote in reminder service (cancels any pending reminders for this player)
        VoteReminderService reminderService = plugin.getVoteReminderService();
        if (reminderService != null) {
            reminderService.recordVote(vote.username());
        }

        // Display toast notification to the player if enabled
        VoteNotificationUtil.displayVoteToast(plugin, vote);

        // Broadcast vote announcement to all online players if enabled
        BroadcastUtil.broadcastVote(plugin, vote);

        // Execute reward commands
        RewardCommandUtil.executeRewardCommands(plugin, vote);
    }

    /**
     * Processes a V1 (RSA-encrypted) vote payload.
     */
    private static Vote processV1Vote(String payload, HytaleVotifierPlugin plugin)
            throws VoteDecryptionException, VoteParseException {
        // Decode Base64 payload
        byte[] encryptedBytes;
        try {
            encryptedBytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new VoteParseException("Invalid Base64 encoding", e);
        }

        // Decrypt with RSA private key
        byte[] decryptedBytes = CryptoUtil.decrypt(encryptedBytes, plugin.getKeyManager().getPrivateKey());

        // Parse vote data
        return VoteParser.parse(decryptedBytes);
    }

    /**
     * Processes a V2 (HMAC-SHA256 signed) vote payload.
     */
    private static Vote processV2Vote(String payload, HytaleVotifierPlugin plugin)
            throws VoteParseException, V2SignatureException {
        return V2VoteParser.parse(payload, plugin.getConfig().voteSites());
    }

}
