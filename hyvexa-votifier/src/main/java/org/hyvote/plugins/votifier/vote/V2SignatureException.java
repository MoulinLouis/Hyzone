package org.hyvote.plugins.votifier.vote;

/**
 * Exception thrown when V2 signature verification fails.
 *
 * <p>This exception indicates that the HMAC-SHA256 signature provided with
 * a V2 vote payload does not match the expected signature, or that no token
 * is configured for the specified service.</p>
 */
public class V2SignatureException extends Exception {

    /**
     * Constructs a new V2SignatureException with the specified detail message.
     *
     * @param message the detail message
     */
    public V2SignatureException(String message) {
        super(message);
    }

    /**
     * Constructs a new V2SignatureException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public V2SignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
