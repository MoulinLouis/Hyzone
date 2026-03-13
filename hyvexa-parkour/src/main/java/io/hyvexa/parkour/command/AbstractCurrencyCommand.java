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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.PermissionUtils;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractCurrencyCommand extends AbstractAsyncCommand {

    private final String commandName;

    protected AbstractCurrencyCommand(String commandName, String description) {
        super(commandName, description);
        this.commandName = commandName;
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    protected abstract String currencyName();

    protected abstract long getCurrency(UUID playerId);

    protected abstract void setCurrency(UUID playerId, long amount);

    protected abstract long addCurrency(UUID playerId, long amount);

    protected abstract long removeCurrency(UUID playerId, long amount);

    /**
     * Called after a successful "add" operation. Override to send notifications to the target.
     */
    protected void onCurrencyAdded(PlayerRef targetRef, long amount, long newTotal) {
        // Default: no notification to target
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
        String[] args = CommandUtils.tokenize(ctx);
        String currency = currencyName();
        if (args.length < 2) {
            player.sendMessage(Message.raw("Usage: /" + commandName + " <set|add|remove|check> <player> [amount]"));
            return;
        }
        String action = args[0].toLowerCase();
        String targetName = args[1];

        PlayerRef targetRef = CommandUtils.findPlayerByName(targetName);
        if (targetRef == null) {
            player.sendMessage(Message.raw("Player not found: " + targetName));
            return;
        }
        UUID targetId = targetRef.getUuid();

        switch (action) {
            case "check" -> {
                long balance = getCurrency(targetId);
                player.sendMessage(Message.raw(targetName + " has " + balance + " " + currency + "."));
            }
            case "set" -> {
                long amount = parseAmount(player, args, 2);
                if (amount < 0) return;
                setCurrency(targetId, amount);
                player.sendMessage(Message.raw("Set " + targetName + "'s " + currency + " to " + amount + "."));
            }
            case "add" -> {
                long amount = parseAmount(player, args, 2);
                if (amount < 0) return;
                long newTotal = addCurrency(targetId, amount);
                player.sendMessage(Message.raw("Added " + amount + " " + currency + " to " + targetName + ". New total: " + newTotal));
                onCurrencyAdded(targetRef, amount, newTotal);
            }
            case "remove" -> {
                long amount = parseAmount(player, args, 2);
                if (amount < 0) return;
                long newTotal = removeCurrency(targetId, amount);
                player.sendMessage(Message.raw("Removed " + amount + " " + currency + " from " + targetName + ". New total: " + newTotal));
            }
            default -> player.sendMessage(Message.raw("Usage: /" + commandName + " <set|add|remove|check> <player> [amount]"));
        }
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
