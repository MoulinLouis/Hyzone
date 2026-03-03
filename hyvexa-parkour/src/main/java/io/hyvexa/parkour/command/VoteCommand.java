package io.hyvexa.parkour.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.core.vote.VoteManager;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VoteCommand extends AbstractAsyncCommand {

    private static final String LINK_COLOR = "#8ab4f8";
    private static final String ACCENT_COLOR = "#f0c674";

    private static final Message HEADER = Message.raw("Vote for Hyvexa!").color(ACCENT_COLOR);
    private static final Message SUBTITLE = Message.raw("Each site takes just a few seconds:");

    private static final Message LINK_1 = Message.join(
            Message.raw(" - "),
            Message.raw("hytale.game").color(LINK_COLOR).link("https://hytale.game/en/servers/?sid=hyvexa"),
            Message.raw(" (+50 feathers)").color(ACCENT_COLOR)
    );
    private static final Message LINK_2 = Message.join(
            Message.raw(" - "),
            Message.raw("hytalecharts.com").color(LINK_COLOR).link("https://hytalecharts.com/servers/hyvexa")
    );
    private static final Message LINK_3 = Message.join(
            Message.raw(" - "),
            Message.raw("hytale-servers.com").color(LINK_COLOR).link("https://hytale-servers.com/server/hyvexa/vote")
    );
    private static final Message LINK_4 = Message.join(
            Message.raw(" - "),
            Message.raw("hytaleonlineservers.com").color(LINK_COLOR).link("https://hytaleonlineservers.com/server-hyvexa.945")
    );

    private static final Message THANKS = Message.raw("Thank you for supporting the server!").color(ACCENT_COLOR);
    private static final Message CHECKING = Message.raw("Checking for unclaimed votes...").color("#a3e635");
    private static final Message NO_VOTES = Message.raw("No unclaimed votes found.").color(ACCENT_COLOR);

    public VoteCommand() {
        super("vote", "Show voting links or claim vote rewards.");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        String[] args = CommandUtils.getArgs(commandContext);
        if (args.length > 0 && "claim".equalsIgnoreCase(args[0])) {
            return handleClaim(commandContext);
        }
        commandContext.sendMessage(HEADER);
        commandContext.sendMessage(SUBTITLE);
        commandContext.sendMessage(LINK_1);
        commandContext.sendMessage(LINK_2);
        commandContext.sendMessage(LINK_3);
        commandContext.sendMessage(LINK_4);
        commandContext.sendMessage(THANKS);
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleClaim(CommandContext ctx) {
        if (!(ctx.sender() instanceof Player player)) {
            ctx.sendMessage(Message.raw("This command can only be used by players."));
            return CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return CompletableFuture.completedFuture(null);
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }
        // Dispatch to world thread for ECS access, then async HTTP, then back to world thread for messages
        return CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                return;
            }
            PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            UUID playerId = playerRef.getUuid();
            if (playerId == null) {
                return;
            }
            Player p = ref.getStore().getComponent(ref, Player.getComponentType());
            if (p != null) {
                p.sendMessage(CHECKING);
            }
            VoteManager.getInstance().checkAndRewardAsync(playerId)
                    .thenAcceptAsync(count -> {
                        if (!ref.isValid()) {
                            return;
                        }
                        Player p2 = ref.getStore().getComponent(ref, Player.getComponentType());
                        if (p2 == null) {
                            return;
                        }
                        if (count <= 0) {
                            p2.sendMessage(NO_VOTES);
                        } else {
                            int total = count * VoteManager.getInstance().getRewardPerVote();
                            String suffix = count > 1 ? " (x" + count + ")" : "";
                            p2.sendMessage(Message.join(
                                    Message.raw("You received ").color("#a3e635"),
                                    Message.raw(total + " feathers").color("#4ade80").bold(true),
                                    Message.raw(" for voting!" + suffix).color("#a3e635")
                            ));
                        }
                    }, world)
                    .exceptionally(ex -> {
                        ctx.sendMessage(Message.raw("Failed to check votes. Try again later.").color("#ef4444"));
                        return null;
                    });
        }, world);
    }
}
