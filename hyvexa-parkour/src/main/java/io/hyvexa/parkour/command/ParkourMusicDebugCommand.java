package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.ambiencefx.config.AmbienceFX;
import com.hypixel.hytale.server.core.asset.type.ambiencefx.config.AmbienceFXMusic;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.parkour.util.ParkourModeGate;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.hypixel.hytale.server.core.command.commands.player.inventory.InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD;

public class ParkourMusicDebugCommand extends AbstractAsyncCommand {

    public ParkourMusicDebugCommand() {
        super("pkmusic", "Show loaded ambience music assets.");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    @Nonnull
    protected java.util.concurrent.CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        if (!(sender instanceof Player player)) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            commandContext.sendMessage(MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (ParkourModeGate.denyIfNotParkour(commandContext, world)) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            handleCommand(commandContext, player);
        }, world);
    }

    private void handleCommand(CommandContext ctx, Player player) {
        String[] tokens = tokenize(ctx);
        if (tokens.length == 0 || !tokens[0].equalsIgnoreCase("debug")) {
            player.sendMessage(Message.raw("Usage: /pkmusic debug [filter]"));
            return;
        }
        String filter = tokens.length >= 2 ? tokens[1] : "";
        Map<String, AmbienceFX> assets = AmbienceFX.getAssetMap().getAssetMap();
        if (assets.isEmpty()) {
            player.sendMessage(Message.raw("No ambience assets loaded."));
            return;
        }
        List<String> matches = new ArrayList<>();
        for (Map.Entry<String, AmbienceFX> entry : assets.entrySet()) {
            AmbienceFXMusic music = entry.getValue().getMusic();
            if (music == null || music.getTracks() == null || music.getTracks().length == 0) {
                continue;
            }
            String id = entry.getKey();
            if (!filter.isBlank() && !id.toLowerCase().contains(filter.toLowerCase())) {
                continue;
            }
            matches.add(id);
        }
        if (matches.isEmpty()) {
            player.sendMessage(Message.raw("No ambience music entries match '" + filter + "'."));
            return;
        }
        Collections.sort(matches, String.CASE_INSENSITIVE_ORDER);
        int limit = Math.min(20, matches.size());
        player.sendMessage(Message.raw("Ambience music assets (" + matches.size() + " total):"));
        for (int i = 0; i < limit; i++) {
            AmbienceFXMusic music = assets.get(matches.get(i)).getMusic();
            String tracks = music != null && music.getTracks() != null
                    ? String.join(", ", music.getTracks())
                    : "(none)";
            player.sendMessage(Message.raw("- " + matches.get(i) + " -> " + tracks));
        }
        if (matches.size() > limit) {
            player.sendMessage(Message.raw("...and " + (matches.size() - limit) + " more"));
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
}
