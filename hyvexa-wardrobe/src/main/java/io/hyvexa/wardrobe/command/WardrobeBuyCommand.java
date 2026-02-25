package io.hyvexa.wardrobe.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.core.wardrobe.WardrobeBridge;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Test command to purchase wardrobe cosmetics with vexa.
 * Usage: /wbuy <cosmetic_id>
 * Example: /wbuy WD_Badge_Hyvexa
 */
public class WardrobeBuyCommand extends AbstractAsyncCommand {

    public WardrobeBuyCommand() {
        super("wbuy", "Buy a wardrobe cosmetic.");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(null);
        }

        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null) return CompletableFuture.completedFuture(null);

        String[] args = CommandUtils.getArgs(ctx);
        if (args.length == 0) {
            String available = WardrobeBridge.getInstance().getAllCosmetics().stream()
                    .map(d -> d.id() + " (" + d.price() + " vexa)")
                    .collect(Collectors.joining(", "));
            player.sendMessage(Message.raw("[Wardrobe] Available: " + available)
                    .color(SystemMessageUtils.SECONDARY));
            player.sendMessage(Message.raw("[Wardrobe] Usage: /wbuy <id>")
                    .color(SystemMessageUtils.SECONDARY));
            return CompletableFuture.completedFuture(null);
        }

        String cosmeticId = args[0];
        String result = WardrobeBridge.getInstance().purchase(playerRef.getUuid(), cosmeticId);

        boolean isError = result.startsWith("Not enough") || result.startsWith("Unknown")
                || result.startsWith("You already");
        String color = isError ? SystemMessageUtils.ERROR : SystemMessageUtils.SUCCESS;
        player.sendMessage(Message.raw("[Wardrobe] " + result).color(color));

        return CompletableFuture.completedFuture(null);
    }
}
