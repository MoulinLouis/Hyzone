package io.hyvexa.ascend.ui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Shared helper to coalesce page refresh ticks on a world thread.
 */
public final class PageRefreshScheduler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PageRefreshScheduler() {
    }

    public static void requestRefresh(
        World world,
        AtomicBoolean refreshInFlight,
        AtomicBoolean refreshRequested,
        Runnable refreshAction,
        Runnable stopAction,
        String pageName
    ) {
        if (world == null || refreshInFlight == null || refreshRequested == null
            || refreshAction == null || stopAction == null) {
            return;
        }
        refreshRequested.set(true);
        if (!refreshInFlight.compareAndSet(false, true)) {
            return;
        }
        scheduleWorker(world, refreshInFlight, refreshRequested, refreshAction, stopAction, pageName);
    }

    private static void scheduleWorker(
        World world,
        AtomicBoolean refreshInFlight,
        AtomicBoolean refreshRequested,
        Runnable refreshAction,
        Runnable stopAction,
        String pageName
    ) {
        try {
            CompletableFuture.runAsync(
                () -> runCoalescedRefreshes(world, refreshInFlight, refreshRequested, refreshAction, stopAction, pageName),
                world
            ).exceptionally(ex -> {
                LOGGER.at(Level.WARNING).withCause(ex).log("Exception while scheduling page refresh for " + pageName);
                refreshInFlight.set(false);
                safeStop(stopAction, pageName);
                return null;
            });
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to enqueue page refresh for " + pageName);
            refreshInFlight.set(false);
            safeStop(stopAction, pageName);
        }
    }

    private static void runCoalescedRefreshes(
        World world,
        AtomicBoolean refreshInFlight,
        AtomicBoolean refreshRequested,
        Runnable refreshAction,
        Runnable stopAction,
        String pageName
    ) {
        try {
            while (refreshRequested.getAndSet(false)) {
                refreshAction.run();
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Exception in page refresh for " + pageName);
            safeStop(stopAction, pageName);
        } finally {
            refreshInFlight.set(false);
            if (refreshRequested.get() && refreshInFlight.compareAndSet(false, true)) {
                scheduleWorker(world, refreshInFlight, refreshRequested, refreshAction, stopAction, pageName);
            }
        }
    }

    private static void safeStop(Runnable stopAction, String pageName) {
        try {
            stopAction.run();
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Exception while stopping page refresh for " + pageName);
        }
    }
}
