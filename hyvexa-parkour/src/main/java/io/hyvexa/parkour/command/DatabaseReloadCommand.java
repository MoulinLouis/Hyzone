package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.core.db.DatabaseConfig;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.parkour.util.ParkourModeGate;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class DatabaseReloadCommand extends AbstractAsyncCommand {

    private static final Message MESSAGE_OP_REQUIRED = Message.raw("You must be OP to use /dbreload.");

    public DatabaseReloadCommand() {
        super("dbreload", "Reload Parkour/database.json and reinitialize the MySQL pool.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        if (!(sender instanceof Player player)) {
            commandContext.sendMessage(Message.raw("This command must be run by a player."));
            return CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            if (ParkourModeGate.denyIfNotParkour(commandContext, world)) {
                return CompletableFuture.completedFuture(null);
            }
        }
        if (!PermissionUtils.isOp(player)) {
            commandContext.sendMessage(MESSAGE_OP_REQUIRED);
            return CompletableFuture.completedFuture(null);
        }

        commandContext.sendMessage(Message.raw("Reloading database config...").color("#ffaa00"));

        DatabaseManager db = DatabaseManager.getInstance();
        try {
            DatabaseConfig config = DatabaseConfig.load();
            if (db.isInitialized()) {
                db.shutdown();
            }
            db.initialize();

            commandContext.sendMessage(Message.raw("Database config reloaded.").color("#44ff44"));
            commandContext.sendMessage(Message.raw("Path: " + DatabaseConfig.getConfigPath()));
            commandContext.sendMessage(Message.raw("Host: " + config.getHost()));
            commandContext.sendMessage(Message.raw("Port: " + config.getPort()));
            commandContext.sendMessage(Message.raw("Database: " + config.getDatabase()));
            commandContext.sendMessage(Message.raw("User: " + config.getUser()));
            commandContext.sendMessage(Message.raw("Password: (hidden)"));
        } catch (Exception e) {
            commandContext.sendMessage(Message.raw("Database reload failed: " + e.getMessage()).color("#ff4444"));
            e.printStackTrace();
        }

        return CompletableFuture.completedFuture(null);
    }
}
