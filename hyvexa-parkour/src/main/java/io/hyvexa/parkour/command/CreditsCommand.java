package io.hyvexa.parkour.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class CreditsCommand extends AbstractAsyncCommand {

    private static final Message HEADER = Message.raw("Hyvexa cosmetic and modpack credits:");
    private static final Message ENTRY_1 = Message.raw(" - Wardrobe framework: Wardrobe-0.3.2+2026.02.19-1a311a592");
    private static final Message ENTRY_2 = Message.raw(" - Violet's Wardrobe 2.2 by Violet");
    private static final Message ENTRY_3 = Message.raw(" - Mobstar's Capes 1.6.5 by Mobstar");
    private static final Message ENTRY_4 = Message.raw(" - Cechoo Animal Cosmetics 0.5.4 by Cechoo");
    private static final Message ENTRY_5 = Message.raw(" - HayHays Animal Masks 1.10.1 by HayHays");
    private static final Message FOOTER = Message.raw(
            "All rights belong to their respective creators. Contact staff for corrections/removal."
    );

    public CreditsCommand() {
        super("credits", "Show third-party content credits used by Hyvexa.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        commandContext.sendMessage(HEADER);
        commandContext.sendMessage(ENTRY_1);
        commandContext.sendMessage(ENTRY_2);
        commandContext.sendMessage(ENTRY_3);
        commandContext.sendMessage(ENTRY_4);
        commandContext.sendMessage(ENTRY_5);
        commandContext.sendMessage(FOOTER);
        return CompletableFuture.completedFuture(null);
    }
}
