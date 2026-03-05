package io.hyvexa.ascend;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.command.AscendCommand;
import io.hyvexa.ascend.command.AscendAdminCommand;
import io.hyvexa.ascend.command.CatCommand;
import io.hyvexa.ascend.command.CinematicTestCommand;
import io.hyvexa.ascend.command.ElevateCommand;
import io.hyvexa.ascend.command.HudPreviewCommand;
import io.hyvexa.ascend.command.SkillCommand;
import io.hyvexa.ascend.command.SummitCommand;
import io.hyvexa.ascend.command.TranscendCommand;
import io.hyvexa.ascend.data.AscendDatabaseSetup;
import io.hyvexa.core.analytics.AnalyticsStore;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.discord.DiscordLinkStore;
import io.hyvexa.core.economy.VexaStore;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.AscendSettingsStore;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.ascend.ghost.GhostRecorder;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.holo.AscendHologramManager;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.interaction.AbstractAscendPageInteraction;
import io.hyvexa.ascend.interaction.AscendDevInteraction;
import io.hyvexa.ascend.interaction.AscendLeaveInteraction;
import io.hyvexa.ascend.interaction.AscendResetInteraction;
import io.hyvexa.ascend.interaction.AscendTranscendenceInteraction;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.mine.MineGateChecker;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.robot.MineRobotManager;
import io.hyvexa.ascend.mine.system.MineBreakSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.transcendence.TranscendenceManager;
import io.hyvexa.ascend.tutorial.TutorialTriggerService;
import io.hyvexa.ascend.passive.PassiveEarningsManager;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.ui.AscendHelpPage;
import io.hyvexa.ascend.ui.AscendLeaderboardPage;
import io.hyvexa.ascend.ui.AscendMapSelectPage;
import io.hyvexa.ascend.ui.AscendMusicPage;
import io.hyvexa.ascend.ui.AscendProfilePage;
import io.hyvexa.ascend.ui.AutomationPage;
import io.hyvexa.ascend.ui.AscendSettingsPage;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.ascend.util.AscendInventoryUtils;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.whitelist.AscendWhitelistManager;
import io.hyvexa.common.whitelist.WhitelistRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import io.hyvexa.common.util.HylogramsBridge;
import io.hyvexa.ascend.system.AscendFinishDetectionSystem;
import io.hyvexa.common.visibility.EntityVisibilityFilterSystem;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public class ParkourAscendPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int FULL_TICK_INTERVAL = 4; // every 4th tick = 200ms at 50ms interval
    private static final long MENU_SYNC_RETRY_DELAY_MS = 50L;
    private static final int MENU_SYNC_MAX_ATTEMPTS = 5;
    private static ParkourAscendPlugin INSTANCE;

    private AscendMapStore mapStore;
    private AscendPlayerStore playerStore;
    private AscendSettingsStore settingsStore;
    private GhostStore ghostStore;
    private GhostRecorder ghostRecorder;
    private AscendRunTracker runTracker;
    private AscendHudManager hudManager;
    private RobotManager robotManager;
    private AscendHologramManager hologramManager;
    private SummitManager summitManager;
    private AscensionManager ascensionManager;
    private TranscendenceManager transcendenceManager;
    private ChallengeManager challengeManager;
    private AchievementManager achievementManager;
    private PassiveEarningsManager passiveEarningsManager;
    private TutorialTriggerService tutorialTriggerService;
    private MineConfigStore mineConfigStore;
    private MineGateChecker mineGateChecker;
    private MineManager mineManager;
    private MinePlayerStore minePlayerStore;
    private MineRobotManager mineRobotManager;
    private AscendWhitelistManager whitelistManager;
    private AscendRuntimeConfig runtimeConfig;
    private ScheduledFuture<?> tickTask;
    private ScheduledFuture<?> mineTickTask;
    private int tickCounter;
    private final AtomicBoolean mineZonesGenerated = new AtomicBoolean(false);
    private final ConcurrentHashMap<UUID, PlayerRef> playerRefCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<World, AtomicBoolean> worldTickInFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<World, Set<UUID>> tickPlayersByWorld = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, World> playerTickWorlds = new ConcurrentHashMap<>();
    private final Set<UUID> playersInAscendWorld = ConcurrentHashMap.newKeySet();

    public ParkourAscendPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    @Override
    protected void setup() {
        // Ensure database is initialized (may already be done by main plugin, but order is not guaranteed)
        try {
            DatabaseManager.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize database for Ascend");
        }

        AscendDatabaseSetup.ensureTables();
        try {
            VexaStore.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize VexaStore for Ascend");
        }
        try {
            DiscordLinkStore.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize DiscordLinkStore for Ascend");
        }
        try {
            AnalyticsStore.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize AnalyticsStore for Ascend");
        }

        // Initialize whitelist manager
        java.nio.file.Path modsFolderPath = java.nio.file.Path.of("mods", "Parkour");
        java.io.File modsFolder = modsFolderPath.toFile();
        if (!modsFolder.exists()) {
            modsFolder.mkdirs();
        }
        java.io.File whitelistFile = modsFolderPath.resolve("ascend_whitelist.json").toFile();
        whitelistManager = new AscendWhitelistManager(whitelistFile);
        WhitelistRegistry.register(whitelistManager);
        runtimeConfig = AscendRuntimeConfig.load();

        // Core stores — fail fast if any fails
        try {
            mapStore = new AscendMapStore();
            mapStore.syncLoad();

            playerStore = new AscendPlayerStore();
            playerStore.syncLoad();

            settingsStore = new AscendSettingsStore();
            settingsStore.syncLoad();
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to initialize core stores for Ascend — plugin will not function");
            return;
        }

        // Mine config
        try {
            mineConfigStore = new MineConfigStore();
            mineConfigStore.syncLoad();
            mineGateChecker = new MineGateChecker(mineConfigStore, playerStore);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize mine config store");
        }

        // Mine player store + manager
        try {
            minePlayerStore = new MinePlayerStore();
            mineManager = new MineManager(mineConfigStore);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize mine manager");
        }

        // Mine robot manager (automated miners)
        if (mineConfigStore != null && minePlayerStore != null) {
            try {
                mineRobotManager = new MineRobotManager(mineConfigStore, minePlayerStore);
                mineRobotManager.start();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to initialize mine robot manager");
            }
        }

        // Ghost system
        try {
            ghostStore = new GhostStore("ascend_ghost_recordings", "ascend");
            ghostStore.syncLoad();

            ghostRecorder = new GhostRecorder(ghostStore);
            ghostRecorder.start();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize ghost system");
        }

        // Pass ghost dependencies to managers
        runTracker = new AscendRunTracker(mapStore, playerStore, ghostRecorder);
        mapStore.setOnChangeListener(runTracker::onMapStoreChanged);
        summitManager = new SummitManager(playerStore, mapStore);
        hudManager = new AscendHudManager(playerStore, mapStore, runTracker, summitManager);

        try {
            robotManager = new RobotManager(mapStore, playerStore, ghostStore);
            robotManager.start();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize robot manager");
        }
        ascensionManager = new AscensionManager(playerStore, runTracker);
        transcendenceManager = new TranscendenceManager(playerStore, runTracker);
        challengeManager = new ChallengeManager(playerStore, mapStore, runTracker);
        achievementManager = new AchievementManager(playerStore);

        // Initialize passive earnings manager
        passiveEarningsManager = new PassiveEarningsManager(
            playerStore, mapStore, ghostStore
        );

        // Initialize tutorial trigger service
        tutorialTriggerService = new TutorialTriggerService(playerStore, runTracker);

        try {
            if (HylogramsBridge.isAvailable()) {
                hologramManager = new AscendHologramManager();
                for (var map : mapStore.listMaps()) {
                    hologramManager.refreshMapHolosIfPresent(map, null);
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize holograms");
        }

        getCommandRegistry().registerCommand(new AscendCommand());
        getCommandRegistry().registerCommand(new AscendAdminCommand());
        getCommandRegistry().registerCommand(new ElevateCommand());
        getCommandRegistry().registerCommand(new SummitCommand());
        getCommandRegistry().registerCommand(new SkillCommand());
        getCommandRegistry().registerCommand(new TranscendCommand());
        getCommandRegistry().registerCommand(new CatCommand());
        getCommandRegistry().registerCommand(new io.hyvexa.ascend.mine.command.MineCommand());
        if (runtimeConfig.isEnableTestCommands()) {
            getCommandRegistry().registerCommand(new CinematicTestCommand());
            getCommandRegistry().registerCommand(new HudPreviewCommand());
        }
        getCommandRegistry().registerCommand(new io.hyvexa.core.queue.RunOrFallQueueCommand());
        registerInteractionCodecs();

        // Register entity visibility filter system if not already registered
        var registry = EntityStore.REGISTRY;
        if (!registry.hasSystemClass(EntityVisibilityFilterSystem.class)) {
            registry.registerSystem(new EntityVisibilityFilterSystem());
        }

        // Per-tick finish line detection (defers store work to world thread)
        if (!registry.hasSystemClass(AscendFinishDetectionSystem.class)) {
            registry.registerSystem(new AscendFinishDetectionSystem(this, runTracker));
        }

        // Mine break system — allows block breaking inside mining zones (overrides NoBreakSystem)
        if (mineManager != null && minePlayerStore != null) {
            if (registry.getEntityEventTypeForClass(BreakBlockEvent.class) == null) {
                registry.registerEntityEventType(BreakBlockEvent.class);
            }
            if (!registry.hasSystemClass(MineBreakSystem.class)) {
                registry.registerSystem(new MineBreakSystem(mineManager, minePlayerStore));
            }
        }

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            try {
                Ref<EntityStore> ref = event.getPlayerRef();
                if (ref == null || !ref.isValid()) {
                    return;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();

                if (world == null || !isAscendWorld(world)) {
                    return;
                }
                // Set mine world reference and generate zones on first ascend world discovery
                if (mineManager != null) {
                    mineManager.setWorld(world);
                    if (mineZonesGenerated.compareAndSet(false, true)) {
                        world.execute(() -> mineManager.generateAllZones(world));
                    }
                }
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) {
                    return;
                }
                // Cache PlayerRef for O(1) lookups
                UUID playerId = playerRef.getUuid();
                if (playerId != null) {
                    playersInAscendWorld.add(playerId);
                    cacheTickPlayer(playerRef);
                }
                // Register player as online for robot spawning
                if (playerId != null && robotManager != null) {
                    robotManager.onPlayerJoin(playerId);
                }
                // Spawn automated miners for this player
                if (playerId != null && mineRobotManager != null) {
                    mineRobotManager.onPlayerJoin(playerId, world);
                }
                // Reset session-specific tracking
                if (playerId != null && playerStore != null) {
                    playerStore.setSessionFirstRunClaimed(playerId, false);
                    playerStore.resetConsecutiveManualRuns(playerId);
                    playerStore.storePlayerName(playerId, playerRef.getUsername());

                    // Initialize ascension timer on first join (if not already set)
                    AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerId);
                    if (progress.getAscensionStartedAt() == null) {
                        progress.setAscensionStartedAt(System.currentTimeMillis());
                    }

                    playerStore.markDirty(playerId);

                    // Check for passive earnings
                    if (passiveEarningsManager != null) {
                        passiveEarningsManager.checkPassiveEarningsOnJoin(playerId);
                    }

                    // Restore active challenge from DB (crash recovery)
                    if (challengeManager != null) {
                        challengeManager.onPlayerConnect(playerId);
                    }

                    // Trigger welcome tutorial for new players
                    if (tutorialTriggerService != null) {
                        tutorialTriggerService.checkWelcome(playerId, ref);
                    }
                }
                CompletableFuture.runAsync(() -> {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player == null) {
                        return;
                    }
                    AscendInventoryUtils.giveMenuItems(player);
                    hudManager.attach(playerRef, player);
                    AscendMusicPage.applyStoredMusic(playerRef);
                    DiscordLinkStore linkStore = DiscordLinkStore.getInstance();
                    linkStore.checkAndRewardVexaOnLoginAsync(playerId)
                            .thenAcceptAsync(rewarded -> {
                                if (rewarded && ref.isValid()) {
                                    linkStore.sendRewardGrantedMessage(player);
                                }
                            }, world)
                            .exceptionally(ex -> {
                                LOGGER.atWarning().withCause(ex).log("Discord link check failed (ascend)");
                                return null;
                            });
                }, world).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
                    LOGGER.atWarning().withCause(ex).log("Exception in PlayerReadyEvent async task");
                    return null;
                });
            } catch (Exception e) {
                LOGGER.atWarning().log("Exception in PlayerReadyEvent (ascend): " + e.getMessage());
            }
        });

        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            try {
                World world = event.getWorld();
                if (world == null) {
                    return;
                }
                var holder = event.getHolder();
                if (holder == null) {
                    return;
                }

                PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
                UUID playerId = playerRef != null ? playerRef.getUuid() : null;
                if (isAscendWorld(world)) {
                    if (playerId != null) {
                        playersInAscendWorld.add(playerId);
                    }
                    cacheTickPlayer(playerRef);
                    // If joining Ascend world, ensure items are present
                    Player player = holder.getComponent(Player.getComponentType());
                    if (player == null) {
                        return;
                    }
                    ensureMenuItemsWhenReady(player, world, MENU_SYNC_MAX_ATTEMPTS);
                    return;
                }

                // Clean up Ascend state on true Ascend -> non-Ascend transitions
                if (playerId != null && playersInAscendWorld.remove(playerId)) {
                    cleanupAscendState(playerId);
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("Exception in AddPlayerToWorldEvent (ascend): " + e.getMessage());
            }
        });

        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            if (playerRef == null) {
                return;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId == null) {
                return;
            }

            playersInAscendWorld.remove(playerId);
            cleanupAscendState(playerId);

            // Disconnect-only cleanup (not needed on world transitions)
            runSafe(() -> AscendMapSelectPage.clearBuyAllCooldown(playerId), "Disconnect cleanup: clearBuyAllCooldown");
            // Evict player from cache (lazy loading - saves memory)
            runSafe(() -> { if (playerStore != null) playerStore.removePlayer(playerId); },
                    "Disconnect cleanup: playerStore");
            runSafe(() -> { if (minePlayerStore != null) minePlayerStore.evict(playerId); },
                    "Disconnect cleanup: minePlayerStore");
            runSafe(() -> { if (mineGateChecker != null) mineGateChecker.evict(playerId); },
                    "Disconnect cleanup: mineGateChecker");
            runSafe(() -> VexaStore.getInstance().evictPlayer(playerId),
                    "Disconnect cleanup: VexaStore");
            runSafe(() -> DiscordLinkStore.getInstance().evictPlayer(playerId),
                    "Disconnect cleanup: DiscordLinkStore");
        });

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            UUID playerId = playerRef != null ? playerRef.getUuid() : null;
            if (playerId != null) {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    World world = store != null && store.getExternalData() != null ? store.getExternalData().getWorld() : null;
                    if (isAscendWorld(world)) {
                        playersInAscendWorld.add(playerId);
                        cacheTickPlayer(playerRef);
                    }
                }
            }
        }

        tickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
            this::tick,
            50, 50, TimeUnit.MILLISECONDS
        );

        if (mineManager != null) {
            mineTickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> mineManager.tick(),
                1000, 1000, TimeUnit.MILLISECONDS
            );
        }
    }

    @Override
    protected void shutdown() {
        runSafe(() -> { if (tickTask != null) { tickTask.cancel(false); tickTask = null; } },
                "Shutdown: tickTask cancel");
        runSafe(() -> { if (mineTickTask != null) { mineTickTask.cancel(false); mineTickTask = null; } },
                "Shutdown: mineTickTask cancel");
        runSafe(() -> worldTickInFlight.clear(), "Shutdown: worldTickInFlight clear");
        runSafe(() -> tickPlayersByWorld.clear(), "Shutdown: tickPlayersByWorld clear");
        runSafe(() -> playerTickWorlds.clear(), "Shutdown: playerTickWorlds clear");
        runSafe(() -> playerRefCache.clear(), "Shutdown: playerRefCache clear");
        runSafe(() -> playersInAscendWorld.clear(), "Shutdown: playersInAscendWorld clear");
        runSafe(() -> { if (ghostRecorder != null) ghostRecorder.stop(); }, "Shutdown: ghostRecorder stop");
        runSafe(() -> { if (robotManager != null) robotManager.stop(); }, "Shutdown: robotManager stop");
        runSafe(() -> { if (mineRobotManager != null) mineRobotManager.stop(); }, "Shutdown: mineRobotManager stop");
        runSafe(() -> { if (playerStore != null) playerStore.flushPendingSave(); }, "Shutdown: playerStore flush");
        runSafe(() -> { if (minePlayerStore != null) minePlayerStore.flushAll(); }, "Shutdown: minePlayerStore flush");
        runSafe(() -> WhitelistRegistry.unregister(), "Shutdown: whitelist unregister");
    }

    public static ParkourAscendPlugin getInstance() {
        return INSTANCE;
    }

    public AscendMapStore getMapStore() {
        return mapStore;
    }

    public AscendPlayerStore getPlayerStore() {
        return playerStore;
    }

    public AscendSettingsStore getSettingsStore() {
        return settingsStore;
    }

    public GhostStore getGhostStore() {
        return ghostStore;
    }

    public AscendRunTracker getRunTracker() {
        return runTracker;
    }

    public AscendHudManager getHudManager() {
        return hudManager;
    }

    public RobotManager getRobotManager() {
        return robotManager;
    }

    public AscendHologramManager getHologramManager() {
        return hologramManager;
    }

    public SummitManager getSummitManager() {
        return summitManager;
    }

    public AscensionManager getAscensionManager() {
        return ascensionManager;
    }

    public TranscendenceManager getTranscendenceManager() {
        return transcendenceManager;
    }

    public ChallengeManager getChallengeManager() {
        return challengeManager;
    }

    public AchievementManager getAchievementManager() {
        return achievementManager;
    }

    public AscendWhitelistManager getWhitelistManager() {
        return whitelistManager;
    }

    public PassiveEarningsManager getPassiveEarningsManager() {
        return passiveEarningsManager;
    }

    public TutorialTriggerService getTutorialTriggerService() {
        return tutorialTriggerService;
    }

    public MineConfigStore getMineConfigStore() {
        return mineConfigStore;
    }

    public MineManager getMineManager() {
        return mineManager;
    }

    public MinePlayerStore getMinePlayerStore() {
        return minePlayerStore;
    }

    public MineRobotManager getMineRobotManager() {
        return mineRobotManager;
    }

    public AscendRuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    /**
     * Get a PlayerRef for a given player UUID.
     * Uses a cache populated on PlayerReadyEvent for O(1) lookup.
     */
    public PlayerRef getPlayerRef(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return playerRefCache.get(playerId);
    }

    private void cacheTickPlayer(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        playerRefCache.put(playerId, playerRef);
        syncTickPlayerWorld(playerRef, playerId);
    }

    private void syncTickPlayerWorld(PlayerRef playerRef, UUID playerId) {
        if (playerId == null) {
            return;
        }
        Ref<EntityStore> ref = playerRef != null ? playerRef.getReference() : null;
        if (ref == null || !ref.isValid()) {
            removeTickPlayerFromWorld(playerId);
            return;
        }
        Store<EntityStore> store = ref.getStore();
        if (store == null || store.getExternalData() == null) {
            removeTickPlayerFromWorld(playerId);
            return;
        }
        World world = store.getExternalData().getWorld();
        if (!isAscendWorld(world)) {
            removeTickPlayerFromWorld(playerId);
            return;
        }
        World previousWorld = playerTickWorlds.put(playerId, world);
        if (previousWorld != null && previousWorld != world) {
            removeTickPlayerFromWorld(previousWorld, playerId);
        }
        tickPlayersByWorld.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    private void removeTickPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        playerRefCache.remove(playerId);
        removeTickPlayerFromWorld(playerId);
    }

    private void removeTickPlayerFromWorld(UUID playerId) {
        if (playerId == null) {
            return;
        }
        World previousWorld = playerTickWorlds.remove(playerId);
        if (previousWorld != null) {
            removeTickPlayerFromWorld(previousWorld, playerId);
        }
    }

    private void removeTickPlayerFromWorld(World world, UUID playerId) {
        if (world == null || playerId == null) {
            return;
        }
        Set<UUID> playerIds = tickPlayersByWorld.get(world);
        if (playerIds == null) {
            return;
        }
        playerIds.remove(playerId);
        if (playerIds.isEmpty()) {
            tickPlayersByWorld.remove(world, playerIds);
        }
    }

    private void tick() {
        if (runTracker == null) {
            return;
        }
        tickCounter++;
        boolean fullTick = tickCounter % FULL_TICK_INTERVAL == 0;

        for (Map.Entry<World, Set<UUID>> entry : tickPlayersByWorld.entrySet()) {
            World world = entry.getKey();
            if (!isAscendWorld(world)) {
                continue;
            }
            Set<UUID> playerIds = entry.getValue();
            if (playerIds == null) {
                continue;
            }
            if (playerIds.isEmpty()) {
                tickPlayersByWorld.remove(world, playerIds);
                continue;
            }
            AtomicBoolean inFlight = worldTickInFlight.computeIfAbsent(world, key -> new AtomicBoolean(false));
            if (!inFlight.compareAndSet(false, true)) {
                continue;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    for (UUID playerId : playerIds) {
                        PlayerRef playerRef = playerRefCache.get(playerId);
                        if (playerRef == null) {
                            removeTickPlayer(playerId);
                            continue;
                        }
                        Ref<EntityStore> ref = playerRef.getReference();
                        if (ref == null || !ref.isValid()) {
                            removeTickPlayerFromWorld(playerId);
                            continue;
                        }
                        Store<EntityStore> store = ref.getStore();
                        if (store == null || store.getExternalData() == null) {
                            removeTickPlayerFromWorld(playerId);
                            continue;
                        }
                        World playerWorld = store.getExternalData().getWorld();
                        if (playerWorld != world) {
                            syncTickPlayerWorld(playerRef, playerId);
                            continue;
                        }
                        // Gate check every tick (50ms) to prevent fast players from passing through
                        if (mineGateChecker != null) {
                            mineGateChecker.checkPlayer(playerId, ref, store);
                        }
                        if (fullTick) {
                            runTracker.checkPlayer(ref, store);
                            hudManager.updateFull(ref, store, playerRef);
                        }
                        hudManager.updateTimer(playerRef);
                        hudManager.updateRunnerBars(playerRef);
                        hudManager.updateToasts(playerRef.getUuid());
                    }
                } finally {
                    inFlight.set(false);
                }
            }, world).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
                LOGGER.atWarning().withCause(ex).log("Exception in tick async task");
                return null;
            });
        }
    }

    public boolean isAscendWorld(World world) {
        return ModeGate.isAscendWorld(world);
    }

    private void ensureMenuItemsWhenReady(Player player, World expectedWorld, int attemptsRemaining) {
        if (player == null || expectedWorld == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            World playerWorld = player.getWorld();
            String expectedName = expectedWorld.getName();
            String playerWorldName = playerWorld != null ? playerWorld.getName() : null;
            boolean inExpectedWorld = playerWorld == expectedWorld
                || (expectedName != null && expectedName.equals(playerWorldName));
            if (inExpectedWorld) {
                AscendInventoryUtils.ensureMenuItems(player);
                return;
            }
            if (attemptsRemaining <= 0) {
                return;
            }
            HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> ensureMenuItemsWhenReady(player, expectedWorld, attemptsRemaining - 1),
                MENU_SYNC_RETRY_DELAY_MS,
                TimeUnit.MILLISECONDS
            );
        }, expectedWorld).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
            LOGGER.atWarning().withCause(ex).log("Exception while syncing Ascend inventory after world switch");
            return null;
        });
    }

    /**
     * Cleans up Ascend-specific state when a player leaves the Ascend world
     * without disconnecting (e.g. teleported to Hub). Mirrors disconnect cleanup
     * but keeps player data cached for quick re-entry.
     */
    private void cleanupAscendState(UUID playerId) {
        runSafe(() -> { if (passiveEarningsManager != null) passiveEarningsManager.onPlayerLeaveAscend(playerId); },
                "Leave cleanup: passiveEarnings");
        runSafe(() -> AscendCommand.onPlayerDisconnect(playerId), "Leave cleanup: AscendCommand");
        runSafe(() -> removeTickPlayer(playerId), "Leave cleanup: playerRefCache");
        runSafe(() -> BaseAscendPage.removeCurrentPage(playerId), "Leave cleanup: removeCurrentPage");
        runSafe(() -> AscendLeaveInteraction.clearPendingLeave(playerId), "Leave cleanup: clearPendingLeave");
        runSafe(() -> AscendSettingsPage.clearPlayer(playerId), "Leave cleanup: AscendSettingsPage");
        runSafe(() -> AscendMusicPage.clearPlayer(playerId), "Leave cleanup: AscendMusicPage");
        runSafe(() -> hudManager.removePlayer(playerId), "Leave cleanup: hudManager");
        runSafe(() -> { if (runTracker != null) runTracker.cancelRun(playerId); }, "Leave cleanup: runTracker");
        runSafe(() -> { if (robotManager != null) robotManager.onPlayerLeave(playerId); }, "Leave cleanup: robotManager");
        runSafe(() -> { if (mineRobotManager != null) mineRobotManager.onPlayerLeave(playerId); }, "Leave cleanup: mineRobotManager");
        runSafe(() -> { if (challengeManager != null) challengeManager.onPlayerDisconnect(playerId); },
                "Leave cleanup: challengeManager");
    }

    private static void runSafe(Runnable action, String logMessage) {
        try { action.run(); }
        catch (Exception e) { LOGGER.atWarning().withCause(e).log(logMessage); }
    }

    private void registerInteractionCodecs() {
        var registry = this.getCodecRegistry(Interaction.CODEC);
        // Cindercloth -> AscendMapSelectPage
        registry.register("Ascend_Dev_Cindercloth_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, plugin) -> new AscendMapSelectPage(playerRef,
                    plugin.getMapStore(), plugin.getPlayerStore(), plugin.getRunTracker(),
                    plugin.getRobotManager(), plugin.getGhostStore()),
                (plugin, player) -> {
                    if (plugin.getMapStore() == null || plugin.getPlayerStore() == null
                            || plugin.getRunTracker() == null || plugin.getGhostStore() == null) {
                        player.sendMessage(AbstractAscendPageInteraction.LOADING_MESSAGE);
                        return false;
                    }
                    return true;
                }, true, true)));
        // Stormsilk -> AscendLeaderboardPage
        registry.register("Ascend_Dev_Stormsilk_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, plugin) -> new AscendLeaderboardPage(playerRef,
                    plugin.getPlayerStore()),
                (plugin, player) -> {
                    if (plugin.getPlayerStore() == null) {
                        player.sendMessage(AbstractAscendPageInteraction.LOADING_MESSAGE);
                        return false;
                    }
                    return true;
                }, false, true)));
        // Cotton -> AutomationPage
        registry.register("Ascend_Dev_Cotton_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, plugin) -> new AutomationPage(playerRef,
                    plugin.getPlayerStore(), plugin.getAscensionManager()),
                (plugin, player) -> {
                    if (plugin.getPlayerStore() == null || plugin.getAscensionManager() == null) {
                        player.sendMessage(AbstractAscendPageInteraction.LOADING_MESSAGE);
                        return false;
                    }
                    return true;
                }, true, true)));
        // Shadoweave -> AscendHelpPage
        registry.register("Ascend_Dev_Shadoweave_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, plugin) -> new AscendHelpPage(playerRef),
                (plugin, player) -> true, false, false)));
        registry.register("Ascend_Reset_Interaction",
            AscendResetInteraction.class, AscendResetInteraction.CODEC);
        registry.register("Ascend_Leave_Interaction",
            AscendLeaveInteraction.class, AscendLeaveInteraction.CODEC);
        // Silk -> AscendProfilePage
        registry.register("Ascend_Dev_Silk_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, plugin) -> new AscendProfilePage(playerRef,
                    plugin.getPlayerStore(), plugin.getRobotManager()),
                (plugin, player) -> {
                    if (plugin.getPlayerStore() == null || plugin.getRobotManager() == null) {
                        player.sendMessage(AbstractAscendPageInteraction.LOADING_MESSAGE);
                        return false;
                    }
                    return true;
                }, true, true)));
        registry.register("Ascend_Transcendence_Interaction",
            AscendTranscendenceInteraction.class, AscendTranscendenceInteraction.CODEC);
        registry.register("Shop_Item_Interaction",
            io.hyvexa.common.interaction.ShopItemInteraction.class,
            io.hyvexa.common.interaction.ShopItemInteraction.CODEC);
        // Mine Select -> MineSelectPage
        registry.register("Mine_Select_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, plugin) -> {
                    MinePlayerProgress progress = plugin.getMinePlayerStore().getOrCreatePlayer(playerRef.getUuid());
                    return new io.hyvexa.ascend.mine.ui.MineSelectPage(playerRef, progress);
                },
                (plugin, player) -> plugin.getMinePlayerStore() != null && plugin.getMineConfigStore() != null,
                true, true)));
        // Mine Sell -> MineSellPage
        registry.register("Mine_Sell_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, plugin) -> {
                    MinePlayerProgress progress = plugin.getMinePlayerStore().getOrCreatePlayer(playerRef.getUuid());
                    return new io.hyvexa.ascend.mine.ui.MineSellPage(playerRef, progress);
                },
                (plugin, player) -> plugin.getMinePlayerStore() != null,
                true, true)));
        // Mine Upgrades -> MineUpgradePage
        registry.register("Mine_Upgrades_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, plugin) -> {
                    MinePlayerProgress progress = plugin.getMinePlayerStore().getOrCreatePlayer(playerRef.getUuid());
                    return new io.hyvexa.ascend.mine.ui.MineUpgradePage(playerRef, progress);
                },
                (plugin, player) -> plugin.getMinePlayerStore() != null,
                true, true)));
    }
}
