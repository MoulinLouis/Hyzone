package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.parkour.ParkourConstants;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.PlayerCountStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.ui.AdminIndexPage;
import io.hyvexa.parkour.ui.CategorySelectPage;
import io.hyvexa.parkour.ui.LeaderboardMenuPage;
import io.hyvexa.parkour.ui.StatsPage;
import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

import static com.hypixel.hytale.server.core.command.commands.player.inventory.InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD;

public class ParkourCommand extends AbstractAsyncCommand {

    private static final Message MESSAGE_OP_REQUIRED = Message.raw("You must be OP to use /pk admin.");

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final SettingsStore settingsStore;
    private final PlayerCountStore playerCountStore;
    private final RunTracker runTracker;

    public ParkourCommand(MapStore mapStore, ProgressStore progressStore, SettingsStore settingsStore,
                          PlayerCountStore playerCountStore, RunTracker runTracker) {
        super("pk", "Open the parkour map list.");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.settingsStore = settingsStore;
        this.playerCountStore = playerCountStore;
        this.runTracker = runTracker;
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        if (!(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            return CompletableFuture.runAsync(() -> {
                handleCommand(commandContext, player, ref, store);
            }, world);
        }
        commandContext.sendMessage(MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
        return CompletableFuture.completedFuture(null);
    }

    private void handleCommand(CommandContext ctx, Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        String[] tokens = tokenize(ctx);
        if (tokens.length == 0 || tokens[0].equalsIgnoreCase("ui")) {
            PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRefComponent != null) {
                player.getPageManager().openCustomPage(ref, store,
                        new CategorySelectPage(playerRefComponent, mapStore, progressStore, runTracker));
            }
            return;
        }
        if (tokens[0].equalsIgnoreCase("leaderboard")) {
            openLeaderboardMenu(player, ref, store);
            return;
        }
        if (tokens[0].equalsIgnoreCase("stats")) {
            showStats(player, store, ref);
            return;
        }
        if (tokens[0].equalsIgnoreCase("admin")) {
            openAdminMenu(ctx, player, store, ref);
            return;
        }
        if (tokens[0].equalsIgnoreCase("items") || tokens[0].equalsIgnoreCase("item")) {
            giveItems(ctx, store, ref);
            return;
        }
        ctx.sendMessage(Message.raw("Usage: /pk [leaderboard|stats|admin|items]"));
    }

    private void openLeaderboardMenu(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new LeaderboardMenuPage(playerRef, mapStore, progressStore, runTracker));
    }

    private void showStats(Player player, Store<EntityStore> store, Ref<EntityStore> ref) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new StatsPage(playerRef, progressStore, mapStore));
    }

    private void openAdminMenu(CommandContext ctx, Player player, Store<EntityStore> store, Ref<EntityStore> ref) {
        if (!PermissionUtils.isOp(player)) {
            ctx.sendMessage(MESSAGE_OP_REQUIRED);
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new AdminIndexPage(playerRef, mapStore, progressStore, settingsStore, playerCountStore));
    }

    private void giveItems(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        var inventory = player.getInventory();
        if (inventory == null) {
            ctx.sendMessage(Message.raw("Inventory not available."));
            return;
        }
        var hotbar = inventory.getHotbar();
        if (hotbar == null) {
            ctx.sendMessage(Message.raw("Hotbar not available."));
            return;
        }
        hotbar.setItemStackForSlot((short) 0, new ItemStack(ParkourConstants.ITEM_MENU, 1));
        hotbar.setItemStackForSlot((short) 1, new ItemStack(ParkourConstants.ITEM_LEADERBOARD, 1));
        hotbar.setItemStackForSlot((short) 2, new ItemStack(ParkourConstants.ITEM_STATS, 1));
        hotbar.setItemStackForSlot((short) 3, new ItemStack(ParkourConstants.ITEM_ADMIN_REMOTE, 1));
        player.sendMessage(Message.raw("Parkour items added to your inventory."));
    }
    private static String[] tokenize(CommandContext ctx) {
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
}
