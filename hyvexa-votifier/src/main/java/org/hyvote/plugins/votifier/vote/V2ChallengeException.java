package org.hyvote.plugins.votifier.vote;

/**
 * Exception thrown when V2 challenge verification fails.
 *
 * <p>This exception indicates that the challenge provided in a V2 vote payload
 * does not match the expected challenge sent by the server during the handshake.</p>
 */
public class V2ChallengeException extends Exception {

    /**
     * Constructs a new V2ChallengeException with the specified detail message.
     *
     * @param message the detail message
     */
    public V2ChallengeException(String message) {
        super(message);
    }

    /**
     * Constructs a new V2ChallengeException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public V2ChallengeException(String message, Throwable cause) {
        super(message, cause);
    }
}
