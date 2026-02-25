package io.hyvexa.purge.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.MultiHudBridge;
import io.hyvexa.core.economy.VexaStore;
import io.hyvexa.purge.data.PurgeScrapStore;
import io.hyvexa.purge.data.PurgeUpgradeState;
import io.hyvexa.purge.data.PurgeUpgradeType;

import io.hyvexa.purge.data.PurgeSessionPlayerState;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeHudManager {

    private static final long HUD_READY_DELAY_MS = 1500L;
    private static final long STREAK_WINDOW_MS = 3000L;
    private final ConcurrentHashMap<UUID, PurgeHud> purgeHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> hudReadyAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PurgeSessionPlayerState> comboPlayers = new ConcurrentHashMap<>();

    public void attach(PlayerRef playerRef, Player player) {
        if (playerRef == null || player == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        PurgeHud hud = purgeHuds.computeIfAbsent(playerId, id -> new PurgeHud(playerRef));
        hud.resetCache();
        hudReadyAt.put(playerId, System.currentTimeMillis() + HUD_READY_DELAY_MS);
        MultiHudBridge.setCustomHud(player, playerRef, hud);
        player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass);
        player.getHudManager().showHudComponents(playerRef, HudComponent.Health, HudComponent.Stamina);
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
            hudReadyAt.remove(playerId);
            comboPlayers.remove(playerId);
        }
    }

    public void registerComboPlayer(UUID playerId, PurgeSessionPlayerState state) {
        if (playerId != null && state != null) {
            comboPlayers.put(playerId, state);
        }
    }

    public void unregisterComboPlayer(UUID playerId) {
        if (playerId != null) {
            comboPlayers.remove(playerId);
            PurgeHud hud = getHud(playerId);
            if (hud != null) {
                hud.updateCombo(0, 0f);
            }
        }
    }

    public void updateUpgradeLevels(UUID playerId, PurgeUpgradeState state) {
        PurgeHud hud = getHud(playerId);
        if (hud == null || state == null) return;
        hud.updateUpgradeLevels(
                state.getLevel(PurgeUpgradeType.HP),
                state.getLevel(PurgeUpgradeType.AMMO),
                state.getLevel(PurgeUpgradeType.SPEED),
                state.getLevel(PurgeUpgradeType.LUCK)
        );
    }

    public void showRunHud(UUID playerId) {
        PurgeHud hud = getHud(playerId);
        if (hud != null) {
            hud.setWaveStatusVisible(true);
            hud.setPlayerHealthVisible(true);
        }
    }

    public void hideRunHud(UUID playerId) {
        PurgeHud hud = getHud(playerId);
        if (hud != null) {
            hud.updateUpgradeLevels(0, 0, 0, 0);
            hud.setPlayerHealthVisible(false);
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

    public void tickComboBars() {
        long now = System.currentTimeMillis();
        for (var entry : comboPlayers.entrySet()) {
            UUID playerId = entry.getKey();
            PurgeHud hud = getHud(playerId);
            if (hud == null) continue;
            PurgeSessionPlayerState state = entry.getValue();
            long lastKill = state.getLastKillTimeMs();
            int streak = state.getKillStreak();
            if (streak < 2 || lastKill == 0) {
                hud.updateCombo(0, 0f);
                continue;
            }
            long elapsed = now - lastKill;
            if (elapsed >= STREAK_WINDOW_MS) {
                hud.updateCombo(0, 0f);
                continue;
            }
            float progress = 1f - (float) elapsed / STREAK_WINDOW_MS;
            hud.updateCombo(streak, progress);
        }
    }

    public void tickSlowUpdates() {
        long now = System.currentTimeMillis();
        int playerCount = Universe.get().getPlayers().size();
        java.util.List<UUID> toRemove = null;
        for (var entry : purgeHuds.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null || !playerRef.isValid()) {
                if (toRemove == null) {
                    toRemove = new java.util.ArrayList<>();
                }
                toRemove.add(playerId);
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                if (toRemove == null) {
                    toRemove = new java.util.ArrayList<>();
                }
                toRemove.add(playerId);
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
            if (!ModeGate.isPurgeWorld(world)) {
                if (toRemove == null) {
                    toRemove = new java.util.ArrayList<>();
                }
                toRemove.add(playerId);
                continue;
            }
            long readyAt = hudReadyAt.getOrDefault(playerId, Long.MAX_VALUE);
            if (now < readyAt) {
                continue;
            }
            PurgeHud hud = entry.getValue();
            hud.updatePlayerCount(playerCount);
            hud.updateVexa(VexaStore.getInstance().getVexa(playerId));
            hud.updateScrap(PurgeScrapStore.getInstance().getScrap(playerId));
        }
        if (toRemove != null) {
            for (UUID playerId : toRemove) {
                purgeHuds.remove(playerId);
                hudReadyAt.remove(playerId);
                comboPlayers.remove(playerId);
            }
        }
    }
}
