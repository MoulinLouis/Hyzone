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

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
}
