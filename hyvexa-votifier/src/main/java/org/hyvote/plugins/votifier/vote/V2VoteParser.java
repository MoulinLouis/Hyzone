package org.hyvote.plugins.votifier.vote;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.hyvote.plugins.votifier.VoteSiteTokenConfig;
import org.hyvote.plugins.votifier.crypto.HmacUtil;

/**
 * Parser for Votifier V2 protocol JSON payloads.
 *
 * <p>V2 protocol format:</p>
 * <pre>
 * {
 *   "payload": "{\"serviceName\":\"...\",\"username\":\"...\",\"address\":\"...\",\"timestamp\":...}",
 *   "signature": "&lt;base64 HMAC-SHA256&gt;"
 * }
 * </pre>
 *
 * <p>The payload field contains a stringified JSON object with the vote data.
 * The signature is an HMAC-SHA256 digest of the payload string, encoded as Base64.</p>
 */
public final class V2VoteParser {

    private static final Gson GSON = new Gson();

    private V2VoteParser() {
        // Utility class - prevent instantiation
    }

    /**
     * Parses and verifies a V2 protocol vote payload (HTTP mode, no challenge verification).
     *
     * @param jsonPayload the JSON string containing wrapper with payload and signature
     * @param voteSiteTokenConfig the vote site token configuration for signature verification
     * @return the parsed Vote
     * @throws VoteParseException if parsing fails due to invalid format
     * @throws V2SignatureException if signature verification fails or no token is configured
     */
    public static Vote parse(String jsonPayload, VoteSiteTokenConfig voteSiteTokenConfig)
            throws VoteParseException, V2SignatureException {
        try {
            return parse(jsonPayload, voteSiteTokenConfig, null);
        } catch (V2ChallengeException e) {
            // Should never happen when expectedChallenge is null
            throw new VoteParseException("Unexpected challenge error", e);
        }
    }

    /**
     * Parses and verifies a V2 protocol vote payload with optional challenge verification.
     *
     * @param jsonPayload the JSON string containing wrapper with payload and signature
     * @param voteSiteTokenConfig the vote site token configuration for signature verification
     * @param expectedChallenge the expected challenge string (null to skip challenge verification)
     * @return the parsed Vote
     * @throws VoteParseException if parsing fails due to invalid format
     * @throws V2SignatureException if signature verification fails or no token is configured
     * @throws V2ChallengeException if challenge verification fails
     */
    public static Vote parse(String jsonPayload, VoteSiteTokenConfig voteSiteTokenConfig, String expectedChallenge)
            throws VoteParseException, V2SignatureException, V2ChallengeException {

        // Parse outer wrapper
        V2Wrapper wrapper;
        try {
            wrapper = GSON.fromJson(jsonPayload, V2Wrapper.class);
        } catch (JsonSyntaxException e) {
            throw new VoteParseException("Invalid V2 JSON format: " + e.getMessage(), e);
        }

        if (wrapper == null) {
            throw new VoteParseException("V2 payload is null");
        }

        if (wrapper.payload() == null || wrapper.payload().isBlank()) {
            throw new VoteParseException("V2 payload missing required 'payload' field");
        }

        if (wrapper.signature() == null || wrapper.signature().isBlank()) {
            throw new VoteParseException("V2 payload missing required 'signature' field");
        }

        // Parse inner payload to extract service name for token lookup
        V2InnerPayload innerPayload;
        try {
            innerPayload = GSON.fromJson(wrapper.payload(), V2InnerPayload.class);
        } catch (JsonSyntaxException e) {
            throw new VoteParseException("Invalid V2 inner payload JSON: " + e.getMessage(), e);
        }

        if (innerPayload == null) {
            throw new VoteParseException("V2 inner payload is null");
        }

        // Validate required fields
        if (innerPayload.serviceName() == null || innerPayload.serviceName().isBlank()) {
            throw new VoteParseException("V2 payload missing serviceName");
        }
        if (innerPayload.username() == null || innerPayload.username().isBlank()) {
            throw new VoteParseException("V2 payload missing username");
        }

        // Verify challenge if expected (socket mode)
        if (expectedChallenge != null) {
            if (innerPayload.challenge() == null || innerPayload.challenge().isBlank()) {
                throw new V2ChallengeException("V2 payload missing challenge");
            }
            if (!expectedChallenge.equals(innerPayload.challenge())) {
                throw new V2ChallengeException("Challenge mismatch");
            }
        }

        // Look up token for this service
        String token = voteSiteTokenConfig.getToken(innerPayload.serviceName());
        if (token == null) {
            throw new V2SignatureException("No token configured for service: " + innerPayload.serviceName());
        }

        // Verify HMAC signature
        if (!HmacUtil.verifySignature(wrapper.payload(), wrapper.signature(), token)) {
            throw new V2SignatureException("Invalid signature for service: " + innerPayload.serviceName());
        }

        // Convert timestamp - V2 may use seconds or milliseconds
        long timestamp = innerPayload.timestamp();
        if (timestamp > 0 && timestamp < 1_000_000_000_000L) {
            // Likely seconds, convert to milliseconds
            timestamp *= 1000;
        } else if (timestamp <= 0) {
            // Fallback to current time if timestamp is invalid
            timestamp = System.currentTimeMillis();
        }

        return new Vote(
                innerPayload.serviceName(),
                innerPayload.username(),
                innerPayload.address() != null ? innerPayload.address() : "",
                timestamp
        );
    }

    /**
     * Outer wrapper containing the signed payload and signature.
     */
    private record V2Wrapper(String payload, String signature) {}

    /**
     * Inner payload containing the vote data.
     */
    private record V2InnerPayload(
            String serviceName,
            String username,
            String address,
            long timestamp,
            String challenge  // Included for completeness but not verified in HTTP mode
    ) {}
}
