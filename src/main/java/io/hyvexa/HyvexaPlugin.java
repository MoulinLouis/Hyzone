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
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.PlayerCountStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import io.hyvexa.parkour.interaction.MenuInteraction;
import io.hyvexa.parkour.interaction.LeaderboardInteraction;
import io.hyvexa.parkour.interaction.LeaveInteraction;
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
import io.hyvexa.parkour.command.ParkourAdminCommand;
import io.hyvexa.parkour.command.ParkourAdminItemCommand;
import io.hyvexa.parkour.command.ParkourCommand;
import io.hyvexa.parkour.command.ParkourItemCommand;
import io.hyvexa.parkour.command.ParkourMusicDebugCommand;
import io.hyvexa.parkour.tracker.RunHud;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.system.NoDropSystem;
import io.hyvexa.parkour.system.NoBreakSystem;
import io.hyvexa.parkour.ui.WelcomePage;
import io.hyvexa.parkour.util.ParkourUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class serves as the entrypoint for your plugin. Use the setup method to register into game registries or add
 * event listeners.
 */
public class HyvexaPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String SERVER_IP_DISPLAY = "play.hyvexa.com";
    private static final int ANNOUNCEMENT_MAX_LINES = 3;
    private static final long ANNOUNCEMENT_DURATION_SECONDS = 10L;
    private static final long WELCOME_AUTO_CLOSE_SECONDS = 8L;
    private static final long PLAYER_COUNT_SAMPLE_SECONDS = PlayerCountStore.DEFAULT_SAMPLE_INTERVAL_SECONDS;
    private static HyvexaPlugin INSTANCE;
    private MapStore mapStore;
    private RunTracker runTracker;
    private ProgressStore progressStore;
    private SettingsStore settingsStore;
    private PlayerCountStore playerCountStore;
    private final ConcurrentHashMap<UUID, RunHud> runHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> runHudReadyAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> runHudWasRunning = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Deque<Announcement>> announcements = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> playtimeSessionStart = new ConcurrentHashMap<>();
    private final AtomicInteger onlinePlayerCount = new AtomicInteger(0);

    public HyvexaPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
        LOGGER.atInfo().log("Hello from " + this.getName() + " version " + this.getManifest().getVersion().toString());
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
        this.settingsStore = new SettingsStore();
        this.settingsStore.syncLoad();
        this.progressStore = new ProgressStore();
        this.progressStore.syncLoad();
        this.playerCountStore = new PlayerCountStore();
        this.playerCountStore.syncLoad();
        this.runTracker = new RunTracker(this.mapStore, this.progressStore, this.settingsStore);
        onlinePlayerCount.set(Universe.get().getPlayers().size());
        HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::tickMapDetection, 200, 200, TimeUnit.MILLISECONDS);
        HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::tickHudUpdates, 20, 20, TimeUnit.MILLISECONDS);
        HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::tickPlaytime, 60, 60, TimeUnit.SECONDS);
        HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::tickCollisionRemoval, 1, 2, TimeUnit.SECONDS);
        HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::tickPlayerCounts, 5,
                PLAYER_COUNT_SAMPLE_SECONDS, TimeUnit.SECONDS);

        this.getCommandRegistry().registerCommand(new CheckpointCommand());
        this.getCommandRegistry().registerCommand(new ParkourCommand(this.mapStore, this.progressStore, this.runTracker));
        this.getCommandRegistry().registerCommand(new ParkourItemCommand());
        this.getCommandRegistry().registerCommand(new ParkourAdminCommand(this.mapStore, this.progressStore, this.settingsStore));
        this.getCommandRegistry().registerCommand(new ParkourAdminItemCommand());
        this.getCommandRegistry().registerCommand(new ParkourMusicDebugCommand());

        this.getCodecRegistry(Interaction.CODEC).register("Parkour_Menu_Interaction", MenuInteraction.class, MenuInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("Parkour_Leaderboard_Interaction", LeaderboardInteraction.class, LeaderboardInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("Parkour_Stats_Interaction", StatsInteraction.class, StatsInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("Parkour_Reset_Interaction", ResetInteraction.class, ResetInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("Parkour_Restart_Checkpoint_Interaction",
                RestartCheckpointInteraction.class, RestartCheckpointInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("Parkour_Leave_Interaction", LeaveInteraction.class, LeaveInteraction.CODEC);

        registerNoDropSystem();
        registerNoBreakSystem();

        this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> disablePlayerCollision(event.getPlayerRef()));
        this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> onlinePlayerCount.incrementAndGet());
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> disablePlayerCollision(event.getPlayerRef()));
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            disablePlayerCollision(playerRef);
        }

        this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> {
            ensureRunHud(event.getPlayerRef());
            startPlaytimeSession(event.getPlayerRef());
        });
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            ensureRunHud(playerRef);
            startPlaytimeSession(playerRef);
        }

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> syncRunInventoryOnReady(event.getPlayerRef()));
        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> event.setBroadcastJoinMessage(false));
        this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event ->
                broadcastPresence(event.getPlayerRef(), "joined"));
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            if (event.getPlayerRef() != null) {
                announcements.remove(event.getPlayerRef().getUuid());
                finishPlaytimeSession(event.getPlayerRef());
            }
            onlinePlayerCount.updateAndGet(current -> Math.max(0, current - 1));
        });
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            syncRunInventoryOnConnect(playerRef);
        }

        this.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                updateDropProtection(player);
            }
        });

        this.getEventRegistry().registerGlobal(PlayerChatEvent.class, event -> {
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
                Message rankPart = Message.raw(rank).color(FormatUtils.getRankColor(rank));
                return Message.join(
                        Message.raw("["),
                        rankPart,
                        Message.raw("] "),
                        Message.raw(name),
                        Message.raw(": "),
                        Message.raw(safeContent)
                );
            });
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
        Deque<Announcement> queue = announcements.computeIfAbsent(playerRef.getUuid(), key -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(entry);
        }
        updateAnnouncementHud(playerRef);
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            Deque<Announcement> currentQueue = announcements.get(playerRef.getUuid());
            if (currentQueue == null) {
                return;
            }
            synchronized (currentQueue) {
                currentQueue.remove(entry);
                if (currentQueue.isEmpty()) {
                    announcements.remove(playerRef.getUuid(), currentQueue);
                }
            }
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
            RunHud hud = runHuds.get(playerRef.getUuid());
            if (hud == null) {
                hud = new RunHud(playerRef);
                runHuds.put(playerRef.getUuid(), hud);
                runHudWasRunning.put(playerRef.getUuid(), false);
                player.getHudManager().setCustomHud(playerRef, hud);
                hud.show();
            }
            hud.updateAnnouncements(getAnnouncementLines(playerRef.getUuid()));
        }, world);
    }

    private List<String> getAnnouncementLines(UUID playerId) {
        Deque<Announcement> queue = announcements.get(playerId);
        if (queue == null) {
            return List.of();
        }
        ArrayList<String> lines = new ArrayList<>(ANNOUNCEMENT_MAX_LINES);
        synchronized (queue) {
            if (queue.isEmpty()) {
                return List.of();
            }
            int startIndex = Math.max(0, queue.size() - ANNOUNCEMENT_MAX_LINES);
            int index = 0;
            for (Announcement entry : queue) {
                if (index++ < startIndex) {
                    continue;
                }
                lines.add(entry.message);
            }
        }
        return lines;
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
            Long start = playtimeSessionStart.get(playerId);
            if (start == null) {
                playtimeSessionStart.put(playerId, now);
                continue;
            }
            long deltaMs = Math.max(0L, now - start);
            if (deltaMs > 0L) {
                progressStore.addPlaytime(playerId, playerRef.getUsername(), deltaMs);
                playtimeSessionStart.put(playerId, now);
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
        Message rankPart = Message.raw(rank).color(FormatUtils.getRankColor(rank));
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

    public RunTracker getRunTracker() {
        return runTracker;
    }

    public SettingsStore getSettingsStore() {
        return settingsStore;
    }

    private void tickMapDetection() {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            if (world == null) {
                continue;
            }
            CompletableFuture.runAsync(() -> {
                runTracker.checkPlayer(ref, store);
                ensureRunHudNow(ref, store, playerRef);
                updateRunHud(ref, store);
            }, world);
        }
    }

    private void tickHudUpdates() {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            if (world == null) {
                continue;
            }
            CompletableFuture.runAsync(() -> {
                ensureRunHudNow(ref, store, playerRef);
                updateRunHud(ref, store);
            }, world);
        }
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
        RunHud hud = runHuds.get(playerRef.getUuid());
        long readyAt = runHudReadyAt.getOrDefault(playerRef.getUuid(), Long.MAX_VALUE);
        if (hud == null || System.currentTimeMillis() < readyAt) {
            return;
        }
        boolean running = elapsedMs != null;
        boolean wasRunning = runHudWasRunning.getOrDefault(playerRef.getUuid(), false);
        int completedMaps = progressStore.getCompletedMapCount(playerRef.getUuid());
        int totalMaps = mapStore.listMaps().size();
        String rankName = progressStore.getRankName(playerRef.getUuid(), mapStore);
        hud.updateInfo(playerRef.getUsername(), rankName, completedMaps, totalMaps, SERVER_IP_DISPLAY);
        if (!running) {
            if (wasRunning) {
                hud.updateText("");
                runHudWasRunning.put(playerRef.getUuid(), false);
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
        hud.updateText(timeText);
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
        RunHud hud = runHuds.get(playerRef.getUuid());
        if (hud == null) {
            hud = new RunHud(playerRef);
            runHuds.put(playerRef.getUuid(), hud);
            runHudWasRunning.put(playerRef.getUuid(), false);
        }
        player.getHudManager().setCustomHud(playerRef, hud);
        hud.show();
        hud.updateAnnouncements(getAnnouncementLines(playerRef.getUuid()));
        runHudReadyAt.putIfAbsent(playerRef.getUuid(), System.currentTimeMillis() + 250L);
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

    private void syncRunInventoryOnReady(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        syncRunInventoryOnConnect(ref, store, playerRef);
        showWelcomeIfFirstJoin(ref, store, playerRef);
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
                scheduleInventoryRetry(playerRef, ref, store, false);
                return;
            }
            InventoryUtils.giveRunItems(player);
            scheduleInventoryRetry(playerRef, ref, store, true);
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
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                World closeWorld = store.getExternalData().getWorld();
                if (closeWorld == null) {
                    return;
                }
                CompletableFuture.runAsync(page::requestClose, closeWorld);
            }, WELCOME_AUTO_CLOSE_SECONDS, TimeUnit.SECONDS);
        }, world);
    }

    private void scheduleInventoryRetry(PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store,
                                        boolean runItems) {
        AtomicInteger attemptsLeft = new AtomicInteger(12);
        ScheduledFuture<?>[] handle = new ScheduledFuture<?>[1];
        handle[0] = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            World world = store.getExternalData().getWorld();
            if (world == null) {
                cancelScheduled(handle[0]);
                return;
            }
            CompletableFuture.runAsync(() -> {
                if (attemptsLeft.decrementAndGet() < 0) {
                    cancelScheduled(handle[0]);
                    return;
                }
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }
                boolean hasActiveMap = runTracker.getActiveMapId(playerRef.getUuid()) != null;
                if (runItems) {
                    if (!hasActiveMap) {
                        cancelScheduled(handle[0]);
                        return;
                    }
                    InventoryUtils.giveRunItems(player);
                    return;
                }
                if (hasActiveMap) {
                    cancelScheduled(handle[0]);
                    return;
                }
                InventoryUtils.giveMenuItems(player);
            }, world);
        }, 250L, 500L, TimeUnit.MILLISECONDS);
    }

    private void cancelScheduled(ScheduledFuture<?> handle) {
        if (handle == null) {
            return;
        }
        handle.cancel(false);
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

}
