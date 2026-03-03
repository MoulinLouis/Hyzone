package io.hyvexa.common.util;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.UUID;

public final class PlayerCleanupHelper {

    @FunctionalInterface
    public interface CleanupAction {
        void cleanup(UUID playerId) throws Exception;
    }

    private PlayerCleanupHelper() {
    }

    public static void cleanup(UUID playerId, HytaleLogger logger, CleanupAction... actions) {
        for (CleanupAction action : actions) {
            try {
                action.cleanup(playerId);
            } catch (Exception e) {
                logger.atWarning().withCause(e).log("Player cleanup failed");
            }
        }
    }
}
