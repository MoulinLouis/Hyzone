package io.hyvexa.runorfall.util;

import com.hypixel.hytale.logger.HytaleLogger;

import java.lang.reflect.Method;
import java.util.UUID;

public final class RunOrFallFeatherBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String FEATHER_STORE_CLASS = "io.hyvexa.parkour.data.FeatherStore";

    private static volatile boolean resolved;
    private static volatile boolean available;
    private static Object featherStoreInstance;
    private static Method getFeathersMethod;
    private static Method evictPlayerMethod;

    private RunOrFallFeatherBridge() {
    }

    public static long getFeathers(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        ensureResolved();
        if (!available) {
            return 0L;
        }
        try {
            Object value = getFeathersMethod.invoke(featherStoreInstance, playerId);
            if (value instanceof Number number) {
                return Math.max(0L, number.longValue());
            }
        } catch (Exception e) {
            available = false;
            LOGGER.atWarning().withCause(e).log("RunOrFall Feather bridge read failed.");
        }
        return 0L;
    }

    public static void evictPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        ensureResolved();
        if (!available) {
            return;
        }
        try {
            evictPlayerMethod.invoke(featherStoreInstance, playerId);
        } catch (Exception e) {
            available = false;
            LOGGER.atWarning().withCause(e).log("RunOrFall Feather bridge evict failed.");
        }
    }

    private static void ensureResolved() {
        if (resolved) {
            return;
        }
        synchronized (RunOrFallFeatherBridge.class) {
            if (resolved) {
                return;
            }
            try {
                Class<?> featherStoreClass = Class.forName(FEATHER_STORE_CLASS);
                Method getInstanceMethod = featherStoreClass.getMethod("getInstance");
                featherStoreInstance = getInstanceMethod.invoke(null);
                getFeathersMethod = featherStoreClass.getMethod("getFeathers", UUID.class);
                evictPlayerMethod = featherStoreClass.getMethod("evictPlayer", UUID.class);
                available = featherStoreInstance != null && getFeathersMethod != null && evictPlayerMethod != null;
            } catch (Exception e) {
                available = false;
                LOGGER.atInfo().log("RunOrFall Feather bridge unavailable (Parkour FeatherStore not found).");
            } finally {
                resolved = true;
            }
        }
    }
}
