package io.hyvexa.wardrobe.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.core.wardrobe.WardrobeBridge;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Admin command to reset all owned wardrobe cosmetics (DB + permissions).
 * Usage: /wreset
 */
public class WardrobeResetCommand extends AbstractAsyncCommand {

    public WardrobeResetCommand() {
        super("wreset", "Reset all owned wardrobe cosmetics.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(null);
        }

        if (!PermissionUtils.isOp(player)) {
            player.sendMessage(Message.raw("[Wardrobe] No permission.").color(SystemMessageUtils.ERROR));
            return CompletableFuture.completedFuture(null);
        }

        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null) return CompletableFuture.completedFuture(null);

        WardrobeBridge.getInstance().resetAll(playerRef.getUuid());
        player.sendMessage(Message.raw("[Wardrobe] All cosmetics reset. Permissions revoked.")
                .color(SystemMessageUtils.SUCCESS));

        return CompletableFuture.completedFuture(null);
    }
}
