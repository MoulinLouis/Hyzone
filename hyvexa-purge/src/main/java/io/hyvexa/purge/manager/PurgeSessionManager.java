package io.hyvexa.purge.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.DamageBypassRegistry;
import io.hyvexa.purge.HyvexaPurgePlugin;
import io.hyvexa.purge.data.*;
import io.hyvexa.purge.hud.PurgeHudManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurgeSessionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConcurrentHashMap<UUID, PurgeSession> activeSessions = new ConcurrentHashMap<>();
    private final PurgeSpawnPointManager spawnPointManager;
    private final PurgeWaveManager waveManager;
    private final PurgeHudManager hudManager;
    private final PurgeSettingsManager settingsManager;

    public PurgeSessionManager(PurgeSpawnPointManager spawnPointManager,
                               PurgeWaveManager waveManager,
                               PurgeHudManager hudManager,
                               PurgeSettingsManager settingsManager) {
        this.spawnPointManager = spawnPointManager;
        this.waveManager = waveManager;
        this.hudManager = hudManager;
        this.settingsManager = settingsManager;
        waveManager.setSessionManager(this);
    }

    public boolean startSession(UUID playerId, Ref<EntityStore> playerRef) {
        if (playerId == null || playerRef == null || !playerRef.isValid()) {
            return false;
        }
        if (activeSessions.containsKey(playerId)) {
            sendMessage(playerRef, "You already have an active Purge session. Use /purge stop to end it.");
            return false;
        }
        if (!spawnPointManager.hasSpawnPoints()) {
            sendMessage(playerRef, "No purge spawn points configured. Use /purgespawn add first.");
            return false;
        }
        if (!waveManager.hasConfiguredWaves()) {
            sendMessage(playerRef, "No purge waves configured. Ask an admin to set waves in /purge admin.");
            return false;
        }

        PurgeSession session = new PurgeSession(playerId, playerRef);
        activeSessions.put(playerId, session);
        DamageBypassRegistry.add(playerId);

        // Grant loadout
        try {
            HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
            if (plugin != null) {
                Store<EntityStore> store = playerRef.getStore();
                Player player = store.getComponent(playerRef, Player.getComponentType());
                if (player != null) {
                    plugin.grantLoadout(player);
                }
                teleportToConfiguredStart(playerRef, store);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to grant loadout: " + e.getMessage());
        }

        // Show wave status HUD
        hudManager.showRunHud(playerId);

        // Start countdown
        waveManager.startCountdown(session);

        LOGGER.atInfo().log("Purge session started for " + playerId);
        return true;
    }

    public void stopSession(UUID playerId, String reason) {
        PurgeSession session = activeSessions.remove(playerId);
        if (session == null) {
            return;
        }
        DamageBypassRegistry.remove(playerId);
        session.setState(SessionState.ENDED);

        runSafe("cancel tasks", session::cancelAllTasks);
        runSafe("remove zombies", () -> waveManager.removeAllZombies(session));
        runSafe("hide hud", () -> hudManager.hideRunHud(playerId));
        runSafe("persist", () -> persistResults(playerId, session));
        runSafe("remove loadout", () -> {
            HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
            if (plugin != null) {
                Ref<EntityStore> ref = session.getPlayerRef();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        plugin.removeLoadout(player);
                    }
                    if ("voluntary stop".equalsIgnoreCase(reason)) {
                        teleportToConfiguredExit(ref, store);
                    }
                }
            }
        });

        // Send summary to player
        int scrap = calculateScrapReward(session.getCurrentWave());
        String summary = "Purge ended - Wave " + session.getCurrentWave()
                + " - " + session.getTotalKills() + " kills"
                + " - " + scrap + " scrap earned"
                + " (" + reason + ")";
        runSafe("send summary", () -> sendMessage(session.getPlayerRef(), summary));

        LOGGER.atInfo().log("Purge session ended for " + playerId + " (" + reason + ")"
                + " wave=" + session.getCurrentWave() + " kills=" + session.getTotalKills());
    }

    public boolean hasActiveSession(UUID playerId) {
        return playerId != null && activeSessions.containsKey(playerId);
    }

    public PurgeSession getSession(UUID playerId) {
        return playerId != null ? activeSessions.get(playerId) : null;
    }

    public void cleanupPlayer(UUID playerId) {
        if (playerId != null && activeSessions.containsKey(playerId)) {
            stopSession(playerId, "disconnect");
        }
    }

    public void shutdown() {
        for (UUID playerId : activeSessions.keySet()) {
            try {
                stopSession(playerId, "server shutdown");
            } catch (Exception e) {
                LOGGER.atWarning().log("Shutdown cleanup failed for " + playerId + ": " + e.getMessage());
            }
        }
        activeSessions.clear();
    }

    private void persistResults(UUID playerId, PurgeSession session) {
        // Update stats
        PurgePlayerStats stats = PurgePlayerStore.getInstance().getOrCreate(playerId);
        stats.updateBestWave(session.getCurrentWave());
        stats.incrementKills(session.getTotalKills());
        stats.incrementSessions();
        PurgePlayerStore.getInstance().save(playerId, stats);

        // Award scrap
        int scrap = calculateScrapReward(session.getCurrentWave());
        if (scrap > 0) {
            PurgeScrapStore.getInstance().addScrap(playerId, scrap);
        }
    }

    static int calculateScrapReward(int wavesReached) {
        if (wavesReached < 5) {
            return 0;
        } else if (wavesReached < 10) {
            return 20;
        } else if (wavesReached < 15) {
            return 60;
        } else if (wavesReached < 20) {
            return 120;
        } else if (wavesReached < 25) {
            return 200;
        } else {
            return 300 + 50 * ((wavesReached - 25) / 5);
        }
    }

    private void sendMessage(Ref<EntityStore> ref, String text) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        try {
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(Message.raw(text));
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private void runSafe(String label, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOGGER.atWarning().log("Cleanup failed [" + label + "]: " + e.getMessage());
        }
    }

    private void teleportToConfiguredStart(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (settingsManager == null) {
            return;
        }
        PurgeLocation location = settingsManager.getSessionStartPoint();
        if (location == null) {
            return;
        }
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null) {
            return;
        }
        store.addComponent(ref, Teleport.getComponentType(),
                new Teleport(world, location.toPosition(), location.toRotation()));
    }

    private void teleportToConfiguredExit(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (settingsManager == null) {
            return;
        }
        PurgeLocation location = settingsManager.getSessionExitPoint();
        if (location == null) {
            return;
        }
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null) {
            return;
        }
        store.addComponent(ref, Teleport.getComponentType(),
                new Teleport(world, location.toPosition(), location.toRotation()));
    }
}
