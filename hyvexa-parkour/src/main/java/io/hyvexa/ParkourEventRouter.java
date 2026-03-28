package io.hyvexa;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import io.hyvexa.core.analytics.AnalyticsStore;
import io.hyvexa.core.discord.DiscordLinkStore;
import io.hyvexa.core.trail.TrailManager;
import io.hyvexa.core.vote.VoteManager;
import io.hyvexa.core.vote.VoteStore;
import io.hyvexa.common.util.AsyncExecutionHelper;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.duel.DuelTracker;
import io.hyvexa.manager.ChatFormatter;
import io.hyvexa.manager.CollisionManager;
import io.hyvexa.manager.HudManager;
import io.hyvexa.manager.InventorySyncManager;
import io.hyvexa.manager.PlaytimeManager;
import io.hyvexa.manager.PlayerCleanupManager;
import io.hyvexa.manager.PlayerPerksManager;
import io.hyvexa.manager.WorldMapManager;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.MedalStore;
import io.hyvexa.parkour.data.PlayerSettingsPersistence;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.RunStateStore;
import io.hyvexa.parkour.ghost.GhostNpcManager;
import io.hyvexa.parkour.pet.PetManager;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.ui.PlayerMusicPage;
import io.hyvexa.parkour.util.PlayerSettingsStore;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

class ParkourEventRouter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // HUD player tracking maps (moved from HyvexaPlugin)
    private final Map<UUID, PlayerRef> playerRefCache = new ConcurrentHashMap<>();
    private final Map<World, AtomicBoolean> hudTickInFlight = new ConcurrentHashMap<>();
    private final Map<World, Set<UUID>> hudPlayersByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, World> playerHudWorlds = new ConcurrentHashMap<>();

    // Dependencies
    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final RunTracker runTracker;
    private final RunStateStore runStateStore;
    private final DuelTracker duelTracker;
    private final HudManager hudManager;
    private final PlayerPerksManager perksManager;
    private final ChatFormatter chatFormatter;
    private final PlaytimeManager playtimeManager;
    private final PlayerCleanupManager cleanupManager;
    private final CollisionManager collisionManager;
    private final InventorySyncManager inventorySyncManager;
    private final WorldMapManager worldMapManager;
    private final AnalyticsStore analyticsStore;
    private final DiscordLinkStore discordLinkStore;
    private final TrailManager trailManager;
    private final VoteStore voteStore;
    private final VoteManager voteManager;
    private final MedalStore medalStore;
    private final PetManager petManager;
    private final GhostNpcManager ghostNpcManager;
    private final PlayerSettingsPersistence playerSettingsPersistence;
    private final BiFunction<PlayerRef, Store<EntityStore>, Boolean> parkourModeCheck;

    ParkourEventRouter(
            MapStore mapStore,
            ProgressStore progressStore,
            RunTracker runTracker,
            RunStateStore runStateStore,
            DuelTracker duelTracker,
            HudManager hudManager,
            PlayerPerksManager perksManager,
            ChatFormatter chatFormatter,
            PlaytimeManager playtimeManager,
            PlayerCleanupManager cleanupManager,
            CollisionManager collisionManager,
            InventorySyncManager inventorySyncManager,
            WorldMapManager worldMapManager,
            AnalyticsStore analyticsStore,
            DiscordLinkStore discordLinkStore,
            TrailManager trailManager,
            VoteStore voteStore,
            VoteManager voteManager,
            MedalStore medalStore,
            PetManager petManager,
            GhostNpcManager ghostNpcManager,
            PlayerSettingsPersistence playerSettingsPersistence,
            BiFunction<PlayerRef, Store<EntityStore>, Boolean> parkourModeCheck
    ) {
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.runTracker = runTracker;
        this.runStateStore = runStateStore;
        this.duelTracker = duelTracker;
        this.hudManager = hudManager;
        this.perksManager = perksManager;
        this.chatFormatter = chatFormatter;
        this.playtimeManager = playtimeManager;
        this.cleanupManager = cleanupManager;
        this.collisionManager = collisionManager;
        this.inventorySyncManager = inventorySyncManager;
        this.worldMapManager = worldMapManager;
        this.analyticsStore = analyticsStore;
        this.discordLinkStore = discordLinkStore;
        this.trailManager = trailManager;
        this.voteStore = voteStore;
        this.voteManager = voteManager;
        this.medalStore = medalStore;
        this.petManager = petManager;
        this.ghostNpcManager = ghostNpcManager;
        this.playerSettingsPersistence = playerSettingsPersistence;
        this.parkourModeCheck = parkourModeCheck;
    }

    // ── Event registration ──────────────────────────────────────────────

    void registerAll(EventRegistry eventRegistry) {
        eventRegistry.registerGlobal(PlayerConnectEvent.class, this::handlePlayerConnect);
        eventRegistry.registerGlobal(PlayerReadyEvent.class, this::handlePlayerReady);
        eventRegistry.registerGlobal(AddPlayerToWorldEvent.class, this::handleAddPlayerToWorld);
        eventRegistry.registerGlobal(PlayerDisconnectEvent.class, this::handlePlayerDisconnect);
        eventRegistry.registerGlobal(PlayerChatEvent.class, this::handlePlayerChat);
        registerVotifierListener(eventRegistry);
    }

    // ── Event handlers ──────────────────────────────────────────────────

    private void handlePlayerConnect(PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        cacheHudPlayer(playerRef);
        try {
            collisionManager.disablePlayerCollision(playerRef);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Exception in PlayerConnectEvent (collision)");
        }
        try {
            if (playerRef != null) {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    if (parkourModeCheck.apply(playerRef, store)) {
                        hudManager.ensureRunHud(playerRef);
                    }
                }
            }
            playtimeManager.startPlaytimeSession(playerRef);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Exception in PlayerConnectEvent (hud/playtime)");
        }
        try {
            playtimeManager.broadcastPresence(playerRef);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Exception in PlayerConnectEvent (broadcast)");
        }
        try {
            UUID playerId = playerRef.getUuid();
            boolean isNew = progressStore.shouldShowWelcome(playerId);
            analyticsStore.logEvent(playerId, "player_join",
                    "{\"is_new\":" + isNew + "}");
            analyticsStore.updatePlayerTimestamps(playerId, isNew);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Analytics: player_join");
        }
    }

    private void handlePlayerReady(PlayerReadyEvent event) {
        try {
            collisionManager.disablePlayerCollision(event.getPlayerRef());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Exception in PlayerReadyEvent (collision)");
        }
        try {
            if (runTracker != null) {
                runTracker.markPlayerReady(event.getPlayerRef());
            }
            inventorySyncManager.syncRunInventoryOnReady(event.getPlayerRef());
            // Disable world map generation to save memory (parkour server doesn't need it)
            worldMapManager.disableWorldMapForPlayer(event.getPlayerRef());
            Ref<EntityStore> ref = event.getPlayerRef();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                Player player = store.getComponent(ref, Player.getComponentType());
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                cacheHudPlayer(playerRef);
                // Single DB load, distribute to all stores
                PlayerSettingsPersistence.PlayerSettings savedSettings = null;
                if (playerRef != null) {
                    UUID pid = playerRef.getUuid();
                    savedSettings = playerSettingsPersistence != null ? playerSettingsPersistence.loadPlayer(pid) : null;
                    if (savedSettings != null) {
                        PlayerSettingsStore.loadFrom(pid, savedSettings);
                        PlayerMusicPage.loadFrom(pid, savedSettings);
                    }
                }
                PlayerMusicPage.applyStoredMusic(event.getPlayerRef());
                boolean parkourWorld = playerRef != null && parkourModeCheck.apply(playerRef, store);
                // Restore HUD hidden state and VIP speed from DB before first HUD attach
                if (parkourWorld && playerRef != null && savedSettings != null) {
                    hudManager.loadHudHiddenFrom(playerRef.getUuid(), savedSettings);
                    if (perksManager != null) {
                        float storedSpeed = perksManager.loadVipSpeedFrom(playerRef.getUuid(), savedSettings);
                        if (storedSpeed > 1.0f && progressStore != null
                                && (progressStore.isVip(playerRef.getUuid()) || progressStore.isFounder(playerRef.getUuid()))) {
                            perksManager.applyVipSpeedMultiplier(ref, store, playerRef, storedSpeed, false);
                        }
                    }
                }
                // Only modify HUD state on Parkour world to avoid racing MultipleHUD composite rebuild
                if (parkourWorld && player != null && playerRef != null) {
                    player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass);
                    hudManager.ensureRunHud(playerRef);
                }
                if (playerRef != null) {
                    scheduleDiscordReadyTasks(ref, store, playerRef, parkourWorld);
                    scheduleVoteReadyTasks(ref, store, playerRef);
                }
                // Hide all existing ghost NPCs from the newly connected player
                if (parkourWorld) {
                    if (ghostNpcManager != null && playerRef != null) {
                        ghostNpcManager.hideGhostsFromPlayer(playerRef.getUuid());
                    }
                }
                // Attempt to restore a saved run from DB if no in-memory run exists
                if (parkourWorld && playerRef != null && runStateStore != null
                        && runTracker.getActiveMapId(playerRef.getUuid()) == null) {
                    final PlayerRef pRef = playerRef;
                    final Ref<EntityStore> fRef = ref;
                    final Store<EntityStore> fStore = store;
                    runStateStore.loadAsync(playerRef.getUuid()).thenAccept(saved -> {
                        if (saved == null) {
                            LOGGER.atInfo().log("No saved run state for " + pRef.getUuid());
                            return;
                        }
                        UUID pid = pRef.getUuid();
                        LOGGER.atInfo().log("Found saved run for " + pid + " on map " + saved.mapId()
                                + " (" + saved.elapsedMs() + "ms, cp " + saved.lastCheckpointIndex() + ")");
                        io.hyvexa.parkour.data.Map savedMap = mapStore.getMapReadonly(saved.mapId());
                        if (savedMap == null || !savedMap.isActive()
                                || savedMap.getUpdatedAt() != saved.mapUpdatedAt()) {
                            LOGGER.atInfo().log("Discarding stale saved run for " + pid
                                    + " (map=" + (savedMap != null) + ", active=" + (savedMap != null && savedMap.isActive())
                                    + ", updatedMatch=" + (savedMap != null && savedMap.getUpdatedAt() == saved.mapUpdatedAt())
                                    + ", age=" + (System.currentTimeMillis() - saved.savedAt()) + "ms)");
                            runStateStore.deleteAsync(pid);
                            return;
                        }
                        if (fStore.getExternalData() == null) { runStateStore.deleteAsync(pid); return; }
                        World world = fStore.getExternalData().getWorld();
                        if (world == null) {
                            runStateStore.deleteAsync(pid);
                            return;
                        }
                        world.execute(() -> {
                            if (fRef == null || !fRef.isValid()) return;
                            runTracker.restoreRun(pid, saved);
                            runTracker.teleportToLastCheckpoint(fRef, fStore, pRef);
                            inventorySyncManager.syncRunInventoryOnReady(fRef);
                            try {
                                Player p = fStore.getComponent(fRef, Player.getComponentType());
                                if (p != null) {
                                    p.sendMessage(SystemMessageUtils.withParkourPrefix(
                                            Message.raw("Run restored on ").color(SystemMessageUtils.SECONDARY),
                                            Message.raw(savedMap.getName()).color(SystemMessageUtils.PRIMARY_TEXT),
                                            Message.raw(" (" + FormatUtils.formatDuration(saved.elapsedMs()) + " elapsed).").color(SystemMessageUtils.SECONDARY)
                                    ));
                                }
                            } catch (Exception e) {
                                LOGGER.atWarning().withCause(e).log("Failed to send run restore message");
                            }
                            runStateStore.deleteAsync(pid);
                        });
                    }).exceptionally(ex -> {
                        LOGGER.atWarning().withCause(ex).log("Failed to restore saved run");
                        return null;
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Exception in PlayerReadyEvent (setup)");
        }
    }

    private void handleAddPlayerToWorld(AddPlayerToWorldEvent event) {
        try {
            event.setBroadcastJoinMessage(false);
            var holder = event.getHolder();
            if (holder != null) {
                cacheHudPlayer(holder.getComponent(PlayerRef.getComponentType()));
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Exception in AddPlayerToWorldEvent");
        }
    }

    private void handlePlayerDisconnect(PlayerDisconnectEvent event) {
        // Each cleanup wrapped individually so one failure doesn't skip the rest
        if (event.getPlayerRef() != null) {
            UUID playerId = event.getPlayerRef().getUuid();

            try { if (duelTracker != null) { duelTracker.handleDisconnect(playerId); } }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: duelTracker"); }

            try { cleanupManager.handleDisconnect(event.getPlayerRef()); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: cleanupManager"); }

            try { if (medalStore != null) { medalStore.evictPlayer(playerId); } }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: MedalStore"); }

            try { if (trailManager != null) { trailManager.stopTrail(playerId); } }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: TrailManager"); }

            try { if (petManager != null) { petManager.despawnPet(playerId); } }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: PetManager"); }

            try { if (voteManager != null) { voteManager.unregisterPlayer(playerId); } }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: VoteManager"); }

            try { if (voteStore != null) { voteStore.evictPlayer(playerId); } }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: VoteStore"); }

            try { removeHudPlayer(playerId); }
            catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: HUD buckets"); }

            try {
                Long sessionStart = playtimeManager.getSessionStart(playerId);
                long sessionMs = sessionStart != null ? System.currentTimeMillis() - sessionStart : 0;
                analyticsStore.logEvent(playerId, "player_leave",
                        "{\"session_ms\":" + sessionMs + "}");
                analyticsStore.updatePlayerTimestamps(playerId, false);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Disconnect cleanup: Analytics");
            }
        }
    }

    private void handlePlayerChat(PlayerChatEvent event) {
        try {
            event.setFormatter((sender, content) -> chatFormatter.formatChatMessage(sender, content));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Exception in PlayerChatEvent");
        }
    }

    private void registerVotifierListener(EventRegistry eventRegistry) {
        try {
            Class.forName("org.hyvote.plugins.votifier.event.VoteEvent");
        } catch (ClassNotFoundException e) {
            LOGGER.atInfo().log("Votifier not present, skipping VoteEvent listener");
            return;
        }
        eventRegistry.registerGlobal(
                org.hyvote.plugins.votifier.event.VoteEvent.class, event -> {
                    try {
                        String username = event.getUsername();
                        if (username == null || username.isBlank()) {
                            return;
                        }
                        // Try online players first (fast path)
                        UUID resolvedId = null;
                        for (PlayerRef playerRef : Universe.get().getPlayers()) {
                            if (playerRef != null && username.equalsIgnoreCase(playerRef.getUsername())) {
                                resolvedId = playerRef.getUuid();
                                break;
                            }
                        }
                        // Fallback to ProgressStore cache for offline players
                        if (resolvedId == null && progressStore != null) {
                            resolvedId = progressStore.getPlayerIdByName(username);
                        }
                        if (resolvedId != null) {
                            if (voteStore != null) {
                                voteStore.recordVote(resolvedId, username, "votifier");
                            }
                        } else {
                            LOGGER.atFine().log("VoteEvent for unknown player: " + username);
                        }
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Failed to handle VoteEvent");
                    }
                });
        LOGGER.atInfo().log("VoteEvent listener registered for votifier integration");
    }

    // ── Helper methods (called from event handlers) ─────────────────────

    private void scheduleDiscordReadyTasks(Ref<EntityStore> ref, Store<EntityStore> store,
                                           PlayerRef playerRef, boolean parkourWorld) {
        if (ref == null || store == null || playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (playerId == null || world == null) {
            return;
        }
        if (discordLinkStore == null) {
            return;
        }
        discordLinkStore.checkAndRewardVexaOnLoginAsync(playerId)
                .thenAcceptAsync(rewarded -> {
                    if (!rewarded || !ref.isValid()) {
                        return;
                    }
                    Store<EntityStore> currentStore = ref.getStore();
                    Player player = currentStore != null
                            ? currentStore.getComponent(ref, Player.getComponentType())
                            : null;
                    if (player != null) {
                        discordLinkStore.sendRewardGrantedMessage(player);
                    }
                }, world)
                .exceptionally(ex -> {
                    LOGGER.atWarning().withCause(ex).log("Discord link check failed");
                    return null;
                });
        if (!parkourWorld || mapStore == null || progressStore == null) {
            return;
        }
        String rank = progressStore.getRankName(playerId, mapStore);
        discordLinkStore.updateRankIfLinkedAsync(playerId, rank)
                .exceptionally(ex -> {
                    LOGGER.atWarning().withCause(ex).log("Discord rank sync failed");
                    return null;
                });
    }

    private void scheduleVoteReadyTasks(Ref<EntityStore> ref, Store<EntityStore> store,
                                        PlayerRef playerRef) {
        if (ref == null || store == null || playerRef == null || voteManager == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        String username = playerRef.getUsername();
        if (playerId == null || username == null) {
            return;
        }
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null) {
            return;
        }
        voteManager.registerPlayer(playerId, username);
        voteManager.checkAndRewardAsync(playerId)
                .thenAcceptAsync(count -> {
                    if (count <= 0 || !ref.isValid()) {
                        return;
                    }
                    Player player = ref.getStore().getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        int total = count * voteManager.getRewardPerVote();
                        String suffix = count > 1 ? " (x" + count + ")" : "";
                        player.sendMessage(Message.join(
                                Message.raw("You received ").color("#a3e635"),
                                Message.raw(total + " feathers").color("#4ade80").bold(true),
                                Message.raw(" for voting!" + suffix).color("#a3e635")
                        ));
                    }
                }, world)
                .exceptionally(ex -> {
                    LOGGER.atWarning().withCause(ex).log("Vote check on login failed");
                    return null;
                });
    }

    // ── HUD player tracking ─────────────────────────────────────────────

    void cacheHudPlayer(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        playerRefCache.put(playerId, playerRef);
        syncHudPlayerWorld(playerRef, playerId);
    }

    private void syncHudPlayerWorld(PlayerRef playerRef, UUID playerId) {
        if (playerId == null) {
            return;
        }
        Ref<EntityStore> ref = playerRef != null ? playerRef.getReference() : null;
        if (ref == null || !ref.isValid()) {
            removeHudPlayerFromWorld(playerId);
            return;
        }
        Store<EntityStore> store = ref.getStore();
        if (store == null || store.getExternalData() == null) {
            removeHudPlayerFromWorld(playerId);
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            removeHudPlayerFromWorld(playerId);
            return;
        }
        World previousWorld = playerHudWorlds.put(playerId, world);
        if (previousWorld != null && previousWorld != world) {
            removeHudPlayerFromWorld(previousWorld, playerId);
        }
        hudPlayersByWorld.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    void removeHudPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        playerRefCache.remove(playerId);
        removeHudPlayerFromWorld(playerId);
    }

    private void removeHudPlayerFromWorld(UUID playerId) {
        if (playerId == null) {
            return;
        }
        World previousWorld = playerHudWorlds.remove(playerId);
        if (previousWorld != null) {
            removeHudPlayerFromWorld(previousWorld, playerId);
        }
    }

    private void removeHudPlayerFromWorld(World world, UUID playerId) {
        if (world == null || playerId == null) {
            return;
        }
        Set<UUID> playerIds = hudPlayersByWorld.get(world);
        if (playerIds == null) {
            return;
        }
        playerIds.remove(playerId);
        if (playerIds.isEmpty()) {
            hudPlayersByWorld.remove(world, playerIds);
        }
    }

    void tickHudUpdates() {
        for (Map.Entry<World, Set<UUID>> entry : hudPlayersByWorld.entrySet()) {
            World world = entry.getKey();
            if (!ModeGate.isParkourWorld(world)) {
                continue;
            }
            Set<UUID> playerIds = entry.getValue();
            if (playerIds == null) {
                continue;
            }
            if (playerIds.isEmpty()) {
                hudPlayersByWorld.remove(world, playerIds);
                continue;
            }
            String worldName = world.getName() != null ? world.getName() : "unknown";
            String contextText = "world=" + worldName + ", players=" + playerIds.size();
            AtomicBoolean inFlight = hudTickInFlight.computeIfAbsent(world, ignored -> new AtomicBoolean(false));
            if (!inFlight.compareAndSet(false, true)) {
                continue;
            }
            try {
                CompletableFuture.runAsync(() -> {
                    try {
                        for (UUID playerId : playerIds) {
                            PlayerRef playerRef = playerRefCache.get(playerId);
                            if (playerRef == null) {
                                removeHudPlayer(playerId);
                                continue;
                            }
                            Ref<EntityStore> ref = playerRef.getReference();
                            if (ref == null || !ref.isValid()) {
                                removeHudPlayerFromWorld(playerId);
                                continue;
                            }
                            Store<EntityStore> store = ref.getStore();
                            if (store == null || store.getExternalData() == null) {
                                removeHudPlayerFromWorld(playerId);
                                continue;
                            }
                            World playerWorld = store.getExternalData().getWorld();
                            if (playerWorld != world) {
                                syncHudPlayerWorld(playerRef, playerId);
                                continue;
                            }
                            if (!ModeGate.isParkourWorld(world)) {
                                continue;
                            }
                            if (hudManager != null) {
                                hudManager.ensureRunHudNow(ref, store, playerRef);
                                hudManager.updateRunHud(ref, store);
                            }
                        }
                    } finally {
                        inFlight.set(false);
                    }
                }, world).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
                    inFlight.set(false);
                    AsyncExecutionHelper.logThrottledWarning(
                            "parkour.hud.tick",
                            "parkour HUD tick update",
                            contextText,
                            ex
                    );
                    return null;
                });
            } catch (Exception e) {
                inFlight.set(false);
                AsyncExecutionHelper.logThrottledWarning(
                        "parkour.hud.tick",
                        "parkour HUD tick update",
                        contextText,
                        e
                );
            }
        }
    }

    void clearHudTracking() {
        playerRefCache.clear();
        hudPlayersByWorld.clear();
        playerHudWorlds.clear();
    }
}
