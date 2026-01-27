package io.hyvexa.parkour.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class DiscordCommand extends AbstractAsyncCommand {

    private static final String LINK_COLOR = "#8ab4f8";
    private static final String DISCORD_TEXT = "discord.gg/2PAygkyFnK";
    private static final String DISCORD_URL = "https://discord.gg/2PAygkyFnK";
    private static final Message MESSAGE_DISCORD = Message.join(
            Message.raw("Discord: "),
            Message.raw(DISCORD_TEXT).color(LINK_COLOR).link(DISCORD_URL)
    );

    public DiscordCommand() {
        super("discord", "Show the Hyvexa Discord link.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        commandContext.sendMessage(MESSAGE_DISCORD);
        return CompletableFuture.completedFuture(null);
    }
}
