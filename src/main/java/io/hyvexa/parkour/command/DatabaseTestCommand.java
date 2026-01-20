package io.hyvexa.parkour.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.parkour.data.DatabaseManager;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class DatabaseTestCommand extends AbstractAsyncCommand {

    private static final Message MESSAGE_OP_REQUIRED = Message.raw("You must be OP to use /dbtest.");

    public DatabaseTestCommand() {
        super("dbtest", "Test MySQL database connection.");
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
        if (!PermissionUtils.isOp(player)) {
            commandContext.sendMessage(MESSAGE_OP_REQUIRED);
            return CompletableFuture.completedFuture(null);
        }

        commandContext.sendMessage(Message.raw("Testing database connection..."));

        DatabaseManager db = DatabaseManager.getInstance();

        // Initialize if not already done
        if (!db.isInitialized()) {
            try {
                commandContext.sendMessage(Message.raw("Initializing connection pool..."));
                db.initialize();
            } catch (Exception e) {
                commandContext.sendMessage(Message.raw("Failed to initialize: " + e.getMessage()).color("#ff4444"));
                return CompletableFuture.completedFuture(null);
            }
        }

        // Run the test
        DatabaseManager.TestResult result = db.testConnection();

        if (result.success) {
            commandContext.sendMessage(Message.raw(result.message).color("#44ff44"));
        } else {
            commandContext.sendMessage(Message.raw(result.message).color("#ff4444"));
        }

        return CompletableFuture.completedFuture(null);
    }
}
