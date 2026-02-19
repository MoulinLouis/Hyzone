package io.hyvexa.runorfall.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.runorfall.data.RunOrFallLocation;
import io.hyvexa.runorfall.data.RunOrFallPlatform;
import io.hyvexa.runorfall.manager.RunOrFallConfigStore;
import io.hyvexa.runorfall.manager.RunOrFallGameManager;
import io.hyvexa.runorfall.ui.RunOrFallAdminPage;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RunOrFallCommand extends AbstractAsyncCommand {
    private static final String PREFIX = "[RunOrFall] ";

    private final RunOrFallConfigStore configStore;
    private final RunOrFallGameManager gameManager;
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();

    public RunOrFallCommand(RunOrFallConfigStore configStore, RunOrFallGameManager gameManager) {
        super("rof", "RunOrFall lobby and admin commands");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
        this.configStore = configStore;
        this.gameManager = gameManager;
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
            ctx.sendMessage(Message.raw(PREFIX + "Player not in world."));
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null) {
            ctx.sendMessage(Message.raw(PREFIX + "World not available."));
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> handle(ctx, player, ref, store, world), world);
    }

    private void handle(CommandContext ctx, Player player, Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        if (!ModeGate.isRunOrFallWorld(world)) {
            player.sendMessage(Message.raw(PREFIX + "You must be in the RunOrFall world."));
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || playerRef.getUuid() == null) {
            player.sendMessage(Message.raw(PREFIX + "Could not resolve your player id."));
            return;
        }
        UUID playerId = playerRef.getUuid();
        String[] args = CommandUtils.getArgs(ctx);
        if (args.length == 0) {
            sendUsage(player);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> gameManager.joinLobby(playerId, world);
            case "leave" -> gameManager.leaveLobby(playerId, true);
            case "status" -> sendStatus(player);
            case "admin" -> openAdminMenu(player, ref, store, playerRef);
            case "start" -> {
                if (!requireOp(player)) {
                    return;
                }
                gameManager.requestStart(true);
                player.sendMessage(Message.raw(PREFIX + "Start requested."));
            }
            case "stop" -> {
                if (!requireOp(player)) {
                    return;
                }
                gameManager.requestStop("stopped by admin");
                player.sendMessage(Message.raw(PREFIX + "Stop requested."));
            }
            case "lobby" -> handleLobby(player, ref, store, world, args);
            case "spawn" -> handleSpawn(player, ref, store, args);
            case "platform" -> handlePlatform(player, ref, store, playerId, args);
            case "voidy" -> handleVoidY(player, args);
            case "breakdelay" -> handleBreakDelay(player, args);
            default -> sendUsage(player);
        }
    }

    private void openAdminMenu(Player player, Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (!requireOp(player)) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new RunOrFallAdminPage(playerRef, configStore, gameManager));
    }

    private void handleLobby(Player player, Ref<EntityStore> ref, Store<EntityStore> store, World world, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw(PREFIX + "Usage: /rof lobby <set|tp>"));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> {
                if (!requireOp(player)) {
                    return;
                }
                RunOrFallLocation location = readLocation(ref, store);
                if (location == null) {
                    player.sendMessage(Message.raw(PREFIX + "Could not read your position."));
                    return;
                }
                configStore.setLobby(location);
                player.sendMessage(Message.raw(PREFIX + "Lobby set."));
            }
            case "tp" -> {
                RunOrFallLocation lobby = configStore.getLobby();
                if (lobby == null) {
                    player.sendMessage(Message.raw(PREFIX + "Lobby is not configured."));
                    return;
                }
                store.addComponent(ref, Teleport.getComponentType(),
                        new Teleport(world, new Vector3d(lobby.x, lobby.y, lobby.z),
                                new Vector3f(lobby.rotX, lobby.rotY, lobby.rotZ)));
                player.sendMessage(Message.raw(PREFIX + "Teleported to lobby."));
            }
            default -> player.sendMessage(Message.raw(PREFIX + "Usage: /rof lobby <set|tp>"));
        }
    }

    private void handleSpawn(Player player, Ref<EntityStore> ref, Store<EntityStore> store, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw(PREFIX + "Usage: /rof spawn <add|list|clear>"));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "add" -> {
                if (!requireOp(player)) {
                    return;
                }
                RunOrFallLocation spawn = readLocation(ref, store);
                if (spawn == null) {
                    player.sendMessage(Message.raw(PREFIX + "Could not read your position."));
                    return;
                }
                configStore.addSpawn(spawn);
                int total = configStore.getSpawns().size();
                player.sendMessage(Message.raw(PREFIX + "Spawn added (#" + total + ")."));
            }
            case "list" -> {
                List<RunOrFallLocation> spawns = configStore.getSpawns();
                if (spawns.isEmpty()) {
                    player.sendMessage(Message.raw(PREFIX + "No spawns configured."));
                    return;
                }
                player.sendMessage(Message.raw(PREFIX + "Spawns: " + spawns.size()));
                for (int i = 0; i < spawns.size(); i++) {
                    RunOrFallLocation s = spawns.get(i);
                    player.sendMessage(Message.raw(PREFIX + "#" + (i + 1) + " "
                            + String.format(Locale.US, "%.1f, %.1f, %.1f", s.x, s.y, s.z)));
                }
            }
            case "clear" -> {
                if (!requireOp(player)) {
                    return;
                }
                configStore.clearSpawns();
                player.sendMessage(Message.raw(PREFIX + "All spawns cleared."));
            }
            default -> player.sendMessage(Message.raw(PREFIX + "Usage: /rof spawn <add|list|clear>"));
        }
    }

    private void handlePlatform(Player player, Ref<EntityStore> ref, Store<EntityStore> store,
                                UUID playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw(PREFIX + "Usage: /rof platform <pos1|pos2|add|list|remove|clear>"));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "pos1" -> {
                if (!requireOp(player)) {
                    return;
                }
                Vector3i pos = readCurrentBlock(ref, store);
                if (pos == null) {
                    player.sendMessage(Message.raw(PREFIX + "Could not read your block position."));
                    return;
                }
                Selection selection = selections.computeIfAbsent(playerId, ignored -> new Selection());
                selection.pos1 = pos;
                player.sendMessage(Message.raw(PREFIX + "pos1 = " + pos.x + ", " + pos.y + ", " + pos.z));
            }
            case "pos2" -> {
                if (!requireOp(player)) {
                    return;
                }
                Vector3i pos = readCurrentBlock(ref, store);
                if (pos == null) {
                    player.sendMessage(Message.raw(PREFIX + "Could not read your block position."));
                    return;
                }
                Selection selection = selections.computeIfAbsent(playerId, ignored -> new Selection());
                selection.pos2 = pos;
                player.sendMessage(Message.raw(PREFIX + "pos2 = " + pos.x + ", " + pos.y + ", " + pos.z));
            }
            case "add" -> {
                if (!requireOp(player)) {
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Message.raw(PREFIX + "Usage: /rof platform add <name>"));
                    return;
                }
                Selection selection = selections.get(playerId);
                if (selection == null || selection.pos1 == null || selection.pos2 == null) {
                    player.sendMessage(Message.raw(PREFIX + "Set pos1 and pos2 first."));
                    return;
                }
                String name = args[2];
                RunOrFallPlatform platform = new RunOrFallPlatform(name,
                        selection.pos1.x, selection.pos1.y, selection.pos1.z,
                        selection.pos2.x, selection.pos2.y, selection.pos2.z);
                configStore.upsertPlatform(platform);
                player.sendMessage(Message.raw(PREFIX + "Platform '" + name + "' saved."));
            }
            case "list" -> {
                List<RunOrFallPlatform> platforms = configStore.getPlatforms();
                if (platforms.isEmpty()) {
                    player.sendMessage(Message.raw(PREFIX + "No platforms configured."));
                    return;
                }
                player.sendMessage(Message.raw(PREFIX + "Platforms: " + platforms.size()));
                for (RunOrFallPlatform platform : platforms) {
                    player.sendMessage(Message.raw(PREFIX + platform.name + " "
                            + "[" + platform.minX + "," + platform.minY + "," + platform.minZ + "] -> "
                            + "[" + platform.maxX + "," + platform.maxY + "," + platform.maxZ + "]"));
                }
            }
            case "remove" -> {
                if (!requireOp(player)) {
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Message.raw(PREFIX + "Usage: /rof platform remove <name>"));
                    return;
                }
                boolean removed = configStore.removePlatform(args[2]);
                player.sendMessage(Message.raw(PREFIX + (removed ? "Platform removed." : "Platform not found.")));
            }
            case "clear" -> {
                if (!requireOp(player)) {
                    return;
                }
                configStore.clearPlatforms();
                player.sendMessage(Message.raw(PREFIX + "All platforms cleared."));
            }
            default -> player.sendMessage(Message.raw(PREFIX + "Usage: /rof platform <pos1|pos2|add|list|remove|clear>"));
        }
    }

    private void handleVoidY(Player player, String[] args) {
        if (!requireOp(player)) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Message.raw(PREFIX + "Usage: /rof voidy <y>"));
            return;
        }
        try {
            double y = Double.parseDouble(args[1]);
            configStore.setVoidY(y);
            player.sendMessage(Message.raw(PREFIX + "Void Y set to " + y + "."));
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw(PREFIX + "Y must be a number."));
        }
    }

    private void handleBreakDelay(Player player, String[] args) {
        if (!requireOp(player)) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Message.raw(PREFIX + "Usage: /rof breakdelay <seconds>"));
            return;
        }
        try {
            double seconds = Double.parseDouble(args[1]);
            if (!Double.isFinite(seconds) || seconds < 0.0) {
                player.sendMessage(Message.raw(PREFIX + "Delay must be >= 0."));
                return;
            }
            configStore.setBlockBreakDelaySeconds(seconds);
            player.sendMessage(Message.raw(PREFIX + "Block break delay set to " + seconds + "s."));
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw(PREFIX + "Delay must be a number."));
        }
    }

    private void sendStatus(Player player) {
        player.sendMessage(Message.raw(PREFIX + gameManager.statusLine()));
        player.sendMessage(Message.raw(PREFIX + "voidY=" + configStore.getVoidY()
                + ", breakDelay=" + configStore.getBlockBreakDelaySeconds() + "s"
                + ", spawns=" + configStore.getSpawns().size()
                + ", platforms=" + configStore.getPlatforms().size()
                + ", lobbySet=" + (configStore.getLobby() != null)));
    }

    private static RunOrFallLocation readLocation(Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return null;
        }
        Vector3d position = transform.getPosition();
        Vector3f rotation = transform.getRotation();
        float rotX = rotation != null ? rotation.getX() : 0f;
        float rotY = rotation != null ? rotation.getY() : 0f;
        float rotZ = rotation != null ? rotation.getZ() : 0f;
        return new RunOrFallLocation(position.getX(), position.getY(), position.getZ(), rotX, rotY, rotZ);
    }

    private static Vector3i readCurrentBlock(Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return null;
        }
        Vector3d position = transform.getPosition();
        int x = (int) Math.floor(position.getX());
        int y = (int) Math.floor(position.getY() - 0.2d);
        int z = (int) Math.floor(position.getZ());
        return new Vector3i(x, y, z);
    }

    private static boolean requireOp(Player player) {
        if (PermissionUtils.isOp(player)) {
            return true;
        }
        player.sendMessage(Message.raw(PREFIX + "You must be OP for this command."));
        return false;
    }

    private void sendUsage(Player player) {
        player.sendMessage(Message.raw(PREFIX + "Usage: /rof <join|leave|status|admin>"));
        player.sendMessage(Message.raw(PREFIX + "Player: /rof join, /rof leave, /rof status"));
        player.sendMessage(Message.raw(PREFIX + "Admin UI: /rof admin"));
        player.sendMessage(Message.raw(PREFIX + "Legacy admin commands remain available if needed."));
    }

    private static final class Selection {
        private Vector3i pos1;
        private Vector3i pos2;
    }
}
