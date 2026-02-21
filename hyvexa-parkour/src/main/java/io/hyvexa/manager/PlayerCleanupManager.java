package io.hyvexa.manager;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.hyvexa.common.visibility.EntityVisibilityManager;
import io.hyvexa.common.util.MultiHudBridge;
import io.hyvexa.parkour.command.CheckpointCommand;
import io.hyvexa.parkour.interaction.LeaveInteraction;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.util.PlayerSettingsStore;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Handles cleanup of player state on disconnect and periodic sweeps. */
public class PlayerCleanupManager {

    private final HudManager hudManager;
    private final AnnouncementManager announcementManager;
    private final PlayerPerksManager perksManager;
    private final PlaytimeManager playtimeManager;
    private final RunTracker runTracker;

    public PlayerCleanupManager(HudManager hudManager,
                                AnnouncementManager announcementManager,
                                PlayerPerksManager perksManager,
                                PlaytimeManager playtimeManager,
                                RunTracker runTracker) {
        this.hudManager = hudManager;
        this.announcementManager = announcementManager;
        this.perksManager = perksManager;
        this.playtimeManager = playtimeManager;
        this.runTracker = runTracker;
    }

    public void handleDisconnect(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        if (hudManager != null) {
            hudManager.clearPlayer(playerId);
        }
        if (perksManager != null) {
            perksManager.clearPlayer(playerId);
        }
        if (announcementManager != null) {
            announcementManager.clearPlayer(playerId);
        }
        if (playtimeManager != null) {
            playtimeManager.finishPlaytimeSession(playerRef);
        }
        PlayerSettingsStore.clearSession(playerId);
        LeaveInteraction.clearPendingLeave(playerId);
        CheckpointCommand.clearCheckpoint(playerId);
        EntityVisibilityManager.get().clearHidden(playerId);
        if (runTracker != null) {
            runTracker.handleDisconnect(playerId);
        }
        MultiHudBridge.evictPlayer(playerId);
    }

    public void tickStalePlayerSweep() {
        var players = Universe.get().getPlayers();
        if (players.isEmpty()) {
            sweepStalePlayerState(Set.of());
            return;
        }
        Set<UUID> onlinePlayers = new HashSet<>(players.size());
        for (PlayerRef playerRef : players) {
            if (playerRef == null) {
                continue;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId != null) {
                onlinePlayers.add(playerId);
            }
        }
        sweepStalePlayerState(onlinePlayers);
    }

    public void sweepStalePlayerState(Set<UUID> onlinePlayers) {
        if (hudManager != null) {
            hudManager.sweepStalePlayers(onlinePlayers);
        }
        if (announcementManager != null) {
            announcementManager.sweepStalePlayers(onlinePlayers);
        }
        if (playtimeManager != null) {
            playtimeManager.sweepStalePlayers(onlinePlayers);
        }
        if (perksManager != null) {
            perksManager.sweepStalePlayers(onlinePlayers);
        }
        if (runTracker != null) {
            runTracker.sweepStalePlayers(onlinePlayers);
        }
        EntityVisibilityManager.get().sweepStaleViewers(onlinePlayers);
    }
}
