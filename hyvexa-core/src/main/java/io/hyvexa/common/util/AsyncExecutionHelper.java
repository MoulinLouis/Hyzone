package io.hyvexa.common.util;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Shared helper for best-effort async execution with throttled warning logs.
 */
public final class AsyncExecutionHelper {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long WARNING_THROTTLE_MS = 5000L;
    private static final ConcurrentHashMap<String, Long> LAST_WARNING_AT = new ConcurrentHashMap<>();

    private AsyncExecutionHelper() {
    }

    public static boolean runBestEffort(Executor executor, Runnable action, String warningKey, String actionName) {
        return runBestEffort(executor, action, warningKey, actionName, null);
    }

    public static boolean runBestEffort(Executor executor, Runnable action, String warningKey,
                                        String actionName, String context) {
        if (executor == null || action == null) {
            return false;
        }
        try {
            CompletableFuture.runAsync(action, executor)
                    .exceptionally(throwable -> {
                        logThrottledWarning(warningKey, actionName, context, throwable);
                        return null;
                    });
            return true;
        } catch (Exception e) {
            logThrottledWarning(warningKey, actionName, context, e);
            return false;
        }
    }

    public static void logThrottledWarning(String warningKey, String actionName, Throwable throwable) {
        logThrottledWarning(warningKey, actionName, null, throwable);
    }

    public static void logThrottledWarning(String warningKey, String actionName, String context, Throwable throwable) {
        String key = normalizeKey(warningKey, actionName, context);
        if (!shouldLog(key)) {
            return;
        }
        Throwable cause = unwrap(throwable);
        String message = buildMessage(actionName, context);
        if (cause != null) {
            LOGGER.at(Level.WARNING).withCause(cause).log(message);
            return;
        }
        LOGGER.at(Level.WARNING).log(message);
    }

    private static String buildMessage(String actionName, String context) {
        String action = actionName != null && !actionName.isBlank() ? actionName : "async action";
        if (context == null || context.isBlank()) {
            return "Async task failed: " + action;
        }
        return "Async task failed: " + action + " [" + context + "]";
    }

    private static String normalizeKey(String warningKey, String actionName, String context) {
        if (warningKey != null && !warningKey.isBlank()) {
            return warningKey;
        }
        if (actionName != null && !actionName.isBlank()) {
            return actionName;
        }
        if (context != null && !context.isBlank()) {
            return context;
        }
        return "async_task_failure";
    }

    private static boolean shouldLog(String warningKey) {
        long now = System.currentTimeMillis();
        AtomicBoolean shouldLog = new AtomicBoolean(false);
        LAST_WARNING_AT.compute(warningKey, (ignored, lastWarningAt) -> {
            if (lastWarningAt == null || now - lastWarningAt >= WARNING_THROTTLE_MS) {
                shouldLog.set(true);
                return now;
            }
            return lastWarningAt;
        });
        return shouldLog.get();
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException || throwable instanceof ExecutionException) {
            if (throwable.getCause() != null) {
                return throwable.getCause();
            }
        }
        return throwable;
    }
}
