package io.hyvexa.purge.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.DamageBypassRegistry;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.purge.PurgeLoadoutService;
import io.hyvexa.purge.data.PurgeLocation;
import io.hyvexa.purge.data.PurgeMapInstance;
import io.hyvexa.purge.data.PurgeParty;
import io.hyvexa.purge.data.PurgePlayerStats;
import io.hyvexa.purge.data.PurgePlayerStore;
import io.hyvexa.purge.data.PurgeScrapStore;
import io.hyvexa.purge.data.PurgeSession;

import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.purge.data.PurgeSessionPlayerState;
import io.hyvexa.purge.data.SessionState;
import io.hyvexa.purge.hud.PurgeHudManager;
import io.hyvexa.purge.ui.PurgeGameOverPage;
import io.hyvexa.purge.util.PurgePlayerNameResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicInteger;

public class PurgeSessionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long CLEANUP_TIMEOUT_SECONDS = 8L;
    private static final float PURGE_SESSION_BASE_PLAYER_HP = 100f;
    private static final String PURGE_SESSION_BASE_HP_MODIFIER = "purge_session_base_hp";

    private final ConcurrentHashMap<String, PurgeSession> sessionsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> sessionIdByPlayer = new ConcurrentHashMap<>();
    private final PurgePartyManager partyManager;
    private final PurgeInstanceManager instanceManager;
    private final PurgeWaveManager waveManager;
    private final PurgeHudManager hudManager;
    private final PurgeWeaponConfigManager weaponConfigManager;
    private final PurgeLoadoutService loadoutService;
    private final AtomicInteger sessionCounter = new AtomicInteger(0);
    private PurgeManagerRegistry registry;

    public PurgeSessionManager(PurgePartyManager partyManager,
                               PurgeInstanceManager instanceManager,
                               PurgeWaveManager waveManager,
                               PurgeHudManager hudManager,
                               PurgeWeaponConfigManager weaponConfigManager,
                               PurgeLoadoutService loadoutService) {
        this.partyManager = partyManager;
        this.instanceManager = instanceManager;
        this.waveManager = waveManager;
        this.hudManager = hudManager;
        this.weaponConfigManager = weaponConfigManager;
        this.loadoutService = loadoutService;
    }

    void initRegistry(PurgeManagerRegistry registry) {
        this.registry = registry;
    }

    private String nextSessionId() {
        return "purge-" + sessionCounter.incrementAndGet();
    }

    // --- Public API ---

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
        if (getSessionByPlayer(requesterId) != null) {
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
            List<UUID> setupFailures = new ArrayList<>();

            for (var entry : validPlayers.entrySet()) {
                UUID pid = entry.getKey();
                Ref<EntityStore> ref = entry.getValue();
                DamageBypassRegistry.add(pid);
                // Set initial weapon before granting loadout
                PurgeSessionPlayerState ps = session.getPlayerState(pid);
                if (ps != null && weaponConfigManager != null) {
                    ps.setCurrentWeaponId(weaponConfigManager.getSessionWeaponId());
                    ps.setCurrentMeleeWeaponId(weaponConfigManager.getSessionMeleeWeaponId());
                }
                try {
                    if (loadoutService != null && ref.isValid()) {
                        Store<EntityStore> store = ref.getStore();
                        Player player = store.getComponent(ref, Player.getComponentType());
                        if (player != null) {
                            loadoutService.grantLoadout(player, ps);
                        }
                        applySessionBaseHealth(ref, store);
                        registry.getClassManager().applyClassEffects(session, pid, ref, store);
                        teleportTo(ref, store, instance.startPoint());
                    }
                    hudManager.showRunHud(pid);
                    if (ps != null && weaponConfigManager != null) {
                        String weaponId = ps.getCurrentWeaponId();
                        String displayName = weaponConfigManager.getDisplayName(weaponId);
                        hudManager.updateWeaponXpHud(pid, weaponId, displayName);
                        String meleeId = ps.getCurrentMeleeWeaponId();
                        if (meleeId != null) {
                            String meleeName = weaponConfigManager.getDisplayName(meleeId);
                            hudManager.updateMeleeXpHud(pid, meleeId, meleeName);
                        }
                    }
                    hudManager.registerComboPlayer(pid, ps);
                    hudManager.registerKillMeter(pid, session);
                    syncPlayerHealthHud(pid, ref);
                } catch (Exception e) {
                    setupFailures.add(pid);
                    LOGGER.atWarning().withCause(e).log("Failed to setup player " + pid);
                }
            }

            if (!setupFailures.isEmpty()) {
                throw new IllegalStateException("Aborting purge start; failed players: " + setupFailures);
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
        if (getSessionByPlayer(memberId) != null) {
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
        performPlayerSessionCleanup(playerId, session);

        // Build stats before world cleanup
        int kills = playerState != null ? playerState.getKills() : 0;
        int bestCombo = playerState != null ? playerState.getBestCombo() : 0;
        int wave = session.getCurrentWave();
        int summaryTotal = calculateTotalScrap(wave, playerState);

        // World-dependent cleanup for this player
        // Pass playerState directly — world.execute() is async and session.removePlayer()
        // below would delete the state before the world thread can access it.
        CompletableFuture<Void> playerCleanupFuture =
                runPlayerWorldCleanup(session, playerId, playerState, endReason,
                        wave, kills, summaryTotal, bestCombo, reason);

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
            performPlayerSessionCleanup(pid, session);

            PurgeSessionPlayerState playerState = session.getPlayerState(pid);
            int kills = playerState != null ? playerState.getKills() : 0;
            int bestCombo = playerState != null ? playerState.getBestCombo() : 0;
            int wave = session.getCurrentWave();
            int stopTotal = calculateTotalScrap(wave, playerState);

            CompletableFuture<Void> playerCleanupFuture =
                    runPlayerWorldCleanup(session, pid, playerState, endReason,
                            wave, kills, stopTotal, bestCombo, reason);
            cleanupFutures.add(playerCleanupFuture);
            sessionIdByPlayer.remove(pid);
        }

        // Step 4: Release instance only after all world cleanup completes.
        CompletableFuture<Void> allCleanup = CompletableFuture.allOf(
                cleanupFutures.toArray(new CompletableFuture[0]));

        allCleanup.whenComplete((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.atWarning().withCause(unwrapCompletionCause(throwable))
                        .log("Cleanup failed for session " + sessionId);
            }
            runSafe("release instance", () -> instanceManager.releaseInstance(session.getInstanceId()));
            LOGGER.atInfo().log("Session " + sessionId + " stopped (" + reason + ")");
        });

        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (!allCleanup.isDone()) {
                LOGGER.atWarning().log("Cleanup still running for session " + sessionId
                        + " after " + CLEANUP_TIMEOUT_SECONDS + "s");
            }
        }, CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    // --- cleanupPlayer ---

    public void cleanupPlayer(UUID playerId) {
        if (playerId != null && getSessionByPlayer(playerId) != null) {
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

    private void performPlayerSessionCleanup(UUID playerId, PurgeSession session) {
        runSafe("persist results", () -> persistResults(playerId, session));
        runSafe("remove bypass", () -> DamageBypassRegistry.remove(playerId));
        runSafe("hide hud", () -> {
            hudManager.unregisterComboPlayer(playerId);
            hudManager.unregisterKillMeter(playerId);
            hudManager.hideRunHud(playerId);
        });
    }

    private void persistResults(UUID playerId, PurgeSession session) {
        PurgeSessionPlayerState playerState = session.getPlayerState(playerId);
        int kills = playerState != null ? playerState.getKills() : 0;

        PurgePlayerStats stats = PurgePlayerStore.getInstance().getOrLoad(playerId);
        stats.updateBestWave(session.getCurrentWave());
        stats.incrementKills(kills);
        stats.incrementSessions();
        PurgePlayerStore.getInstance().save(playerId, stats);

        int totalScrap = calculateTotalScrap(session.getCurrentWave(), playerState);
        if (totalScrap > 0) {
            PurgeScrapStore.getInstance().addScrap(playerId, totalScrap);
            PurgeScrapStore.getInstance().flushPlayerAsync(playerId);
        }

        int bestCombo = playerState != null ? playerState.getBestCombo() : 0;
        registry.getMissionManager().recordSessionResult(playerId, session.getCurrentWave(), kills, bestCombo);
    }

    private static final int SCRAP_TIER_1_WAVE = 5;
    private static final int SCRAP_TIER_2_WAVE = 10;
    private static final int SCRAP_TIER_3_WAVE = 15;
    private static final int SCRAP_TIER_4_WAVE = 20;
    private static final int SCRAP_TIER_5_WAVE = 25;

    static int calculateScrapReward(int wavesReached) {
        if (wavesReached < SCRAP_TIER_1_WAVE) {
            return 0;
        } else if (wavesReached < SCRAP_TIER_2_WAVE) {
            return 20;
        } else if (wavesReached < SCRAP_TIER_3_WAVE) {
            return 60;
        } else if (wavesReached < SCRAP_TIER_4_WAVE) {
            return 120;
        } else if (wavesReached < SCRAP_TIER_5_WAVE) {
            return 200;
        } else {
            return 300 + 50 * ((wavesReached - SCRAP_TIER_5_WAVE) / 5);
        }
    }

    private int calculateTotalScrap(int wave, PurgeSessionPlayerState playerState) {
        int base = calculateScrapReward(wave);
        double mult = playerState != null
                ? registry.getClassManager().getScrapMultiplier(playerState) : 1.0;
        int bonus = playerState != null ? playerState.getBonusScrapFromClass() : 0;
        return (int) (base * mult) + bonus;
    }

    private CompletableFuture<Void> runPlayerWorldCleanup(PurgeSession session, UUID playerId,
                                                          PurgeSessionPlayerState playerState,
                                                          SessionEndReason endReason,
                                                          int wave, int kills, int totalScrap,
                                                          int bestCombo, String reason) {
        CompletableFuture<Void> cleanupFuture = new CompletableFuture<>();
        if (playerState == null) {
            cleanupFuture.complete(null);
            return cleanupFuture;
        }
        Runnable cleanup = () -> {
            try {
                performPlayerCleanup(session, playerId, playerState, endReason,
                        wave, kills, totalScrap, bestCombo, reason);
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
                                       SessionEndReason endReason,
                                       int wave, int kills, int totalScrap,
                                       int bestCombo, String reason) {
        Ref<EntityStore> ref = playerState.getPlayerRef();
        runSafe("revert class effects", () -> {
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                registry.getClassManager().revertClassEffects(session, playerId, ref, store);
            }
        });
        runSafe("revert upgrades", () -> {
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                registry.getUpgradeManager().revertPlayerUpgrades(session, playerId, ref, store);
            }
        });
        runSafe("restore base health", () -> {
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            clearSessionBaseHealth(ref, store, true);
        });
        runSafe("remove loadout", () -> {
            if (!endReason.shouldRestoreIdleLoadout() || !isPurgeWorldRef(ref)) {
                return;
            }
            if (loadoutService == null) {
                return;
            }
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                loadoutService.removeLoadout(player);
            }
            if (endReason.shouldTeleportToExit()) {
                PurgeMapInstance instance = instanceManager.getInstance(session.getInstanceId());
                if (instance != null) {
                    teleportTo(ref, store, instance.exitPoint());
                }
            }
        });
        runSafe("show summary", () -> {
            if (endReason.shouldShowGameOverPage() && ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef != null) {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        player.getPageManager().openCustomPage(ref, store,
                                new PurgeGameOverPage(playerRef, wave, kills, totalScrap, bestCombo, reason));
                        return;
                    }
                }
            }
            // Fallback to chat message
            String summary = "Purge ended - Wave " + wave
                    + " - " + kills + " kills"
                    + " - " + totalScrap + " scrap earned"
                    + " (" + reason + ")";
            sendMessage(ref, summary);
        });
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

    private void teleportTo(Ref<EntityStore> ref, Store<EntityStore> store, PurgeLocation location) {
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

        for (var entry : validPlayers.entrySet()) {
            UUID pid = entry.getKey();
            Ref<EntityStore> ref = entry.getValue();
            sessionIdByPlayer.remove(pid);
            runSafe("remove bypass rollback " + pid, () -> DamageBypassRegistry.remove(pid));
            runSafe("restore base health rollback " + pid, () -> {
                if (ref != null && ref.isValid()) {
                    clearSessionBaseHealth(ref, ref.getStore(), true);
                }
            });
            runSafe("hide hud rollback " + pid, () -> {
                hudManager.unregisterComboPlayer(pid);
                hudManager.unregisterKillMeter(pid);
                hudManager.hideRunHud(pid);
            });
            if (loadoutService != null) {
                runSafe("restore idle loadout rollback " + pid,
                        () -> restoreIdleLoadoutIfInPurgeWorld(ref));
            }
        }
    }

    private void restoreIdleLoadoutIfInPurgeWorld(Ref<EntityStore> ref) {
        if (loadoutService == null || ref == null || !ref.isValid() || !isPurgeWorldRef(ref)) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            loadoutService.removeLoadout(player);
        }
    }

    private String getPlayerName(UUID playerId) {
        return PurgePlayerNameResolver.resolve(playerId, PurgePlayerNameResolver.FallbackStyle.FULL_UUID);
    }

    private void applySessionBaseHealth(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid() || store == null) {
            return;
        }
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }
        int healthIndex = DefaultEntityStatTypes.getHealth();
        statMap.removeModifier(healthIndex, PURGE_SESSION_BASE_HP_MODIFIER);
        statMap.update();

        EntityStatValue health = statMap.get(healthIndex);
        float currentMax = health != null ? health.getMax() : PURGE_SESSION_BASE_PLAYER_HP;
        if (currentMax <= 0f) {
            currentMax = PURGE_SESSION_BASE_PLAYER_HP;
        }
        float delta = PURGE_SESSION_BASE_PLAYER_HP - currentMax;
        if (Math.abs(delta) > 0.01f) {
            statMap.putModifier(healthIndex, PURGE_SESSION_BASE_HP_MODIFIER,
                    new StaticModifier(Modifier.ModifierTarget.MAX,
                            StaticModifier.CalculationType.ADDITIVE, delta));
        }
        statMap.update();
        statMap.maximizeStatValue(healthIndex);
        statMap.update();
    }

    private void clearSessionBaseHealth(Ref<EntityStore> ref, Store<EntityStore> store, boolean healToFull) {
        if (ref == null || !ref.isValid() || store == null) {
            return;
        }
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }
        int healthIndex = DefaultEntityStatTypes.getHealth();
        statMap.removeModifier(healthIndex, PURGE_SESSION_BASE_HP_MODIFIER);
        statMap.update();
        if (healToFull) {
            statMap.maximizeStatValue(healthIndex);
            statMap.update();
        }
    }

    private void syncPlayerHealthHud(UUID playerId, Ref<EntityStore> ref) {
        if (playerId == null || ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return;
        }
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }
        EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
        if (health == null) {
            return;
        }
        hudManager.updatePlayerHealth(playerId, Math.round(health.get()), Math.round(health.getMax()));
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

        boolean shouldShowGameOverPage() {
            return this == VOLUNTARY_STOP || this == VICTORY || this == TEAM_WIPED;
        }

        boolean shouldRestoreIdleLoadout() {
            return this != LEFT_WORLD && this != DISCONNECT;
        }

        boolean shouldTeleportToExit() {
            return this == VOLUNTARY_STOP || this == VICTORY;
        }
    }
}
