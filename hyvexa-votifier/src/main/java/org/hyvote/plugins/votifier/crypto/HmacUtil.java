package org.hyvote.plugins.votifier.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * HMAC-SHA256 utilities for Votifier V2 protocol signature verification.
 *
 * <p>The V2 protocol uses HMAC-SHA256 with per-service tokens (shared secrets)
 * to authenticate vote payloads instead of RSA encryption.</p>
 */
public final class HmacUtil {

    /**
     * The HMAC algorithm used by Votifier V2 protocol.
     */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private HmacUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Computes HMAC-SHA256 signature for the given payload using the token.
     *
     * @param payload the payload string to sign
     * @param token the shared secret token
     * @return the HMAC signature bytes
     * @throws InvalidKeyException if the token is invalid for HMAC
     * @throws NoSuchAlgorithmException if HmacSHA256 is not available
     */
    public static byte[] computeSignature(String payload, String token)
            throws InvalidKeyException, NoSuchAlgorithmException {
        SecretKeySpec keySpec = new SecretKeySpec(
                token.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(keySpec);
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Verifies a Base64-encoded HMAC-SHA256 signature using timing-safe comparison.
     *
     * <p>Uses {@link MessageDigest#isEqual(byte[], byte[])} for constant-time comparison
     * to prevent timing attacks.</p>
     *
     * @param payload the payload string that was signed
     * @param signatureBase64 the Base64-encoded signature to verify
     * @param token the shared secret token
     * @return true if the signature is valid, false otherwise
     */
    public static boolean verifySignature(String payload, String signatureBase64, String token) {
        try {
            byte[] expected = computeSignature(payload, token);
            byte[] actual = Base64.getDecoder().decode(signatureBase64);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            // Any exception (invalid key, malformed Base64, etc.) means invalid signature
            return false;
        }
    }
}
