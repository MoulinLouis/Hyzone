package io.hyvexa.parkour.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class VoteCommand extends AbstractAsyncCommand {

    private static final Message HEADER = Message.raw("Vote for Hyvexa!").color(ChatColors.ACCENT_COLOR);
    private static final Message SUBTITLE = Message.raw("Each site takes just a few seconds:");

    private static final Message LINK_1 = Message.join(
            Message.raw(" - "),
            Message.raw("hytale.game").color(ChatColors.LINK_COLOR).link("https://hytale.game/en/servers/?sid=hyvexa"),
            Message.raw(" (+50 feathers)").color(ChatColors.ACCENT_COLOR)
    );
    private static final Message LINK_2 = Message.join(
            Message.raw(" - "),
            Message.raw("hytalecharts.com").color(ChatColors.LINK_COLOR).link("https://hytalecharts.com/servers/hyvexa"),
            Message.raw(" (+50 feathers)").color(ChatColors.ACCENT_COLOR)
    );
    private static final Message LINK_3 = Message.join(
            Message.raw(" - "),
            Message.raw("hytale-servers.com").color(ChatColors.LINK_COLOR).link("https://hytale-servers.com/server/hyvexa/vote"),
            Message.raw(" (+50 feathers)").color(ChatColors.ACCENT_COLOR)
    );
    private static final Message LINK_4 = Message.join(
            Message.raw(" - "),
            Message.raw("hytaleonlineservers.com").color(ChatColors.LINK_COLOR).link("https://hytaleonlineservers.com/server-hyvexa.945"),
            Message.raw(" (+50 feathers)").color(ChatColors.ACCENT_COLOR)
    );

    private static final Message THANKS = Message.raw("Thank you for supporting the server!").color(ChatColors.ACCENT_COLOR);

    public VoteCommand() {
        super("vote", "Show voting links.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        commandContext.sendMessage(HEADER);
        commandContext.sendMessage(SUBTITLE);
        commandContext.sendMessage(LINK_1);
        commandContext.sendMessage(LINK_2);
        commandContext.sendMessage(LINK_3);
        commandContext.sendMessage(LINK_4);
        commandContext.sendMessage(THANKS);
        return CompletableFuture.completedFuture(null);
    }
}
