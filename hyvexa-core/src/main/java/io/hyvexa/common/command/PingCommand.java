package io.hyvexa.common.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class PingCommand extends AbstractAsyncCommand {

    public PingCommand() {
        super("ping", "Pong!");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        ctx.sendMessage(Message.raw("Pong!"));
        return CompletableFuture.completedFuture(null);
    }
}
