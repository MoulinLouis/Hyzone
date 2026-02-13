package io.hyvexa.common.util;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Iterator;
import java.util.Map;

/**
 * Shared helper for best-effort async execution with throttled warning logs.
 */
public final class AsyncExecutionHelper {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long WARNING_THROTTLE_MS = 5000L;
    private static final long CLEANUP_THRESHOLD_MS = 3_600_000L; // 1 hour
    private static final int MAX_WARNING_ENTRIES = 500;
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
            LOGGER.atWarning().withCause(cause).log(message);
            return;
        }
        LOGGER.atWarning().log(message);
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
        if (LAST_WARNING_AT.size() > MAX_WARNING_ENTRIES) {
            evictStaleEntries(now);
        }
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

    private static void evictStaleEntries(long now) {
        Iterator<Map.Entry<String, Long>> it = LAST_WARNING_AT.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() >= CLEANUP_THRESHOLD_MS) {
                it.remove();
            }
        }
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
