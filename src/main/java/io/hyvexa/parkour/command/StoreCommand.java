package io.hyvexa.parkour.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class StoreCommand extends AbstractAsyncCommand {

    private static final String LINK_COLOR = "#8ab4f8";
    private static final String STORE_URL = "https://store.hyvexa.com";
    private static final Message STORE_MESSAGE = Message.join(
            Message.raw("Store: "),
            Message.raw("store.hyvexa.com").color(LINK_COLOR).link(STORE_URL)
    );

    public StoreCommand() {
        super("store", "Show the Hyvexa store link.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        commandContext.sendMessage(STORE_MESSAGE);
        return CompletableFuture.completedFuture(null);
    }
}
