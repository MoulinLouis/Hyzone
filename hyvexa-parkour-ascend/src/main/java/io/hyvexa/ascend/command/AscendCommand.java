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
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.ui.AscendMapSelectPage;
import io.hyvexa.ascend.util.AscendModeGate;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.List;
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

            String[] args = CommandUtils.getArgs(ctx);

            if (args.length == 0) {
                openMapMenu(player, playerRef, ref, store);
                return;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "stats" -> showStatus(player, playerRef);
                case "elevate" -> handleElevate(player, playerRef);
                default -> ctx.sendMessage(Message.raw("Unknown subcommand. Use: /ascend, /ascend stats, /ascend elevate"));
            }
        }, world);
    }

    private void showStatus(Player player, PlayerRef playerRef) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getPlayerStore() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        AscendPlayerStore playerStore = plugin.getPlayerStore();
        long coins = playerStore.getCoins(playerRef.getUuid());
        AscendMapStore mapStore = plugin.getMapStore();
        List<AscendMap> maps = mapStore != null ? mapStore.listMapsSorted() : List.of();
        long product = playerStore.getMultiplierProduct(playerRef.getUuid(), maps, AscendConstants.MULTIPLIER_SLOTS);
        int elevationMultiplier = playerStore.getElevationMultiplier(playerRef.getUuid());
        double[] digits = playerStore.getMultiplierDisplayValues(playerRef.getUuid(), maps, AscendConstants.MULTIPLIER_SLOTS);
        StringBuilder digitsText = new StringBuilder();
        for (int i = 0; i < digits.length; i++) {
            if (i > 0) {
                digitsText.append('x');
            }
            digitsText.append(String.format(java.util.Locale.US, "%.2f", Math.max(1.0, digits[i])));
        }
        player.sendMessage(Message.raw("[Ascend] Coins: " + coins + " | Product: " + product
            + " | Elevation: x" + elevationMultiplier + " | Digits: " + digitsText)
            .color(SystemMessageUtils.PRIMARY_TEXT));
    }

    private void handleElevate(Player player, PlayerRef playerRef) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getPlayerStore() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        AscendPlayerStore playerStore = plugin.getPlayerStore();
        long coins = playerStore.getCoins(playerRef.getUuid());
        int gain = (int) (coins / 1000L);
        if (gain <= 0) {
            player.sendMessage(Message.raw("[Ascend] You need 1000 coins to elevate.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }
        playerStore.setCoins(playerRef.getUuid(), 0L);
        int newMultiplier = playerStore.addElevationMultiplier(playerRef.getUuid(), gain);
        player.sendMessage(Message.raw("[Ascend] Elevation +" + gain + " (x" + newMultiplier + ").")
            .color(SystemMessageUtils.SUCCESS));
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

}
