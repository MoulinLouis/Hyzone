package io.hyvexa.duel.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.duel.DuelConstants;
import io.hyvexa.duel.DuelMatch;
import io.hyvexa.duel.DuelTracker;
import io.hyvexa.duel.data.DuelPreferenceStore;
import io.hyvexa.duel.data.DuelStats;
import io.hyvexa.duel.data.DuelStatsStore;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.tracker.RunTracker;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.hypixel.hytale.server.core.command.commands.player.inventory.InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD;

public class DuelCommand extends AbstractAsyncCommand {

    private final DuelTracker duelTracker;
    private final RunTracker runTracker;

    public DuelCommand(DuelTracker duelTracker, RunTracker runTracker) {
        super("duel", "Join the duel queue.");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
        this.duelTracker = duelTracker;
        this.runTracker = runTracker;
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        if (!(sender instanceof Player player)) {
            commandContext.sendMessage(Message.raw("This command can only be used in-game."));
            return CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            return CompletableFuture.runAsync(() -> handleCommand(commandContext, player, ref, store), world);
        }
        commandContext.sendMessage(MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
        return CompletableFuture.completedFuture(null);
    }

    private void handleCommand(CommandContext ctx, Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        String[] tokens = tokenize(ctx);
        if (tokens.length == 0) {
            handleJoin(ctx, playerRef);
            return;
        }
        String sub = tokens[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "leave" -> handleLeave(ctx, playerRef);
            case "forfeit" -> handleForfeit(ctx, playerRef);
            case "stats" -> handleStats(ctx, playerRef, tokens);
            case "admin" -> handleAdmin(ctx, playerRef, player, tokens);
            default -> ctx.sendMessage(Message.raw("Usage: /duel [leave|forfeit|stats|admin]"));
        }
    }

    private void handleJoin(CommandContext ctx, PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        if (duelTracker.isInMatch(playerId)) {
            ctx.sendMessage(Message.raw(DuelConstants.MSG_IN_MATCH));
            return;
        }
        if (duelTracker.isQueued(playerId)) {
            int pos = duelTracker.getQueuePosition(playerId);
            ctx.sendMessage(Message.raw(String.format(DuelConstants.MSG_QUEUE_ALREADY, pos)));
            return;
        }
        if (runTracker != null && runTracker.getActiveMapId(playerId) != null) {
            ctx.sendMessage(Message.raw(DuelConstants.MSG_IN_PARKOUR));
            return;
        }
        if (!duelTracker.hasAvailableMaps(playerId)) {
            ctx.sendMessage(Message.raw(DuelConstants.MSG_NO_MAPS));
            return;
        }
        boolean joined = duelTracker.enqueue(playerId);
        if (!joined) {
            int pos = duelTracker.getQueuePosition(playerId);
            ctx.sendMessage(Message.raw(String.format(DuelConstants.MSG_QUEUE_ALREADY, pos)));
            return;
        }
        int pos = duelTracker.getQueuePosition(playerId);
        String categories = resolveCategoryLabel(playerId);
        ctx.sendMessage(Message.raw(String.format(DuelConstants.MSG_QUEUE_JOINED, categories, pos)));
        duelTracker.tryMatch();
    }

    private String resolveCategoryLabel(UUID playerId) {
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        DuelPreferenceStore prefs = plugin != null ? plugin.getDuelPreferenceStore() : null;
        return prefs != null ? prefs.formatEnabledLabel(playerId) : "Easy/Medium/Hard/Insane";
    }

    private void handleLeave(CommandContext ctx, PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        if (duelTracker.dequeue(playerId)) {
            ctx.sendMessage(Message.raw(DuelConstants.MSG_QUEUE_LEFT));
        } else {
            ctx.sendMessage(Message.raw("You're not in the queue."));
        }
    }

    private void handleForfeit(CommandContext ctx, PlayerRef playerRef) {
        if (!duelTracker.isInMatch(playerRef.getUuid())) {
            ctx.sendMessage(Message.raw("You're not in a match."));
            return;
        }
        duelTracker.handleForfeit(playerRef.getUuid());
    }

    private void handleStats(CommandContext ctx, PlayerRef playerRef, String[] tokens) {
        DuelStatsStore statsStore = duelTracker.getStatsStore();
        if (statsStore == null) {
            ctx.sendMessage(Message.raw("Stats store unavailable."));
            return;
        }
        if (tokens.length < 2) {
            DuelStats stats = statsStore.getStats(playerRef.getUuid());
            if (stats == null) {
                ctx.sendMessage(Message.raw(String.format(DuelConstants.MSG_STATS_NONE, playerRef.getUsername())));
                return;
            }
            ctx.sendMessage(Message.raw(String.format(DuelConstants.MSG_STATS, playerRef.getUsername(),
                    stats.getWins(), stats.getLosses(), stats.getWinRate())));
            return;
        }
        String targetName = tokens[1];
        DuelStats stats = null;
        PlayerRef targetRef = findOnlineByName(targetName);
        if (targetRef != null) {
            stats = statsStore.getStats(targetRef.getUuid());
            if (stats != null) {
                ctx.sendMessage(Message.raw(String.format(DuelConstants.MSG_STATS, targetRef.getUsername(),
                        stats.getWins(), stats.getLosses(), stats.getWinRate())));
                return;
            }
        }
        stats = statsStore.getStatsByName(targetName);
        if (stats == null) {
            ctx.sendMessage(Message.raw(String.format(DuelConstants.MSG_STATS_NONE, targetName)));
            return;
        }
        String name = stats.getPlayerName() != null ? stats.getPlayerName() : targetName;
        ctx.sendMessage(Message.raw(String.format(DuelConstants.MSG_STATS, name, stats.getWins(), stats.getLosses(),
                stats.getWinRate())));
    }

    private void handleAdmin(CommandContext ctx, PlayerRef playerRef, Player player, String[] tokens) {
        if (!PermissionUtils.isOp(player)) {
            ctx.sendMessage(Message.raw("You must be OP to use /duel admin."));
            return;
        }
        if (tokens.length < 2) {
            ctx.sendMessage(Message.raw("Usage: /duel admin [maps|queue|matches|cancel|force]"));
            return;
        }
        String sub = tokens[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "maps" -> handleAdminMaps(ctx);
            case "queue" -> handleAdminQueue(ctx);
            case "matches" -> handleAdminMatches(ctx);
            case "cancel" -> handleAdminCancel(ctx, tokens);
            case "force" -> handleAdminForce(ctx, playerRef, tokens);
            default -> ctx.sendMessage(Message.raw("Usage: /duel admin [maps|queue|matches|cancel|force]"));
        }
    }

    private void handleAdminMaps(CommandContext ctx) {
        List<Map> maps = HyvexaPlugin.getInstance().getMapStore().listDuelEnabledMaps();
        if (maps.isEmpty()) {
            ctx.sendMessage(Message.raw("No duel-enabled maps."));
            return;
        }
        ctx.sendMessage(Message.raw("Duel-enabled maps (" + maps.size() + "):"));
        for (Map map : maps) {
            String name = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
            ctx.sendMessage(Message.raw("- " + name + " (" + map.getId() + ")"));
        }
    }

    private void handleAdminQueue(CommandContext ctx) {
        List<UUID> queued = duelTracker.getQueueSnapshot();
        if (queued.isEmpty()) {
            ctx.sendMessage(Message.raw("Duel queue is empty."));
            return;
        }
        ctx.sendMessage(Message.raw("Duel queue (" + queued.size() + "):"));
        int index = 1;
        for (UUID id : queued) {
            PlayerRef ref = Universe.get().getPlayer(id);
            String name = ref != null ? ref.getUsername() : id.toString();
            ctx.sendMessage(Message.raw(index + ". " + name));
            index++;
        }
    }

    private void handleAdminMatches(CommandContext ctx) {
        List<DuelMatch> matches = duelTracker.getActiveMatches();
        if (matches.isEmpty()) {
            ctx.sendMessage(Message.raw("No active matches."));
            return;
        }
        ctx.sendMessage(Message.raw("Active matches (" + matches.size() + "):"));
        for (DuelMatch match : matches) {
            String p1 = resolveName(match.getPlayer1());
            String p2 = resolveName(match.getPlayer2());
            ctx.sendMessage(Message.raw("- " + match.getMatchId() + ": " + p1 + " vs " + p2
                    + " on " + match.getMapId() + " (" + match.getState() + ")"));
        }
    }

    private void handleAdminCancel(CommandContext ctx, String[] tokens) {
        if (tokens.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /duel admin cancel <matchId>"));
            return;
        }
        String matchId = tokens[2];
        duelTracker.cancelMatch(matchId);
        ctx.sendMessage(Message.raw("Canceled match " + matchId + "."));
    }

    private void handleAdminForce(CommandContext ctx, PlayerRef adminRef, String[] tokens) {
        if (tokens.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /duel admin force <player>"));
            return;
        }
        if (adminRef == null) {
            ctx.sendMessage(Message.raw("Admin player not available."));
            return;
        }
        String targetName = tokens[2];
        PlayerRef targetRef = findOnlineByName(targetName);
        if (targetRef == null) {
            ctx.sendMessage(Message.raw("Player not online: " + targetName));
            return;
        }
        UUID adminId = adminRef.getUuid();
        UUID targetId = targetRef.getUuid();
        if (adminId.equals(targetId)) {
            ctx.sendMessage(Message.raw("You can't force a duel against yourself."));
            return;
        }
        if (duelTracker.isInMatch(adminId) || duelTracker.isInMatch(targetId)) {
            ctx.sendMessage(Message.raw("One of the players is already in a match."));
            return;
        }
        if (!duelTracker.hasAvailableMaps(adminId, targetId)) {
            ctx.sendMessage(Message.raw(DuelConstants.MSG_NO_MAPS));
            return;
        }
        boolean started = duelTracker.forceMatch(adminId, targetId);
        if (!started) {
            ctx.sendMessage(Message.raw("Failed to start forced match."));
            return;
        }
        ctx.sendMessage(Message.raw("Forced duel started with " + targetRef.getUsername() + "."));
    }

    private String resolveName(UUID playerId) {
        PlayerRef ref = Universe.get().getPlayer(playerId);
        return ref != null ? ref.getUsername() : playerId.toString();
    }

    private PlayerRef findOnlineByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef != null && name.equalsIgnoreCase(playerRef.getUsername())) {
                return playerRef;
            }
        }
        return null;
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
