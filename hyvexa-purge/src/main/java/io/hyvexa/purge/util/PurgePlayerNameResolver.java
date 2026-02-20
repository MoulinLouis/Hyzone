package io.hyvexa.purge.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.UUID;

public final class PurgePlayerNameResolver {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PurgePlayerNameResolver() {
    }

    public enum FallbackStyle {
        FULL_UUID,
        SHORT_UUID
    }

    public static String resolve(UUID playerId, FallbackStyle fallbackStyle) {
        if (playerId == null) {
            return "unknown";
        }
        try {
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef != null && playerRef.getUsername() != null && !playerRef.getUsername().isBlank()) {
                return playerRef.getUsername();
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to resolve player name for " + playerId + ": " + e.getMessage());
        }
        if (fallbackStyle == FallbackStyle.SHORT_UUID) {
            String value = playerId.toString();
            return value.length() > 8 ? value.substring(0, 8) : value;
        }
        return playerId.toString();
    }
}
