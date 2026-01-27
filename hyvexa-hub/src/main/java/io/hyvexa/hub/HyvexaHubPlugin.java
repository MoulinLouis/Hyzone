package io.hyvexa.hub;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.core.event.ModeEnterEvent;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.state.PlayerMode;
import io.hyvexa.core.state.PlayerModeStateStore;
import io.hyvexa.hub.command.HubCommand;
import io.hyvexa.hub.hud.HubHud;
import io.hyvexa.hub.interaction.HubMenuInteraction;
import io.hyvexa.hub.routing.HubRouter;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.UUID;
import java.util.logging.Level;

public class HyvexaHubPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String HUB_WORLD_NAME = "Hub";
    private static final String PARKOUR_WORLD_NAME = "Parkour";
    private static final String ASCEND_WORLD_NAME = "Ascend";
    private static HyvexaHubPlugin INSTANCE;

    private PlayerModeStateStore modeStore;
    private HubRouter router;
    private final java.util.concurrent.ConcurrentHashMap<UUID, HubHud> hubHuds = new java.util.concurrent.ConcurrentHashMap<>();

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
        modeStore = PlayerModeStateStore.getInstance();
        modeStore.ensureTable();
        modeStore.syncLoad();
        router = new HubRouter(modeStore, HytaleServer.get().getEventBus());
        preloadWorlds();

        registerInteractionCodecs();
        this.getCommandRegistry().registerCommand(new HubCommand(this));

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            ensureHubState(playerRef);
        });
        this.getEventRegistry().registerGlobal(ModeEnterEvent.class, event -> {
            if (event.getMode() != PlayerMode.HUB) {
                return;
            }
            PlayerRef playerRef = event.getPlayerRef();
            if (playerRef == null) {
                return;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            attachHubHud(ref, store, playerRef);
        });

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            ensureHubState(playerRef);
        }
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
            hud.show();
            hud.applyStaticText();
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

    public PlayerModeStateStore getModeStore() {
        return modeStore;
    }

    private void ensureHubState(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        PlayerMode currentMode = modeStore.getCurrentMode(playerId);
        if (currentMode == PlayerMode.NONE) {
            router.routeToHub(playerRef);
        }
    }
}
