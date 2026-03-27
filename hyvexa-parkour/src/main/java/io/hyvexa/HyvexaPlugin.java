package io.hyvexa;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.duel.DuelQueue;
import io.hyvexa.duel.DuelTracker;
import io.hyvexa.duel.command.DuelCommand;
import io.hyvexa.duel.data.DuelMatchStore;
import io.hyvexa.duel.data.DuelPreferenceStore;
import io.hyvexa.duel.data.DuelStatsStore;
import io.hyvexa.duel.interaction.DuelMenuInteraction;
import io.hyvexa.duel.interaction.ForfeitInteraction;
import io.hyvexa.core.bridge.GameModeBridge;
import io.hyvexa.core.analytics.AnalyticsStore;
import io.hyvexa.core.analytics.PlayerAnalytics;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.discord.DiscordLinkStore;
import io.hyvexa.core.cosmetic.CosmeticStore;
import io.hyvexa.core.wardrobe.WardrobeBridge;
import io.hyvexa.core.economy.VexaStore;
import io.hyvexa.core.trail.TrailManager;


import io.hyvexa.core.economy.FeatherStore;
import io.hyvexa.core.vote.VoteConfig;
import io.hyvexa.core.vote.VoteManager;
import io.hyvexa.core.vote.VoteStore;

import io.hyvexa.parkour.data.GlobalMessageStore;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ParkourDatabaseSetup;
import io.hyvexa.parkour.data.PlayerSettingsPersistence;
import io.hyvexa.parkour.data.MedalRewardStore;
import io.hyvexa.parkour.data.MedalStore;
import io.hyvexa.parkour.data.PlayerCountStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.RunStateStore;
import io.hyvexa.parkour.data.SettingsStore;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import io.hyvexa.parkour.interaction.ParkourInteractionBridge;
import io.hyvexa.parkour.interaction.MenuInteraction;
import io.hyvexa.parkour.interaction.LeaderboardInteraction;
import io.hyvexa.parkour.interaction.LeaveInteraction;
import io.hyvexa.parkour.interaction.LeavePracticeInteraction;
import io.hyvexa.parkour.interaction.PracticeCheckpointInteraction;
import io.hyvexa.parkour.interaction.PlayerSettingsInteraction;
import io.hyvexa.parkour.interaction.PracticeInteraction;
import io.hyvexa.parkour.interaction.ResetInteraction;
import io.hyvexa.parkour.interaction.RestartCheckpointInteraction;
import io.hyvexa.parkour.interaction.RunOrFallJoinBridgeInteraction;
import io.hyvexa.parkour.interaction.StatsInteraction;
import io.hyvexa.parkour.interaction.ToggleFlyInteraction;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import io.hyvexa.manager.WorldMapManager;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.parkour.ghost.GhostNpcManager;
import io.hyvexa.parkour.ghost.GhostRecorder;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.common.skin.PurgeSkinStore;
import io.hyvexa.parkour.command.CheckpointCommand;
import io.hyvexa.parkour.command.CosmeticTestCommand;
import io.hyvexa.parkour.command.PetTestCommand;
import io.hyvexa.parkour.pet.PetManager;
import io.hyvexa.parkour.command.CreditsCommand;
import io.hyvexa.parkour.command.VexaCommand;
import io.hyvexa.parkour.command.AnalyticsCommand;
import io.hyvexa.parkour.command.LinkCommand;
import io.hyvexa.parkour.command.MobGalleryCommand;
import io.hyvexa.parkour.command.UnlinkCommand;
import io.hyvexa.parkour.command.DatabaseClearCommand;
import io.hyvexa.parkour.command.DatabaseReloadCommand;
import io.hyvexa.parkour.command.DatabaseTestCommand;
import io.hyvexa.parkour.command.DiscordCommand;
import io.hyvexa.parkour.command.MessageTestCommand;
import io.hyvexa.parkour.command.ParkourAdminItemCommand;
import io.hyvexa.parkour.command.ParkourCommand;
import io.hyvexa.parkour.command.ParkourMusicDebugCommand;
import io.hyvexa.parkour.command.RulesCommand;
import io.hyvexa.parkour.command.StoreCommand;
import io.hyvexa.parkour.command.SpectatorCommand;
import io.hyvexa.parkour.command.FeatherCommand;

import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.system.NoDropSystem;
import io.hyvexa.parkour.system.NoBreakSystem;
import io.hyvexa.parkour.system.NoLaunchpadEditSystem;
import io.hyvexa.parkour.system.NoPlayerDamageSystem;
import io.hyvexa.parkour.system.NoPlayerKnockbackSystem;
import io.hyvexa.parkour.system.NoWeaponDamageSystem;
import io.hyvexa.common.visibility.EntityVisibilityFilterSystem;
import io.hyvexa.parkour.system.RunTrackerTickSystem;
import io.hyvexa.parkour.ui.AdminPageUtils;
import io.hyvexa.parkour.ui.ParkourAdminNavigator;
import io.hyvexa.parkour.ui.PlayerMusicPage;
import io.hyvexa.parkour.util.PlayerSettingsStore;
import io.hyvexa.manager.AnnouncementManager;
import io.hyvexa.manager.ChatFormatter;
import io.hyvexa.manager.CollisionManager;
import io.hyvexa.manager.HudManager;
import io.hyvexa.manager.InventorySyncManager;
import io.hyvexa.manager.LeaderboardHologramManager;
import io.hyvexa.manager.PlaytimeManager;
import io.hyvexa.manager.PlayerCleanupManager;
import io.hyvexa.manager.PlayerPerksManager;
import io.hyvexa.parkour.ParkourTimingConstants;
import io.hyvexa.common.WorldConstants;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HyvexaPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long PLAYER_COUNT_SAMPLE_SECONDS = PlayerCountStore.DEFAULT_SAMPLE_INTERVAL_SECONDS;
    public static final String PARKOUR_WORLD_NAME = WorldConstants.WORLD_PARKOUR;
    private static final String DISCORD_URL = "https://discord.gg/2PAygkyFnK";
    private static final String JOIN_LANGUAGE_NOTICE =
            "This is an English-speaking community server. Please use English only in the chat. "
            + "For other languages, join our ";
    private static final String JOIN_LANGUAGE_NOTICE_SUFFIX = ". Happy Jumping <3";
    private static HyvexaPlugin INSTANCE;
    private MapStore mapStore;
    private RunTracker runTracker;
    private ProgressStore progressStore;
    private SettingsStore settingsStore;
    private PlayerCountStore playerCountStore;
    private GlobalMessageStore globalMessageStore;
    private VoteStore voteStore;
    private MedalStore medalStore;
    private MedalRewardStore medalRewardStore;
    private DuelQueue duelQueue;
    private DuelTracker duelTracker;
    private DuelStatsStore duelStatsStore;
    private DuelMatchStore duelMatchStore;
    private DuelPreferenceStore duelPreferenceStore;
    private AnalyticsStore analyticsStore;
    private HudManager hudManager;
    private AnnouncementManager announcementManager;
    private PlayerPerksManager perksManager;
    private ChatFormatter chatFormatter;
    private PlaytimeManager playtimeManager;
    private PlayerCleanupManager cleanupManager;
    private LeaderboardHologramManager leaderboardHologramManager;
    private CollisionManager collisionManager;
    private InventorySyncManager inventorySyncManager;
    private WorldMapManager worldMapManager;
    private ScheduledFuture<?> hudUpdateTask;
    private ScheduledFuture<?> playtimeTask;
    private ScheduledFuture<?> collisionTask;
    private ScheduledFuture<?> playerCountTask;
    private ScheduledFuture<?> stalePlayerSweepTask;
    private ScheduledFuture<?> runStateAutosaveTask;
    private ScheduledFuture<?> teleportDebugTask;
    private ScheduledFuture<?> duelTickTask;
    private ScheduledFuture<?> votePollingTask;

    private VoteManager voteManager;
    private PlayerSettingsPersistence playerSettingsPersistence;
    private RunStateStore runStateStore;
    private Thread shutdownHook;
    private GhostStore ghostStore;
    private GhostRecorder ghostRecorder;
    private GhostNpcManager ghostNpcManager;
    private PetManager petManager;
    private DiscordLinkStore discordLinkStore;
    private io.hyvexa.core.trail.TrailManager trailManager;
    private io.hyvexa.core.economy.CurrencyStore vexaStore;
    private io.hyvexa.core.economy.CurrencyStore featherStore;
    private CosmeticStore cosmeticStore;
    private ParkourEventRouter eventRouter;

    public HyvexaPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
        LOGGER.atInfo().log("Hello from " + this.getName() + " version " + this.getManifest().getVersion().toString());
    }

    @Override
    public CompletableFuture<Void> preLoad() {
        registerInteractionCodecs();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());
        var folder = new File("mods/Parkour");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        try {
            DatabaseManager.createAndRegister().initialize();
            LOGGER.atInfo().log("Database connection initialized");
            ParkourDatabaseSetup.ensureTables();
            this.playerSettingsPersistence = new PlayerSettingsPersistence(DatabaseManager.get());
            this.playerSettingsPersistence.ensureTable();
            PlayerSettingsStore.setPersistence(this.playerSettingsPersistence);
            PlayerMusicPage.setPersistence(this.playerSettingsPersistence);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to initialize database");
        }
        this.voteStore = new VoteStore(DatabaseManager.get());
        this.medalRewardStore = new MedalRewardStore(DatabaseManager.get());
        this.medalStore = new MedalStore(DatabaseManager.get());
        initSafe("VexaStore", () -> VexaStore.createAndRegister(DatabaseManager.get()));
        this.vexaStore = VexaStore.get();
        initSafe("DiscordLinkStore", () -> DiscordLinkStore.createAndRegister(DatabaseManager.get()));
        this.discordLinkStore = DiscordLinkStore.get();
        initSafe("DiscordLinkStore.initialize", () -> discordLinkStore.initialize());
        initSafe("FeatherStore", () -> FeatherStore.createAndRegister(DatabaseManager.get()));
        this.featherStore = FeatherStore.get();
        initSafe("VoteStore", () -> voteStore.initialize());
        initSafe("MedalRewardStore", () -> medalRewardStore.initialize());
        initSafe("MedalStore", () -> medalStore.initialize());
        initSafe("CosmeticStore", () -> CosmeticStore.createAndRegister(DatabaseManager.get()));
        this.cosmeticStore = CosmeticStore.get();
        initSafe("AnalyticsStore", () -> {
            this.analyticsStore = AnalyticsStore.createAndRegister(DatabaseManager.get());
            analyticsStore.purgeOldEvents(90);
        });
        initSafe("VoteManager", () -> {
            VoteConfig voteConfig = VoteConfig.load();
            this.voteManager = new VoteManager();
            this.voteManager.initialize(voteConfig, voteStore, featherStore);
        });
        this.collisionManager = new CollisionManager();
        this.mapStore = new MapStore(DatabaseManager.get());
        this.mapStore.syncLoad();
        this.mapStore.setOnChangeListener(this::onMapStoreChanged);
        this.settingsStore = new SettingsStore(DatabaseManager.get());
        this.settingsStore.syncLoad();
        PlayerAnalytics analytics = analyticsStore;
        cosmeticStore.setAnalytics(analytics);
        discordLinkStore.setAnalytics(analytics);
        discordLinkStore.setVexaStore(vexaStore);
        WardrobeBridge wardrobeBridge = new WardrobeBridge(DatabaseManager.get());
        wardrobeBridge.setAnalytics(analytics);
        wardrobeBridge.setCurrencyStores(vexaStore, featherStore);
        PurgeSkinStore.get().setVexaStore(vexaStore);
        this.progressStore = new ProgressStore(DatabaseManager.get());
        this.progressStore.setAnalytics(analytics);
        this.progressStore.syncLoad();
        this.playerCountStore = new PlayerCountStore(DatabaseManager.get());
        this.playerCountStore.syncLoad();
        this.globalMessageStore = new GlobalMessageStore(DatabaseManager.get());
        this.globalMessageStore.syncLoad();
        this.runTracker = new RunTracker(this.mapStore, this.progressStore, this.settingsStore,
                this.medalStore, this.medalRewardStore, analytics, featherStore);
        this.runStateStore = new RunStateStore(DatabaseManager.get());
        this.runStateStore.ensureTable();
        this.runTracker.setRunStateStore(this.runStateStore);
        // Saved runs persist indefinitely — only invalidated when the map changes
        // or is deleted (FK CASCADE). No time-based cleanup.
        // JVM shutdown hook — fires on kill/Ctrl+C even when plugin shutdown() is skipped
        this.shutdownHook = new Thread(() -> {
            try {
                if (runTracker != null && runStateStore != null && DatabaseManager.get().isInitialized()) {
                    runTracker.saveAllActiveRuns(runStateStore);
                }
            } catch (Exception e) {
                // Can't use LOGGER in shutdown hook — JVM may have closed logging
            }
        }, "RunStateSaveHook");
        Runtime.getRuntime().addShutdownHook(this.shutdownHook);
        this.ghostStore = new GhostStore("parkour_ghost_recordings", "parkour", DatabaseManager.get());
        this.ghostStore.syncLoad();
        this.ghostRecorder = new GhostRecorder(this.ghostStore);
        this.ghostRecorder.start();
        this.ghostNpcManager = new GhostNpcManager(this.ghostStore, this.mapStore);
        this.ghostNpcManager.start();
        this.petManager = new PetManager();
        this.petManager.start();
        this.trailManager = new TrailManager();
        this.runTracker.setGhostRecorder(this.ghostRecorder);
        this.runTracker.setGhostNpcManager(this.ghostNpcManager);
        this.duelStatsStore = new DuelStatsStore(DatabaseManager.get());
        this.duelStatsStore.syncLoad();
        this.duelMatchStore = new DuelMatchStore(DatabaseManager.get());
        this.duelMatchStore.ensureTable();
        this.duelPreferenceStore = new DuelPreferenceStore(DatabaseManager.get());
        this.duelPreferenceStore.syncLoad();
        this.duelQueue = new DuelQueue();
        this.duelTracker = new DuelTracker(duelQueue, duelMatchStore, duelStatsStore, duelPreferenceStore, mapStore, progressStore, settingsStore, analytics);
        this.perksManager = new PlayerPerksManager(progressStore, mapStore, playerSettingsPersistence);
        this.chatFormatter = new ChatFormatter(progressStore, mapStore, perksManager);
        this.hudManager = new HudManager(progressStore, mapStore, runTracker, duelTracker, perksManager,
                vexaStore, featherStore, playerSettingsPersistence);
        this.announcementManager = new AnnouncementManager(globalMessageStore, hudManager,
                this::scheduleTick, this::cancelScheduled);
        this.playtimeManager = new PlaytimeManager(progressStore, playerCountStore);
        this.cleanupManager = new PlayerCleanupManager(hudManager, announcementManager, perksManager, playtimeManager,
                runTracker);
        this.leaderboardHologramManager = new LeaderboardHologramManager(progressStore, mapStore,
                PARKOUR_WORLD_NAME, medalStore);
        this.inventorySyncManager = new InventorySyncManager(mapStore, progressStore, runTracker,
                this::shouldApplyParkourMode, DISCORD_URL, JOIN_LANGUAGE_NOTICE, JOIN_LANGUAGE_NOTICE_SUFFIX);
        this.worldMapManager = new WorldMapManager(true);
        ParkourInteractionBridge.configure(new ParkourInteractionBridge.Services(
                mapStore, progressStore, runTracker, duelTracker, medalStore, duelPreferenceStore,
                ghostNpcManager, hudManager, this::hideRunHud, this::showRunHud,
                this::applyVipSpeedMultiplier));
        this.runTracker.setDuelTracker(duelTracker);
        this.runTracker.getValidator().setPluginServices(hudManager,
                io.hyvexa.core.cosmetic.CosmeticManager.get(),
                discordLinkStore,
                this::invalidateRankCache,
                this::refreshLeaderboardHologram, this::refreshMapLeaderboardHologram);
        this.progressStore.setRankCacheInvalidator(this::invalidateRankCache);
        registerRunTrackerTickSystem();
        hudUpdateTask = scheduleTick("hud updates", this::tickHudUpdates,
                ParkourTimingConstants.HUD_UPDATE_INTERVAL_MS, ParkourTimingConstants.HUD_UPDATE_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        playtimeTask = scheduleTick("playtime", this::tickPlaytime,
                ParkourTimingConstants.PLAYTIME_TICK_INTERVAL_SECONDS, ParkourTimingConstants.PLAYTIME_TICK_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        collisionTask = scheduleTick("collision removal", this::tickCollisionRemoval, 1,
                ParkourTimingConstants.COLLISION_REMOVAL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        playerCountTask = scheduleTick("player counts", this::tickPlayerCounts, 5, PLAYER_COUNT_SAMPLE_SECONDS,
                TimeUnit.SECONDS);
        stalePlayerSweepTask = scheduleTick("stale player sweep", this::tickStalePlayerSweep,
                ParkourTimingConstants.STALE_PLAYER_SWEEP_INTERVAL_SECONDS,
                ParkourTimingConstants.STALE_PLAYER_SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
        runStateAutosaveTask = scheduleTick("run state autosave", this::tickRunStateAutosave,
                60, 60, TimeUnit.SECONDS);
        teleportDebugTask = scheduleTick("teleport debug", this::tickTeleportDebug,
                ParkourTimingConstants.TELEPORT_DEBUG_INTERVAL_SECONDS,
                ParkourTimingConstants.TELEPORT_DEBUG_INTERVAL_SECONDS, TimeUnit.SECONDS);
        duelTickTask = scheduleTick("duel tick", this::tickDuel,
                ParkourTimingConstants.DUEL_TICK_INTERVAL_MS, ParkourTimingConstants.DUEL_TICK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        int votePollInterval = voteManager.getPollIntervalSeconds();
        votePollingTask = scheduleTick("vote polling", voteManager::pollAllPlayers,
                votePollInterval, votePollInterval, TimeUnit.SECONDS);
        announcementManager.refreshChatAnnouncements();
        scheduleLeaderboardHologramRefresh();

        GameModeBridge.register(GameModeBridge.PARKOUR_RESTART_CHECKPOINT,
                (ref, firstRun, time, type, ctx) ->
                        new RestartCheckpointInteraction().handle(ref, firstRun, time, type, ctx));

        ParkourAdminNavigator adminNavigator = new ParkourAdminNavigator(
                mapStore, progressStore, settingsStore, playerCountStore, medalRewardStore,
                globalMessageStore, this::invalidateRankCache, this::refreshChatAnnouncements,
                this::broadcastAnnouncement, this::buildMapLeaderboardHologramLines);
        AdminPageUtils.configure(adminNavigator);

        this.getCommandRegistry().registerCommand(new CheckpointCommand());
        this.getCommandRegistry().registerCommand(new DiscordCommand());
        this.getCommandRegistry().registerCommand(new RulesCommand());
        this.getCommandRegistry().registerCommand(new ParkourCommand(this.mapStore, this.progressStore, this.settingsStore,
                this.playerCountStore, this.runTracker, this.medalStore, this.medalRewardStore,
                this.duelTracker, this::refreshLeaderboardHologram, adminNavigator, vexaStore));
        this.getCommandRegistry().registerCommand(new ParkourAdminItemCommand());
        this.getCommandRegistry().registerCommand(new ParkourMusicDebugCommand());
        this.getCommandRegistry().registerCommand(new StoreCommand());
        this.getCommandRegistry().registerCommand(new DatabaseClearCommand());
        this.getCommandRegistry().registerCommand(new DatabaseReloadCommand());
        this.getCommandRegistry().registerCommand(new DatabaseTestCommand());
        this.getCommandRegistry().registerCommand(new MessageTestCommand());
        this.getCommandRegistry().registerCommand(new DuelCommand(this.duelTracker, this.runTracker, this.mapStore));
        this.getCommandRegistry().registerCommand(new VexaCommand(vexaStore));
        this.getCommandRegistry().registerCommand(new LinkCommand(discordLinkStore));
        this.getCommandRegistry().registerCommand(new UnlinkCommand(discordLinkStore));
        this.getCommandRegistry().registerCommand(new CosmeticTestCommand());
        this.getCommandRegistry().registerCommand(new PetTestCommand());
        this.getCommandRegistry().registerCommand(new MobGalleryCommand());
        this.getCommandRegistry().registerCommand(new AnalyticsCommand(analyticsStore));
        this.getCommandRegistry().registerCommand(new FeatherCommand(featherStore));
        this.getCommandRegistry().registerCommand(new CreditsCommand());
        this.getCommandRegistry().registerCommand(new SpectatorCommand());
        this.getCommandRegistry().registerCommand(new io.hyvexa.core.queue.RunOrFallQueueCommand());


        registerNoDropSystem();
        registerNoBreakSystem();
        registerNoLaunchpadEditSystem();
        // Defer systems that access module singletons (EntityTrackerSystems, DamageModule)
        // to avoid blocking during plugin setup when those modules aren't ready yet
        HytaleServer.SCHEDULED_EXECUTOR.schedule(this::registerDeferredSystems, 1, TimeUnit.SECONDS);

        this.eventRouter = new ParkourEventRouter(
                mapStore, progressStore, runTracker, runStateStore, duelTracker,
                hudManager, perksManager, chatFormatter,
                playtimeManager, cleanupManager, collisionManager, inventorySyncManager,
                worldMapManager, analyticsStore, discordLinkStore, vexaStore, featherStore,
                cosmeticStore, trailManager, voteStore, voteManager, medalStore,
                petManager, ghostNpcManager, playerSettingsPersistence,
                this::shouldApplyParkourMode);
        this.eventRouter.registerAll(this.getEventRegistry());
        collisionManager.disableAllCollisions();

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (eventRouter != null) { eventRouter.cacheHudPlayer(playerRef); }
            if (playerRef != null) {
                PlayerSettingsPersistence.PlayerSettings ps = playerSettingsPersistence != null
                        ? playerSettingsPersistence.loadPlayer(playerRef.getUuid()) : null;
                if (ps != null) {
                    PlayerSettingsStore.loadFrom(playerRef.getUuid(), ps);
                    PlayerMusicPage.loadFrom(playerRef.getUuid(), ps);
                }
            }
            Ref<EntityStore> ref = playerRef != null ? playerRef.getReference() : null;
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                if (shouldApplyParkourMode(playerRef, store)) {
                    hudManager.ensureRunHud(playerRef);
                }
            }
            playtimeManager.startPlaytimeSession(playerRef);
            if (runTracker != null) {
                runTracker.markPlayerReady(playerRef);
            }
            PlayerMusicPage.applyStoredMusic(playerRef);
        }

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            inventorySyncManager.syncRunInventoryOnConnect(playerRef);
        }

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            var ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                Player player = ref.getStore().getComponent(ref, Player.getComponentType());
                if (player != null) {
                    inventorySyncManager.updateDropProtection(player);
                }
            }
        }
    }

    public static HyvexaPlugin getInstance() {
        return INSTANCE;
    }

    public MapStore getMapStore() {
        return mapStore;
    }

    public ProgressStore getProgressStore() {
        return progressStore;
    }

    public PlayerCountStore getPlayerCountStore() {
        return playerCountStore;
    }

    public GlobalMessageStore getGlobalMessageStore() {
        return globalMessageStore;
    }

    public MedalStore getMedalStore() {
        return medalStore;
    }

    public MedalRewardStore getMedalRewardStore() {
        return medalRewardStore;
    }

    /**
     * Invalidates cached rank name for a player.
     * Call after map completion or any event that changes rank.
     */
    public void invalidateRankCache(UUID playerId) {
        if (perksManager != null) {
            perksManager.invalidateRankCache(playerId);
        }
    }

    /**
     * Invalidates all cached rank names.
     * Call when total XP changes (maps added/removed/edited).
     */
    public void invalidateAllRankCaches() {
        if (perksManager != null) {
            perksManager.invalidateAllRankCaches();
        }
    }

    public void refreshLeaderboardHologram(Store<EntityStore> store) {
        if (leaderboardHologramManager != null) {
            leaderboardHologramManager.refreshLeaderboardHologram(store);
        }
    }

    public void refreshMapLeaderboardHologram(String mapId, Store<EntityStore> store) {
        if (leaderboardHologramManager != null) {
            leaderboardHologramManager.refreshMapLeaderboardHologram(mapId, store);
        }
    }

    public void logMapHologramDebug(String message) {
        LOGGER.atFine().log(message);
    }

    public List<String> buildMapLeaderboardHologramLines(String mapId) {
        if (leaderboardHologramManager != null) {
            return leaderboardHologramManager.buildMapLeaderboardHologramLines(mapId);
        }
        return new ArrayList<>();
    }

    public float getVipSpeedMultiplier(UUID playerId) {
        if (perksManager == null) {
            return 1.0f;
        }
        return perksManager.getVipSpeedMultiplier(playerId);
    }

    public void applyVipSpeedMultiplier(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
                                        float multiplier, boolean notify) {
        if (perksManager != null) {
            perksManager.applyVipSpeedMultiplier(ref, store, playerRef, multiplier, notify);
        }
    }

    /**
     * Called when MapStore changes. Invalidates caches that depend on map data.
     */
    private void onMapStoreChanged() {
        if (progressStore != null) {
            progressStore.invalidateTotalXpCache();
        }
        if (perksManager != null) {
            perksManager.invalidateAllRankCaches();
        }
    }

    public void broadcastAnnouncement(String message, PlayerRef sender) {
        if (announcementManager != null) {
            announcementManager.broadcastAnnouncement(message, sender);
        }
    }

    private void scheduleLeaderboardHologramRefresh() {
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                refreshLeaderboardHologram();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to refresh leaderboard hologram");
            }
        }, ParkourTimingConstants.LEADERBOARD_HOLOGRAM_REFRESH_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void refreshLeaderboardHologram() {
        if (leaderboardHologramManager != null) {
            leaderboardHologramManager.refreshLeaderboardHologram();
        }
    }

    private void tickPlaytime() {
        if (playtimeManager != null) {
            playtimeManager.tickPlaytime();
        }
        if (runTracker != null) {
            runTracker.flushPendingJumps();
        }
    }

    private void tickPlayerCounts() {
        if (playtimeManager != null) {
            playtimeManager.tickPlayerCounts();
        }
    }

    private void tickStalePlayerSweep() {
        if (cleanupManager != null) {
            cleanupManager.tickStalePlayerSweep();
        }
    }

    private void tickRunStateAutosave() {
        if (runTracker != null && runStateStore != null) {
            runTracker.autosaveActiveRuns(runStateStore);
        }
    }

    private void tickTeleportDebug() {
        if (runTracker == null) {
            return;
        }
        if (settingsStore == null || !settingsStore.isTeleportDebugEnabled()) {
            runTracker.drainTeleportStats();
            return;
        }
        Map<UUID, RunTracker.TeleportStatsSnapshot> snapshots = runTracker.drainTeleportStats();
        if (snapshots.isEmpty()) {
            return;
        }
        Map<UUID, String> onlineNames = new HashMap<>();
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) {
                continue;
            }
            onlineNames.put(playerRef.getUuid(), playerRef.getUsername());
        }
        for (Map.Entry<UUID, RunTracker.TeleportStatsSnapshot> entry : snapshots.entrySet()) {
            RunTracker.TeleportStatsSnapshot snapshot = entry.getValue();
            if (snapshot == null || snapshot.isEmpty()) {
                continue;
            }
            UUID playerId = entry.getKey();
            String name = onlineNames.get(playerId);
            if (name == null || name.isBlank()) {
                name = progressStore != null ? progressStore.getPlayerName(playerId) : null;
            }
            if (name == null || name.isBlank()) {
                name = "Player";
            }
            LOGGER.atInfo().log("Teleport debug (last " + ParkourTimingConstants.TELEPORT_DEBUG_INTERVAL_SECONDS + "s): " + name + " "
                    + playerId + " start=" + snapshot.startTrigger
                    + " leave=" + snapshot.leaveTrigger
                    + " runRespawn=" + snapshot.runRespawn
                    + " idleRespawn=" + snapshot.idleRespawn
                    + " finish=" + snapshot.finish
                    + " checkpoint=" + snapshot.checkpoint);
        }
    }

    public void refreshChatAnnouncements() {
        if (announcementManager != null) {
            announcementManager.refreshChatAnnouncements();
        }
    }

    public RunTracker getRunTracker() {
        return runTracker;
    }

    public GhostNpcManager getGhostNpcManager() {
        return ghostNpcManager;
    }

    public DuelTracker getDuelTracker() {
        return duelTracker;
    }

    public DuelPreferenceStore getDuelPreferenceStore() {
        return duelPreferenceStore;
    }

    public SettingsStore getSettingsStore() {
        return settingsStore;
    }

    public HudManager getHudManager() {
        return hudManager;
    }

    public AnnouncementManager getAnnouncementManager() {
        return announcementManager;
    }

    public PlayerPerksManager getPerksManager() {
        return perksManager;
    }

    public PlaytimeManager getPlaytimeManager() {
        return playtimeManager;
    }

    public PlayerCleanupManager getCleanupManager() {
        return cleanupManager;
    }

    private void tickHudUpdates() {
        if (eventRouter != null) {
            eventRouter.tickHudUpdates();
        }
    }

    private void tickDuel() {
        if (duelTracker == null) {
            return;
        }
        duelTracker.sweepQueuedPlayersInRun(runTracker);
        duelTracker.tick();
    }

    private void tickCollisionRemoval() {
        collisionManager.disableAllCollisions();
    }

    public void hideRunHud(PlayerRef playerRef) {
        if (hudManager != null) {
            hudManager.hideRunHud(playerRef);
        }
    }

    public void showRunHud(PlayerRef playerRef) {
        if (hudManager != null) {
            hudManager.showRunHud(playerRef);
        }
        if (announcementManager != null) {
            announcementManager.updateAnnouncementHud(playerRef);
        }
    }

    public boolean shouldApplyParkourMode(World world) {
        return isParkourWorld(world);
    }

    private boolean shouldApplyParkourMode(PlayerRef playerRef, Store<EntityStore> store) {
        if (playerRef == null || store == null) {
            return false;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return false;
        }
        if (store.getExternalData() == null) return false;
        World world = store.getExternalData().getWorld();
        return isParkourWorld(world);
    }

    public boolean isParkourWorld(World world) {
        return ModeGate.isParkourWorld(world);
    }

    private void initSafe(String name, Runnable init) {
        try { init.run(); }
        catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to initialize " + name); }
    }

    private void shutdownSafe(String name, Runnable action) {
        try { action.run(); }
        catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to shut down " + name); }
    }

    private void cancelScheduled(ScheduledFuture<?> handle) {
        if (handle == null) {
            return;
        }
        handle.cancel(false);
    }

    private ScheduledFuture<?> scheduleTick(String name, Runnable task, long initialDelay, long period, TimeUnit unit) {
        return HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                task.run();
            } catch (Throwable error) {
                LOGGER.atSevere().withCause(error).log("Tick task failed (" + name + ")");
            }
        }, initialDelay, period, unit);
    }

    private void registerNoDropSystem() {
        var registry = EntityStore.REGISTRY;
        if (registry.getEntityEventTypeForClass(DropItemEvent.PlayerRequest.class) == null) {
            registry.registerEntityEventType(DropItemEvent.PlayerRequest.class);
        }
        if (!registry.hasSystemClass(NoDropSystem.class)) {
            registry.registerSystem(new NoDropSystem());
        }
    }

    private void registerNoBreakSystem() {
        var registry = EntityStore.REGISTRY;
        if (registry.getEntityEventTypeForClass(BreakBlockEvent.class) == null) {
            registry.registerEntityEventType(BreakBlockEvent.class);
        }
        if (!registry.hasSystemClass(NoBreakSystem.class)) {
            registry.registerSystem(new NoBreakSystem());
        }
    }

    private void registerNoLaunchpadEditSystem() {
        var registry = EntityStore.REGISTRY;
        if (registry.getEntityEventTypeForClass(UseBlockEvent.Pre.class) == null) {
            registry.registerEntityEventType(UseBlockEvent.Pre.class);
        }
        if (!registry.hasSystemClass(NoLaunchpadEditSystem.class)) {
            registry.registerSystem(new NoLaunchpadEditSystem());
        }
    }

    /**
     * Registers systems that depend on module singletons (EntityTrackerSystems, DamageModule).
     * Must be called after plugin setup completes to avoid deadlock during initialization.
     */
    private void registerDeferredSystems() {
        registerPlayerVisibilitySystem();
        registerNoPlayerDamageSystem();
        registerNoWeaponDamageSystem();
        registerNoPlayerKnockbackSystem();
    }

    private void registerPlayerVisibilitySystem() {
        var registry = EntityStore.REGISTRY;
        if (!registry.hasSystemClass(EntityVisibilityFilterSystem.class)) {
            registry.registerSystem(new EntityVisibilityFilterSystem());
        }
    }

    private void registerRunTrackerTickSystem() {
        var registry = EntityStore.REGISTRY;
        if (!registry.hasSystemClass(RunTrackerTickSystem.class)) {
            registry.registerSystem(new RunTrackerTickSystem(this, runTracker, perksManager));
        }
    }

    private void registerNoWeaponDamageSystem() {
        var registry = EntityStore.REGISTRY;
        if (registry.getEntityEventTypeForClass(com.hypixel.hytale.server.core.modules.entity.damage.Damage.class) == null) {
            registry.registerEntityEventType(com.hypixel.hytale.server.core.modules.entity.damage.Damage.class);
        }
        if (!registry.hasSystemClass(NoWeaponDamageSystem.class)) {
            registry.registerSystem(new NoWeaponDamageSystem(
                    () -> settingsStore != null && settingsStore.isWeaponDamageDisabled()
            ));
        }
    }

    private void registerNoPlayerDamageSystem() {
        var registry = EntityStore.REGISTRY;
        if (registry.getEntityEventTypeForClass(com.hypixel.hytale.server.core.modules.entity.damage.Damage.class) == null) {
            registry.registerEntityEventType(com.hypixel.hytale.server.core.modules.entity.damage.Damage.class);
        }
        if (!registry.hasSystemClass(NoPlayerDamageSystem.class)) {
            registry.registerSystem(new NoPlayerDamageSystem());
        }
    }

    private void registerNoPlayerKnockbackSystem() {
        var registry = EntityStore.REGISTRY;
        if (!registry.hasSystemClass(NoPlayerKnockbackSystem.class)) {
            registry.registerSystem(new NoPlayerKnockbackSystem(
                    () -> settingsStore != null && settingsStore.isWeaponDamageDisabled()
            ));
        }
    }

    private void registerInteractionCodecs() {
        var registry = this.getCodecRegistry(Interaction.CODEC);
        registry.register("Parkour_Menu_Interaction", MenuInteraction.class, MenuInteraction.CODEC);
        registry.register("Parkour_Leaderboard_Interaction", LeaderboardInteraction.class, LeaderboardInteraction.CODEC);
        registry.register("Parkour_Stats_Interaction", StatsInteraction.class, StatsInteraction.CODEC);
        registry.register("Parkour_Reset_Interaction", ResetInteraction.class, ResetInteraction.CODEC);
        registry.register("Parkour_Practice_Interaction", PracticeInteraction.class, PracticeInteraction.CODEC);
        registry.register("Parkour_Leave_Practice_Interaction",
                LeavePracticeInteraction.class, LeavePracticeInteraction.CODEC);
        registry.register("Parkour_Practice_Checkpoint_Interaction",
                PracticeCheckpointInteraction.class, PracticeCheckpointInteraction.CODEC);
        registry.register("Parkour_Restart_Checkpoint_Interaction",
                RestartCheckpointInteraction.class, RestartCheckpointInteraction.CODEC);
        registry.register("Parkour_Leave_Interaction", LeaveInteraction.class, LeaveInteraction.CODEC);
        registry.register("Parkour_PlayerSettings_Interaction",
                PlayerSettingsInteraction.class, PlayerSettingsInteraction.CODEC);
        registry.register("Media_RemoteControl",
                PlayerSettingsInteraction.class, PlayerSettingsInteraction.CODEC);
        registry.register("Forfeit_Interaction", ForfeitInteraction.class, ForfeitInteraction.CODEC);
        registry.register("Duel_Menu_Interaction", DuelMenuInteraction.class, DuelMenuInteraction.CODEC);
        registry.register("Parkour_Toggle_Fly_Interaction", ToggleFlyInteraction.class, ToggleFlyInteraction.CODEC);
        registry.register("RunOrFall_Join_Bridge_Interaction",
                RunOrFallJoinBridgeInteraction.class, RunOrFallJoinBridgeInteraction.CODEC);
        registry.register("Shop_Item_Interaction",
                io.hyvexa.common.interaction.ShopItemInteraction.class,
                io.hyvexa.common.interaction.ShopItemInteraction.CODEC);
    }

    @Override
    protected void shutdown() {
        LOGGER.atInfo().log("Shutdown: starting plugin shutdown");
        shutdownSafe("hudUpdateTask", () -> cancelScheduled(hudUpdateTask));
        shutdownSafe("playtimeTask", () -> cancelScheduled(playtimeTask));
        shutdownSafe("collisionTask", () -> cancelScheduled(collisionTask));
        shutdownSafe("playerCountTask", () -> cancelScheduled(playerCountTask));
        shutdownSafe("stalePlayerSweepTask", () -> cancelScheduled(stalePlayerSweepTask));
        shutdownSafe("runStateAutosaveTask", () -> cancelScheduled(runStateAutosaveTask));
        shutdownSafe("teleportDebugTask", () -> cancelScheduled(teleportDebugTask));
        shutdownSafe("duelTickTask", () -> cancelScheduled(duelTickTask));
        shutdownSafe("votePollingTask", () -> cancelScheduled(votePollingTask));
        shutdownSafe("VoteManager", () -> { if (voteManager != null) voteManager.shutdown(); });
        shutdownSafe("ghostRecorder", () -> { if (ghostRecorder != null) ghostRecorder.stop(); });
        shutdownSafe("ghostNpcManager", () -> { if (ghostNpcManager != null) ghostNpcManager.stop(); });
        shutdownSafe("petManager", () -> { if (petManager != null) petManager.stop(); });
        shutdownSafe("announcementManager", () -> { if (announcementManager != null) announcementManager.shutdown(); });
        // Remove JVM hook to avoid double-save (plugin shutdown is handling it)
        try { if (shutdownHook != null) { Runtime.getRuntime().removeShutdownHook(shutdownHook); } }
        catch (Exception ignored) { }
        shutdownSafe("saveAllActiveRuns", () -> { if (runTracker != null && runStateStore != null) runTracker.saveAllActiveRuns(runStateStore); });
        shutdownSafe("eventRouter HUD tracking", () -> { if (eventRouter != null) eventRouter.clearHudTracking(); });
        shutdownSafe("progressStore flush", () -> { if (progressStore != null) progressStore.flushPendingSave(); });
        shutdownSafe("analytics aggregation", () -> analyticsStore.computeDailyAggregates(java.time.LocalDate.now()));
        shutdownSafe("AdminPageUtils", AdminPageUtils::clear);
        shutdownSafe("DatabaseManager", () -> DatabaseManager.get().shutdown());

        super.shutdown();
    }

}
