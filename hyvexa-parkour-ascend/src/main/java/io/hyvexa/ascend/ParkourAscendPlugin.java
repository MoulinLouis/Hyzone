package io.hyvexa.ascend;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.command.AscendCommand;
import io.hyvexa.ascend.command.AscendAdminCommand;
import io.hyvexa.ascend.data.AscendDatabaseSetup;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.ghost.GhostStore;
import io.hyvexa.ascend.ghost.GhostRecorder;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.holo.AscendHologramManager;
import io.hyvexa.ascend.hud.AscendHud;
import io.hyvexa.ascend.interaction.AscendDevCinderclothInteraction;
import io.hyvexa.ascend.interaction.AscendDevCottonInteraction;
import io.hyvexa.ascend.interaction.AscendDevStormsilkInteraction;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import io.hyvexa.common.util.HylogramsBridge;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class ParkourAscendPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String ASCEND_WORLD_NAME = "Ascend";
    private static ParkourAscendPlugin INSTANCE;

    private AscendMapStore mapStore;
    private AscendPlayerStore playerStore;
    private GhostStore ghostStore;
    private GhostRecorder ghostRecorder;
    private AscendRunTracker runTracker;
    private RobotManager robotManager;
    private AscendHologramManager hologramManager;
    private SummitManager summitManager;
    private AscensionManager ascensionManager;
    private AchievementManager achievementManager;
    private ScheduledFuture<?> runTrackerTask;
    private final ConcurrentHashMap<UUID, AscendHud> ascendHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> ascendHudAttached = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> ascendHudReadyAt = new ConcurrentHashMap<>();

    public ParkourAscendPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
        LOGGER.atInfo().log("Hello from " + this.getName() + " version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up Ascend plugin...");

        AscendDatabaseSetup.ensureTables();

        mapStore = new AscendMapStore();
        mapStore.syncLoad();

        playerStore = new AscendPlayerStore();
        playerStore.syncLoad();

        // Initialize ghost system
        ghostStore = new GhostStore();
        ghostStore.syncLoad();

        ghostRecorder = new GhostRecorder(ghostStore);
        ghostRecorder.start();

        // Pass ghost dependencies to managers
        runTracker = new AscendRunTracker(mapStore, playerStore, ghostRecorder);

        robotManager = new RobotManager(mapStore, playerStore, ghostStore);
        robotManager.start();

        summitManager = new SummitManager(playerStore);
        ascensionManager = new AscensionManager(playerStore);
        achievementManager = new AchievementManager(playerStore, mapStore);

        if (HylogramsBridge.isAvailable()) {
            hologramManager = new AscendHologramManager();
            if (mapStore != null) {
                for (var map : mapStore.listMaps()) {
                    hologramManager.refreshMapHolosIfPresent(map, null);
                }
            }
        }

        getCommandRegistry().registerCommand(new AscendCommand());
        getCommandRegistry().registerCommand(new AscendAdminCommand());
        registerInteractionCodecs();

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
                // Register player as online for robot spawning
                UUID playerId = playerRef.getUuid();
                if (playerId != null && robotManager != null) {
                    robotManager.onPlayerJoin(playerId);
                }
                // Reset session-specific tracking
                if (playerId != null && playerStore != null) {
                    playerStore.setSessionFirstRunClaimed(playerId, false);
                    playerStore.resetConsecutiveManualRuns(playerId);
                }
                CompletableFuture.runAsync(() -> {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player == null) {
                        return;
                    }
                    resetAscendInventory(player);
                    ensureAscendItems(player);
                    attachAscendHud(playerRef, player);
                }, world);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in PlayerReadyEvent (ascend): " + e.getMessage());
            }
        });

        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            try {
                World world = event.getWorld();
                if (world == null || !isAscendWorld(world)) {
                    return;
                }
                var holder = event.getHolder();
                if (holder == null) {
                    return;
                }
                Player player = holder.getComponent(Player.getComponentType());
                if (player == null) {
                    return;
                }
                ensureAscendItems(player);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Exception in AddPlayerToWorldEvent (ascend): " + e.getMessage());
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
            ascendHuds.remove(playerId);
            ascendHudAttached.remove(playerId);
            ascendHudReadyAt.remove(playerId);
            if (runTracker != null) {
                runTracker.cancelRun(playerId);
            }
            if (robotManager != null) {
                robotManager.onPlayerLeave(playerId);
            }
        });

        LOGGER.atInfo().log("Ascend plugin setup complete");

        runTrackerTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
            this::tickRunTracker,
            200, 200, TimeUnit.MILLISECONDS
        );
    }

    @Override
    protected void shutdown() {
        LOGGER.atInfo().log("Shutting down Ascend plugin...");

        if (runTrackerTask != null) {
            runTrackerTask.cancel(false);
            runTrackerTask = null;
        }
        if (ghostRecorder != null) {
            ghostRecorder.stop();
        }
        if (robotManager != null) {
            robotManager.stop();
        }
        if (playerStore != null) {
            playerStore.flushPendingSave();
        }
        LOGGER.atInfo().log("Ascend plugin shutdown complete");
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

    public GhostStore getGhostStore() {
        return ghostStore;
    }

    public AscendRunTracker getRunTracker() {
        return runTracker;
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

    public AchievementManager getAchievementManager() {
        return achievementManager;
    }

    private void tickRunTracker() {
        if (runTracker == null) {
            return;
        }
        Map<World, List<PlayerTickContext>> playersByWorld = collectPlayersByWorld();
        for (Map.Entry<World, List<PlayerTickContext>> entry : playersByWorld.entrySet()) {
            World world = entry.getKey();
            if (!isAscendWorld(world)) {
                continue;
            }
            List<PlayerTickContext> players = entry.getValue();
            CompletableFuture.runAsync(() -> {
                for (PlayerTickContext context : players) {
                    if (context.ref == null || !context.ref.isValid()) {
                        continue;
                    }
                    runTracker.checkPlayer(context.ref, context.store);
                    updateAscendHud(context);
                }
            }, world);
        }
    }

    private void updateAscendHud(PlayerTickContext context) {
        Player player = context.store.getComponent(context.ref, Player.getComponentType());
        if (player == null || context.playerRef == null) {
            return;
        }
        UUID playerId = context.playerRef.getUuid();
        AscendHud hud = ascendHuds.get(playerId);
        boolean needsAttach = !Boolean.TRUE.equals(ascendHudAttached.get(playerId));
        if (needsAttach || hud == null) {
            attachAscendHud(context.playerRef, player);
            return;
        }
        // Always ensure HUD is set on player (in case they came from another world)
        player.getHudManager().setCustomHud(context.playerRef, hud);
        long readyAt = ascendHudReadyAt.getOrDefault(playerId, Long.MAX_VALUE);
        if (System.currentTimeMillis() < readyAt) {
            return;
        }
        hud.applyStaticText();
        if (playerStore != null) {
            long coins = playerStore.getCoins(playerId);
            List<AscendMap> maps = mapStore != null ? mapStore.listMapsSorted() : List.of();
            long product = playerStore.getMultiplierProduct(playerId, maps, AscendConstants.MULTIPLIER_SLOTS);
            double[] digits = playerStore.getMultiplierDisplayValues(playerId, maps, AscendConstants.MULTIPLIER_SLOTS);
            int elevationMultiplier = playerStore.getElevationMultiplier(playerId);
            boolean showElevation = coins >= 1000L;
            hud.updateEconomy(coins, product, digits, elevationMultiplier, showElevation);

            // Update prestige HUD
            var summitLevels = playerStore.getSummitLevels(playerId);
            int ascensionCount = playerStore.getAscensionCount(playerId);
            int skillPoints = playerStore.getAvailableSkillPoints(playerId);
            hud.updatePrestige(summitLevels, ascensionCount, skillPoints);
        }
    }

    private Map<World, List<PlayerTickContext>> collectPlayersByWorld() {
        Map<World, List<PlayerTickContext>> playersByWorld = new HashMap<>();
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
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

    private void attachAscendHud(PlayerRef playerRef, Player player) {
        if (playerRef == null || player == null) {
            return;
        }
        AscendHud hud = ascendHuds.computeIfAbsent(playerRef.getUuid(), id -> new AscendHud(playerRef));
        player.getHudManager().setCustomHud(playerRef, hud);
        player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass);
        hud.resetCache();
        hud.show();
        hud.applyStaticText();
        ascendHudAttached.put(playerRef.getUuid(), true);
        ascendHudReadyAt.put(playerRef.getUuid(), System.currentTimeMillis() + 250L);
    }

    private boolean isAscendWorld(World world) {
        if (world == null || world.getName() == null) {
            return false;
        }
        return ASCEND_WORLD_NAME.equalsIgnoreCase(world.getName());
    }


    private static void resetAscendInventory(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        clearContainer(inventory.getHotbar());
        clearContainer(inventory.getStorage());
        clearContainer(inventory.getBackpack());
        clearContainer(inventory.getTools());
        clearContainer(inventory.getUtility());
        clearContainer(inventory.getArmor());
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) {
            return;
        }
        if (hotbar.getCapacity() <= 0) {
            return;
        }
        hotbar.setItemStackForSlot((short) 0, new ItemStack(AscendConstants.ITEM_DEV_CINDERCLOTH, 1), false);
        hotbar.setItemStackForSlot((short) 1, new ItemStack(AscendConstants.ITEM_DEV_STORMSILK, 1), false);
        hotbar.setItemStackForSlot((short) 2, new ItemStack(AscendConstants.ITEM_DEV_COTTON, 1), false);
        short slot = (short) (hotbar.getCapacity() - 1);
        hotbar.setItemStackForSlot(slot, new ItemStack("Hub_Server_Selector", 1), false);
    }

    private static void ensureAscendItems(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        ItemContainer hotbar = inventory.getHotbar();
        short capacity = hotbar.getCapacity();
        if (capacity <= 0) {
            return;
        }
        setIfMissing(hotbar, (short) 0, AscendConstants.ITEM_DEV_CINDERCLOTH);
        setIfMissing(hotbar, (short) 1, AscendConstants.ITEM_DEV_STORMSILK);
        setIfMissing(hotbar, (short) 2, AscendConstants.ITEM_DEV_COTTON);
        short slot = (short) (capacity - 1);
        setIfMissing(hotbar, slot, "Hub_Server_Selector");
    }

    private static void setIfMissing(ItemContainer hotbar, short slot, String itemId) {
        if (hotbar == null || itemId == null || itemId.isBlank()) {
            return;
        }
        if (slot < 0 || slot >= hotbar.getCapacity()) {
            return;
        }
        ItemStack existing = hotbar.getItemStack(slot);
        if (existing == null || ItemStack.isEmpty(existing) || !itemId.equals(existing.getItemId())) {
            hotbar.setItemStackForSlot(slot, new ItemStack(itemId, 1), false);
        }
    }

    private static void clearContainer(ItemContainer container) {
        if (container == null) {
            return;
        }
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            container.setItemStackForSlot(slot, ItemStack.EMPTY, false);
        }
    }

    private void registerInteractionCodecs() {
        var registry = this.getCodecRegistry(Interaction.CODEC);
        registry.register("Ascend_Dev_Cindercloth_Interaction",
            AscendDevCinderclothInteraction.class, AscendDevCinderclothInteraction.CODEC);
        registry.register("Ascend_Dev_Stormsilk_Interaction",
            AscendDevStormsilkInteraction.class, AscendDevStormsilkInteraction.CODEC);
        registry.register("Ascend_Dev_Cotton_Interaction",
            AscendDevCottonInteraction.class, AscendDevCottonInteraction.CODEC);
    }
}
