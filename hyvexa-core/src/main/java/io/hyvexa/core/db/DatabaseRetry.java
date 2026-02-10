package io.hyvexa.core.db;

import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.logging.Level;

public final class DatabaseRetry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final long DEFAULT_INITIAL_DELAY_MS = 100L;
    public static final long MAX_DELAY_MS = 5000L;

    private DatabaseRetry() {
    }

    public static <T> T executeWithRetry(Callable<T> operation, String operationName) throws SQLException {
        return executeWithRetry(operation, operationName, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_DELAY_MS);
    }

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

    public static void executeWithRetryVoid(VoidCallable operation, String operationName) throws SQLException {
        executeWithRetry(() -> {
            operation.call();
            return null;
        }, operationName);
    }

    @FunctionalInterface
    public interface VoidCallable {
        void call() throws SQLException;
    }
}
