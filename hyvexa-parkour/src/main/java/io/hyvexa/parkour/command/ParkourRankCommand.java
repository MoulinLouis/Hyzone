package io.hyvexa.parkour.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.util.ParkourModeGate;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ParkourRankCommand extends AbstractAsyncCommand {

    private static final Message MESSAGE_OP_REQUIRED = Message.raw("You must be OP to use /pkrank.");

    private final ProgressStore progressStore;

    public ParkourRankCommand(ProgressStore progressStore) {
        super("pkrank", "Set a player's rank for Tebex rewards.");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
        this.progressStore = progressStore;
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        if (sender instanceof Player player) {
            if (ParkourModeGate.denyIfNotParkour(commandContext, ParkourModeGate.resolvePlayerId(player))) {
                return CompletableFuture.completedFuture(null);
            }
            if (!PermissionUtils.isOp(player)) {
                commandContext.sendMessage(MESSAGE_OP_REQUIRED);
                return CompletableFuture.completedFuture(null);
            }
        }

        String[] tokens = tokenize(commandContext);
        if (tokens.length < 2) {
            commandContext.sendMessage(Message.raw("Usage: /pkrank <player|uuid> <vip|founder|none>"));
            return CompletableFuture.completedFuture(null);
        }

        if (progressStore == null) {
            commandContext.sendMessage(Message.raw("Progress store not available.").color("#ff4444"));
            return CompletableFuture.completedFuture(null);
        }

        String target = tokens[0];
        String rankInput = tokens[1].toLowerCase(Locale.ROOT);
        RankChoice rankChoice = RankChoice.fromInput(rankInput);
        if (rankChoice == null) {
            commandContext.sendMessage(Message.raw("Unknown rank: " + rankInput + ". Use vip, founder, or none."));
            return CompletableFuture.completedFuture(null);
        }

        PlayerRef onlineMatch = findOnlineByName(target);
        UUID playerId = onlineMatch != null ? onlineMatch.getUuid() : parseUuid(target);
        if (playerId == null) {
            playerId = progressStore.getPlayerIdByName(target);
        }
        if (playerId == null) {
            commandContext.sendMessage(Message.raw("Player not found: " + target).color("#ff4444"));
            return CompletableFuture.completedFuture(null);
        }

        String resolvedName = onlineMatch != null ? onlineMatch.getUsername() : progressStore.getPlayerName(playerId);
        if (resolvedName == null || resolvedName.isBlank()) {
            resolvedName = target;
        }

        boolean changed = progressStore.setPlayerRank(playerId, resolvedName, rankChoice.vip, rankChoice.founder);
        if (changed) {
            commandContext.sendMessage(Message.raw("Rank updated for " + resolvedName + " (" + rankChoice.label + ").")
                    .color("#44ff44"));
        } else {
            commandContext.sendMessage(Message.raw("Rank already set for " + resolvedName + " (" + rankChoice.label + ").")
                    .color("#ffaa00"));
        }
        return CompletableFuture.completedFuture(null);
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

    private enum RankChoice {
        VIP("vip", true, false),
        FOUNDER("founder", false, true),
        NONE("none", false, false);

        private final String label;
        private final boolean vip;
        private final boolean founder;

        RankChoice(String label, boolean vip, boolean founder) {
            this.label = label;
            this.vip = vip;
            this.founder = founder;
        }

        private static RankChoice fromInput(String input) {
            if (input == null) {
                return null;
            }
            return switch (input) {
                case "vip" -> VIP;
                case "founder" -> FOUNDER;
                case "none", "clear", "remove" -> NONE;
                default -> null;
            };
        }
    }
}
