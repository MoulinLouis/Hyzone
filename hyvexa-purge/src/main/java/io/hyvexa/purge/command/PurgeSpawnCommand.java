package io.hyvexa.purge.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.purge.data.PurgeSpawnPoint;
import io.hyvexa.purge.manager.PurgeSpawnPointManager;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class PurgeSpawnCommand extends AbstractAsyncCommand {

    private final PurgeSpawnPointManager spawnPointManager;

    public PurgeSpawnCommand(PurgeSpawnPointManager spawnPointManager) {
        super("purgespawn", "Manage Purge spawn points");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
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
        if (!PermissionUtils.isOp(player)) {
            ctx.sendMessage(Message.raw("You must be OP to use this command."));
            return CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player not in world."));
            return CompletableFuture.completedFuture(null);
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null) {
            ctx.sendMessage(Message.raw("Could not resolve your world."));
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> handleCommand(ctx, player, ref, store), world);
    }

    private void handleCommand(CommandContext ctx, Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        String[] args = CommandUtils.getArgs(ctx);
        if (args.length == 0) {
            player.sendMessage(Message.raw("Usage: /purgespawn <add|remove|list|clear>"));
            return;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "add" -> handleAdd(player, ref, store);
            case "remove" -> handleRemove(player, args);
            case "list" -> handleList(player);
            case "clear" -> handleClear(player);
            default -> player.sendMessage(Message.raw("Usage: /purgespawn <add|remove|list|clear>"));
        }
    }

    private void handleAdd(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            player.sendMessage(Message.raw("Could not read your position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        float yaw = rot != null ? rot.getY() : 0f;
        int id = spawnPointManager.addSpawnPoint(pos.getX(), pos.getY(), pos.getZ(), yaw);
        if (id > 0) {
            player.sendMessage(Message.raw("Spawn point #" + id + " added at "
                    + String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ())));
        } else if (!spawnPointManager.isPersistenceAvailable()) {
            player.sendMessage(Message.raw(spawnPointManager.getPersistenceDisabledMessage()));
        } else {
            player.sendMessage(Message.raw("Failed to add spawn point."));
        }
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw("Usage: /purgespawn remove <id>"));
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw("ID must be a number."));
            return;
        }
        if (spawnPointManager.removeSpawnPoint(id)) {
            player.sendMessage(Message.raw("Spawn point #" + id + " removed."));
        } else if (!spawnPointManager.isPersistenceAvailable()) {
            player.sendMessage(Message.raw(spawnPointManager.getPersistenceDisabledMessage()));
        } else {
            player.sendMessage(Message.raw("Spawn point #" + id + " not found."));
        }
    }

    private void handleList(Player player) {
        Collection<PurgeSpawnPoint> points = spawnPointManager.getAll();
        if (points.isEmpty()) {
            player.sendMessage(Message.raw("No spawn points configured."));
            return;
        }
        player.sendMessage(Message.raw("-- Purge Spawn Points (" + points.size() + ") --"));
        for (PurgeSpawnPoint p : points) {
            player.sendMessage(Message.raw("#" + p.id() + ": "
                    + String.format("%.1f, %.1f, %.1f (yaw: %.1f)", p.x(), p.y(), p.z(), p.yaw())));
        }
    }

    private void handleClear(Player player) {
        if (!spawnPointManager.isPersistenceAvailable()) {
            player.sendMessage(Message.raw(spawnPointManager.getPersistenceDisabledMessage()));
            return;
        }
        spawnPointManager.clearAll();
        player.sendMessage(Message.raw("All spawn points cleared."));
    }
}
