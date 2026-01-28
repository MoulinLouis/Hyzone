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
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.parkour.data.GlobalMessageStore;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.PlayerCountStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import io.hyvexa.parkour.interaction.MenuInteraction;
import io.hyvexa.parkour.interaction.LeaderboardInteraction;
import io.hyvexa.parkour.interaction.LeaveInteraction;
import io.hyvexa.parkour.interaction.PlayerSettingsInteraction;
import io.hyvexa.parkour.interaction.PracticeInteraction;
import io.hyvexa.parkour.interaction.ResetInteraction;
import io.hyvexa.parkour.interaction.RestartCheckpointInteraction;
import io.hyvexa.parkour.interaction.StatsInteraction;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollision;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.InventoryUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.parkour.command.CheckpointCommand;
import io.hyvexa.parkour.command.DatabaseClearCommand;
import io.hyvexa.parkour.command.DatabaseReloadCommand;
import io.hyvexa.parkour.command.DatabaseTestCommand;
import io.hyvexa.parkour.command.DiscordCommand;
import io.hyvexa.parkour.command.MessageTestCommand;
import io.hyvexa.parkour.command.ParkourAdminCommand;
import io.hyvexa.parkour.command.ParkourAdminItemCommand;
import io.hyvexa.parkour.command.ParkourCommand;
import io.hyvexa.parkour.command.ParkourItemCommand;
import io.hyvexa.parkour.command.ParkourMusicDebugCommand;
import io.hyvexa.parkour.command.RulesCommand;
import io.hyvexa.parkour.command.StoreCommand;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.system.NoDropSystem;
import io.hyvexa.parkour.system.NoBreakSystem;
import io.hyvexa.parkour.system.NoLaunchpadEditSystem;
import io.hyvexa.parkour.system.NoPlayerDamageSystem;
import io.hyvexa.parkour.system.NoPlayerKnockbackSystem;
import io.hyvexa.parkour.system.NoWeaponDamageSystem;
import io.hyvexa.parkour.system.PlayerVisibilityFilterSystem;
import io.hyvexa.parkour.ui.WelcomePage;
import io.hyvexa.parkour.ui.PlayerMusicPage;
import io.hyvexa.parkour.visibility.PlayerVisibilityManager;
import io.hyvexa.manager.AnnouncementManager;
import io.hyvexa.manager.HudManager;
import io.hyvexa.manager.PlaytimeManager;
import io.hyvexa.manager.PlayerCleanupManager;
import io.hyvexa.manager.PlayerPerksManager;

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
import java.util.logging.Level;

/**
 * This class serves as the entrypoint for your plugin. Use the setup method to register into game registries or add
 * event listeners.
 */
public class HyvexaPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long PLAYER_COUNT_SAMPLE_SECONDS = PlayerCountStore.DEFAULT_SAMPLE_INTERVAL_SECONDS;
    private static final long STALE_PLAYER_SWEEP_SECONDS = 120L;
    private static final long TELEPORT_DEBUG_INTERVAL_SECONDS = 120L;
    private static final boolean DISABLE_WORLD_MAP = true; // Parkour server doesn't need world map
    private static final String PARKOUR_WORLD_NAME = "Parkour";
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
    private DuelQueue duelQueue;
    private DuelTracker duelTracker;
    private DuelStatsStore duelStatsStore;
    private DuelMatchStore duelMatchStore;
    private DuelPreferenceStore duelPreferenceStore;
    private HudManager hudManager;
    private AnnouncementManager announcementManager;
    private PlayerPerksManager perksManager;
    private PlaytimeManager playtimeManager;
    private PlayerCleanupManager cleanupManager;
    private ScheduledFuture<?> mapDetectionTask;
    private ScheduledFuture<?> hudUpdateTask;
    private ScheduledFuture<?> playtimeTask;
    private ScheduledFuture<?> collisionTask;
    private ScheduledFuture<?> playerCountTask;
    private ScheduledFuture<?> stalePlayerSweepTask;
    private ScheduledFuture<?> teleportDebugTask;
    private ScheduledFuture<?> duelTickTask;

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
        // Initialize database connection
        try {
            DatabaseManager.getInstance().initialize();
            LOGGER.atInfo().log("Database connection initialized");
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("Failed to initialize database: " + e.getMessage());
        }
        this.mapStore = new MapStore();
        this.mapStore.syncLoad();
        this.mapStore.setOnChangeListener(this::onMapStoreChanged);
        this.settingsStore = new SettingsStore();
        this.settingsStore.syncLoad();
        this.progressStore = new ProgressStore();
        this.progressStore.syncLoad();
        this.playerCountStore = new PlayerCountStore();
        this.playerCountStore.syncLoad();
        this.globalMessageStore = new GlobalMessageStore();
        this.globalMessageStore.syncLoad();
        this.runTracker = new RunTracker(this.mapStore, this.progressStore, this.settingsStore);
        this.duelStatsStore = new DuelStatsStore();
        this.duelStatsStore.syncLoad();
        this.duelMatchStore = new DuelMatchStore();
        this.duelMatchStore.ensureTable();
        this.duelPreferenceStore = new DuelPreferenceStore();
        this.duelPreferenceStore.syncLoad();
        this.duelQueue = new DuelQueue();
        this.duelTracker = new DuelTracker(duelQueue, duelMatchStore, duelStatsStore, duelPreferenceStore, mapStore);
        this.perksManager = new PlayerPerksManager(progressStore, mapStore);
        this.hudManager = new HudManager(progressStore, mapStore, runTracker, duelTracker, perksManager);
        this.announcementManager = new AnnouncementManager(globalMessageStore, hudManager,
                this::scheduleTick, this::cancelScheduled);
        this.playtimeManager = new PlaytimeManager(progressStore, playerCountStore);
        this.cleanupManager = new PlayerCleanupManager(hudManager, announcementManager, perksManager, playtimeManager,
                runTracker, PlayerVisibilityManager.get());
        this.playtimeManager.setOnlineCount(Universe.get().getPlayers().size());
        mapDetectionTask = scheduleTick("map detection", this::tickMapDetection, 200, 200, TimeUnit.MILLISECONDS);
        hudUpdateTask = scheduleTick("hud updates", this::tickHudUpdates, 100, 100, TimeUnit.MILLISECONDS);
        playtimeTask = scheduleTick("playtime", this::tickPlaytime, 60, 60, TimeUnit.SECONDS);
        collisionTask = scheduleTick("collision removal", this::tickCollisionRemoval, 1, 2, TimeUnit.SECONDS);
        playerCountTask = scheduleTick("player counts", this::tickPlayerCounts, 5, PLAYER_COUNT_SAMPLE_SECONDS,
                TimeUnit.SECONDS);
        stalePlayerSweepTask = scheduleTick("stale player sweep", this::tickStalePlayerSweep, STALE_PLAYER_SWEEP_SECONDS,
                STALE_PLAYER_SWEEP_SECONDS, TimeUnit.SECONDS);
        teleportDebugTask = scheduleTick("teleport debug", this::tickTeleportDebug, TELEPORT_DEBUG_INTERVAL_SECONDS,
                TELEPORT_DEBUG_INTERVAL_SECONDS, TimeUnit.SECONDS);
        duelTickTask = scheduleTick("duel tick", this::tickDuel, 100, 100, TimeUnit.MILLISECONDS);
        announcementManager.refreshChatAnnouncements();


        this.getCommandRegistry().registerCommand(new CheckpointCommand());
        this.getCommandRegistry().registerCommand(new DiscordCommand());
        this.getCommandRegistry().registerCommand(new RulesCommand());
        this.getCommandRegistry().registerCommand(new ParkourCommand(this.mapStore, this.progressStore, this.settingsStore,
                this.playerCountStore, this.runTracker));
        this.getCommandRegistry().registerCommand(new ParkourAdminItemCommand());
        this.getCommandRegistry().registerCommand(new ParkourMusicDebugCommand());
        this.getCommandRegistry().registerCommand(new StoreCommand());
        this.getCommandRegistry().registerCommand(new DatabaseClearCommand());
        this.getCommandRegistry().registerCommand(new DatabaseReloadCommand());
        this.getCommandRegistry().registerCommand(new DatabaseTestCommand());
        this.getCommandRegistry().registerCommand(new MessageTestCommand());
        this.getCommandRegistry().registerCommand(new DuelCommand(this.duelTracker, this.runTracker));

        registerNoDropSystem();
        registerNoBreakSystem();
        registerNoLaunchpadEditSystem();
        // Defer systems that access module singletons (EntityTrackerSystems, DamageModule)
        // to avoid blocking during plugin setup when those modules aren't ready yet
        HytaleServer.SCHEDULED_EXECUTOR.schedule(this::registerDeferredSystems, 1, TimeUnit.SECONDS);

        this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> {
            try {
                disablePlayerCollision(event.getPlayerRef());
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in PlayerConnectEvent (collision): " + e.getMessage());
            }
        });
        this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> {
            try {
                playtimeManager.incrementOnlineCount();
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in PlayerConnectEvent (count): " + e.getMessage());
            }
        });
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            try {
                disablePlayerCollision(event.getPlayerRef());
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in PlayerReadyEvent (collision): " + e.getMessage());
            }
        });
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            disablePlayerCollision(playerRef);
        }

        this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> {
            try {
                PlayerRef playerRef = event.getPlayerRef();
                if (playerRef != null) {
                    Ref<EntityStore> ref = playerRef.getReference();
                    if (ref != null && ref.isValid()) {
                        Store<EntityStore> store = ref.getStore();
                        if (shouldApplyParkourMode(playerRef, store)) {
                            hudManager.ensureRunHud(playerRef);
                        }
                    }
                }
                playtimeManager.startPlaytimeSession(event.getPlayerRef());
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in PlayerConnectEvent (hud/playtime): " + e.getMessage());
            }
        });
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
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

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            try {
                if (runTracker != null) {
                    runTracker.markPlayerReady(event.getPlayerRef());
                }
                syncRunInventoryOnReady(event.getPlayerRef());
                PlayerMusicPage.applyStoredMusic(event.getPlayerRef());
                // Disable world map generation to save memory (parkour server doesn't need it)
                if (DISABLE_WORLD_MAP) {
                    disableWorldMapForPlayer(event.getPlayerRef());
                }
                Ref<EntityStore> ref = event.getPlayerRef();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                    if (playerRef != null && shouldApplyParkourMode(playerRef, store)) {
                        hudManager.ensureRunHud(playerRef);
                    }
                }
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in PlayerReadyEvent (inventory): " + e.getMessage());
            }
        });
        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            try {
                event.setBroadcastJoinMessage(false);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in AddPlayerToWorldEvent: " + e.getMessage());
            }
        });
        this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> {
            try {
                playtimeManager.broadcastPresence(event.getPlayerRef(), true);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in PlayerConnectEvent (broadcast): " + e.getMessage());
            }
        });
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            try {
                if (event.getPlayerRef() != null) {
                    UUID playerId = event.getPlayerRef().getUuid();
                    playtimeManager.broadcastPresence(event.getPlayerRef(), false);
                    if (duelTracker != null) {
                        duelTracker.handleDisconnect(playerId);
                    }
                    cleanupManager.handleDisconnect(event.getPlayerRef());
                }
                playtimeManager.decrementOnlineCount();
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in PlayerDisconnectEvent: " + e.getMessage());
            }
        });
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            syncRunInventoryOnConnect(playerRef);
        }

        this.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, event -> {
            try {
                if (event.getEntity() instanceof Player player) {
                    updateDropProtection(player);
                }
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in LivingEntityInventoryChangeEvent: " + e.getMessage());
            }
        });

        this.getEventRegistry().registerGlobal(PlayerChatEvent.class, event -> {
            try {
                event.setFormatter((sender, content) -> {
                    if (sender == null) {
                        return Message.raw(content != null ? content : "");
                    }
                    String name = sender.getUsername();
                    if (name == null || name.isBlank()) {
                        name = "Player";
                    }
                    String safeContent = content != null ? content : "";
                    // Check OP status directly via PermissionsModule (no store access needed)
                    boolean isOp = false;
                    UUID senderUuid = sender.getUuid();
                    if (senderUuid != null) {
                        var permissions = com.hypixel.hytale.server.core.permissions.PermissionsModule.get();
                        if (permissions != null) {
                            isOp = permissions.getGroupsForUser(senderUuid).contains("OP");
                        }
                    }
                    if (isOp) {
                        Message rankPart = Message.raw("Admin").color("#ff0000");
                        return Message.join(
                                Message.raw("["),
                                rankPart,
                                Message.raw("] "),
                                Message.raw(name),
                                Message.raw(": "),
                                Message.raw(safeContent)
                        );
                    }
                    String rank = progressStore != null ? progressStore.getRankName(sender.getUuid(), mapStore) : "Unranked";
                    Message rankPart = FormatUtils.getRankMessage(rank);
                    String badgeLabel = perksManager != null ? perksManager.getSpecialRankLabel(sender.getUuid()) : null;
                    String badgeColor = perksManager != null ? perksManager.getSpecialRankColor(sender.getUuid()) : null;
                    if (badgeLabel != null) {
                        return Message.join(
                                Message.raw("["),
                                rankPart,
                                Message.raw("] "),
                                Message.raw("(").color("#ffffff"),
                                Message.raw(badgeLabel).color(badgeColor != null ? badgeColor : "#b2c0c7"),
                                Message.raw(")").color("#ffffff"),
                                Message.raw(" "),
                                Message.raw(name),
                                Message.raw(": "),
                                Message.raw(safeContent)
                        );
                    }
                    return Message.join(
                            Message.raw("["),
                            rankPart,
                            Message.raw("] "),
                            Message.raw(name),
                            Message.raw(": "),
                            Message.raw(safeContent)
                    );
                });
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in PlayerChatEvent: " + e.getMessage());
            }
        });

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            var ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                Player player = ref.getStore().getComponent(ref, Player.getComponentType());
                if (player != null) {
                    updateDropProtection(player);
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
    private void tickPlaytime() {
        if (playtimeManager != null) {
            playtimeManager.tickPlaytime();
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
            LOGGER.atInfo().log("Teleport debug (last " + TELEPORT_DEBUG_INTERVAL_SECONDS + "s): " + name + " "
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

    private static final class PlayerTickContext {
        private final PlayerRef playerRef;
        private final Ref<EntityStore> ref;
        private final Store<EntityStore> store;

        private PlayerTickContext(PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
            this.playerRef = playerRef;
            this.ref = ref;
            this.store = store;
        }
    }

    public RunTracker getRunTracker() {
        return runTracker;
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

    private void tickMapDetection() {
        Map<World, List<PlayerTickContext>> playersByWorld = collectPlayersByWorld();
        for (Map.Entry<World, List<PlayerTickContext>> entry : playersByWorld.entrySet()) {
            World world = entry.getKey();
            if (!isParkourWorld(world)) {
                continue;
            }
            List<PlayerTickContext> players = entry.getValue();
            CompletableFuture.runAsync(() -> {
                for (PlayerTickContext context : players) {
                    if (context.ref == null || !context.ref.isValid()) {
                        continue;
                    }
                    UUID playerId = context.playerRef != null ? context.playerRef.getUuid() : null;
                    if (!shouldApplyParkourMode(playerId, world)) {
                        continue;
                    }
                    if (runTracker != null) {
                        boolean inDuel = duelTracker != null && duelTracker.isInMatch(playerId);
                        if (!inDuel && perksManager != null) {
                            String activeMapId = runTracker.getActiveMapId(playerId);
                            if (activeMapId != null) {
                                perksManager.disableVipSpeedBoost(context.ref, context.store, context.playerRef);
                            } else if (perksManager.shouldDisableVipSpeedForStartTrigger(context.store, context.ref,
                                    context.playerRef)) {
                                perksManager.disableVipSpeedBoost(context.ref, context.store, context.playerRef);
                            }
                        }
                    }
                    runTracker.checkPlayer(context.ref, context.store);
                    if (hudManager != null) {
                        hudManager.ensureRunHudNow(context.ref, context.store, context.playerRef);
                        hudManager.updateRunHud(context.ref, context.store);
                    }
                }
            }, world);
        }
    }

    private void tickHudUpdates() {
        Map<World, List<PlayerTickContext>> playersByWorld = collectPlayersByWorld();
        for (Map.Entry<World, List<PlayerTickContext>> entry : playersByWorld.entrySet()) {
            World world = entry.getKey();
            if (!isParkourWorld(world)) {
                continue;
            }
            List<PlayerTickContext> players = entry.getValue();
            CompletableFuture.runAsync(() -> {
                for (PlayerTickContext context : players) {
                    if (context.ref == null || !context.ref.isValid()) {
                        continue;
                    }
                    UUID playerId = context.playerRef != null ? context.playerRef.getUuid() : null;
                    if (!shouldApplyParkourMode(playerId, world)) {
                        continue;
                    }
                    if (hudManager != null) {
                        hudManager.ensureRunHudNow(context.ref, context.store, context.playerRef);
                        hudManager.updateRunHud(context.ref, context.store);
                    }
                }
            }, world);
        }
    }

    private void tickDuel() {
        if (duelTracker == null) {
            return;
        }
        duelTracker.sweepQueuedPlayersInRun(runTracker);
        duelTracker.tick();
    }

    private Map<World, List<PlayerTickContext>> collectPlayersByWorld() {
        Map<World, List<PlayerTickContext>> playersByWorld = new HashMap<>();
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            if (world == null) {
                continue;
            }
            playersByWorld.computeIfAbsent(world, ignored -> new ArrayList<>())
                    .add(new PlayerTickContext(playerRef, ref, store));
        }
        return playersByWorld;
    }

    private void tickCollisionRemoval() {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            disablePlayerCollision(playerRef);
        }
    }


    private void disablePlayerCollision(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        disablePlayerCollision(playerRef.getReference());
    }

    private void disablePlayerCollision(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> store.tryRemoveComponent(ref, HitboxCollision.getComponentType()), world);
    }

    private void updateDropProtection(Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        boolean allowDrop = PermissionUtils.isOp(player);
        applyDropFilter(inventory.getHotbar(), allowDrop);
        applyDropFilter(inventory.getStorage(), allowDrop);
        applyDropFilter(inventory.getBackpack(), allowDrop);
        applyDropFilter(inventory.getTools(), allowDrop);
        applyDropFilter(inventory.getUtility(), allowDrop);
        applyDropFilter(inventory.getArmor(), allowDrop);
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

    private boolean shouldApplyParkourMode(UUID playerId, World world) {
        if (playerId == null) {
            return false;
        }
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
        World world = store.getExternalData().getWorld();
        return isParkourWorld(world);
    }

    private boolean isParkourWorld(World world) {
        if (world == null || world.getName() == null) {
            return false;
        }
        return PARKOUR_WORLD_NAME.equalsIgnoreCase(world.getName());
    }

    private void syncRunInventoryOnConnect(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        if (!shouldApplyParkourMode(playerRef, store)) {
            return;
        }
        syncRunInventoryOnConnect(ref, store, playerRef);
    }

    private void sendLanguageNotice(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            Message link = Message.raw("Discord").color("#8ab4f8").link(DISCORD_URL);
            Message message = Message.join(
                    Message.raw(JOIN_LANGUAGE_NOTICE),
                    link,
                    Message.raw(JOIN_LANGUAGE_NOTICE_SUFFIX)
            );
            player.sendMessage(message);
        }, world);
    }

    private void syncRunInventoryOnReady(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        // Must dispatch to World thread before accessing components
        CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                return;
            }
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            if (!shouldApplyParkourMode(playerRef, store)) {
                return;
            }
            syncRunInventoryOnConnect(ref, store, playerRef);
            showWelcomeIfFirstJoin(ref, store, playerRef);
            sendLanguageNotice(playerRef);
        }, world);
    }

    private void syncRunInventoryOnConnect(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            if (!shouldApplyParkourMode(playerRef, store)) {
                return;
            }
            String activeMap = runTracker.getActiveMapId(playerRef.getUuid());
            if (activeMap == null) {
                InventoryUtils.giveMenuItems(player);
                return;
            }
            boolean practiceEnabled = runTracker.isPracticeEnabled(playerRef.getUuid());
            InventoryUtils.giveRunItems(player, mapStore != null ? mapStore.getMap(activeMap) : null, practiceEnabled);
        }, world);
    }

    private void showWelcomeIfFirstJoin(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (progressStore == null || playerRef == null || !progressStore.shouldShowWelcome(playerRef.getUuid())) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            progressStore.markWelcomeShown(playerRef.getUuid(), playerRef.getUsername());
            WelcomePage page = new WelcomePage(playerRef);
            player.getPageManager().openCustomPage(ref, store, page);
        }, world);
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
                LOGGER.at(Level.SEVERE).log("Tick task failed (" + name + "): " + error.getMessage());
            }
        }, initialDelay, period, unit);
    }

    private void applyDropFilter(ItemContainer container, boolean allowDrop) {
        if (container == null) {
            return;
        }
        SlotFilter filter = allowDrop ? SlotFilter.ALLOW : SlotFilter.DENY;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            container.setSlotFilter(FilterActionType.DROP, slot, filter);
            container.setSlotFilter(FilterActionType.REMOVE, slot, filter);
        }
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
        if (!registry.hasSystemClass(PlayerVisibilityFilterSystem.class)) {
            registry.registerSystem(new PlayerVisibilityFilterSystem());
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

    /**
     * Disables world map generation for a player to save memory.
     * On a parkour server with a static world, players don't need the world map feature.
     */
    private void disableWorldMapForPlayer(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null) {
                return;
            }
            WorldMapTracker tracker = player.getWorldMapTracker();
            if (tracker != null) {
                // Set view radius to 0 to prevent map generation
                tracker.setViewRadiusOverride(0);
                // Clear any already loaded map data
                tracker.clear();
                String name = playerRef != null ? playerRef.getUsername() : "unknown";
                LOGGER.atInfo().log("Disabled world map for player: " + name);
            }
        }, world);
    }

    private void registerInteractionCodecs() {
        var registry = this.getCodecRegistry(Interaction.CODEC);
        registry.register("Parkour_Menu_Interaction", MenuInteraction.class, MenuInteraction.CODEC);
        registry.register("Parkour_Leaderboard_Interaction", LeaderboardInteraction.class, LeaderboardInteraction.CODEC);
        registry.register("Parkour_Stats_Interaction", StatsInteraction.class, StatsInteraction.CODEC);
        registry.register("Parkour_Reset_Interaction", ResetInteraction.class, ResetInteraction.CODEC);
        registry.register("Parkour_Practice_Interaction", PracticeInteraction.class, PracticeInteraction.CODEC);
        registry.register("Parkour_Restart_Checkpoint_Interaction",
                RestartCheckpointInteraction.class, RestartCheckpointInteraction.CODEC);
        registry.register("Parkour_Leave_Interaction", LeaveInteraction.class, LeaveInteraction.CODEC);
        registry.register("Parkour_PlayerSettings_Interaction",
                PlayerSettingsInteraction.class, PlayerSettingsInteraction.CODEC);
        registry.register("Media_RemoteControl",
                PlayerSettingsInteraction.class, PlayerSettingsInteraction.CODEC);
        registry.register("Forfeit_Interaction", ForfeitInteraction.class, ForfeitInteraction.CODEC);
        registry.register("Duel_Menu_Interaction", DuelMenuInteraction.class, DuelMenuInteraction.CODEC);
    }

    @Override
    protected void shutdown() {
        cancelScheduled(mapDetectionTask);
        cancelScheduled(hudUpdateTask);
        cancelScheduled(playtimeTask);
        cancelScheduled(collisionTask);
        cancelScheduled(playerCountTask);
        cancelScheduled(stalePlayerSweepTask);
        cancelScheduled(teleportDebugTask);
        cancelScheduled(duelTickTask);
        if (announcementManager != null) {
            announcementManager.shutdown();
        }
        if (progressStore != null) {
            progressStore.flushPendingSave();
        }
        DatabaseManager.getInstance().shutdown();
        super.shutdown();
    }

}
