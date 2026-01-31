package io.hyvexa.hub;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
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
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.hub.HubConstants;
import io.hyvexa.hub.command.HubCommand;
import io.hyvexa.hub.hud.HubHud;
import io.hyvexa.hub.interaction.HubMenuInteraction;
import io.hyvexa.hub.routing.HubRouter;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class HyvexaHubPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String HUB_WORLD_NAME = "Hub";
    private static final String PARKOUR_WORLD_NAME = "Parkour";
    private static final String ASCEND_WORLD_NAME = "Ascend";
    private static HyvexaHubPlugin INSTANCE;

    private HubRouter router;
    private final ConcurrentHashMap<UUID, HubHud> hubHuds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> hubHudAttached = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> hubHudReadyAt = new ConcurrentHashMap<>();
    private ScheduledFuture<?> hubHudTask;

    public HyvexaHubPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
        LOGGER.atInfo().log("Hello from " + this.getName() + " version " + this.getManifest().getVersion().toString());
    }

    public static HyvexaHubPlugin getInstance() {
        return INSTANCE;
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());
        var folder = new File("mods/Hub");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        if (!DatabaseManager.getInstance().isInitialized()) {
            try {
                DatabaseManager.getInstance().initialize();
                LOGGER.atInfo().log("Database connection initialized");
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).log("Failed to initialize database: " + e.getMessage());
            }
        }
        router = new HubRouter();
        preloadWorlds();

        registerInteractionCodecs();
        this.getCommandRegistry().registerCommand(new HubCommand(this));

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (!isHubWorld(store)) {
                return;
            }
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            World world = store.getExternalData().getWorld();
            if (world == null) {
                return;
            }
            CompletableFuture.runAsync(() -> {
                if (!ref.isValid()) {
                    return;
                }
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }
                clearInventory(player);
                giveHubItems(player);
                attachHubHud(ref, store, playerRef);
            }, world);
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
            hubHuds.remove(playerId);
            hubHudAttached.remove(playerId);
            hubHudReadyAt.remove(playerId);
        });

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            Ref<EntityStore> ref = playerRef != null ? playerRef.getReference() : null;
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            if (isHubWorld(store)) {
                attachHubHud(ref, store, playerRef);
            }
        }

        hubHudTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                this::tickHubHud, 200, 200, TimeUnit.MILLISECONDS
        );
    }

    private void attachHubHud(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        var world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (!ref.isValid() || !playerRef.isValid()) {
                return;
            }
            var player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (player == null) {
                return;
            }
            HubHud hud = hubHuds.computeIfAbsent(playerRef.getUuid(), ignored -> new HubHud(playerRef));
            player.getHudManager().setCustomHud(playerRef, hud);
            player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass);
            hud.resetCache();
            hud.show();
            hud.applyStaticText();
            hubHudAttached.put(playerRef.getUuid(), true);
            hubHudReadyAt.put(playerRef.getUuid(), System.currentTimeMillis() + 250L);
        }, world);
    }

    private void registerInteractionCodecs() {
        var registry = this.getCodecRegistry(Interaction.CODEC);
        registry.register("Hub_Menu_Interaction", HubMenuInteraction.class, HubMenuInteraction.CODEC);
    }

    private void preloadWorlds() {
        try {
            Universe.get().loadWorld(HUB_WORLD_NAME);
            Universe.get().loadWorld(PARKOUR_WORLD_NAME);
            Universe.get().loadWorld(ASCEND_WORLD_NAME);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to preload hub worlds: " + e.getMessage());
        }
    }

    public HubRouter getRouter() {
        return router;
    }

    private void giveHubItems(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot((short) 0, new ItemStack(HubConstants.ITEM_SERVER_SELECTOR, 1), false);
    }

    private static void clearInventory(Player player) {
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
    }

    private static void clearContainer(ItemContainer container) {
        if (container == null) {
            return;
        }
        container.clear();
    }

    private void tickHubHud() {
        Map<World, List<PlayerTickContext>> playersByWorld = collectPlayersByWorld();
        for (Map.Entry<World, List<PlayerTickContext>> entry : playersByWorld.entrySet()) {
            World world = entry.getKey();
            if (!isHubWorld(world)) {
                continue;
            }
            List<PlayerTickContext> players = entry.getValue();
            CompletableFuture.runAsync(() -> {
                for (PlayerTickContext context : players) {
                    if (context.ref == null || !context.ref.isValid()) {
                        continue;
                    }
                    UUID playerId = context.playerRef != null ? context.playerRef.getUuid() : null;
                    if (playerId == null) {
                        continue;
                    }
                    HubHud hud = hubHuds.get(playerId);
                    boolean needsAttach = !Boolean.TRUE.equals(hubHudAttached.get(playerId));
                    if (needsAttach || hud == null) {
                        attachHubHud(context.ref, context.store, context.playerRef);
                        continue;
                    }
                    // Always ensure HUD is set on player (in case they came from another world)
                    Player player = context.store.getComponent(context.ref, Player.getComponentType());
                    if (player != null) {
                        player.getHudManager().setCustomHud(context.playerRef, hud);
                        player.getHudManager().hideHudComponents(context.playerRef, HudComponent.Compass);
                    }
                    long readyAt = hubHudReadyAt.getOrDefault(playerId, Long.MAX_VALUE);
                    if (System.currentTimeMillis() < readyAt) {
                        continue;
                    }
                    hud.applyStaticText();
                }
            }, world);
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
            playersByWorld.computeIfAbsent(world, ignored -> new ArrayList<>())
                    .add(new PlayerTickContext(ref, store, playerRef));
        }
        return playersByWorld;
    }

    private static final class PlayerTickContext {
        private final Ref<EntityStore> ref;
        private final Store<EntityStore> store;
        private final PlayerRef playerRef;

        private PlayerTickContext(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
            this.ref = ref;
            this.store = store;
            this.playerRef = playerRef;
        }
    }

    private boolean isHubWorld(World world) {
        if (world == null || world.getName() == null) {
            return false;
        }
        return HUB_WORLD_NAME.equalsIgnoreCase(world.getName());
    }

    private boolean isHubWorld(Store<EntityStore> store) {
        if (store == null || store.getExternalData() == null) {
            return false;
        }
        var world = store.getExternalData().getWorld();
        if (world == null || world.getName() == null) {
            return false;
        }
        return HUB_WORLD_NAME.equalsIgnoreCase(world.getName());
    }

    @Override
    protected void shutdown() {
        if (hubHudTask != null) {
            hubHudTask.cancel(false);
            hubHudTask = null;
        }
    }
}
