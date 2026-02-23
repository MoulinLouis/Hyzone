package io.hyvexa.purge.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.DamageBypassRegistry;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.purge.HyvexaPurgePlugin;
import io.hyvexa.purge.data.*;
import io.hyvexa.purge.hud.PurgeHudManager;
import io.hyvexa.purge.util.PurgePlayerNameResolver;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class PurgeSessionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long CLEANUP_TIMEOUT_SECONDS = 8L;

    private final ConcurrentHashMap<String, PurgeSession> sessionsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> sessionIdByPlayer = new ConcurrentHashMap<>();
    private final PurgePartyManager partyManager;
    private final PurgeInstanceManager instanceManager;
    private final PurgeWaveManager waveManager;
    private final PurgeHudManager hudManager;
    private volatile PurgeUpgradeManager upgradeManager;
    private final AtomicInteger sessionCounter = new AtomicInteger(0);

    public PurgeSessionManager(PurgePartyManager partyManager,
                               PurgeInstanceManager instanceManager,
                               PurgeWaveManager waveManager,
                               PurgeHudManager hudManager) {
        this.partyManager = partyManager;
        this.instanceManager = instanceManager;
        this.waveManager = waveManager;
        this.hudManager = hudManager;
        waveManager.setSessionManager(this);
    }

    public void setUpgradeManager(PurgeUpgradeManager upgradeManager) {
        this.upgradeManager = upgradeManager;
    }

    private String nextSessionId() {
        return "purge-" + sessionCounter.incrementAndGet();
    }

    // --- Public API ---

    public boolean hasActiveSession(UUID playerId) {
        return playerId != null && sessionIdByPlayer.containsKey(playerId);
    }

    public PurgeSession getSessionByPlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        String sessionId = sessionIdByPlayer.get(playerId);
        return sessionId != null ? sessionsById.get(sessionId) : null;
    }

    // --- startSession ---

    public void startSession(UUID requesterId, Ref<EntityStore> requesterRef) {
        if (requesterId == null || requesterRef == null || !requesterRef.isValid()) {
            return;
        }

        // Phase 1 — Validation
        if (hasActiveSession(requesterId)) {
            sendMessage(requesterRef, "You already have an active Purge session. Use /purge stop to end it.");
            return;
        }
        if (!waveManager.hasConfiguredWaves()) {
            sendMessage(requesterRef, "No purge waves configured. Ask an admin to set waves in /purge admin.");
            return;
        }

        // Collect valid players
        Map<UUID, Ref<EntityStore>> validPlayers = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();

        PurgeParty party = partyManager.getPartyByPlayer(requesterId);
        if (party != null) {
            for (UUID memberId : party.getMembersSnapshot()) {
                validateAndCollect(memberId, validPlayers, skipped);
            }
        } else {
            validateAndCollect(requesterId, validPlayers, skipped);
        }

        if (validPlayers.isEmpty()) {
            sendMessage(requesterRef, "No valid players to start a session.");
            return;
        }

        // Phase 2 — Atomic allocation
        PurgeMapInstance instance = instanceManager.acquireAvailableInstance();
        if (instance == null) {
            sendMessage(requesterRef, "All lobbies are busy, try again later.");
            return;
        }

        // Phase 3 — Point of no return
        String sessionId = null;
        PurgeSession session = null;
        try {
            // Dissolve party (consumed on launch)
            if (party != null) {
                partyManager.dissolveParty(party.getPartyId());
            }

            sessionId = nextSessionId();
            session = new PurgeSession(sessionId, instance.instanceId(), validPlayers);
            sessionsById.put(sessionId, session);
            for (UUID pid : validPlayers.keySet()) {
                sessionIdByPlayer.put(pid, sessionId);
            }

            // Notify requester about skipped members
            if (!skipped.isEmpty()) {
                sendMessage(requesterRef, "Starting with " + validPlayers.size()
                        + " player(s). Skipped: " + String.join(", ", skipped));
            }

            // Setup each player
            HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
            for (var entry : validPlayers.entrySet()) {
                UUID pid = entry.getKey();
                Ref<EntityStore> ref = entry.getValue();
                DamageBypassRegistry.add(pid);
                // Set initial weapon before granting loadout
                PurgeSessionPlayerState ps = session.getPlayerState(pid);
                if (ps != null && plugin != null) {
                    ps.setCurrentWeaponId(plugin.getWeaponConfigManager().getSessionWeaponId());
                }
                try {
                    if (plugin != null && ref.isValid()) {
                        Store<EntityStore> store = ref.getStore();
                        Player player = store.getComponent(ref, Player.getComponentType());
                        if (player != null) {
                            plugin.grantLoadout(player, ps);
                        }
                        teleportToStart(ref, store, instance);
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Failed to setup player " + pid);
                }
                hudManager.showRunHud(pid);
                hudManager.updatePlayerHealth(pid, 100, 100);
            }

            // Start countdown
            waveManager.startCountdown(session);

            LOGGER.atInfo().log("Purge session " + sessionId + " started with "
                    + validPlayers.size() + " player(s) in instance " + instance.instanceId());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to start session, rolling back and releasing instance");
            rollbackFailedSessionStart(sessionId, session, validPlayers);
            runSafe("release instance", () -> instanceManager.releaseInstance(instance.instanceId()));
        }
    }

    private void validateAndCollect(UUID memberId,
                                    Map<UUID, Ref<EntityStore>> validPlayers,
                                    List<String> skipped) {
        if (hasActiveSession(memberId)) {
            skipped.add(getPlayerName(memberId) + " (already in a session)");
            return;
        }
        Ref<EntityStore> ref = hudManager.getPlayerRef(memberId);
        if (ref == null || !ref.isValid()) {
            skipped.add(getPlayerName(memberId) + " (not available)");
            return;
        }
        // Verify player is in Purge world
        try {
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
            if (world == null || !ModeGate.isPurgeWorld(world)) {
                skipped.add(getPlayerName(memberId) + " (not in Purge world)");
                return;
            }
        } catch (Exception e) {
            skipped.add(getPlayerName(memberId) + " (not available)");
            return;
        }
        validPlayers.put(memberId, ref);
    }

    // --- leaveSession (individual stop) ---

    public void leaveSession(UUID playerId, String reason) {
        if (playerId == null) {
            return;
        }
        String sessionId = sessionIdByPlayer.get(playerId);
        if (sessionId == null) {
            return;
        }
        PurgeSession session = sessionsById.get(sessionId);
        if (session == null) {
            sessionIdByPlayer.remove(playerId);
            return;
        }
        SessionEndReason endReason = SessionEndReason.fromReason(reason);

        // Step 2: Handle upgrade phase before cleanup
        if (session.getPendingUpgradeChoices().remove(playerId)) {
            if (session.getPendingUpgradeChoices().isEmpty()) {
                runSafe("cancel upgrade timeout", () -> {
                    var task = session.getUpgradeTimeoutTask();
                    if (task != null) {
                        task.cancel(false);
                        session.setUpgradeTimeoutTask(null);
                    }
                });
                if (session.getState() == SessionState.UPGRADE_PICK) {
                    session.setState(SessionState.INTERMISSION);
                    waveManager.startIntermission(session);
                }
            }
        }

        // Step 3: Player cleanup
        PurgeSessionPlayerState playerState = session.getPlayerState(playerId);
        runSafe("persist results", () -> persistResults(playerId, session));
        runSafe("remove bypass", () -> DamageBypassRegistry.remove(playerId));
        runSafe("hide hud", () -> hudManager.hideRunHud(playerId));

        // Build summary before world cleanup
        int kills = playerState != null ? playerState.getKills() : 0;
        int summaryScrap = calculateScrapReward(session.getCurrentWave());
        String summary = "Purge ended - Wave " + session.getCurrentWave()
                + " - " + kills + " kills"
                + " - " + summaryScrap + " scrap earned"
                + " (" + reason + ")";

        // World-dependent cleanup for this player
        // Pass playerState directly — world.execute() is async and session.removePlayer()
        // below would delete the state before the world thread can access it.
        CompletableFuture<Void> playerCleanupFuture =
                runPlayerWorldCleanup(session, playerId, playerState, endReason, summary);

        // Step 4: Remove player from session + index
        session.removePlayer(playerId);
        sessionIdByPlayer.remove(playerId);

        LOGGER.atInfo().log("Player " + playerId + " left session " + sessionId + " (" + reason + ")");

        // Step 5: Check session closure
        if (session.getConnectedCount() == 0) {
            stopSessionById(sessionId, "no players connected", List.of(playerCleanupFuture));
        }
    }

    // --- stopSessionById (global technical stop) ---

    public void stopSessionById(String sessionId, String reason) {
        stopSessionById(sessionId, reason, List.of());
    }

    private void stopSessionById(String sessionId, String reason, List<CompletableFuture<Void>> priorCleanupFutures) {
        if (sessionId == null) {
            return;
        }
        PurgeSession session = sessionsById.remove(sessionId);
        if (session == null) {
            return;
        }
        SessionEndReason endReason = SessionEndReason.fromReason(reason);
        session.setState(SessionState.ENDED);

        // Step 1: Cancel all tasks
        runSafe("cancel tasks", session::cancelAllTasks);

        // Step 2: Remove all zombies
        CompletableFuture<Void> zombieCleanup = waveManager.removeAllZombies(session);
        List<CompletableFuture<Void>> cleanupFutures = new ArrayList<>();
        cleanupFutures.add(zombieCleanup);
        if (priorCleanupFutures != null && !priorCleanupFutures.isEmpty()) {
            cleanupFutures.addAll(priorCleanupFutures);
        }

        // Step 3: For each player still in session
        Set<UUID> participants = session.getParticipants();
        for (UUID pid : participants) {
            runSafe("persist " + pid, () -> persistResults(pid, session));
            runSafe("cleanup " + pid, () -> {
                DamageBypassRegistry.remove(pid);
                hudManager.hideRunHud(pid);
            });

            PurgeSessionPlayerState playerState = session.getPlayerState(pid);
            int kills = playerState != null ? playerState.getKills() : 0;
            int scrap = calculateScrapReward(session.getCurrentWave());
            String summary = "Purge ended - Wave " + session.getCurrentWave()
                    + " - " + kills + " kills"
                    + " - " + scrap + " scrap earned"
                    + " (" + reason + ")";

            CompletableFuture<Void> playerCleanupFuture =
                    runPlayerWorldCleanup(session, pid, playerState, endReason, summary);
            cleanupFutures.add(playerCleanupFuture);
            sessionIdByPlayer.remove(pid);
        }

        // Step 4: Release instance only after all world cleanup completes.
        CompletableFuture<Void> allCleanup = CompletableFuture.allOf(
                cleanupFutures.toArray(new CompletableFuture[0]));
        allCleanup.orTimeout(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((unused, throwable) -> {
            if (throwable != null) {
                Throwable cause = unwrapCompletionCause(throwable);
                if (cause instanceof TimeoutException) {
                    LOGGER.atWarning().log("Cleanup timeout for session " + sessionId
                            + " after " + CLEANUP_TIMEOUT_SECONDS
                            + "s; releasing instance with pending cleanup.");
                } else {
                    LOGGER.atWarning().withCause(cause).log("Cleanup failed for session " + sessionId);
                }
            }
            runSafe("release instance", () -> instanceManager.releaseInstance(session.getInstanceId()));
            LOGGER.atInfo().log("Session " + sessionId + " stopped (" + reason + ")");
        });
    }

    // --- cleanupPlayer ---

    public void cleanupPlayer(UUID playerId) {
        if (playerId != null && sessionIdByPlayer.containsKey(playerId)) {
            leaveSession(playerId, "disconnect");
        }
    }

    // --- shutdown ---

    public void shutdown() {
        for (String sessionId : sessionsById.keySet()) {
            try {
                stopSessionById(sessionId, "server shutdown");
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Shutdown cleanup failed for session " + sessionId);
            }
        }
        sessionsById.clear();
        sessionIdByPlayer.clear();
    }

    // --- Private helpers ---

    private void persistResults(UUID playerId, PurgeSession session) {
        PurgeSessionPlayerState playerState = session.getPlayerState(playerId);
        int kills = playerState != null ? playerState.getKills() : 0;

        PurgePlayerStats stats = PurgePlayerStore.getInstance().getOrCreate(playerId);
        stats.updateBestWave(session.getCurrentWave());
        stats.incrementKills(kills);
        stats.incrementSessions();
        PurgePlayerStore.getInstance().save(playerId, stats);

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

    private CompletableFuture<Void> runPlayerWorldCleanup(PurgeSession session, UUID playerId,
                                                          PurgeSessionPlayerState playerState,
                                                          SessionEndReason endReason, String summary) {
        CompletableFuture<Void> cleanupFuture = new CompletableFuture<>();
        if (playerState == null) {
            cleanupFuture.complete(null);
            return cleanupFuture;
        }
        Runnable cleanup = () -> {
            try {
                performPlayerCleanup(session, playerId, playerState, endReason, summary);
                cleanupFuture.complete(null);
            } catch (Exception e) {
                cleanupFuture.completeExceptionally(e);
            }
        };
        Ref<EntityStore> ref = playerState.getPlayerRef();
        World world = resolveWorld(ref);
        if (world != null) {
            try {
                world.execute(cleanup);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to schedule world cleanup, running inline");
                cleanup.run();
            }
        } else {
            cleanup.run();
        }
        return cleanupFuture;
    }

    private void performPlayerCleanup(PurgeSession session, UUID playerId,
                                       PurgeSessionPlayerState playerState,
                                       SessionEndReason endReason, String summary) {
        Ref<EntityStore> ref = playerState.getPlayerRef();
        runSafe("revert upgrades", () -> {
            PurgeUpgradeManager um = upgradeManager;
            if (um == null) {
                return;
            }
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                um.revertPlayerUpgrades(session, playerId, ref, store);
            }
        });
        runSafe("heal player", () -> {
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
            if (statMap != null) {
                statMap.maximizeStatValue(DefaultEntityStatTypes.getHealth());
            }
        });
        runSafe("remove loadout", () -> {
            if (!endReason.shouldRestoreIdleLoadout() || !isPurgeWorldRef(ref)) {
                return;
            }
            HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
            if (plugin == null) {
                return;
            }
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                plugin.removeLoadout(player);
            }
            if (endReason.shouldTeleportToExit()) {
                PurgeMapInstance instance = instanceManager.getInstance(session.getInstanceId());
                if (instance != null) {
                    teleportToExit(ref, store, instance);
                }
            }
        });
        runSafe("send summary", () -> sendMessage(ref, summary));
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
            LOGGER.atWarning().withCause(e).log("Failed to send purge message");
        }
    }

    private void runSafe(String label, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Cleanup failed [" + label + "]");
        }
    }

    private Throwable unwrapCompletionCause(Throwable throwable) {
        if (throwable instanceof CompletionException ce && ce.getCause() != null) {
            return ce.getCause();
        }
        return throwable;
    }

    private void teleportToStart(Ref<EntityStore> ref, Store<EntityStore> store, PurgeMapInstance instance) {
        PurgeLocation location = instance.startPoint();
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

    private void teleportToExit(Ref<EntityStore> ref, Store<EntityStore> store, PurgeMapInstance instance) {
        PurgeLocation location = instance.exitPoint();
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

    private World resolveWorld(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        if (store == null || store.getExternalData() == null) {
            return null;
        }
        return store.getExternalData().getWorld();
    }

    private boolean isPurgeWorldRef(Ref<EntityStore> ref) {
        World world = resolveWorld(ref);
        return world != null && ModeGate.isPurgeWorld(world);
    }

    private void rollbackFailedSessionStart(String sessionId,
                                            PurgeSession session,
                                            Map<UUID, Ref<EntityStore>> validPlayers) {
        if (session != null) {
            runSafe("cancel failed start tasks", session::cancelAllTasks);
        }
        if (sessionId != null) {
            sessionsById.remove(sessionId);
        }

        HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
        for (var entry : validPlayers.entrySet()) {
            UUID pid = entry.getKey();
            Ref<EntityStore> ref = entry.getValue();
            sessionIdByPlayer.remove(pid);
            runSafe("remove bypass rollback " + pid, () -> DamageBypassRegistry.remove(pid));
            runSafe("hide hud rollback " + pid, () -> hudManager.hideRunHud(pid));
            if (plugin != null) {
                runSafe("restore idle loadout rollback " + pid,
                        () -> restoreIdleLoadoutIfInPurgeWorld(plugin, ref));
            }
        }
    }

    private void restoreIdleLoadoutIfInPurgeWorld(HyvexaPurgePlugin plugin, Ref<EntityStore> ref) {
        if (plugin == null || ref == null || !ref.isValid() || !isPurgeWorldRef(ref)) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            plugin.removeLoadout(player);
        }
    }

    private String getPlayerName(UUID playerId) {
        return PurgePlayerNameResolver.resolve(playerId, PurgePlayerNameResolver.FallbackStyle.FULL_UUID);
    }

    private enum SessionEndReason {
        VOLUNTARY_STOP,
        VICTORY,
        LEFT_WORLD,
        DISCONNECT,
        TEAM_WIPED,
        NO_PLAYERS_CONNECTED,
        SERVER_SHUTDOWN,
        OTHER;

        static SessionEndReason fromReason(String reason) {
            if (reason == null) {
                return OTHER;
            }
            return switch (reason.trim().toLowerCase(Locale.ROOT)) {
                case "voluntary stop" -> VOLUNTARY_STOP;
                case "victory" -> VICTORY;
                case "left world" -> LEFT_WORLD;
                case "disconnect" -> DISCONNECT;
                case "team wiped" -> TEAM_WIPED;
                case "no players connected" -> NO_PLAYERS_CONNECTED;
                case "server shutdown" -> SERVER_SHUTDOWN;
                default -> OTHER;
            };
        }

        boolean shouldRestoreIdleLoadout() {
            return this != LEFT_WORLD && this != DISCONNECT;
        }

        boolean shouldTeleportToExit() {
            return this == VOLUNTARY_STOP || this == VICTORY;
        }
    }
}
