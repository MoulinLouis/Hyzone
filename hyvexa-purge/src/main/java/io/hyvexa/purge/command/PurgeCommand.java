package io.hyvexa.purge.command;

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
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.purge.data.PurgePlayerStats;
import io.hyvexa.purge.data.PurgePlayerStore;
import io.hyvexa.purge.data.PurgeScrapStore;
import io.hyvexa.purge.manager.PurgeSessionManager;
import io.hyvexa.purge.manager.PurgeSpawnPointManager;
import io.hyvexa.purge.ui.PurgeAdminIndexPage;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PurgeCommand extends AbstractAsyncCommand {

    private static final Message MESSAGE_OP_REQUIRED = Message.raw("You must be OP to use /purge admin.");

    private final PurgeSessionManager sessionManager;
    private final PurgeSpawnPointManager spawnPointManager;

    public PurgeCommand(PurgeSessionManager sessionManager, PurgeSpawnPointManager spawnPointManager) {
        super("purge", "Purge zombie survival commands");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
        this.sessionManager = sessionManager;
        this.spawnPointManager = spawnPointManager;
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
        return CompletableFuture.runAsync(() -> handleCommand(ctx, player, ref, store, world), world);
    }

    private void handleCommand(CommandContext ctx, Player player, Ref<EntityStore> ref,
                               Store<EntityStore> store, World world) {
        String[] args = CommandUtils.getArgs(ctx);
        if (args.length == 0) {
            player.sendMessage(Message.raw("Usage: /purge <start|stop|stats|admin>"));
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;
        if (playerId == null) {
            player.sendMessage(Message.raw("Could not identify player."));
            return;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "start" -> handleStart(player, ref, world, playerId);
            case "stop" -> handleStop(player, playerId);
            case "stats" -> handleStats(player, playerId);
            case "admin" -> openAdminMenu(player, ref, store);
            default -> player.sendMessage(Message.raw("Usage: /purge <start|stop|stats|admin>"));
        }
    }

    private void openAdminMenu(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!PermissionUtils.isOp(player)) {
            player.sendMessage(MESSAGE_OP_REQUIRED);
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgeAdminIndexPage(playerRef, spawnPointManager));
    }

    private void handleStart(Player player, Ref<EntityStore> ref, World world, UUID playerId) {
        if (!ModeGate.isPurgeWorld(world)) {
            player.sendMessage(Message.raw("You must be in the Purge world to start a session."));
            return;
        }
        boolean started = sessionManager.startSession(playerId, ref);
        if (started) {
            player.sendMessage(Message.raw("Purge session starting..."));
        }
    }

    private void handleStop(Player player, UUID playerId) {
        if (!sessionManager.hasActiveSession(playerId)) {
            player.sendMessage(Message.raw("No active Purge session."));
            return;
        }
        sessionManager.stopSession(playerId, "voluntary stop");
    }

    private void handleStats(Player player, UUID playerId) {
        PurgePlayerStats stats = PurgePlayerStore.getInstance().getOrCreate(playerId);
        long scrap = PurgeScrapStore.getInstance().getScrap(playerId);
        player.sendMessage(Message.raw("-- Purge Stats --"));
        player.sendMessage(Message.raw("Best wave: " + stats.getBestWave()));
        player.sendMessage(Message.raw("Total kills: " + stats.getTotalKills()));
        player.sendMessage(Message.raw("Total sessions: " + stats.getTotalSessions()));
        player.sendMessage(Message.raw("Scrap: " + scrap));
    }
}
