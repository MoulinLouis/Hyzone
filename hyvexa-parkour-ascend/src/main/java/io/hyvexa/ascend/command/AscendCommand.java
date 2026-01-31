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
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.ui.AscendMapSelectPage;
import io.hyvexa.ascend.util.AscendModeGate;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class AscendCommand extends AbstractAsyncCommand {

    public AscendCommand() {
        super("ascend", "Open the Ascend menu");
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

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player not in world."));
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            if (AscendModeGate.denyIfNotAscend(ctx, world)) {
                return;
            }

            String[] args = getArgs(ctx);

            if (args.length == 0) {
                openMapMenu(player, playerRef, ref, store);
                return;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "collect" -> handleCollect(player, playerRef);
                case "stats" -> showStatus(player, playerRef);
                default -> ctx.sendMessage(Message.raw("Unknown subcommand. Use: /ascend, /ascend collect, /ascend stats"));
            }
        }, world);
    }

    private String[] getArgs(CommandContext ctx) {
        String input = ctx.getInputString();
        if (input == null || input.trim().isEmpty()) {
            return new String[0];
        }
        String[] tokens = input.trim().split("\\s+");
        if (tokens.length == 0) {
            return tokens;
        }
        String first = tokens[0];
        if (first.startsWith("/")) {
            first = first.substring(1);
        }
        String commandName = ctx.getCalledCommand().getName();
        if (first.equalsIgnoreCase(commandName)) {
            if (tokens.length == 1) {
                return new String[0];
            }
            String[] trimmed = new String[tokens.length - 1];
            System.arraycopy(tokens, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return tokens;
    }

    private void showStatus(Player player, PlayerRef playerRef) {
        AscendPlayerStore playerStore = ParkourAscendPlugin.getInstance().getPlayerStore();
        long coins = playerStore.getCoins(playerRef.getUuid());
        long pending = playerStore.getTotalPendingCoins(playerRef.getUuid());
        player.sendMessage(Message.raw("[Ascend] Coins: " + coins + " | Pending: " + pending)
            .color(SystemMessageUtils.PRIMARY_TEXT));
    }

    private void openMapMenu(Player player, PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        AscendMapStore mapStore = plugin.getMapStore();
        AscendPlayerStore playerStore = plugin.getPlayerStore();
        AscendRunTracker runTracker = plugin.getRunTracker();
        if (mapStore == null || playerStore == null || runTracker == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
            new AscendMapSelectPage(playerRef, mapStore, playerStore, runTracker));
    }

    private void handleCollect(Player player, PlayerRef playerRef) {
        AscendPlayerStore playerStore = ParkourAscendPlugin.getInstance().getPlayerStore();
        long totalPending = playerStore.collectPendingCoins(playerRef.getUuid());
        if (totalPending <= 0) {
            player.sendMessage(Message.raw("[Ascend] No earnings to collect.").color(SystemMessageUtils.SECONDARY));
            return;
        }
        player.sendMessage(Message.raw("[Ascend] Collected " + totalPending + " coins!")
            .color(SystemMessageUtils.SUCCESS));
    }

}
