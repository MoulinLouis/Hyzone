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
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import io.hyvexa.parkour.interaction.MenuInteraction;
import io.hyvexa.parkour.interaction.LeaderboardInteraction;
import io.hyvexa.parkour.interaction.LeaveInteraction;
import io.hyvexa.parkour.interaction.ResetInteraction;
import io.hyvexa.parkour.interaction.StatsInteraction;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
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
import io.hyvexa.parkour.command.ParkourCommand;
import io.hyvexa.parkour.command.ParkourItemCommand;
import io.hyvexa.parkour.command.ParkourMusicDebugCommand;
import io.hyvexa.parkour.tracker.RunHud;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.system.NoDropSystem;
import io.hyvexa.parkour.system.NoBreakSystem;

import javax.annotation.Nonnull;
import java.io.File;
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
    private static final String SERVER_IP_DISPLAY = "play.hyvexa.com:5500";
    private static HyvexaPlugin INSTANCE;
    private MapStore mapStore;
    private RunTracker runTracker;
    private ProgressStore progressStore;
    private SettingsStore settingsStore;
    private final ConcurrentHashMap<UUID, RunHud> runHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> runHudReadyAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> runHudWasRunning = new ConcurrentHashMap<>();

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
        this.runTracker = new RunTracker(this.mapStore, this.progressStore, this.settingsStore);
        HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::tickMapDetection, 200, 200, TimeUnit.MILLISECONDS);
        HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::tickHudUpdates, 20, 20, TimeUnit.MILLISECONDS);

        this.getCommandRegistry().registerCommand(new CheckpointCommand());
        this.getCommandRegistry().registerCommand(new ParkourCommand(this.mapStore, this.progressStore, this.runTracker));
        this.getCommandRegistry().registerCommand(new ParkourItemCommand());
        this.getCommandRegistry().registerCommand(new ParkourAdminCommand(this.mapStore, this.progressStore, this.settingsStore));
        this.getCommandRegistry().registerCommand(new ParkourMusicDebugCommand());

        this.getCodecRegistry(Interaction.CODEC).register("Parkour_Menu_Interaction", MenuInteraction.class, MenuInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("Parkour_Leaderboard_Interaction", LeaderboardInteraction.class, LeaderboardInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("Parkour_Stats_Interaction", StatsInteraction.class, StatsInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("Parkour_Reset_Interaction", ResetInteraction.class, ResetInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("Parkour_Leave_Interaction", LeaveInteraction.class, LeaveInteraction.CODEC);

        registerNoDropSystem();
        registerNoBreakSystem();

        this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> disablePlayerCollision(event.getPlayerRef()));
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            disablePlayerCollision(playerRef);
        }

        this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> ensureRunHud(event.getPlayerRef()));
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            ensureRunHud(playerRef);
        }

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> syncRunInventoryOnReady(event.getPlayerRef()));
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
                    return Message.raw(content);
                }
                String rank = progressStore != null ? progressStore.getRankName(sender.getUuid(), mapStore) : "Unranked";
                String name = sender.getUsername();
                Message rankPart = Message.raw(rank).color(FormatUtils.getRankColor(rank));
                return Message.join(
                        Message.raw("["),
                        rankPart,
                        Message.raw("] "),
                        Message.raw(name),
                        Message.raw(": "),
                        Message.raw(content)
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


    private void disablePlayerCollision(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        store.tryRemoveComponent(ref, HitboxCollision.getComponentType());
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
