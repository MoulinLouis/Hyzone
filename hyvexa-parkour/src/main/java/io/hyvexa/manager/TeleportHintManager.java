package io.hyvexa.manager;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.parkour.data.ProgressStore;

import java.util.UUID;

/**
 * Manages teleport item usage hints for new players.
 * Shows persistent hints for first 5 teleport uses and corridor navigation hints.
 */
public class TeleportHintManager {

    private static final int MAX_HINT_COUNT = 5;
    private final ProgressStore progressStore;

    public TeleportHintManager(ProgressStore progressStore) {
        this.progressStore = progressStore;
    }

    /**
     * Records a teleport item use and shows hint if appropriate.
     *
     * @param playerRef the player reference
     */
    public void onTeleportItemUse(PlayerRef playerRef) {
        if (playerRef == null || progressStore == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        String playerName = playerRef.getUsername();

        int useCount = progressStore.getTeleportItemUseCount(playerId);

        if (useCount < MAX_HINT_COUNT) {
            progressStore.incrementTeleportItemUseCount(playerId, playerName);
            playerRef.sendMessage(SystemMessageUtils.withParkourPrefix(
                    Message.raw("💡 Map Selector").color(SystemMessageUtils.INFO)
            ));
        }
    }

    /**
     * Shows a corridor walking hint to guide players to use the map selector.
     *
     * @param playerRef the player reference
     */
    public void showCorridorWalkingHint(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }

        playerRef.sendMessage(SystemMessageUtils.withParkourPrefix(
                Message.raw("💡 Tip: Use Map Selector to find easier maps!").color(SystemMessageUtils.INFO)
        ));
    }
}
