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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.HylogramsBridge;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.parkour.ParkourConstants;
import io.hyvexa.core.economy.VexaStore;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.PlayerCountStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.ui.AdminIndexPage;
import io.hyvexa.parkour.ui.CategorySelectPage;
import io.hyvexa.parkour.ui.LeaderboardMenuPage;
import io.hyvexa.parkour.ui.StatsPage;
import io.hyvexa.parkour.util.ParkourModeGate;
import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
            handleConsoleCommand(commandContext);
            return CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            return CompletableFuture.runAsync(() -> {
                handleCommand(commandContext, player, ref, store, world);
            }, world);
        }
        commandContext.sendMessage(MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
        return CompletableFuture.completedFuture(null);
    }

    private void handleConsoleCommand(CommandContext ctx) {
        String[] tokens = CommandUtils.tokenize(ctx);
        if (tokens.length >= 2 && tokens[0].equalsIgnoreCase("admin")) {
            if (tokens[1].equalsIgnoreCase("rank")) {
                handleAdminRank(ctx, null, tokens);
                return;
            }
            if (tokens[1].equalsIgnoreCase("vexa")) {
                handleAdminVexa(ctx, null, tokens);
                return;
            }
        }
        ctx.sendMessage(Message.raw("Usage: /pk admin rank <give|remove|broadcast> <player|uuid> <vip|founder>"));
        ctx.sendMessage(Message.raw("Usage: /pk admin vexa give <player|uuid> <amount>"));
    }

    private void handleCommand(CommandContext ctx, Player player, Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
        if (ParkourModeGate.denyIfNotParkour(ctx, world)) {
            return;
        }
        if (playerRefComponent != null) {
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin != null && plugin.getDuelTracker() != null
                    && plugin.getDuelTracker().isInMatch(playerRefComponent.getUuid())) {
                ctx.sendMessage(Message.raw("You can't use parkour commands during a duel."));
                return;
            }
        }
        String[] tokens = CommandUtils.tokenize(ctx);
        if (tokens.length == 0 || tokens[0].equalsIgnoreCase("ui")) {
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
            if (tokens.length >= 2 && tokens[1].equalsIgnoreCase("rank")) {
                handleAdminRank(ctx, player, tokens);
                return;
            }
            if (tokens.length >= 2 && tokens[1].equalsIgnoreCase("vexa")) {
                handleAdminVexa(ctx, player, tokens);
                return;
            }
            if (tokens.length >= 3 && tokens[1].equalsIgnoreCase("hologram")
                    && tokens[2].equalsIgnoreCase("refresh")) {
                handleAdminHologramRefresh(ctx, player, store);
                return;
            }
            openAdminMenu(ctx, player, store, ref);
            return;
        }
        if (tokens[0].equalsIgnoreCase("items") || tokens[0].equalsIgnoreCase("item")) {
            giveItems(ctx, store, ref);
            return;
        }
        if (tokens[0].equalsIgnoreCase("holograms")) {
            listHolograms(ctx);
            return;
        }
        ctx.sendMessage(Message.raw("Usage: /pk [leaderboard|stats|admin|items|holograms]"));
        ctx.sendMessage(Message.raw("Usage: /pk admin hologram refresh"));
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

    private record ResolvedPlayer(UUID playerId, String name) {}

    private ResolvedPlayer resolvePlayer(CommandContext ctx, String target) {
        PlayerRef onlineMatch = findOnlineByName(target);
        UUID playerId = onlineMatch != null ? onlineMatch.getUuid() : parseUuid(target);
        if (playerId == null && progressStore != null) {
            playerId = progressStore.getPlayerIdByName(target);
        }
        if (playerId == null) {
            ctx.sendMessage(Message.raw("Player not found: " + target).color("#ff4444"));
            return null;
        }
        String resolvedName = onlineMatch != null ? onlineMatch.getUsername()
                : (progressStore != null ? progressStore.getPlayerName(playerId) : null);
        if (resolvedName == null || resolvedName.isBlank()) {
            resolvedName = target;
        }
        return new ResolvedPlayer(playerId, resolvedName);
    }

    private void handleAdminRank(CommandContext ctx, Player player, String[] tokens) {
        if (player != null && !PermissionUtils.isOp(player)) {
            ctx.sendMessage(MESSAGE_OP_REQUIRED);
            return;
        }
        if (progressStore == null) {
            ctx.sendMessage(Message.raw("Progress store not available.").color("#ff4444"));
            return;
        }
        if (tokens.length >= 3 && tokens[2].equalsIgnoreCase("broadcast")) {
            handleAdminRankBroadcast(ctx, tokens);
            return;
        }
        if (tokens.length < 5) {
            ctx.sendMessage(Message.raw("Usage: /pk admin rank <give|remove> <player|uuid> <vip|founder>"));
            ctx.sendMessage(Message.raw("Usage: /pk admin rank broadcast <player|uuid> <vip|founder>"));
            return;
        }
        String action = tokens[2].toLowerCase(Locale.ROOT);
        String target = tokens[3];
        String rankInput = tokens[4].toLowerCase(Locale.ROOT);

        boolean isAdd = "give".equals(action);
        boolean isRemove = "remove".equals(action);
        if (!isAdd && !isRemove) {
            ctx.sendMessage(Message.raw("Unknown action: " + action + ". Use give or remove."));
            return;
        }
        boolean isVip = "vip".equals(rankInput);
        boolean isFounder = "founder".equals(rankInput);
        if (!isVip && !isFounder) {
            ctx.sendMessage(Message.raw("Unknown rank: " + rankInput + ". Use vip or founder."));
            return;
        }

        ResolvedPlayer resolved = resolvePlayer(ctx, target);
        if (resolved == null) {
            return;
        }

        boolean currentVip = progressStore.isVip(resolved.playerId);
        boolean currentFounder = progressStore.isFounder(resolved.playerId);
        boolean newVip = isVip ? isAdd : currentVip;
        boolean newFounder = isFounder ? isAdd : currentFounder;
        // Rank precedence: Founder implies VIP. Removing VIP from a Founder keeps VIP.
        // Removing Founder always clears Founder but preserves any standalone VIP.
        if (isVip && isRemove) {
            newVip = currentFounder;
        } else if (isFounder && isRemove) {
            newFounder = false;
        }

        boolean changed = progressStore.setPlayerRank(resolved.playerId, resolved.name, newVip, newFounder);
        String label = (isVip ? "VIP" : "Founder") + " " + (isAdd ? "added" : "removed");
        if (changed) {
            ctx.sendMessage(Message.raw(label + " for " + resolved.name + ".").color("#44ff44"));
        } else {
            ctx.sendMessage(Message.raw(resolved.name + " already has that rank setting.").color("#ffaa00"));
        }
    }

    private void handleAdminRankBroadcast(CommandContext ctx, String[] tokens) {
        if (tokens.length < 5) {
            ctx.sendMessage(Message.raw("Usage: /pk admin rank broadcast <player|uuid> <vip|founder>"));
            return;
        }
        if (progressStore == null) {
            ctx.sendMessage(Message.raw("Progress store not available.").color("#ff4444"));
            return;
        }
        String rankInput = tokens[4].toLowerCase(Locale.ROOT);
        boolean isFounder = "founder".equals(rankInput);
        if (!"vip".equals(rankInput) && !isFounder) {
            ctx.sendMessage(Message.raw("Unknown rank: " + rankInput + ". Use vip or founder."));
            return;
        }

        ResolvedPlayer resolved = resolvePlayer(ctx, tokens[3]);
        if (resolved == null) {
            return;
        }

        broadcastSupporterMessage(resolved.name, isFounder);
    }

    private void handleAdminVexa(CommandContext ctx, Player player, String[] tokens) {
        if (player != null && !PermissionUtils.isOp(player)) {
            ctx.sendMessage(MESSAGE_OP_REQUIRED);
            return;
        }
        if (tokens.length < 5) {
            ctx.sendMessage(Message.raw("Usage: /pk admin vexa give <player|uuid> <amount>"));
            return;
        }
        String action = tokens[2].toLowerCase(Locale.ROOT);
        if (!"give".equals(action)) {
            ctx.sendMessage(Message.raw("Unknown action: " + action + ". Use give."));
            return;
        }
        String target = tokens[3];
        long amount;
        try {
            amount = Long.parseLong(tokens[4]);
        } catch (NumberFormatException e) {
            ctx.sendMessage(Message.raw("Invalid amount: " + tokens[4]).color("#ff4444"));
            return;
        }
        if (amount <= 0) {
            ctx.sendMessage(Message.raw("Amount must be positive.").color("#ff4444"));
            return;
        }

        ResolvedPlayer resolved = resolvePlayer(ctx, target);
        if (resolved == null) {
            return;
        }

        long newBalance = VexaStore.getInstance().addVexa(resolved.playerId, amount);
        ctx.sendMessage(Message.raw("Gave " + amount + " vexa to " + resolved.name + " (balance: " + newBalance + ")").color("#44ff44"));
    }

    private void broadcastSupporterMessage(String playerName, boolean founder) {
        Message rankPart = Message.raw(founder ? "FOUNDER" : "VIP")
                .color(founder ? "#ff8a3d" : "#b76cff");
        Message message = SystemMessageUtils.withServerPrefix(
                Message.raw("[").color(SystemMessageUtils.SECONDARY),
                rankPart,
                Message.raw("] ").color(SystemMessageUtils.SECONDARY),
                Message.raw(playerName).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(" supports our server and helps us a lot!").color(SystemMessageUtils.SECONDARY)
        );
        Message ggMessage = SystemMessageUtils.withServerPrefix(
                Message.raw("SEND GG IN THE CHAT!").color(SystemMessageUtils.SUCCESS).bold(true)
        );
        Universe.get().getWorlds().forEach((id, world) -> world.execute(() -> {
            for (PlayerRef targetRef : world.getPlayerRefs()) {
                targetRef.sendMessage(message);
                targetRef.sendMessage(ggMessage);
            }
        }));
    }

    private static PlayerRef findOnlineByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String target = name.trim().toLowerCase(Locale.ROOT);
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            String username = playerRef.getUsername();
            if (username != null && username.toLowerCase(Locale.ROOT).equals(target)) {
                return playerRef;
            }
        }
        return null;
    }

    private static UUID parseUuid(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(input.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
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
        hotbar.setItemStackForSlot((short) 8, new ItemStack(ParkourConstants.ITEM_HUB_MENU, 1));
        player.sendMessage(Message.raw("Parkour items added to your inventory."));
    }

    private void listHolograms(CommandContext ctx) {
        try {
            var names = HylogramsBridge.listHologramNames();
            if (names.isEmpty()) {
                ctx.sendMessage(SystemMessageUtils.serverInfo("No holograms found."));
                return;
            }
            ctx.sendMessage(SystemMessageUtils.serverInfo("Holograms (" + names.size() + "): " + String.join(", ", names)));
        } catch (IllegalStateException e) {
            ctx.sendMessage(SystemMessageUtils.serverError(e.getMessage()));
        } catch (Exception e) {
            ctx.sendMessage(SystemMessageUtils.serverError("Failed to list holograms."));
        }
    }

    private void handleAdminHologramRefresh(CommandContext ctx, Player player, Store<EntityStore> store) {
        if (player != null && !PermissionUtils.isOp(player)) {
            ctx.sendMessage(MESSAGE_OP_REQUIRED);
            return;
        }
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin == null) {
            ctx.sendMessage(SystemMessageUtils.serverError("Plugin not available."));
            return;
        }
        if (!HylogramsBridge.isAvailable()) {
            ctx.sendMessage(SystemMessageUtils.serverError("Hylograms plugin is not available."));
            return;
        }
        if (store == null) {
            ctx.sendMessage(SystemMessageUtils.serverError("Player store not available."));
            return;
        }
        plugin.refreshLeaderboardHologram(store);
        ctx.sendMessage(SystemMessageUtils.serverInfo("Leaderboard hologram refreshed."));
    }

}
