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
import io.hyvexa.parkour.interaction.ResetInteraction;
import io.hyvexa.parkour.interaction.RestartCheckpointInteraction;
import io.hyvexa.parkour.interaction.StatsInteraction;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollision;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.Message;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.InventoryUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.parkour.command.CheckpointCommand;
import io.hyvexa.parkour.command.DiscordCommand;
import io.hyvexa.parkour.command.ParkourAdminCommand;
import io.hyvexa.parkour.command.ParkourAdminItemCommand;
import io.hyvexa.parkour.command.ParkourCommand;
import io.hyvexa.parkour.command.ParkourItemCommand;
import io.hyvexa.parkour.command.ParkourMusicDebugCommand;
import io.hyvexa.parkour.tracker.RunHud;
import io.hyvexa.parkour.tracker.RunRecordsHud;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.system.NoDropSystem;
import io.hyvexa.parkour.system.NoBreakSystem;
import io.hyvexa.parkour.system.NoPlayerKnockbackSystem;
import io.hyvexa.parkour.system.NoWeaponDamageSystem;
import io.hyvexa.parkour.system.PlayerVisibilityFilterSystem;
import io.hyvexa.parkour.ui.WelcomePage;
import io.hyvexa.parkour.util.ParkourUtils;
import io.hyvexa.parkour.visibility.PlayerVisibilityManager;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * This class serves as the entrypoint for your plugin. Use the setup method to register into game registries or add
 * event listeners.
 */
public class HyvexaPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String SERVER_IP_DISPLAY = "play.hyvexa.com";
    private static final int ANNOUNCEMENT_MAX_LINES = 3;
    private static final long ANNOUNCEMENT_DURATION_SECONDS = 10L;
    private static final long PLAYER_COUNT_SAMPLE_SECONDS = PlayerCountStore.DEFAULT_SAMPLE_INTERVAL_SECONDS;
    private static final long STALE_PLAYER_SWEEP_SECONDS = 120L;
    private static final long TELEPORT_DEBUG_INTERVAL_SECONDS = 120L;
    private static final String CHAT_LINK_PLACEHOLDER = "{link}";
    private static final String CHAT_LINK_LABEL = "click here";
    private static final String DISCORD_URL = "https://discord.gg/BDA7gRF5";
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
    private final ConcurrentHashMap<UUID, RunHud> runHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, RunRecordsHud> runRecordHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> runHudIsRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> runHudReadyAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> runHudWasRunning = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentLinkedDeque<Announcement>> announcements =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> playtimeSessionStart = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> cachedRankNames = new ConcurrentHashMap<>();
    private final AtomicInteger onlinePlayerCount = new AtomicInteger(0);
    private ScheduledFuture<?> mapDetectionTask;
    private ScheduledFuture<?> hudUpdateTask;
    private ScheduledFuture<?> playtimeTask;
    private ScheduledFuture<?> collisionTask;
    private ScheduledFuture<?> playerCountTask;
    private ScheduledFuture<?> stalePlayerSweepTask;
    private ScheduledFuture<?> teleportDebugTask;
    private ScheduledFuture<?> chatAnnouncementTask;
    private final Object chatAnnouncementLock = new Object();
    private List<Message> chatAnnouncements = List.of();
    private int chatAnnouncementIndex = 0;

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
        var folder = new File("Parkour");
        if (!folder.exists()) {
            folder.mkdirs();
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
        onlinePlayerCount.set(Universe.get().getPlayers().size());
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
        refreshChatAnnouncements();

        this.getCommandRegistry().registerCommand(new CheckpointCommand());
        this.getCommandRegistry().registerCommand(new DiscordCommand());
        this.getCommandRegistry().registerCommand(new ParkourCommand(this.mapStore, this.progressStore, this.settingsStore,
                this.playerCountStore, this.runTracker));
        this.getCommandRegistry().registerCommand(new ParkourAdminItemCommand());
        this.getCommandRegistry().registerCommand(new ParkourMusicDebugCommand());

        registerNoDropSystem();
        registerNoBreakSystem();
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
                onlinePlayerCount.incrementAndGet();
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
                ensureRunHud(event.getPlayerRef());
                startPlaytimeSession(event.getPlayerRef());
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in PlayerConnectEvent (hud/playtime): " + e.getMessage());
            }
        });
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            ensureRunHud(playerRef);
            startPlaytimeSession(playerRef);
        }

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            try {
                syncRunInventoryOnReady(event.getPlayerRef());
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
                broadcastPresence(event.getPlayerRef(), "joined");
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in PlayerConnectEvent (broadcast): " + e.getMessage());
            }
        });
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            try {
                if (event.getPlayerRef() != null) {
                    UUID playerId = event.getPlayerRef().getUuid();
                    // Clean up HUD tracking state
                    runHuds.remove(playerId);
                    runRecordHuds.remove(playerId);
                    runHudReadyAt.remove(playerId);
                    runHudWasRunning.remove(playerId);
                    runHudIsRecords.remove(playerId);
                    cachedRankNames.remove(playerId);
                    PlayerVisibilityManager.get().clearHidden(playerId);
                    // Clean up announcements
                    announcements.remove(playerId);
                    // Clean up run tracking state (active runs and idle fall detection)
                    runTracker.clearPlayer(playerId);
                    // Persist playtime before removing session
                    finishPlaytimeSession(event.getPlayerRef());
                }
                onlinePlayerCount.updateAndGet(current -> Math.max(0, current - 1));
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
        if (playerId != null) {
            cachedRankNames.remove(playerId);
        }
    }

    /**
     * Invalidates all cached rank names.
     * Call when total XP changes (maps added/removed/edited).
     */
    public void invalidateAllRankCaches() {
        cachedRankNames.clear();
    }

    /**
     * Called when MapStore changes. Invalidates caches that depend on map data.
     */
    private void onMapStoreChanged() {
        if (progressStore != null) {
            progressStore.invalidateTotalXpCache();
        }
        invalidateAllRankCaches();
    }

    public void broadcastAnnouncement(String message, PlayerRef sender) {
        if (message == null) {
            return;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        Message chatMessage = Message.raw("ADMIN MESSAGE: " + trimmed).color("#ff4d4d");
        if (sender != null) {
            queueAnnouncement(sender, trimmed);
        }
        for (PlayerRef target : Universe.get().getPlayers()) {
            if (sender != null && sender.equals(target)) {
                continue;
            }
            queueAnnouncement(target, trimmed);
            target.sendMessage(chatMessage);
        }
    }


    private void queueAnnouncement(PlayerRef playerRef, String message) {
        if (playerRef == null) {
            return;
        }
        Announcement entry = new Announcement(message);
        announcements.compute(playerRef.getUuid(), (key, queue) -> {
            ConcurrentLinkedDeque<Announcement> target = queue != null ? queue : new ConcurrentLinkedDeque<>();
            target.addLast(entry);
            return target;
        });
        updateAnnouncementHud(playerRef);
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            announcements.computeIfPresent(playerRef.getUuid(), (key, queue) -> {
                queue.remove(entry);
                return queue.isEmpty() ? null : queue;
            });
            updateAnnouncementHud(playerRef);
        }, ANNOUNCEMENT_DURATION_SECONDS, TimeUnit.SECONDS);
    }

    private void updateAnnouncementHud(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        var ref = playerRef.getReference();
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
            RunHud hud = getActiveHud(playerRef);
            if (hud == null) {
                hud = getOrCreateHud(playerRef, false);
                attachHud(playerRef, player, hud, false);
            }
            hud.updateAnnouncements(getAnnouncementLines(playerRef.getUuid()));
        }, world);
    }

    private List<String> getAnnouncementLines(UUID playerId) {
        Deque<Announcement> queue = announcements.get(playerId);
        if (queue == null) {
            return List.of();
        }
        ArrayDeque<String> lines = new ArrayDeque<>(ANNOUNCEMENT_MAX_LINES);
        for (Announcement entry : queue) {
            if (lines.size() == ANNOUNCEMENT_MAX_LINES) {
                lines.removeFirst();
            }
            lines.addLast(entry.message);
        }
        if (lines.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(lines);
    }

    private void tickPlaytime() {
        if (progressStore == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) {
                continue;
            }
            UUID playerId = playerRef.getUuid();
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                playtimeSessionStart.remove(playerId);
                continue;
            }
            long[] deltaMs = new long[1];
            playtimeSessionStart.compute(playerId, (key, start) -> {
                if (start == null) {
                    return now;
                }
                long delta = Math.max(0L, now - start);
                if (delta > 0L) {
                    deltaMs[0] = delta;
                }
                return now;
            });
            if (deltaMs[0] > 0L) {
                progressStore.addPlaytime(playerId, playerRef.getUsername(), deltaMs[0]);
            }
        }
    }

    private void startPlaytimeSession(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        playtimeSessionStart.putIfAbsent(playerRef.getUuid(), System.currentTimeMillis());
    }

    private void tickPlayerCounts() {
        if (playerCountStore == null) {
            return;
        }
        int count = onlinePlayerCount.get();
        playerCountStore.recordSample(System.currentTimeMillis(), count);
    }

    private void tickChatAnnouncements() {
        Message message;
        synchronized (chatAnnouncementLock) {
            if (chatAnnouncements.isEmpty()) {
                return;
            }
            if (chatAnnouncementIndex >= chatAnnouncements.size()) {
                chatAnnouncementIndex = 0;
            }
            message = chatAnnouncements.get(chatAnnouncementIndex);
            chatAnnouncementIndex = (chatAnnouncementIndex + 1) % chatAnnouncements.size();
        }
        var players = Universe.get().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        for (PlayerRef playerRef : players) {
            playerRef.sendMessage(message);
        }
    }

    private void tickStalePlayerSweep() {
        var players = Universe.get().getPlayers();
        if (players.isEmpty()) {
            sweepStalePlayerState(Set.of());
            return;
        }
        Set<UUID> onlinePlayers = new HashSet<>(players.size());
        for (PlayerRef playerRef : players) {
            if (playerRef == null) {
                continue;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId != null) {
                onlinePlayers.add(playerId);
            }
        }
        sweepStalePlayerState(onlinePlayers);
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

    private void sweepStalePlayerState(Set<UUID> onlinePlayers) {
        runHuds.keySet().removeIf(id -> !onlinePlayers.contains(id));
        runRecordHuds.keySet().removeIf(id -> !onlinePlayers.contains(id));
        runHudIsRecords.keySet().removeIf(id -> !onlinePlayers.contains(id));
        runHudReadyAt.keySet().removeIf(id -> !onlinePlayers.contains(id));
        runHudWasRunning.keySet().removeIf(id -> !onlinePlayers.contains(id));
        announcements.keySet().removeIf(id -> !onlinePlayers.contains(id));
        playtimeSessionStart.keySet().removeIf(id -> !onlinePlayers.contains(id));
        cachedRankNames.keySet().removeIf(id -> !onlinePlayers.contains(id));
        if (runTracker != null) {
            runTracker.sweepStalePlayers(onlinePlayers);
        }
        PlayerVisibilityManager.get().sweepStalePlayers(onlinePlayers);
    }

    public void refreshChatAnnouncements() {
        List<Message> rebuilt = buildChatAnnouncements();
        synchronized (chatAnnouncementLock) {
            chatAnnouncements = rebuilt;
            chatAnnouncementIndex = 0;
        }
        rescheduleChatAnnouncements();
    }

    private void rescheduleChatAnnouncements() {
        cancelScheduled(chatAnnouncementTask);
        chatAnnouncementTask = null;
        long intervalMinutes = globalMessageStore != null
                ? globalMessageStore.getIntervalMinutes()
                : GlobalMessageStore.DEFAULT_INTERVAL_MINUTES;
        long intervalSeconds = Math.max(60L, intervalMinutes * 60L);
        if (!chatAnnouncements.isEmpty()) {
            chatAnnouncementTask = scheduleTick("chat announcements", this::tickChatAnnouncements, 60,
                    intervalSeconds, TimeUnit.SECONDS);
        }
    }

    private List<Message> buildChatAnnouncements() {
        if (globalMessageStore == null) {
            return List.of();
        }
        List<String> messages = globalMessageStore.getMessages();
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Message> built = new ArrayList<>();
        for (String message : messages) {
            Message formatted = buildChatAnnouncementMessage(message);
            if (formatted != null) {
                built.add(formatted);
            }
        }
        return List.copyOf(built);
    }

    private Message buildChatAnnouncementMessage(String template) {
        if (template == null) {
            return null;
        }
        String trimmed = template.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Message link = Message.raw(CHAT_LINK_LABEL).color("#8ab4f8").link(DISCORD_URL);
        if (!trimmed.contains(CHAT_LINK_PLACEHOLDER)) {
            return Message.join(
                    Message.raw(trimmed),
                    Message.raw(" ("),
                    link,
                    Message.raw(").")
            );
        }
        List<Message> parts = new ArrayList<>();
        int index = 0;
        while (index < trimmed.length()) {
            int next = trimmed.indexOf(CHAT_LINK_PLACEHOLDER, index);
            if (next < 0) {
                String tail = trimmed.substring(index);
                if (!tail.isEmpty()) {
                    parts.add(Message.raw(tail));
                }
                break;
            }
            if (next > index) {
                parts.add(Message.raw(trimmed.substring(index, next)));
            }
            parts.add(link);
            index = next + CHAT_LINK_PLACEHOLDER.length();
        }
        if (parts.isEmpty()) {
            return link;
        }
        return Message.join(parts.toArray(new Message[0]));
    }

    private void finishPlaytimeSession(PlayerRef playerRef) {
        if (playerRef == null || progressStore == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        Long start = playtimeSessionStart.remove(playerId);
        if (start == null) {
            return;
        }
        long deltaMs = Math.max(0L, System.currentTimeMillis() - start);
        if (deltaMs <= 0L) {
            return;
        }
        progressStore.addPlaytime(playerId, playerRef.getUsername(), deltaMs);
    }

    private void broadcastPresence(PlayerRef playerRef, String verb) {
        if (playerRef == null) {
            return;
        }
        String name = playerRef.getUsername();
        if (name == null || name.isBlank()) {
            name = ParkourUtils.resolveName(playerRef.getUuid(), progressStore);
        }
        String rank = progressStore != null ? progressStore.getRankName(playerRef.getUuid(), mapStore) : "Unranked";
        Message rankPart = FormatUtils.getRankMessage(rank);
        Message message = Message.join(
                Message.raw("["),
                rankPart,
                Message.raw("] "),
                Message.raw(name),
                Message.raw(" " + verb + " the server!")
        );
        for (PlayerRef target : Universe.get().getPlayers()) {
            target.sendMessage(message);
        }
    }

    private static class Announcement {
        private final String message;

        private Announcement(String message) {
            this.message = message;
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

    public SettingsStore getSettingsStore() {
        return settingsStore;
    }

    private void tickMapDetection() {
        Map<World, List<PlayerTickContext>> playersByWorld = collectPlayersByWorld();
        for (Map.Entry<World, List<PlayerTickContext>> entry : playersByWorld.entrySet()) {
            World world = entry.getKey();
            List<PlayerTickContext> players = entry.getValue();
            CompletableFuture.runAsync(() -> {
                for (PlayerTickContext context : players) {
                    if (context.ref == null || !context.ref.isValid()) {
                        continue;
                    }
                    runTracker.checkPlayer(context.ref, context.store);
                    ensureRunHudNow(context.ref, context.store, context.playerRef);
                    updateRunHud(context.ref, context.store);
                }
            }, world);
        }
    }

    private void tickHudUpdates() {
        Map<World, List<PlayerTickContext>> playersByWorld = collectPlayersByWorld();
        for (Map.Entry<World, List<PlayerTickContext>> entry : playersByWorld.entrySet()) {
            World world = entry.getKey();
            List<PlayerTickContext> players = entry.getValue();
            CompletableFuture.runAsync(() -> {
                for (PlayerTickContext context : players) {
                    if (context.ref == null || !context.ref.isValid()) {
                        continue;
                    }
                    ensureRunHudNow(context.ref, context.store, context.playerRef);
                    updateRunHud(context.ref, context.store);
                }
            }, world);
        }
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

    private void updateRunHud(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        Long elapsedMs = runTracker.getElapsedTimeMs(playerRef.getUuid());
        RunHud hud = getActiveHud(playerRef);
        long readyAt = runHudReadyAt.getOrDefault(playerRef.getUuid(), Long.MAX_VALUE);
        if (hud == null || System.currentTimeMillis() < readyAt) {
            return;
        }
        boolean running = elapsedMs != null;
        boolean wasRunning = runHudWasRunning.getOrDefault(playerRef.getUuid(), false);
        if (!running && wasRunning) {
            RunHud baseHud = getOrCreateHud(playerRef, false);
            attachHud(playerRef, player, baseHud, false);
            hud = baseHud;
        } else if (running && !Boolean.TRUE.equals(runHudIsRecords.get(playerRef.getUuid()))) {
            RunHud recordsHud = getOrCreateHud(playerRef, true);
            attachHud(playerRef, player, recordsHud, true);
            hud = recordsHud;
        }
        int completedMaps = progressStore.getCompletedMapCount(playerRef.getUuid());
        int totalMaps = mapStore.getMapCount();
        String rankName = cachedRankNames.computeIfAbsent(playerRef.getUuid(),
                id -> progressStore.getRankName(id, mapStore));
        hud.updateInfo(playerRef.getUsername(), rankName, completedMaps, totalMaps, SERVER_IP_DISPLAY);
        if (!running) {
            if (wasRunning) {
                hud.updateText("");
                hud.updateCheckpointText("");
                runHudWasRunning.put(playerRef.getUuid(), false);
            }
            if (hud instanceof RunRecordsHud recordsHud) {
                recordsHud.updateTopTimes(List.of());
            }
            return;
        }
        runHudWasRunning.put(playerRef.getUuid(), true);
        String mapId = runTracker.getActiveMapId(playerRef.getUuid());
        String mapName = mapId;
        if (mapId != null) {
            var map = mapStore.getMap(mapId);
            if (map != null && map.getName() != null && !map.getName().isBlank()) {
                mapName = map.getName();
            }
        }
        String timeText = (mapName == null ? "Map" : mapName) + " - " + FormatUtils.formatDuration(elapsedMs);
        RunTracker.CheckpointProgress checkpointProgress = runTracker.getCheckpointProgress(playerRef.getUuid());
        String checkpointText = "";
        if (checkpointProgress != null && checkpointProgress.total > 0) {
            checkpointText = checkpointProgress.touched + "/" + checkpointProgress.total;
        }
        if (hud instanceof RunRecordsHud recordsHud) {
            recordsHud.updateRunDetails(timeText, buildTopTimes(mapId, playerRef.getUuid()));
        } else {
            hud.updateText(timeText);
        }
        hud.updateCheckpointText(checkpointText);
    }

    private List<RunRecordsHud.RecordLine> buildTopTimes(String mapId, UUID playerId) {
        if (mapId == null) {
            return List.of();
        }
        List<Map.Entry<UUID, Long>> entries = progressStore.getLeaderboardEntries(mapId);
        if (entries.isEmpty()) {
            return List.of();
        }
        List<RunRecordsHud.RecordLine> lines = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if (i < entries.size()) {
                UUID entryPlayerId = entries.get(i).getKey();
                Long time = entries.get(i).getValue();
                String name = progressStore.getPlayerName(entryPlayerId);
                if (name == null || name.isBlank()) {
                    name = "Player";
                }
                String trimmed = trimName(name, 14);
                lines.add(new RunRecordsHud.RecordLine(String.valueOf(i + 1), trimmed,
                        FormatUtils.formatDuration(time)));
            } else {
                lines.add(RunRecordsHud.RecordLine.empty(i + 1));
            }
        }
        int selfIndex = -1;
        long selfTime = 0L;
        if (progressStore.getBestTimeMs(playerId, mapId) != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).getKey().equals(playerId)) {
                    selfIndex = i;
                    selfTime = entries.get(i).getValue();
                    break;
                }
            }
        }
        if (selfIndex >= 0) {
            String name = progressStore.getPlayerName(playerId);
            if (name == null || name.isBlank()) {
                name = "Player";
            }
            String trimmed = trimName(name, 14);
            lines.add(new RunRecordsHud.RecordLine(String.valueOf(selfIndex + 1), trimmed,
                    FormatUtils.formatDuration(selfTime)));
        } else {
            lines.add(RunRecordsHud.RecordLine.empty(0));
        }
        return lines;
    }

    private static String trimName(String name, int maxLength) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        if (maxLength <= 3) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed.substring(0, maxLength - 3) + "...";
    }

    private void ensureRunHud(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        runHuds.remove(playerId);
        runHudReadyAt.remove(playerId);
        runHudWasRunning.remove(playerId);
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> ensureRunHudNow(ref, store, playerRef), world);
    }

    private void ensureRunHudNow(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        RunHud hud = getOrCreateHud(playerRef, false);
        attachHud(playerRef, player, hud, false);
        hud.updateAnnouncements(getAnnouncementLines(playerRef.getUuid()));
        runHudReadyAt.putIfAbsent(playerRef.getUuid(), System.currentTimeMillis() + 250L);
    }

    private RunHud getActiveHud(PlayerRef playerRef) {
        if (playerRef == null) {
            return null;
        }
        UUID playerId = playerRef.getUuid();
        if (Boolean.TRUE.equals(runHudIsRecords.get(playerId))) {
            return runRecordHuds.get(playerId);
        }
        return runHuds.get(playerId);
    }

    private RunHud getOrCreateHud(PlayerRef playerRef, boolean records) {
        UUID playerId = playerRef.getUuid();
        if (records) {
            return runRecordHuds.computeIfAbsent(playerId, ignored -> new RunRecordsHud(playerRef));
        }
        return runHuds.computeIfAbsent(playerId, ignored -> new RunHud(playerRef));
    }

    private void attachHud(PlayerRef playerRef, Player player, RunHud hud, boolean records) {
        runHudIsRecords.put(playerRef.getUuid(), records);
        player.getHudManager().setCustomHud(playerRef, hud);
        hud.resetCache();
        hud.show();
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
            String activeMap = runTracker.getActiveMapId(playerRef.getUuid());
            if (activeMap == null) {
                InventoryUtils.giveMenuItems(player);
                return;
            }
            InventoryUtils.giveRunItems(player, mapStore != null ? mapStore.getMap(activeMap) : null);
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

    /**
     * Registers systems that depend on module singletons (EntityTrackerSystems, DamageModule).
     * Must be called after plugin setup completes to avoid deadlock during initialization.
     */
    private void registerDeferredSystems() {
        registerPlayerVisibilitySystem();
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
        registry.register("Parkour_Restart_Checkpoint_Interaction",
                RestartCheckpointInteraction.class, RestartCheckpointInteraction.CODEC);
        registry.register("Parkour_Leave_Interaction", LeaveInteraction.class, LeaveInteraction.CODEC);
        registry.register("Parkour_PlayerSettings_Interaction",
                PlayerSettingsInteraction.class, PlayerSettingsInteraction.CODEC);
        registry.register("Media_RemoteControl",
                PlayerSettingsInteraction.class, PlayerSettingsInteraction.CODEC);
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
        cancelScheduled(chatAnnouncementTask);
        if (progressStore != null) {
            progressStore.flushPendingSave();
        }
        super.shutdown();
    }

}
