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

    private final DiscordLinkStore discordLinkStore;

    public LinkCommand(DiscordLinkStore discordLinkStore) {
        super("link", "Link your Discord account");
        this.setPermissionGroup(GameMode.Adventure);
        this.discordLinkStore = discordLinkStore;
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
        if (store.getExternalData() == null) return CompletableFuture.completedFuture(null);
        World world = store.getExternalData().getWorld();
        return CompletableFuture.runAsync(() -> handleLink(player, ref, store), world);
    }

    private void handleLink(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!ref.isValid()) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }

        // Check if already linked
        if (discordLinkStore.isLinked(playerId)) {
            player.sendMessage(Message.raw("Your account is already linked to Discord!").color("#a3e635"));
            return;
        }

        // Generate or retrieve existing code
        String code = discordLinkStore.generateCode(playerId);
        if (code == null) {
            player.sendMessage(Message.raw("Failed to generate link code. Try again later.").color("#ef4444"));
            return;
        }

        // Display formatted message
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.join(
                Message.raw("Your link code: ").color(ChatColors.MUTED_COLOR),
                Message.raw(code).color(ChatColors.HIGHLIGHT_COLOR).bold(true)
        ));
        player.sendMessage(Message.join(
                Message.raw("Go to Discord and type ").color(ChatColors.MUTED_COLOR),
                Message.raw("/link " + code).color(ChatColors.HIGHLIGHT_COLOR)
        ));
        player.sendMessage(Message.raw("Code expires in 5 minutes.").color(ChatColors.MUTED_COLOR));
        player.sendMessage(Message.join(
                Message.raw("Discord: ").color(ChatColors.MUTED_COLOR),
                Message.raw("discord.gg/2PAygkyFnK").color(ChatColors.LINK_COLOR).link(ChatColors.DISCORD_URL)
        ));
        player.sendMessage(Message.raw(""));
    }
}
