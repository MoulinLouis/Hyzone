package io.hyvexa.purge.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.MultiHudBridge;
import io.hyvexa.core.economy.GemStore;
import io.hyvexa.purge.data.PurgeScrapStore;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeHudManager {

    private final ConcurrentHashMap<UUID, PurgeHud> purgeHuds = new ConcurrentHashMap<>();

    public void attach(PlayerRef playerRef, Player player) {
        if (playerRef == null || player == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        PurgeHud hud = new PurgeHud(playerRef);
        purgeHuds.put(playerId, hud);
        MultiHudBridge.setCustomHud(player, playerRef, hud);
        player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass);
        MultiHudBridge.showIfNeeded(hud);
    }

    public PurgeHud getHud(UUID playerId) {
        return playerId != null ? purgeHuds.get(playerId) : null;
    }

    public Ref<EntityStore> getPlayerRef(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        try {
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef != null) {
                return playerRef.getReference();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public void removePlayer(UUID playerId) {
        if (playerId != null) {
            purgeHuds.remove(playerId);
        }
    }

    public void showRunHud(UUID playerId) {
        PurgeHud hud = getHud(playerId);
        if (hud != null) {
            hud.setWaveStatusVisible(true);
        }
    }

    public void hideRunHud(UUID playerId) {
        PurgeHud hud = getHud(playerId);
        if (hud != null) {
            hud.setWaveStatusVisible(false);
            hud.resetCache();
        }
    }

    public void updateWaveStatus(UUID playerId, int wave, int alive, int total) {
        PurgeHud hud = getHud(playerId);
        if (hud != null) {
            hud.updateWaveStatus(wave, alive, total);
        }
    }

    public void updatePlayerHealth(UUID playerId, int current, int max) {
        PurgeHud hud = getHud(playerId);
        if (hud != null) {
            hud.updatePlayerHealth(current, max);
        }
    }

    public void updateIntermission(UUID playerId, int seconds) {
        PurgeHud hud = getHud(playerId);
        if (hud != null) {
            hud.updateIntermission(seconds);
        }
    }

    public void tickSlowUpdates() {
        int playerCount = Universe.get().getPlayers().size();
        for (var entry : purgeHuds.entrySet()) {
            UUID playerId = entry.getKey();
            PurgeHud hud = entry.getValue();
            hud.updatePlayerCount(playerCount);
            hud.updateGems(GemStore.getInstance().getGems(playerId));
            hud.updateScrap(PurgeScrapStore.getInstance().getScrap(playerId));
        }
    }
}
