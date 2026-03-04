package org.hyvote.plugins.votifier.vote;

/**
 * Detects whether a vote payload uses Votifier V1 or V2 protocol.
 *
 * <p>Detection strategy:</p>
 * <ul>
 *   <li>V2 is detected if the payload is JSON starting with '{' and contains both "payload" and "signature" fields</li>
 *   <li>V1 is the fallback for any other payload (Base64-encoded RSA-encrypted data)</li>
 *   <li>UNKNOWN for empty or null payloads</li>
 * </ul>
 */
public final class ProtocolDetector {

    /**
     * Votifier protocol versions.
     */
    public enum Protocol {
        /**
         * V1 protocol using RSA encryption (Base64-encoded encrypted payload).
         */
        V1_RSA,

        /**
         * V2 protocol using HMAC-SHA256 authentication (JSON with payload and signature fields).
         */
        V2_JSON,

        /**
         * Unable to determine protocol (empty or invalid payload).
         */
        UNKNOWN
    }

    private ProtocolDetector() {
        // Utility class - prevent instantiation
    }

    /**
     * Detects the protocol based on payload content.
     *
     * <p>V2 is detected by checking if the payload is valid JSON with "payload" and "signature" fields.
     * V1 is the fallback for any other payload (Base64-encoded RSA data).</p>
     *
     * @param payload the raw payload string from the HTTP request
     * @return the detected protocol
     */
    public static Protocol detect(String payload) {
        if (payload == null || payload.isBlank()) {
            return Protocol.UNKNOWN;
        }

        String trimmed = payload.trim();

        // V2 payloads are JSON objects starting with {
        if (trimmed.startsWith("{")) {
            // Quick validation: check for V2 structure markers
            if (trimmed.contains("\"payload\"") && trimmed.contains("\"signature\"")) {
                return Protocol.V2_JSON;
            }
        }

        // Default to V1 (Base64-encoded RSA)
        return Protocol.V1_RSA;
    }
}
