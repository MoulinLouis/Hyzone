package io.hyvexa.ascend.command;

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
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.hud.HudPreviewHud;
import io.hyvexa.common.util.PermissionUtils;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Temporary command to show HUD design preview.
 * Usage: /hudpreview
 * Replaces current HUD with the 3-variation preview. Rejoin to restore.
 */
public class HudPreviewCommand extends AbstractAsyncCommand {

    public HudPreviewCommand() {
        super("hudpreview", "Show HUD design variations preview");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            ctx.sendMessage(Message.raw("Players only."));
            return CompletableFuture.completedFuture(null);
        }
        if (!PermissionUtils.isOp(player)) {
            ctx.sendMessage(Message.raw("You must be OP to use this command."));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            // Stop the normal HUD from overwriting the preview
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin != null && plugin.getHudManager() != null) {
                plugin.getHudManager().setPreviewMode(playerRef.getUuid(), true);
            }

            HudPreviewHud hud = new HudPreviewHud(playerRef);
            player.getHudManager().setCustomHud(playerRef, hud);
            hud.show();

            player.sendMessage(Message.raw("[Preview] HUD design preview displayed. Rejoin to restore normal HUD.").color("#ec4899"));
        }, world);
    }
}
