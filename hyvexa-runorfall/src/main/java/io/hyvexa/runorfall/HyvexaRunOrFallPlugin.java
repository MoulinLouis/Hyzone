package io.hyvexa.runorfall;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.MultiHudBridge;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.economy.VexaStore;
import io.hyvexa.runorfall.command.RunOrFallCommand;
import io.hyvexa.runorfall.hud.RunOrFallHud;
import io.hyvexa.runorfall.interaction.RunOrFallBlinkInteraction;
import io.hyvexa.runorfall.interaction.RunOrFallJoinInteraction;
import io.hyvexa.runorfall.interaction.RunOrFallStatsInteraction;
import io.hyvexa.runorfall.manager.RunOrFallConfigStore;
import io.hyvexa.runorfall.manager.RunOrFallGameManager;
import io.hyvexa.runorfall.manager.RunOrFallStatsStore;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

public class HyvexaRunOrFallPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String ITEM_JOIN = "Ingredient_Life_Essence";
    private static final String ITEM_LEAVE = "Ingredient_Earth_Essence";
    private static final String ITEM_BLINK = "Ingredient_Lightning_Essence";
    private static final String ITEM_STATS = "Food_Candy_Cane";
    private static final String ITEM_LEADERBOARD = "WinterHoliday_Snowflake";
    private static final short SLOT_PRIMARY = 0;
    private static final short SLOT_LEADERBOARD = 1;
    private static final short SLOT_STATS = 2;
    private static final short SLOT_GAME_SELECTOR = 8;
    private static HyvexaRunOrFallPlugin INSTANCE;

    private RunOrFallConfigStore configStore;
    private RunOrFallStatsStore statsStore;
    private RunOrFallGameManager gameManager;
    private static final long HUD_READY_DELAY_MS = 250L;
    private final ConcurrentHashMap<UUID, RunOrFallHud> runOrFallHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> hudReadyAt = new ConcurrentHashMap<>();
    private ScheduledFuture<?> hudUpdateTask;

    public HyvexaRunOrFallPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    public static HyvexaRunOrFallPlugin getInstance() {
        return INSTANCE;
    }

    private enum HotbarState {
        DEFAULT,
        LOBBY,
        IN_GAME
    }

    @Override
    public CompletableFuture<Void> preLoad() {
        registerInteractionCodecs();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected void setup() {
        File folder = new File("mods/RunOrFall");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        try {
            if (!DatabaseManager.getInstance().isInitialized()) {
                DatabaseManager.getInstance().initialize();
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("RunOrFall database initialization failed.");
        }
        try {
            VexaStore.getInstance().initialize();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("RunOrFall VexaStore initialization failed.");
        }

        configStore = new RunOrFallConfigStore(new File(folder, "config.json"));
        statsStore = new RunOrFallStatsStore();
        gameManager = new RunOrFallGameManager(configStore, statsStore);

        this.getCommandRegistry().registerCommand(new RunOrFallCommand(configStore, gameManager));

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
            if (!ModeGate.isRunOrFallWorld(world)) {
                return;
            }
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null || playerRef.getReference() == null || !playerRef.getReference().isValid()) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            attachRunOrFallHud(playerRef, player);
            refreshRunOrFallHotbar(playerRef.getUuid());
        });

        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            var holder = event.getHolder();
            if (holder == null) {
                return;
            }
            PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId == null) {
                return;
            }
            World world = event.getWorld();
            if (ModeGate.isRunOrFallWorld(world)) {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    Player player = ref.getStore().getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        attachRunOrFallHud(playerRef, player);
                    }
                }
                refreshRunOrFallHotbar(playerId);
                return;
            }
            runOrFallHuds.remove(playerId);
            hudReadyAt.remove(playerId);
            gameManager.leaveLobby(playerId, false);
        });

        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            if (playerRef == null || playerRef.getUuid() == null) {
                return;
            }
            UUID playerId = playerRef.getUuid();
            gameManager.handleDisconnect(playerId);
            runOrFallHuds.remove(playerId);
            hudReadyAt.remove(playerId);
            VexaStore.getInstance().evictPlayer(playerId);
        });

        hudUpdateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                this::tickHudUpdates, 5000L, 5000L, TimeUnit.MILLISECONDS
        );

        LOGGER.atInfo().log("HyvexaRunOrFall plugin loaded");
    }

    private void registerInteractionCodecs() {
        var registry = this.getCodecRegistry(Interaction.CODEC);
        registry.register("RunOrFall_Join_Interaction", RunOrFallJoinInteraction.class, RunOrFallJoinInteraction.CODEC);
        registry.register("RunOrFall_Blink_Interaction", RunOrFallBlinkInteraction.class, RunOrFallBlinkInteraction.CODEC);
        registry.register("RunOrFall_Stats_Interaction", RunOrFallStatsInteraction.class, RunOrFallStatsInteraction.CODEC);
    }

    public RunOrFallStatsStore getStatsStore() {
        return statsStore;
    }

    public RunOrFallGameManager getGameManager() {
        return gameManager;
    }

    public void updateCountdownHud(UUID playerId, String text) {
        if (playerId == null) {
            return;
        }
        RunOrFallHud hud = runOrFallHuds.get(playerId);
        if (hud == null) {
            return;
        }
        hud.updateCountdownText(text);
    }

    public void updateBrokenBlocksHud(UUID playerId, int brokenBlocks) {
        if (playerId == null) {
            return;
        }
        RunOrFallHud hud = runOrFallHuds.get(playerId);
        if (hud == null) {
            return;
        }
        boolean visible = gameManager != null && gameManager.isInActiveRound(playerId);
        hud.updateBrokenBlocks(brokenBlocks, visible);
    }

    public void refreshRunOrFallHotbar(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (!ModeGate.isRunOrFallWorld(world)) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        HotbarState state = HotbarState.DEFAULT;
        if (gameManager != null) {
            if (gameManager.isInActiveRound(playerId)) {
                state = HotbarState.IN_GAME;
            } else if (gameManager.isJoined(playerId)) {
                state = HotbarState.LOBBY;
            }
        }
        applyRunOrFallHotbar(player, state);
    }

    private void applyRunOrFallHotbar(Player player, HotbarState state) {
        if (player == null || player.getInventory() == null || player.getInventory().getHotbar() == null) {
            return;
        }
        var hotbar = player.getInventory().getHotbar();
        short capacity = hotbar.getCapacity();
        clearControlledItems(hotbar, capacity);
        if (state == HotbarState.IN_GAME) {
            setHotbarItem(hotbar, capacity, SLOT_PRIMARY, ITEM_BLINK);
            return;
        }
        String primaryItem = state == HotbarState.LOBBY ? ITEM_LEAVE : ITEM_JOIN;
        setHotbarItem(hotbar, capacity, SLOT_PRIMARY, primaryItem);
        setHotbarItem(hotbar, capacity, SLOT_LEADERBOARD, ITEM_LEADERBOARD);
        setHotbarItem(hotbar, capacity, SLOT_STATS, ITEM_STATS);
        if (state == HotbarState.DEFAULT) {
            setHotbarItem(hotbar, capacity, SLOT_GAME_SELECTOR, WorldConstants.ITEM_SERVER_SELECTOR);
        }
    }

    private void clearControlledItems(ItemContainer hotbar, short capacity) {
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            String itemId = stack.getItemId();
            if (ITEM_STATS.equals(itemId) || ITEM_JOIN.equals(itemId) || ITEM_LEAVE.equals(itemId)
                    || ITEM_BLINK.equals(itemId)
                    || ITEM_LEADERBOARD.equals(itemId)
                    || WorldConstants.ITEM_SERVER_SELECTOR.equals(itemId)) {
                hotbar.setItemStackForSlot(slot, ItemStack.EMPTY, false);
            }
        }
    }

    private void setHotbarItem(ItemContainer hotbar, short capacity, short slot, String itemId) {
        if (slot >= 0 && slot < capacity) {
            hotbar.setItemStackForSlot(slot, new ItemStack(itemId, 1), false);
        }
    }

    private void attachRunOrFallHud(PlayerRef playerRef, Player player) {
        if (playerRef == null || player == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        RunOrFallHud hud = new RunOrFallHud(playerRef);
        runOrFallHuds.put(playerId, hud);
        hudReadyAt.put(playerId, System.currentTimeMillis() + HUD_READY_DELAY_MS);
        MultiHudBridge.setCustomHud(player, playerRef, hud);
        player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass);
        MultiHudBridge.showIfNeeded(hud);
        hud.updateCountdownText(null);
        int brokenBlocks = gameManager != null ? gameManager.getBrokenBlocksCount(playerId) : 0;
        boolean showBrokenBlocks = gameManager != null && gameManager.isInActiveRound(playerId);
        hud.updateBrokenBlocks(brokenBlocks, showBrokenBlocks);
        hud.updatePlayerCount();
        hud.updateVexa(VexaStore.getInstance().getVexa(playerId));
    }

    private void tickHudUpdates() {
        long now = System.currentTimeMillis();
        for (var entry : runOrFallHuds.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null) {
                runOrFallHuds.remove(playerId);
                hudReadyAt.remove(playerId);
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                runOrFallHuds.remove(playerId);
                hudReadyAt.remove(playerId);
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
            if (!ModeGate.isRunOrFallWorld(world)) {
                runOrFallHuds.remove(playerId);
                hudReadyAt.remove(playerId);
                continue;
            }
            long readyAt = hudReadyAt.getOrDefault(playerId, Long.MAX_VALUE);
            if (now < readyAt) {
                continue;
            }
            RunOrFallHud hud = entry.getValue();
            hud.updatePlayerCount();
            hud.updateVexa(VexaStore.getInstance().getVexa(playerId));
        }
    }

    @Override
    protected void shutdown() {
        if (hudUpdateTask != null) {
            hudUpdateTask.cancel(false);
            hudUpdateTask = null;
        }
        runOrFallHuds.clear();
        hudReadyAt.clear();
        if (gameManager != null) {
            gameManager.shutdown();
        }
    }
}
