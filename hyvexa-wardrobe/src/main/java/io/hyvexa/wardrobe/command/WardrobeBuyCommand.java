package io.hyvexa.wardrobe.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
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

    private final WardrobeBridge wardrobeBridge;

    public WardrobeBuyCommand(WardrobeBridge wardrobeBridge) {
        super("wbuy", "Buy a wardrobe cosmetic.");
        this.wardrobeBridge = wardrobeBridge;
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

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return CompletableFuture.completedFuture(null);
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return CompletableFuture.completedFuture(null);

        String[] args = CommandUtils.tokenize(ctx);
        if (args.length == 0) {
            String available = wardrobeBridge.getAllCosmetics().stream()
                    .map(WardrobeBridge.WardrobeCosmeticDef::id)
                    .collect(Collectors.joining(", "));
            player.sendMessage(Message.raw("[Wardrobe] Available: " + available)
                    .color(SystemMessageUtils.SECONDARY));
            player.sendMessage(Message.raw("[Wardrobe] Usage: /wbuy <id>")
                    .color(SystemMessageUtils.SECONDARY));
            return CompletableFuture.completedFuture(null);
        }

        String cosmeticId = args[0];
        WardrobeBridge.PurchaseResult result = wardrobeBridge.purchase(playerRef.getUuid(), cosmeticId);
        String color = result.success() ? SystemMessageUtils.SUCCESS : SystemMessageUtils.ERROR;
        player.sendMessage(Message.raw("[Wardrobe] " + result.message()).color(color));

        return CompletableFuture.completedFuture(null);
    }
}
