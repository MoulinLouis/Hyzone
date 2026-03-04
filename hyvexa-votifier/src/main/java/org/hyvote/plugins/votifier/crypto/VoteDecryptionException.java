package org.hyvote.plugins.votifier.crypto;

/**
 * Exception thrown when vote decryption fails.
 *
 * <p>This can occur due to invalid encrypted data, wrong key, or tampered payloads.</p>
 */
public class VoteDecryptionException extends Exception {

    /**
     * Creates a new decryption exception with the specified message.
     *
     * @param message the detail message
     */
    public VoteDecryptionException(String message) {
        super(message);
    }

    /**
     * Creates a new decryption exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public VoteDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
