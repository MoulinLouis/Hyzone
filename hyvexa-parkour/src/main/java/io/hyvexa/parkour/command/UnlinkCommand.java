package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.core.discord.DiscordLinkStore;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class UnlinkCommand extends AbstractAsyncCommand {

    public UnlinkCommand() {
        super("unlink", "Unlink a player's Discord account (OP only)");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            ctx.sendMessage(Message.raw("This command can only be used by players."));
            return CompletableFuture.completedFuture(null);
        }
        if (!PermissionUtils.isOp(player)) {
            ctx.sendMessage(Message.raw("You must be OP to use this command."));
            return CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player not in world."));
            return CompletableFuture.completedFuture(null);
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        return CompletableFuture.runAsync(() -> handleCommand(ctx, player), world);
    }

    private void handleCommand(CommandContext ctx, Player player) {
        String[] args = CommandUtils.getArgs(ctx);
        if (args.length < 1) {
            player.sendMessage(Message.raw("Usage: /unlink <player>"));
            return;
        }
        String targetName = args[0];

        PlayerRef targetRef = findPlayer(targetName);
        if (targetRef == null) {
            player.sendMessage(Message.raw("Player not found (must be online): " + targetName));
            return;
        }

        boolean removed = DiscordLinkStore.getInstance().unlinkPlayer(targetRef.getUuid());
        if (removed) {
            player.sendMessage(Message.raw("Unlinked " + targetName + "'s Discord account.").color("#a3e635"));
        } else {
            player.sendMessage(Message.raw(targetName + " has no linked Discord account.").color("#94a3b8"));
        }
    }

    private PlayerRef findPlayer(String name) {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef != null && name.equalsIgnoreCase(playerRef.getUsername())) {
                return playerRef;
            }
        }
        return null;
    }
}
