package io.hyvexa.parkour.tracker;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.protocol.MovementStates;
import io.hyvexa.parkour.data.ProgressStore;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks player jumps and flushes accumulated counts to the progress store. */
class JumpTracker {

    private final ConcurrentHashMap<UUID, Boolean> previousOnGround = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> pendingJumps = new ConcurrentHashMap<>();
    private final ProgressStore progressStore;

    JumpTracker(ProgressStore progressStore) {
        this.progressStore = progressStore;
    }

    void trackJump(PlayerRef playerRef, MovementStates movementStates) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        boolean currentOnGround = movementStates != null && movementStates.onGround;
        Boolean wasOnGround = previousOnGround.put(playerId, currentOnGround);
        if (wasOnGround != null && wasOnGround && !currentOnGround) {
            pendingJumps.merge(playerId, 1, Integer::sum);
        }
    }

    void flushPendingJumps() {
        if (pendingJumps.isEmpty() || progressStore == null) {
            return;
        }
        // Snapshot + compare-and-remove drain to avoid losing increments that arrive mid-flush.
        for (java.util.Map.Entry<UUID, Integer> entry : new ArrayList<>(pendingJumps.entrySet())) {
            UUID playerId = entry.getKey();
            Integer count = entry.getValue();
            if (playerId == null || count == null) {
                continue;
            }
            if (!pendingJumps.remove(playerId, count)) {
                continue;
            }
            if (count <= 0) {
                continue;
            }
            String playerName = progressStore.getPlayerName(playerId);
            progressStore.addJumps(playerId, playerName, count);
        }
    }

    void clearPlayer(UUID playerId) {
        previousOnGround.remove(playerId);
        pendingJumps.remove(playerId);
    }
}
