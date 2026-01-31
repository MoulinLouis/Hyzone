package io.hyvexa.core.db;

import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.logging.Level;

/**
 * Utility for retrying database operations with exponential backoff.
 */
public final class DatabaseRetry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Default number of retry attempts */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** Default initial delay between retries in milliseconds */
    public static final long DEFAULT_INITIAL_DELAY_MS = 100L;

    /** Maximum delay between retries in milliseconds */
    public static final long MAX_DELAY_MS = 5000L;

    private DatabaseRetry() {
    }

    /**
     * Execute a database operation with retry logic.
     *
     * @param operation The operation to execute
     * @param operationName Human-readable name for logging
     * @param <T> Return type
     * @return The result of the operation
     * @throws SQLException if all retries fail
     */
    public static <T> T executeWithRetry(Callable<T> operation, String operationName) throws SQLException {
        return executeWithRetry(operation, operationName, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_DELAY_MS);
    }

    /**
     * Execute a database operation with retry logic.
     *
     * @param operation The operation to execute
     * @param operationName Human-readable name for logging
     * @param maxRetries Maximum number of retry attempts
     * @param initialDelayMs Initial delay between retries
     * @param <T> Return type
     * @return The result of the operation
     * @throws SQLException if all retries fail
     */
    public static <T> T executeWithRetry(Callable<T> operation, String operationName,
                                          int maxRetries, long initialDelayMs) throws SQLException {
        SQLException lastException = null;
        long delay = initialDelayMs;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return operation.call();
            } catch (SQLException e) {
                lastException = e;
                if (attempt < maxRetries) {
                    LOGGER.at(Level.WARNING).log(operationName + " failed (attempt " + attempt
                            + "/" + maxRetries + "), retrying in " + delay + "ms: " + e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Retry interrupted", ie);
                    }
                    delay = Math.min(delay * 2, MAX_DELAY_MS);
                }
            } catch (Exception e) {
                throw new SQLException("Unexpected error during " + operationName, e);
            }
        }

        LOGGER.at(Level.SEVERE).log(operationName + " failed after " + maxRetries + " attempts");
        throw lastException;
    }

    /**
     * Execute a void database operation with retry logic.
     *
     * @param operation The operation to execute
     * @param operationName Human-readable name for logging
     * @throws SQLException if all retries fail
     */
    public static void executeWithRetryVoid(VoidCallable operation, String operationName) throws SQLException {
        executeWithRetry(() -> {
            operation.call();
            return null;
        }, operationName);
    }

    /**
     * Functional interface for void operations that can throw SQLException.
     */
    @FunctionalInterface
    public interface VoidCallable {
        void call() throws SQLException;
    }
}
