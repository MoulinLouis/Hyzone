package io.hyvexa.runorfall;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.runorfall.command.RunOrFallCommand;
import io.hyvexa.runorfall.manager.RunOrFallConfigStore;
import io.hyvexa.runorfall.manager.RunOrFallGameManager;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.UUID;

public class HyvexaRunOrFallPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PREFIX = "[RunOrFall] ";

    private RunOrFallConfigStore configStore;
    private RunOrFallGameManager gameManager;

    public HyvexaRunOrFallPlugin(@Nonnull JavaPluginInit init) {
        super(init);
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
        gameManager = new RunOrFallGameManager(configStore);

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
            var player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (player == null) {
                return;
            }
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
            if (!ModeGate.isRunOrFallWorld(world)) {
                gameManager.leaveLobby(playerId, false);
            }
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

    @Override
    protected void shutdown() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
    }
}
