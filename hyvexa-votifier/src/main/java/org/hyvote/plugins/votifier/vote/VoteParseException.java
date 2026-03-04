package org.hyvote.plugins.votifier.vote;

/**
 * Exception thrown when vote parsing fails.
 *
 * <p>This can occur due to invalid vote format, missing required fields,
 * or malformed protocol data.</p>
 */
public class VoteParseException extends Exception {

    /**
     * Creates a new parse exception with the specified message.
     *
     * @param message the detail message
     */
    public VoteParseException(String message) {
        super(message);
    }

    /**
     * Creates a new parse exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public VoteParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
