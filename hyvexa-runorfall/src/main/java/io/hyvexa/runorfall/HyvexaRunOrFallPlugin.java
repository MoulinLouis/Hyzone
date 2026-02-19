package io.hyvexa.runorfall;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.runorfall.command.RunOrFallCommand;
import io.hyvexa.runorfall.interaction.RunOrFallStatsInteraction;
import io.hyvexa.runorfall.manager.RunOrFallConfigStore;
import io.hyvexa.runorfall.manager.RunOrFallGameManager;
import io.hyvexa.runorfall.manager.RunOrFallStatsStore;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

public class HyvexaRunOrFallPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PREFIX = "[RunOrFall] ";
    private static final String ITEM_STATS = "Food_Candy_Cane";
    private static final short SLOT_STATS = 0;
    private static final short SLOT_GAME_SELECTOR = 8;
    private static HyvexaRunOrFallPlugin INSTANCE;

    private RunOrFallConfigStore configStore;
    private RunOrFallStatsStore statsStore;
    private RunOrFallGameManager gameManager;

    public HyvexaRunOrFallPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    public static HyvexaRunOrFallPlugin getInstance() {
        return INSTANCE;
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
            enforceRunOrFallHotbar(player);
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                    PREFIX + "Use /rof join to enter lobby. Admin setup: /rof admin"));
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
                Ref<EntityStore> playerEntityRef = playerRef.getReference();
                if (playerEntityRef != null && playerEntityRef.isValid()) {
                    Store<EntityStore> playerStore = playerEntityRef.getStore();
                    Player player = playerStore.getComponent(playerEntityRef, Player.getComponentType());
                    if (player != null) {
                        enforceRunOrFallHotbar(player);
                    }
                }
                return;
            }
            gameManager.leaveLobby(playerId, false);
        });

        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            if (playerRef == null || playerRef.getUuid() == null) {
                return;
            }
            gameManager.handleDisconnect(playerRef.getUuid());
        });

        LOGGER.atInfo().log("HyvexaRunOrFall plugin loaded");
    }

    private void registerInteractionCodecs() {
        var registry = this.getCodecRegistry(Interaction.CODEC);
        registry.register("RunOrFall_Stats_Interaction", RunOrFallStatsInteraction.class, RunOrFallStatsInteraction.CODEC);
    }

    public RunOrFallStatsStore getStatsStore() {
        return statsStore;
    }

    private void enforceRunOrFallHotbar(Player player) {
        if (player == null || player.getInventory() == null || player.getInventory().getHotbar() == null) {
            return;
        }
        var hotbar = player.getInventory().getHotbar();
        short capacity = hotbar.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }
            String itemId = stack.getItemId();
            if (ITEM_STATS.equals(itemId) || WorldConstants.ITEM_SERVER_SELECTOR.equals(itemId)) {
                hotbar.setItemStackForSlot(slot, ItemStack.EMPTY, false);
            }
        }
        if (SLOT_STATS >= 0 && SLOT_STATS < capacity) {
            hotbar.setItemStackForSlot(SLOT_STATS, new ItemStack(ITEM_STATS, 1), false);
        }
        if (SLOT_GAME_SELECTOR >= 0 && SLOT_GAME_SELECTOR < capacity) {
            hotbar.setItemStackForSlot(SLOT_GAME_SELECTOR,
                    new ItemStack(WorldConstants.ITEM_SERVER_SELECTOR, 1), false);
        }
    }

    @Override
    protected void shutdown() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
    }
}
