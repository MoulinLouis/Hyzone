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
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.state.ModeMessages;

import javax.annotation.Nonnull;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;

public class DatabaseClearCommand extends AbstractAsyncCommand {

    private static final Message MESSAGE_OP_REQUIRED = Message.raw("You must be OP to use /dbclear.");

    public DatabaseClearCommand() {
        super("dbclear", "Clear all parkour data from the MySQL database.");
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
            if (store.getExternalData() == null) return CompletableFuture.completedFuture(null);
            World world = store.getExternalData().getWorld();
            if (ModeGate.denyIfNot(commandContext, world, WorldConstants.WORLD_PARKOUR, ModeMessages.MESSAGE_ENTER_PARKOUR)) {
                return CompletableFuture.completedFuture(null);
            }
        }
        if (!PermissionUtils.isOp(player)) {
            commandContext.sendMessage(MESSAGE_OP_REQUIRED);
            return CompletableFuture.completedFuture(null);
        }

        DatabaseManager db = DatabaseManager.get();
        if (!db.isInitialized()) {
            try {
                db.initialize();
            } catch (Exception e) {
                commandContext.sendMessage(Message.raw("Failed to initialize database: " + e.getMessage())
                        .color("#ff4444"));
                return CompletableFuture.completedFuture(null);
            }
        }

        commandContext.sendMessage(Message.raw("Clearing all parkour database data...").color("#ffaa00"));

        String[] tables = {
                "player_checkpoint_times",
                "player_completions",
                "map_checkpoints",
                "global_messages",
                "global_message_settings",
                "player_count_samples",
                "players",
                "maps",
                "settings"
        };

        boolean cleared = db.withTransaction(conn -> {
            try (Statement stmt = conn.createStatement()) {
                for (String table : tables) {
                    stmt.executeUpdate("DELETE FROM " + table);
                }
            }
        });
        if (!cleared) {
            commandContext.sendMessage(Message.raw("Database clear failed.").color("#ff4444"));
            return CompletableFuture.completedFuture(null);
        }

        commandContext.sendMessage(Message.raw("Database cleared. Restart the server to reset in-memory caches.")
                .color("#44ff44"));
        return CompletableFuture.completedFuture(null);
    }
}
