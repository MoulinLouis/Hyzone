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
import io.hyvexa.ascend.data.AscendPlayerEventHandler;
import io.hyvexa.core.analytics.AnalyticsStore;
import io.hyvexa.core.analytics.PlayerAnalytics;
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
import io.hyvexa.ascend.interaction.AscendInteractionBridge;
import io.hyvexa.ascend.interaction.AscendDevInteraction;
import io.hyvexa.ascend.interaction.ConveyorChestInteraction;
import io.hyvexa.ascend.interaction.AscendLeaveInteraction;
import io.hyvexa.ascend.interaction.MineChestInteraction;
import io.hyvexa.ascend.interaction.MineEggChestInteraction;
import io.hyvexa.ascend.interaction.AscendResetInteraction;
import io.hyvexa.ascend.interaction.AscendTranscendenceInteraction;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.robot.RunnerSpeedCalculator;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.mine.MineBonusCalculator;
import io.hyvexa.ascend.mine.MineGateChecker;
import io.hyvexa.ascend.mine.MineManager;
import io.hyvexa.ascend.mine.egg.EggRouletteAnimation;
import io.hyvexa.ascend.mine.data.BlockConfigStore;
import io.hyvexa.ascend.mine.data.ConveyorConfigStore;
import io.hyvexa.ascend.mine.data.GateConfigStore;
import io.hyvexa.ascend.mine.data.MineHierarchyStore;
import io.hyvexa.ascend.mine.data.MinerConfigStore;
import io.hyvexa.ascend.mine.data.TierConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.robot.MineRobotManager;
import io.hyvexa.ascend.mine.hud.MineHudManager;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.system.BlockVisualHelper;
import io.hyvexa.ascend.mine.system.MineBreakSystem;
import io.hyvexa.ascend.mine.system.MineDamageSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.transcendence.TranscendenceManager;
import io.hyvexa.ascend.tutorial.TutorialTriggerService;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.ui.AscendHelpPage;
import io.hyvexa.ascend.ui.AscendLeaderboardPage;
import io.hyvexa.ascend.ui.AscendAdminNavigator;
import io.hyvexa.ascend.ui.AscendMapSelectPage;
import io.hyvexa.ascend.ui.AscendMenuNavigator;
import io.hyvexa.ascend.ui.AscendMusicPage;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public class ParkourAscendPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
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
    private TutorialTriggerService tutorialTriggerService;
    private PlayerAnalytics analytics;
    private RunnerSpeedCalculator runnerSpeedCalculator;
    private MineHierarchyStore mineHierarchyStore;
    private BlockConfigStore blockConfigStore;
    private TierConfigStore tierConfigStore;
    private GateConfigStore gateConfigStore;
    private MinerConfigStore minerConfigStore;
    private ConveyorConfigStore conveyorConfigStore;
    private MineBonusCalculator mineBonusCalculator;
    private MineGateChecker mineGateChecker;
    private MineManager mineManager;
    private MinePlayerStore minePlayerStore;
    private MineRobotManager mineRobotManager;
    private MineBreakSystem mineBreakSystem;
    private MineDamageSystem mineDamageSystem;
    private MineHudManager mineHudManager;
    private MineAchievementTracker mineAchievementTracker;
    private io.hyvexa.ascend.mine.egg.EggOpenService eggOpenService;
    private AscendPlayerEventHandler eventHandler;
    private AscendWhitelistManager whitelistManager;
    private AscendRuntimeConfig runtimeConfig;
    private DiscordLinkStore discordLinkStore;
    private AscendTickHandler tickHandler;
    private ScheduledFuture<?> tickTask;
    private ScheduledFuture<?> mineTickTask;
    private final AtomicBoolean mineZonesGenerated = new AtomicBoolean(false);

    public ParkourAscendPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    @Override
    protected void setup() {
        // No database/shared-store init — core already initialized them

        AscendDatabaseSetup.ensureTables();
        this.discordLinkStore = DiscordLinkStore.get();

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
            mapStore = new AscendMapStore(DatabaseManager.get());
            mapStore.syncLoad();

            playerStore = new AscendPlayerStore(DatabaseManager.get());
            playerStore.syncLoad();

            settingsStore = new AscendSettingsStore(DatabaseManager.get());
            settingsStore.syncLoad();
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to initialize core stores for Ascend — plugin will not function");
            return;
        }

        // Mine config stores
        try {
            var db = DatabaseManager.get();
            mineHierarchyStore = new MineHierarchyStore(db);
            blockConfigStore = new BlockConfigStore(db);
            tierConfigStore = new TierConfigStore(db);
            gateConfigStore = new GateConfigStore(db);
            minerConfigStore = new MinerConfigStore(db, mineHierarchyStore::getMineId);
            conveyorConfigStore = new ConveyorConfigStore(db, mineHierarchyStore::getMineId, minerConfigStore);

            if (db.isInitialized()) {
                try (var conn = db.getConnection()) {
                    mineHierarchyStore.syncLoad(conn);
                    blockConfigStore.syncLoad(conn);
                    tierConfigStore.syncLoad(conn);
                    gateConfigStore.syncLoad(conn);
                    minerConfigStore.syncLoad(conn);
                    conveyorConfigStore.syncLoad(conn);
                }
            }

            mineBonusCalculator = new MineBonusCalculator(minerConfigStore);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize mine config stores");
            mineHierarchyStore = null;
            blockConfigStore = null;
            tierConfigStore = null;
            gateConfigStore = null;
            minerConfigStore = null;
            conveyorConfigStore = null;
            mineBonusCalculator = null;
        }

        // Mine player store + manager + gate checker
        if (mineHierarchyStore != null) {
            try {
                minePlayerStore = new MinePlayerStore(DatabaseManager.get());
                mineManager = new MineManager(mineHierarchyStore, blockConfigStore, this::getPlayerRef);
                mineGateChecker = new MineGateChecker(gateConfigStore, mineHierarchyStore, playerStore, minePlayerStore);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to initialize mine manager");
                minePlayerStore = null;
                mineManager = null;
                mineGateChecker = null;
            }
        }

        // Mine HUD manager
        if (minePlayerStore != null && mineManager != null && mineHierarchyStore != null) {
            mineHudManager = new MineHudManager(minePlayerStore, mineManager, mineHierarchyStore, this::getPlayerRef);
            mineManager.setMineHudManager(mineHudManager);
            mineManager.initTimer();
        }

        // Mine achievement tracker
        mineAchievementTracker = new MineAchievementTracker(minePlayerStore, playerStore, this::getPlayerRef, DatabaseManager.get());

        // Mine robot manager (automated miners)
        if (mineHierarchyStore != null && minePlayerStore != null) {
            eggOpenService = new io.hyvexa.ascend.mine.egg.EggOpenService(minePlayerStore, mineAchievementTracker);
            try {
                mineRobotManager = new MineRobotManager(mineHierarchyStore, minerConfigStore,
                    conveyorConfigStore, minePlayerStore, mineManager, mineAchievementTracker);
                mineRobotManager.start();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to initialize mine robot manager");
            }
        }

        // Ghost system
        try {
            ghostStore = new GhostStore("ascend_ghost_recordings", "ascend", DatabaseManager.get());
            ghostStore.syncLoad();

            ghostRecorder = new GhostRecorder(ghostStore, this::getPlayerRef);
            ghostRecorder.start();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize ghost system");
        }

        // Pass ghost dependencies to managers
        analytics = AnalyticsStore.get();
        runTracker = new AscendRunTracker(
            mapStore,
            playerStore,
            ghostRecorder,
            settingsStore,
            mineBonusCalculator,
            minePlayerStore,
            analytics
        );
        mapStore.setOnChangeListener(runTracker::onMapStoreChanged);
        ascensionManager = new AscensionManager(playerStore, runTracker, analytics);
        transcendenceManager = new TranscendenceManager(playerStore, runTracker, analytics);
        challengeManager = new ChallengeManager(playerStore, mapStore, runTracker, analytics, DatabaseManager.get());
        summitManager = new SummitManager(
            playerStore,
            mapStore,
            challengeManager,
            ascensionManager,
            analytics
        );
        achievementManager = new AchievementManager(playerStore, analytics);
        tutorialTriggerService = new TutorialTriggerService(playerStore, runTracker, this::getPlayerRef);
        runTracker.setPrestigeServices(
            achievementManager,
            ascensionManager,
            challengeManager,
            summitManager,
            tutorialTriggerService
        );
        runnerSpeedCalculator = new RunnerSpeedCalculator(
            summitManager,
            ascensionManager,
            playerStore,
            mineBonusCalculator,
            minePlayerStore
        );
        hudManager = new AscendHudManager(playerStore, mapStore, runTracker, summitManager, VexaStore.get());
        if (mineGateChecker != null) {
            mineGateChecker.setHudManagers(hudManager, mineHudManager);
        }

        tickHandler = new AscendTickHandler(runTracker, hudManager, mineHudManager, mineGateChecker);

        try {
            robotManager = new RobotManager(
                mapStore,
                playerStore,
                ghostStore,
                runnerSpeedCalculator,
                runTracker,
                ascensionManager,
                challengeManager,
                summitManager,
                achievementManager,
                this::getPlayerRef
            );
            runTracker.setRobotManager(robotManager);
            hudManager.setRobotManager(robotManager);
            robotManager.start();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize robot manager");
        }
        playerStore.setRuntimeServices(challengeManager, robotManager);

        eventHandler = new AscendPlayerEventHandler(
            playerStore,
            challengeManager,
            tutorialTriggerService,
            ascensionManager,
            hudManager,
            robotManager,
            achievementManager,
            mapStore,
            ghostStore,
            this::getPlayerRef
        );
        runTracker.setEventHandler(eventHandler);
        if (robotManager != null) {
            robotManager.setEventHandler(eventHandler);
        }

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

        AscendAdminNavigator adminNavigator = new AscendAdminNavigator(
            playerStore,
            mapStore,
            settingsStore,
            ascensionManager,
            challengeManager,
            robotManager,
            achievementManager,
            whitelistManager,
            mineHierarchyStore,
            minerConfigStore,
            tierConfigStore,
            conveyorConfigStore,
            gateConfigStore,
            blockConfigStore,
            mineManager,
            hologramManager
        );
        AscendCommand ascendCommand = new AscendCommand(
            playerStore,
            mapStore,
            ghostStore,
            runTracker,
            hudManager,
            robotManager,
            achievementManager,
            ascensionManager,
            challengeManager,
            summitManager,
            transcendenceManager,
            tutorialTriggerService,
            runnerSpeedCalculator,
            analytics,
            runtimeConfig
        );
        ascendCommand.setEventHandler(eventHandler);
        getCommandRegistry().registerCommand(ascendCommand);
        getCommandRegistry().registerCommand(new AscendAdminCommand(
            adminNavigator,
            mapStore,
            whitelistManager,
            mineHierarchyStore,
            hologramManager
        ));
        getCommandRegistry().registerCommand(new ElevateCommand(
            playerStore, challengeManager, robotManager, hudManager, mapStore, achievementManager));
        getCommandRegistry().registerCommand(new SummitCommand(
            summitManager, playerStore, challengeManager, robotManager, hudManager, achievementManager));
        getCommandRegistry().registerCommand(new SkillCommand(ascensionManager));
        getCommandRegistry().registerCommand(new TranscendCommand(
            playerStore, transcendenceManager, robotManager, achievementManager));
        getCommandRegistry().registerCommand(new CatCommand(playerStore, hudManager, achievementManager));
        getCommandRegistry().registerCommand(new io.hyvexa.ascend.mine.command.MineCommand(
            mineGateChecker, minePlayerStore, blockConfigStore, minerConfigStore, tierConfigStore,
            mineAchievementTracker, mineRobotManager, mineHierarchyStore));
        if (runtimeConfig.isEnableTestCommands()) {
            getCommandRegistry().registerCommand(new CinematicTestCommand());
            getCommandRegistry().registerCommand(new HudPreviewCommand(hudManager));
        }
        getCommandRegistry().registerCommand(new io.hyvexa.core.queue.RunOrFallQueueCommand());
        AscendInteractionBridge.configure(new AscendInteractionBridge.Services(
            mapStore,
            playerStore,
            settingsStore,
            ghostStore,
            runTracker,
            hudManager,
            robotManager,
            ascensionManager,
            transcendenceManager,
            summitManager,
            challengeManager,
            achievementManager,
            tutorialTriggerService,
            runnerSpeedCalculator,
            eggOpenService,
            minePlayerStore,
            blockConfigStore,
            minerConfigStore,
            tierConfigStore,
            mineAchievementTracker,
            mineRobotManager,
            mineGateChecker,
            createMenuNavigator(),
            analytics,
            eventHandler,
            mineHierarchyStore
        ));
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
        if (mineHierarchyStore != null && mineManager != null && minePlayerStore != null) {
            if (registry.getEntityEventTypeForClass(BreakBlockEvent.class) == null) {
                registry.registerEntityEventType(BreakBlockEvent.class);
            }
            if (!registry.hasSystemClass(MineBreakSystem.class)) {
                mineBreakSystem = new MineBreakSystem(mineManager, minePlayerStore,
                    mineHudManager, mineAchievementTracker);
                registry.registerSystem(mineBreakSystem);
            }
            // Mine damage system — scales block damage by mining speed upgrade
            if (registry.getEntityEventTypeForClass(DamageBlockEvent.class) == null) {
                registry.registerEntityEventType(DamageBlockEvent.class);
            }
            if (!registry.hasSystemClass(MineDamageSystem.class)) {
                mineDamageSystem = new MineDamageSystem(mineManager, minePlayerStore,
                    blockConfigStore, mineHudManager, mineAchievementTracker);
                registry.registerSystem(mineDamageSystem);
            }
        }

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            try {
                Ref<EntityStore> ref = event.getPlayerRef();
                if (ref == null || !ref.isValid()) {
                    return;
                }
                Store<EntityStore> store = ref.getStore();
                if (store == null || store.getExternalData() == null) {
                    return;
                }
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
                if (mineRobotManager != null) {
                    mineRobotManager.prepareWorld(world);
                }
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) {
                    return;
                }
                // Cache PlayerRef for O(1) lookups
                UUID playerId = playerRef.getUuid();
                if (playerId != null) {
                    tickHandler.addPlayerInAscendWorld(playerId);
                    tickHandler.cacheTickPlayer(playerRef);
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
                    playerStore.settings().setSessionFirstRunClaimed(playerId, false);
                    playerStore.gameplay().resetConsecutiveManualRuns(playerId);
                    playerStore.storePlayerName(playerId, playerRef.getUsername());

                    // Initialize ascension timer on first join (if not already set)
                    AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerId);
                    if (progress.gameplay().getAscensionStartedAt() == null) {
                        progress.gameplay().setAscensionStartedAt(System.currentTimeMillis());
                    }

                    playerStore.markDirty(playerId);

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
                    boolean restoreToMine = false;
                    MinePlayerProgress mineProgress = null;
                    if (minePlayerStore != null && mineGateChecker != null && mineGateChecker.canAccessMine(playerId)) {
                        mineProgress = minePlayerStore.getOrCreatePlayer(playerId);
                        restoreToMine = mineProgress.isInMine();
                    }

                    // Restore persisted HUD hidden state before first attach
                    hudManager.loadHudHiddenFromStore(playerId);
                    hudManager.attach(playerRef, player);
                    // Restore persisted players_hidden state
                    if (playerStore != null && playerStore.settings().isPlayersHidden(playerId)) {
                        Universe.get().getWorlds().forEach((wId, w) ->
                                w.execute(() -> tickHandler.applyHiddenStateForPlayer(playerRef, w)));
                    }
                    if (restoreToMine) {
                        mineGateChecker.giveMineItems(player, playerId);
                        hudManager.setMineMode(playerId, true);
                        MineHudManager mhm = mineHudManager;
                        if (mhm != null) {
                            mhm.attachHud(playerRef, player);
                        }
                        mineGateChecker.applyHasteSpeed(mineProgress, ref, store, playerRef);
                    } else {
                        AscendInventoryUtils.giveMenuItems(player);
                    }
                    AscendMusicPage.applyStoredMusic(playerRef);
                    DiscordLinkStore linkStore = discordLinkStore;
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
                        tickHandler.addPlayerInAscendWorld(playerId);
                    }
                    tickHandler.cacheTickPlayer(playerRef);
                    // If joining Ascend world, ensure items are present
                    Player player = holder.getComponent(Player.getComponentType());
                    if (player == null) {
                        return;
                    }
                    boolean playerInMine = false;
                    if (playerId != null && minePlayerStore != null) {
                        MinePlayerProgress mp = minePlayerStore.getOrCreatePlayer(playerId);
                        playerInMine = mp.isInMine();
                    }
                    if (!playerInMine) {
                        ensureMenuItemsWhenReady(player, world, MENU_SYNC_MAX_ATTEMPTS);
                    }
                    return;
                }

                // Clean up Ascend state on true Ascend -> non-Ascend transitions
                if (playerId != null && tickHandler.removePlayerFromAscendWorld(playerId)) {
                    // Pass event's Player/PlayerRef — PlayerRef.getReference() is null during transitions
                    Player player = holder.getComponent(Player.getComponentType());
                    cleanupAscendState(playerId, player, playerRef);
                    // Clear inMine flag — player explicitly left Ascend world
                    if (minePlayerStore != null) {
                        MinePlayerProgress mp = minePlayerStore.getPlayer(playerId);
                        if (mp != null && mp.isInMine()) {
                            mp.setInMine(false);
                            minePlayerStore.markDirty(playerId);
                        }
                    }
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

            tickHandler.removePlayerFromAscendWorld(playerId);
            cleanupAscendState(playerId, null, null);

            // Clean up event handler state
            runSafe(() -> { if (eventHandler != null) eventHandler.cleanupPlayer(playerId); },
                    "Disconnect cleanup: eventHandler");
            // Disconnect-only cleanup (not needed on world transitions)
            runSafe(() -> AscendMapSelectPage.clearBuyAllCooldown(playerId), "Disconnect cleanup: clearBuyAllCooldown");
            // Evict player from cache (lazy loading - saves memory)
            runSafe(() -> { if (playerStore != null) playerStore.removePlayer(playerId); },
                    "Disconnect cleanup: playerStore");
            runSafe(() -> { if (minePlayerStore != null) minePlayerStore.evict(playerId); },
                    "Disconnect cleanup: minePlayerStore");
            runSafe(() -> { if (mineManager != null) mineManager.getBlockDamageTracker().evict(playerId); },
                    "Disconnect cleanup: blockDamageTracker");
            runSafe(() -> { World w = mineManager != null ? mineManager.getWorld() : null;
                            if (w != null) BlockVisualHelper.evictPlayer(playerId, w); },
                    "Disconnect cleanup: blockVisualHelper");
            runSafe(() -> { if (mineBreakSystem != null) mineBreakSystem.evict(playerId); },
                    "Disconnect cleanup: mineBreakSystem");
            runSafe(() -> { if (mineDamageSystem != null) mineDamageSystem.evict(playerId); },
                    "Disconnect cleanup: mineDamageSystem");
            runSafe(() -> { if (mineAchievementTracker != null) mineAchievementTracker.evict(playerId); },
                    "Disconnect cleanup: mineAchievementTracker");
            runSafe(() -> { if (mineGateChecker != null) mineGateChecker.evict(playerId); },
                    "Disconnect cleanup: mineGateChecker");
            runSafe(() -> EggRouletteAnimation.cancelIfActive(playerId),
                    "Disconnect cleanup: eggRoulette");
        });

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            UUID playerId = playerRef != null ? playerRef.getUuid() : null;
            if (playerId != null) {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    World world = store != null && store.getExternalData() != null ? store.getExternalData().getWorld() : null;
                    if (isAscendWorld(world)) {
                        tickHandler.addPlayerInAscendWorld(playerId);
                        tickHandler.cacheTickPlayer(playerRef);
                    }
                }
            }
        }

        tickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
            tickHandler::tick,
            50, 50, TimeUnit.MILLISECONDS
        );

        if (mineManager != null) {
            mineTickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    mineManager.tick();
                    World w = mineManager.getWorld();
                    if (w != null) BlockVisualHelper.cleanupIdleNpcs(w);
                },
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
        runSafe(() -> { if (tickHandler != null) tickHandler.clearAll(); },
                "Shutdown: tickHandler clear");
        runSafe(AscendInteractionBridge::clear, "Shutdown: interaction bridge clear");
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

    public AscendRuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    public AscendPlayerEventHandler getEventHandler() {
        return eventHandler;
    }

    /**
     * Get a PlayerRef for a given player UUID.
     * Uses a cache populated on PlayerReadyEvent for O(1) lookup.
     */
    public PlayerRef getPlayerRef(UUID playerId) {
        return tickHandler != null ? tickHandler.getPlayerRef(playerId) : null;
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
    private void cleanupAscendState(UUID playerId, Player player, PlayerRef playerRef) {
        runSafe(() -> AscendCommand.onPlayerDisconnect(playerId), "Leave cleanup: AscendCommand");
        // HUD cleanup BEFORE removeTickPlayer — removePlayer needs playerRefCache to detach from MultiHudBridge
        runSafe(() -> { if (mineHudManager != null) mineHudManager.removePlayer(playerId, player, playerRef); },
                "Leave cleanup: mineHudManager");
        runSafe(() -> hudManager.removePlayer(playerId), "Leave cleanup: hudManager");
        runSafe(() -> tickHandler.removeTickPlayer(playerId), "Leave cleanup: tickHandler");
        runSafe(() -> BaseAscendPage.removeCurrentPage(playerId), "Leave cleanup: removeCurrentPage");
        runSafe(() -> AscendLeaveInteraction.clearPendingLeave(playerId), "Leave cleanup: clearPendingLeave");
        runSafe(() -> AscendSettingsPage.clearPlayer(playerId), "Leave cleanup: AscendSettingsPage");
        runSafe(() -> AscendMusicPage.clearPlayer(playerId), "Leave cleanup: AscendMusicPage");
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
                (ref, store, playerRef, services) -> new AscendMapSelectPage(playerRef,
                    services.mapStore(), services.playerStore(), services.runTracker(),
                    services.robotManager(), services.ghostStore(),
                    services.ascensionManager(), services.challengeManager(), services.summitManager(),
                    services.transcendenceManager(), services.achievementManager(),
                    services.tutorialTriggerService(), services.runnerSpeedCalculator(),
                    services.analytics(), services.eventHandler()),
                (services, player) -> {
                    if (services.mapStore() == null || services.playerStore() == null
                            || services.runTracker() == null || services.ghostStore() == null) {
                        player.sendMessage(AbstractAscendPageInteraction.LOADING_MESSAGE);
                        return false;
                    }
                    return true;
                }, true, true)));
        // Stormsilk -> AscendLeaderboardPage
        registry.register("Ascend_Dev_Stormsilk_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, services) -> new AscendLeaderboardPage(playerRef,
                    services.playerStore()),
                (services, player) -> {
                    if (services.playerStore() == null) {
                        player.sendMessage(AbstractAscendPageInteraction.LOADING_MESSAGE);
                        return false;
                    }
                    return true;
                }, false, true)));
        // Cotton -> AutomationPage
        registry.register("Ascend_Dev_Cotton_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, services) -> new AutomationPage(playerRef,
                    services.playerStore(), services.ascensionManager()),
                (services, player) -> {
                    if (services.playerStore() == null || services.ascensionManager() == null) {
                        player.sendMessage(AbstractAscendPageInteraction.LOADING_MESSAGE);
                        return false;
                    }
                    return true;
                }, true, true)));
        // Shadoweave -> AscendHelpPage
        registry.register("Ascend_Dev_Shadoweave_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, services) -> new AscendHelpPage(playerRef),
                (services, player) -> true, false, false)));
        registry.register("Ascend_Reset_Interaction",
            AscendResetInteraction.class, AscendResetInteraction.CODEC);
        registry.register("Ascend_Leave_Interaction",
            AscendLeaveInteraction.class, AscendLeaveInteraction.CODEC);
        // Silk -> AscendProfilePage
        registry.register("Ascend_Dev_Silk_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, services) -> services.menuNavigator().createProfilePage(playerRef),
                (services, player) -> {
                    if (services.playerStore() == null || services.robotManager() == null) {
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
        registry.register("Mine_Chest_Interaction",
            MineChestInteraction.class, MineChestInteraction.CODEC);
        registry.register("Conveyor_Chest_Interaction",
            ConveyorChestInteraction.class, ConveyorChestInteraction.CODEC);
        registry.register("Mine_Egg_Chest_Interaction",
            MineEggChestInteraction.class, MineEggChestInteraction.CODEC);
        // Mine Sell -> MineSellPage
        registry.register("Mine_Sell_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, services) -> {
                    MinePlayerProgress progress = services.minePlayerStore().getOrCreatePlayer(playerRef.getUuid());
                    return new io.hyvexa.ascend.mine.ui.MineSellPage(playerRef, progress,
                        services.blockConfigStore(), services.minePlayerStore(), services.mineAchievementTracker());
                },
                (services, player) -> services.minePlayerStore() != null,
                true, true)));
        // Mine Upgrades -> MinePage
        registry.register("Mine_Upgrades_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, services) -> {
                    MinePlayerProgress progress = services.minePlayerStore().getOrCreatePlayer(playerRef.getUuid());
                    return new io.hyvexa.ascend.mine.ui.MinePage(playerRef, progress,
                        services.minerConfigStore(), services.tierConfigStore(),
                        services.minePlayerStore(), services.mineRobotManager(),
                        services.mineGateChecker(), services.mineAchievementTracker(),
                        services.mineHierarchyStore());
                },
                (services, player) -> services.minePlayerStore() != null && services.minerConfigStore() != null,
                true, true)));
        // Mine Leaderboard -> MineLeaderboardPage
        registry.register("Mine_Leaderboard_Interaction",
            AscendDevInteraction.class, AscendDevInteraction.codec(() -> new AscendDevInteraction(
                (ref, store, playerRef, services) -> new io.hyvexa.ascend.mine.ui.MineLeaderboardPage(
                    playerRef, services.mineAchievementTracker()),
                (services, player) -> services.mineAchievementTracker() != null,
                false, true)));
    }

    private AscendMenuNavigator createMenuNavigator() {
        return new AscendMenuNavigator(
            playerStore,
            mapStore,
            ghostStore,
            runnerSpeedCalculator,
            achievementManager,
            robotManager,
            hudManager
        );
    }
}
