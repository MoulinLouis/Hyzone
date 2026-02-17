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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.command.AscendCommand;
import io.hyvexa.ascend.command.AscendAdminCommand;
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
import io.hyvexa.core.economy.GemStore;
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
import io.hyvexa.ascend.interaction.AscendDevCinderclothInteraction;
import io.hyvexa.ascend.interaction.AscendDevCottonInteraction;
import io.hyvexa.ascend.interaction.AscendDevShadoweaveInteraction;
import io.hyvexa.ascend.interaction.AscendDevSilkInteraction;
import io.hyvexa.ascend.interaction.AscendDevStormsilkInteraction;
import io.hyvexa.ascend.interaction.AscendLeaveInteraction;
import io.hyvexa.ascend.interaction.AscendResetInteraction;
import io.hyvexa.ascend.interaction.AscendTranscendenceInteraction;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.transcendence.TranscendenceManager;
import io.hyvexa.ascend.tutorial.TutorialTriggerService;
import io.hyvexa.ascend.passive.PassiveEarningsManager;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.ui.AscendMapSelectPage;
import io.hyvexa.ascend.ui.AscendMusicPage;
import io.hyvexa.ascend.ui.AscendSettingsPage;
import io.hyvexa.ascend.ui.BaseAscendPage;
import io.hyvexa.ascend.util.AscendInventoryUtils;
import io.hyvexa.ascend.util.AscendModeGate;
import io.hyvexa.common.whitelist.AscendWhitelistManager;
import io.hyvexa.common.whitelist.WhitelistRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import io.hyvexa.common.util.HylogramsBridge;
import io.hyvexa.ascend.system.AscendFinishDetectionSystem;
import io.hyvexa.common.visibility.EntityVisibilityFilterSystem;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private AscendWhitelistManager whitelistManager;
    private AscendRuntimeConfig runtimeConfig;
    private ScheduledFuture<?> tickTask;
    private int tickCounter;
    private final ConcurrentHashMap<UUID, PlayerRef> playerRefCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<World, AtomicBoolean> worldTickInFlight = new ConcurrentHashMap<>();
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
            GemStore.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize GemStore for Ascend");
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
        if (runtimeConfig.isEnableTestCommands()) {
            getCommandRegistry().registerCommand(new CinematicTestCommand());
            getCommandRegistry().registerCommand(new HudPreviewCommand());
        }
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
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) {
                    return;
                }
                // Cache PlayerRef for O(1) lookups
                UUID playerId = playerRef.getUuid();
                if (playerId != null) {
                    playersInAscendWorld.add(playerId);
                    playerRefCache.put(playerId, playerRef);
                }
                // Register player as online for robot spawning
                if (playerId != null && robotManager != null) {
                    robotManager.onPlayerJoin(playerId);
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
                    try {
                        DiscordLinkStore.getInstance().checkAndRewardGems(playerId, player);
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Discord link check failed (ascend)");
                    }
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
            runSafe(() -> GemStore.getInstance().evictPlayer(playerId),
                    "Disconnect cleanup: GemStore");
            runSafe(() -> DiscordLinkStore.getInstance().evictPlayer(playerId),
                    "Disconnect cleanup: DiscordLinkStore");
        });

        tickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
            this::tick,
            50, 50, TimeUnit.MILLISECONDS
        );
    }

    @Override
    protected void shutdown() {
        runSafe(() -> { if (tickTask != null) { tickTask.cancel(false); tickTask = null; } },
                "Shutdown: tickTask cancel");
        runSafe(() -> worldTickInFlight.clear(), "Shutdown: worldTickInFlight clear");
        runSafe(() -> playersInAscendWorld.clear(), "Shutdown: playersInAscendWorld clear");
        runSafe(() -> { if (ghostRecorder != null) ghostRecorder.stop(); }, "Shutdown: ghostRecorder stop");
        runSafe(() -> { if (robotManager != null) robotManager.stop(); }, "Shutdown: robotManager stop");
        runSafe(() -> { if (playerStore != null) playerStore.flushPendingSave(); }, "Shutdown: playerStore flush");
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

    private void tick() {
        if (runTracker == null) {
            return;
        }
        tickCounter++;
        boolean fullTick = tickCounter % FULL_TICK_INTERVAL == 0;

        Map<World, List<PlayerTickContext>> playersByWorld = collectPlayersByWorld();
        for (Map.Entry<World, List<PlayerTickContext>> entry : playersByWorld.entrySet()) {
            World world = entry.getKey();
            if (!isAscendWorld(world)) {
                continue;
            }
            List<PlayerTickContext> players = entry.getValue();
            AtomicBoolean inFlight = worldTickInFlight.computeIfAbsent(world, key -> new AtomicBoolean(false));
            if (!inFlight.compareAndSet(false, true)) {
                continue;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    for (PlayerTickContext context : players) {
                        if (context.ref == null || !context.ref.isValid()) {
                            continue;
                        }
                        if (fullTick) {
                            runTracker.checkPlayer(context.ref, context.store);
                            hudManager.updateFull(context.ref, context.store, context.playerRef);
                        }
                        hudManager.updateTimer(context.playerRef);
                        hudManager.updateRunnerBars(context.playerRef);
                        hudManager.updateToasts(context.playerRef.getUuid());
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

    private Map<World, List<PlayerTickContext>> collectPlayersByWorld() {
        Map<World, List<PlayerTickContext>> playersByWorld = new HashMap<>();
        for (PlayerRef playerRef : playerRefCache.values()) {
            Ref<EntityStore> ref = playerRef != null ? playerRef.getReference() : null;
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                continue;
            }
            World world = store.getExternalData().getWorld();
            if (world == null) {
                continue;
            }
            playersByWorld.computeIfAbsent(world, key -> new ArrayList<>())
                .add(new PlayerTickContext(ref, store, playerRef));
        }
        return playersByWorld;
    }

    private static class PlayerTickContext {
        private final Ref<EntityStore> ref;
        private final Store<EntityStore> store;
        private final PlayerRef playerRef;

        private PlayerTickContext(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
            this.ref = ref;
            this.store = store;
            this.playerRef = playerRef;
        }
    }

    public boolean isAscendWorld(World world) {
        return AscendModeGate.isAscendWorld(world);
    }

    private void ensureMenuItemsWhenReady(Player player, World expectedWorld, int attemptsRemaining) {
        if (player == null || expectedWorld == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            World playerWorld = player.getWorld();
            if (isAscendWorld(playerWorld)) {
                AscendInventoryUtils.ensureMenuItems(player);
                return;
            }
            if (playerWorld != null || attemptsRemaining <= 0) {
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
        runSafe(() -> playerRefCache.remove(playerId), "Leave cleanup: playerRefCache");
        runSafe(() -> BaseAscendPage.removeCurrentPage(playerId), "Leave cleanup: removeCurrentPage");
        runSafe(() -> AscendLeaveInteraction.clearPendingLeave(playerId), "Leave cleanup: clearPendingLeave");
        runSafe(() -> AscendSettingsPage.clearPlayer(playerId), "Leave cleanup: AscendSettingsPage");
        runSafe(() -> AscendMusicPage.clearPlayer(playerId), "Leave cleanup: AscendMusicPage");
        runSafe(() -> hudManager.removePlayer(playerId), "Leave cleanup: hudManager");
        runSafe(() -> { if (runTracker != null) runTracker.cancelRun(playerId); }, "Leave cleanup: runTracker");
        runSafe(() -> { if (robotManager != null) robotManager.onPlayerLeave(playerId); }, "Leave cleanup: robotManager");
        runSafe(() -> { if (challengeManager != null) challengeManager.onPlayerDisconnect(playerId); },
                "Leave cleanup: challengeManager");
    }

    private static void runSafe(Runnable action, String logMessage) {
        try { action.run(); }
        catch (Exception e) { LOGGER.atWarning().withCause(e).log(logMessage); }
    }

    private void registerInteractionCodecs() {
        var registry = this.getCodecRegistry(Interaction.CODEC);
        registry.register("Ascend_Dev_Cindercloth_Interaction",
            AscendDevCinderclothInteraction.class, AscendDevCinderclothInteraction.CODEC);
        registry.register("Ascend_Dev_Stormsilk_Interaction",
            AscendDevStormsilkInteraction.class, AscendDevStormsilkInteraction.CODEC);
        registry.register("Ascend_Dev_Cotton_Interaction",
            AscendDevCottonInteraction.class, AscendDevCottonInteraction.CODEC);
        registry.register("Ascend_Dev_Shadoweave_Interaction",
            AscendDevShadoweaveInteraction.class, AscendDevShadoweaveInteraction.CODEC);
        registry.register("Ascend_Reset_Interaction",
            AscendResetInteraction.class, AscendResetInteraction.CODEC);
        registry.register("Ascend_Leave_Interaction",
            AscendLeaveInteraction.class, AscendLeaveInteraction.CODEC);
        registry.register("Ascend_Dev_Silk_Interaction",
            AscendDevSilkInteraction.class, AscendDevSilkInteraction.CODEC);
        registry.register("Ascend_Transcendence_Interaction",
            AscendTranscendenceInteraction.class, AscendTranscendenceInteraction.CODEC);
    }
}
