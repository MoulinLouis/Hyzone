package io.hyvexa.ascend.mine.command;

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
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.ui.MineSellPage;
import io.hyvexa.ascend.mine.ui.MineUpgradePage;
import io.hyvexa.ascend.ui.MineBagPage;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.core.state.ModeMessages;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class MineCommand extends AbstractAsyncCommand {

    public MineCommand() {
        super("mine", "Mine commands");
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
        if (ref == null || !ref.isValid()) {
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            if (ModeGate.denyIfNot(ctx, world, WorldConstants.WORLD_ASCEND, ModeMessages.MESSAGE_ENTER_ASCEND)) {
                return;
            }

            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin == null) {
                return;
            }

            MinePlayerStore mineStore = plugin.getMinePlayerStore();
            if (mineStore == null) {
                player.sendMessage(Message.raw("[Mine] Mining system not available.").color(SystemMessageUtils.SECONDARY));
                return;
            }

            String[] args = CommandUtils.getArgs(ctx);
            MinePlayerProgress progress = mineStore.getOrCreatePlayer(playerRef.getUuid());

            if (args.length == 0) {
                MineBagPage page = new MineBagPage(playerRef, progress);
                player.getPageManager().openCustomPage(ref, store, page);
                return;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "sell" -> {
                    MineSellPage page = new MineSellPage(playerRef, progress);
                    player.getPageManager().openCustomPage(ref, store, page);
                }
                case "upgrades" -> {
                    MineUpgradePage page = new MineUpgradePage(playerRef, progress);
                    player.getPageManager().openCustomPage(ref, store, page);
                }
                default -> player.sendMessage(Message.raw("Unknown subcommand. Use: /mine, /mine sell, /mine upgrades"));
            }
        }, world);
    }
}
