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
import io.hyvexa.core.economy.VexaStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VexaCommand extends AbstractAsyncCommand {

    public VexaCommand() {
        super("vexa", "Manage player vexa");
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
        if (args.length < 2) {
            player.sendMessage(Message.raw("Usage: /vexa <set|add|remove|check> <player> [amount]"));
            return;
        }
        String action = args[0].toLowerCase();
        String targetName = args[1];

        PlayerRef targetRef = findPlayer(targetName);
        if (targetRef == null) {
            player.sendMessage(Message.raw("Player not found: " + targetName));
            return;
        }
        UUID targetId = targetRef.getUuid();

        switch (action) {
            case "check" -> {
                long vexa = VexaStore.getInstance().getVexa(targetId);
                player.sendMessage(Message.raw(targetName + " has " + vexa + " vexa."));
            }
            case "set" -> {
                long amount = parseAmount(player, args, 2);
                if (amount < 0) return;
                VexaStore.getInstance().setVexa(targetId, amount);
                player.sendMessage(Message.raw("Set " + targetName + "'s vexa to " + amount + "."));
            }
            case "add" -> {
                long amount = parseAmount(player, args, 2);
                if (amount < 0) return;
                long newTotal = VexaStore.getInstance().addVexa(targetId, amount);
                player.sendMessage(Message.raw("Added " + amount + " vexa to " + targetName + ". New total: " + newTotal));
            }
            case "remove" -> {
                long amount = parseAmount(player, args, 2);
                if (amount < 0) return;
                long newTotal = VexaStore.getInstance().removeVexa(targetId, amount);
                player.sendMessage(Message.raw("Removed " + amount + " vexa from " + targetName + ". New total: " + newTotal));
            }
            default -> player.sendMessage(Message.raw("Usage: /vexa <set|add|remove|check> <player> [amount]"));
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

    private long parseAmount(Player player, String[] args, int index) {
        if (index >= args.length) {
            player.sendMessage(Message.raw("Amount is required."));
            return -1;
        }
        try {
            long value = Long.parseLong(args[index]);
            if (value < 0) {
                player.sendMessage(Message.raw("Amount must be non-negative."));
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("Amount must be a number."));
            return -1;
        }
    }
}
