package io.hyvexa.runorfall.util;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.economy.FeatherStore;

import java.util.UUID;

public final class RunOrFallFeatherBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private RunOrFallFeatherBridge() {
    }

    public static long getFeathers(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        try {
            return FeatherStore.getInstance().getFeathers(playerId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("RunOrFall Feather bridge read failed.");
            return 0L;
        }
    }

    public static long getCachedFeathers(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        try {
            return FeatherStore.getInstance().getCachedFeathers(playerId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("RunOrFall Feather bridge cached read failed.");
            return 0L;
        }
    }

    public static void evictPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        try {
            FeatherStore.getInstance().evictPlayer(playerId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("RunOrFall Feather bridge evict failed.");
        }
    }

    public static boolean addFeathers(UUID playerId, long amount) {
        if (playerId == null || amount <= 0L) {
            return false;
        }
        try {
            FeatherStore.getInstance().addFeathers(playerId, amount);
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("RunOrFall Feather bridge add failed.");
            return false;
        }
    }
}
