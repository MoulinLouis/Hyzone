package io.hyvexa.parkour.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class RulesCommand extends AbstractAsyncCommand {

    private static final Message RULES_MESSAGE = Message.raw(
            """
            Duel & Parkour Rules:
            - Cuts and smart strategies are allowed.
            - No cheating (mods, macros, or automation).
            - No flying, speed boosts, or external movement advantages.
            - No bug abuse or glitching to get better times.
            If you find a bug, report it instead of abusing it.
            """
    );

    public RulesCommand() {
        super("rules", "Show rules about cheating and bug abuse.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        commandContext.sendMessage(RULES_MESSAGE);
        return CompletableFuture.completedFuture(null);
    }
}

