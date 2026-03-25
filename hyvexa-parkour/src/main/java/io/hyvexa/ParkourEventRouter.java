package io.hyvexa;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.core.analytics.AnalyticsStore;
import io.hyvexa.core.cosmetic.CosmeticStore;
import io.hyvexa.core.discord.DiscordLinkStore;
import io.hyvexa.core.economy.CurrencyStore;
import io.hyvexa.core.trail.TrailManager;
import io.hyvexa.core.vote.VoteManager;
import io.hyvexa.core.vote.VoteStore;
import io.hyvexa.duel.DuelTracker;
import io.hyvexa.manager.AnnouncementManager;
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
import io.hyvexa.parkour.data.SettingsStore;
import io.hyvexa.parkour.ghost.GhostNpcManager;
import io.hyvexa.parkour.pet.PetManager;
import io.hyvexa.parkour.tracker.RunTracker;

import io.hyvexa.common.util.AsyncExecutionHelper;
import io.hyvexa.common.util.ModeGate;

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
    private final SettingsStore settingsStore;
    private final RunTracker runTracker;
    private final RunStateStore runStateStore;
    private final DuelTracker duelTracker;
    private final HudManager hudManager;
    private final AnnouncementManager announcementManager;
    private final PlayerPerksManager perksManager;
    private final ChatFormatter chatFormatter;
    private final PlaytimeManager playtimeManager;
    private final PlayerCleanupManager cleanupManager;
    private final CollisionManager collisionManager;
    private final InventorySyncManager inventorySyncManager;
    private final WorldMapManager worldMapManager;
    private final AnalyticsStore analyticsStore;
    private final DiscordLinkStore discordLinkStore;
    private final CurrencyStore vexaStore;
    private final CurrencyStore featherStore;
    private final CosmeticStore cosmeticStore;
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
            SettingsStore settingsStore,
            RunTracker runTracker,
            RunStateStore runStateStore,
            DuelTracker duelTracker,
            HudManager hudManager,
            AnnouncementManager announcementManager,
            PlayerPerksManager perksManager,
            ChatFormatter chatFormatter,
            PlaytimeManager playtimeManager,
            PlayerCleanupManager cleanupManager,
            CollisionManager collisionManager,
            InventorySyncManager inventorySyncManager,
            WorldMapManager worldMapManager,
            AnalyticsStore analyticsStore,
            DiscordLinkStore discordLinkStore,
            CurrencyStore vexaStore,
            CurrencyStore featherStore,
            CosmeticStore cosmeticStore,
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
        this.settingsStore = settingsStore;
        this.runTracker = runTracker;
        this.runStateStore = runStateStore;
        this.duelTracker = duelTracker;
        this.hudManager = hudManager;
        this.announcementManager = announcementManager;
        this.perksManager = perksManager;
        this.chatFormatter = chatFormatter;
        this.playtimeManager = playtimeManager;
        this.cleanupManager = cleanupManager;
        this.collisionManager = collisionManager;
        this.inventorySyncManager = inventorySyncManager;
        this.worldMapManager = worldMapManager;
        this.analyticsStore = analyticsStore;
        this.discordLinkStore = discordLinkStore;
        this.vexaStore = vexaStore;
        this.featherStore = featherStore;
        this.cosmeticStore = cosmeticStore;
        this.trailManager = trailManager;
        this.voteStore = voteStore;
        this.voteManager = voteManager;
        this.medalStore = medalStore;
        this.petManager = petManager;
        this.ghostNpcManager = ghostNpcManager;
        this.playerSettingsPersistence = playerSettingsPersistence;
        this.parkourModeCheck = parkourModeCheck;
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
        com.hypixel.hytale.component.Ref<EntityStore> ref = playerRef != null ? playerRef.getReference() : null;
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
                            com.hypixel.hytale.component.Ref<EntityStore> ref = playerRef.getReference();
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
