package io.hyvexa.parkour.command;

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
import io.hyvexa.core.discord.DiscordLinkStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LinkCommand extends AbstractAsyncCommand {

    private static final String DISCORD_URL = "https://discord.gg/2PAygkyFnK";
    private static final String LINK_COLOR = "#8ab4f8";
    private static final String HIGHLIGHT_COLOR = "#fbbf24";
    private static final String MUTED_COLOR = "#94a3b8";

    public LinkCommand() {
        super("link", "Link your Discord account");
        this.setPermissionGroup(GameMode.Adventure);
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
        return CompletableFuture.runAsync(() -> handleLink(player), world);
    }

    private void handleLink(Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }

        DiscordLinkStore linkStore = DiscordLinkStore.getInstance();

        // Check if already linked
        if (linkStore.isLinked(playerId)) {
            player.sendMessage(Message.raw("Your account is already linked to Discord!").color("#a3e635"));
            return;
        }

        // Generate or retrieve existing code
        String code = linkStore.generateCode(playerId);
        if (code == null) {
            player.sendMessage(Message.raw("Failed to generate link code. Try again later.").color("#ef4444"));
            return;
        }

        // Display formatted message
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.join(
                Message.raw("Your link code: ").color(MUTED_COLOR),
                Message.raw(code).color(HIGHLIGHT_COLOR).bold(true)
        ));
        player.sendMessage(Message.join(
                Message.raw("Go to Discord and type ").color(MUTED_COLOR),
                Message.raw("/link " + code).color(HIGHLIGHT_COLOR)
        ));
        player.sendMessage(Message.raw("Code expires in 5 minutes.").color(MUTED_COLOR));
        player.sendMessage(Message.join(
                Message.raw("Discord: ").color(MUTED_COLOR),
                Message.raw("discord.gg/2PAygkyFnK").color(LINK_COLOR).link(DISCORD_URL)
        ));
        player.sendMessage(Message.raw(""));
    }
}
