package io.hyvexa.common.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class HelloCommand extends AbstractAsyncCommand {

    public HelloCommand() {
        super("hello", "Say hi!");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (sender instanceof Player player) {
            player.sendMessage(Message.raw("Hi, " + player.getUsername() + "!"));
        } else {
            ctx.sendMessage(Message.raw("Hi!"));
        }
        return CompletableFuture.completedFuture(null);
    }
}
