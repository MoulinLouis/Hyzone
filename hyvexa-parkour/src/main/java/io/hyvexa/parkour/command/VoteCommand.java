package io.hyvexa.parkour.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class VoteCommand extends AbstractAsyncCommand {

    private static final String LINK_COLOR = "#8ab4f8";
    private static final String ACCENT_COLOR = "#f0c674";

    private static final Message HEADER = Message.raw("Vote for Hyvexa!").color(ACCENT_COLOR);
    private static final Message SUBTITLE = Message.raw("Each site takes just a few seconds:");

    private static final Message LINK_1 = Message.join(
            Message.raw(" - "),
            Message.raw("hytalecharts.com").color(LINK_COLOR).link("https://hytalecharts.com/servers/hyvexa")
    );
    private static final Message LINK_2 = Message.join(
            Message.raw(" - "),
            Message.raw("hytale.game").color(LINK_COLOR).link("https://hytale.game/en/servers/?sid=hyvexa")
    );
    private static final Message LINK_3 = Message.join(
            Message.raw(" - "),
            Message.raw("hytale-serverlist.com").color(LINK_COLOR).link("https://hytale-serverlist.com/servers/server-details/696aa738502c903e279c2945")
    );
    private static final Message LINK_4 = Message.join(
            Message.raw(" - "),
            Message.raw("hytale-servers.com").color(LINK_COLOR).link("https://hytale-servers.com/server/hyvexa")
    );

    private static final Message THANKS = Message.raw("Thank you for supporting the server!").color(ACCENT_COLOR);

    public VoteCommand() {
        super("vote", "Show voting links for Hyvexa.");
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
