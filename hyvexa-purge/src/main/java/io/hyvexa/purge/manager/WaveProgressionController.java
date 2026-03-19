package io.hyvexa.purge.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeSessionPlayerState;
import io.hyvexa.purge.data.PurgeUpgradeOffer;
import io.hyvexa.purge.data.PurgeUpgradeState;
import io.hyvexa.purge.data.SessionState;
import io.hyvexa.purge.hud.PurgeHudManager;
import io.hyvexa.purge.ui.PurgeUpgradePickPage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class WaveProgressionController {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int INTERMISSION_SECONDS = 5;
    private static final int COUNTDOWN_SECONDS = 5;
    private static final int UPGRADE_TIMEOUT_SECONDS = 15;

    private final PurgeWaveManager waveManager;
    private final PurgeWaveConfigManager waveConfigManager;
    private final PurgeHudManager hudManager;
    private PurgeManagerRegistry registry;

    WaveProgressionController(PurgeWaveManager waveManager,
                              PurgeWaveConfigManager waveConfigManager,
                              PurgeHudManager hudManager) {
        this.waveManager = waveManager;
        this.waveConfigManager = waveConfigManager;
        this.hudManager = hudManager;
    }

    void initRegistry(PurgeManagerRegistry registry) {
        this.registry = registry;
    }

    void startCountdown(PurgeSession session) {
        session.setState(SessionState.COUNTDOWN);
        AtomicInteger countdown = new AtomicInteger(COUNTDOWN_SECONDS);

        ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                if (session.getState() == SessionState.ENDED) {
                    session.cancelAllTasks();
                    return;
                }
                int remaining = countdown.getAndDecrement();
                if (remaining <= 0) {
                    session.cancelSpawnTask(); // reusing spawn task slot for countdown
                    waveManager.startNextWave(session);
                    return;
                }
                waveManager.sendMessageToAll(session, "Wave " + (session.getCurrentWave() + 1) + " starting in " + remaining + "...");
                session.forEachConnectedParticipant(pid -> hudManager.updateIntermission(pid, remaining));
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Countdown tick error");
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        session.setSpawnTask(task);
    }

    void onWaveComplete(PurgeSession session) {
        // Cancel wave tick
        ScheduledFuture<?> wt = session.getWaveTick();
        if (wt != null) {
            wt.cancel(false);
            session.setWaveTick(null);
        }

        // Revive players who died this wave
        waveManager.revivePlayersDownedThisWave(session);

        // Sum total kills across all players for the summary
        int totalKills = 0;
        for (UUID pid : session.getParticipants()) {
            PurgeSessionPlayerState ps = session.getPlayerState(pid);
            if (ps != null) totalKills += ps.getKills();
        }
        waveManager.sendMessageToAll(session, "Wave " + session.getCurrentWave() + " complete! (" + totalKills + " team kill credits)");

        if (!waveConfigManager.hasWave(session.getCurrentWave() + 1)) {
            handleVictory(session);
            return;
        }

        session.setState(SessionState.UPGRADE_PICK);
        showUpgradePopup(session);
    }

    private void showUpgradePopup(PurgeSession session) {
        PurgeUpgradeManager um = registry.getUpgradeManager();
        World world = waveManager.getPurgeWorld();
        if (world == null) {
            session.setState(SessionState.INTERMISSION);
            startIntermission(session);
            return;
        }

        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                if (store == null || session.getState() == SessionState.ENDED) {
                    return;
                }

                boolean anyShown = false;
                for (UUID pid : session.getAliveConnectedParticipants()) {
                    PurgeSessionPlayerState ps = session.getPlayerState(pid);
                    if (ps == null) continue;
                    Ref<EntityStore> ref = ps.getPlayerRef();
                    if (ref == null || !ref.isValid()) continue;

                    Player player = store.getComponent(ref, Player.getComponentType());
                    PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
                    if (player == null || pRef == null) continue;

                    // Per-player random upgrade selection with luck-adjusted rarity
                    PurgeUpgradeState upgradeState = session.getUpgradeState(pid);
                    int playerLuck = upgradeState != null ? upgradeState.getLuck() : 0;
                    List<PurgeUpgradeOffer> offered = um.selectRandomOffers(3, playerLuck);

                    Runnable onComplete = () -> {
                        if (session.getState() == SessionState.ENDED) {
                            return;
                        }
                        session.getPendingUpgradeChoices().remove(pid);
                        if (session.getPendingUpgradeChoices().isEmpty()) {
                            cancelUpgradeTimeout(session);
                            session.setState(SessionState.INTERMISSION);
                            startIntermission(session);
                        }
                    };

                    session.getPendingUpgradeChoices().add(pid);
                    PurgeUpgradePickPage page = new PurgeUpgradePickPage(pRef, pid, session, um, offered, onComplete, hudManager);
                    player.getPageManager().openCustomPage(ref, store, page);
                    anyShown = true;
                }

                if (!anyShown) {
                    session.setState(SessionState.INTERMISSION);
                    startIntermission(session);
                } else {
                    // Start 15s timeout for upgrade selection
                    ScheduledFuture<?> timeout = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                        try {
                            if (session.getState() != SessionState.UPGRADE_PICK) {
                                return;
                            }
                            session.getPendingUpgradeChoices().clear();
                            session.setState(SessionState.INTERMISSION);
                            startIntermission(session);
                        } catch (Exception e) {
                            LOGGER.atWarning().withCause(e).log("Upgrade timeout error");
                        }
                    }, UPGRADE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    session.setUpgradeTimeoutTask(timeout);
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to show upgrade popup");
                session.setState(SessionState.INTERMISSION);
                startIntermission(session);
            }
        });
    }

    private void cancelUpgradeTimeout(PurgeSession session) {
        ScheduledFuture<?> task = session.getUpgradeTimeoutTask();
        if (task != null) {
            task.cancel(false);
            session.setUpgradeTimeoutTask(null);
        }
    }

    void handleVictory(PurgeSession session) {
        if (session.getState() == SessionState.ENDED) {
            return;
        }
        waveManager.sendMessageToAll(session, "You won! You cleared all configured Purge waves.");
        registry.getSessionManager().stopSessionById(session.getSessionId(), "victory");
    }

    void startIntermission(PurgeSession session) {
        synchronized (session) {
            if (session.getState() == SessionState.ENDED) {
                return;
            }
            ScheduledFuture<?> existing = session.getIntermissionTask();
            if (existing != null && !existing.isDone() && !existing.isCancelled()) {
                return;
            }
            session.cancelIntermissionTask();
            if (session.getState() != SessionState.INTERMISSION) {
                session.setState(SessionState.INTERMISSION);
            }
            AtomicInteger countdown = new AtomicInteger(INTERMISSION_SECONDS);

            ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
                try {
                    if (session.getState() == SessionState.ENDED) {
                        session.cancelIntermissionTask();
                        return;
                    }
                    int remaining = countdown.getAndDecrement();
                    if (remaining <= 0) {
                        session.cancelIntermissionTask();
                        waveManager.startNextWave(session);
                        return;
                    }
                    session.forEachConnectedParticipant(pid -> hudManager.updateIntermission(pid, remaining));
                    waveManager.sendMessageToAll(session, "Next wave in " + remaining + "...");
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Intermission tick error");
                }
            }, 1000, 1000, TimeUnit.MILLISECONDS);
            session.setIntermissionTask(task);
        }
    }

}
