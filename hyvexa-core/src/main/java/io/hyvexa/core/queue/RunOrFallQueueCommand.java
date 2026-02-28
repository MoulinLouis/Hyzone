package io.hyvexa.core.queue;

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
import io.hyvexa.common.util.CommandUtils;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RunOrFallQueueCommand extends AbstractAsyncCommand {
    private static final String PREFIX = "[RunOrFall] ";

    public RunOrFallQueueCommand() {
        super("rofqueue", "Queue for RunOrFall from any world");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            ctx.sendMessage(Message.raw(PREFIX + "This command can only be used by players."));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return CompletableFuture.completedFuture(null);
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }

        String[] args = CommandUtils.getArgs(ctx);
        return CompletableFuture.runAsync(() -> handle(player, ref, store, world, args), world);
    }

    private void handle(Player player, Ref<EntityStore> ref, Store<EntityStore> store,
                        World world, String[] args) {
        if (!ref.isValid()) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || playerRef.getUuid() == null) {
            player.sendMessage(Message.raw(PREFIX + "Could not resolve your player id."));
            return;
        }

        UUID playerId = playerRef.getUuid();
        String worldName = world.getName() != null ? world.getName() : "unknown";
        RunOrFallQueueStore queueStore = RunOrFallQueueStore.getInstance();

        if (args.length > 0 && "leave".equalsIgnoreCase(args[0])) {
            if (queueStore.dequeue(playerId)) {
                player.sendMessage(Message.raw(PREFIX + "Removed from RunOrFall queue."));
            } else {
                player.sendMessage(Message.raw(PREFIX + "You are not in the queue."));
            }
            return;
        }

        if (queueStore.isQueued(playerId)) {
            queueStore.dequeue(playerId);
            player.sendMessage(Message.raw(PREFIX + "Removed from RunOrFall queue."));
        } else {
            queueStore.enqueue(playerId, worldName);
            int queueSize = queueStore.getQueueSize();
            RunOrFallQueueStore.LobbyInfoProvider info = queueStore.getLobbyInfoProvider();
            if (info != null) {
                player.sendMessage(Message.raw(PREFIX + "Queued for RunOrFall (" + queueSize
                        + " in queue, " + info.getLobbySize() + " in lobby). State: " + info.getGameState()));
            } else {
                player.sendMessage(Message.raw(PREFIX + "Queued for RunOrFall (" + queueSize + " in queue)."));
            }
        }
    }
}
