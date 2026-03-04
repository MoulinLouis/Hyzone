package org.hyvote.plugins.votifier.storage;

/**
 * Exception thrown when a storage operation fails.
 */
public class StorageException extends Exception {

    /**
     * Creates a new StorageException with the specified message.
     *
     * @param message the error message
     */
    public StorageException(String message) {
        super(message);
    }

    /**
     * Creates a new StorageException with the specified message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
