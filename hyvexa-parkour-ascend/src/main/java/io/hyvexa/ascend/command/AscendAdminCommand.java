package io.hyvexa.ascend.command;

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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.ui.AscendAdminPage;
import io.hyvexa.ascend.holo.AscendHologramManager;
import io.hyvexa.common.util.HylogramsBridge;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AscendAdminCommand extends AbstractAsyncCommand {

    public AscendAdminCommand() {
        super("as", "Ascend admin tools");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
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
            ctx.sendMessage(Message.raw("You must be OP to use Ascend admin commands."));
            return CompletableFuture.completedFuture(null);
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player not in world."));
            return CompletableFuture.completedFuture(null);
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        return CompletableFuture.runAsync(() -> handleCommand(ctx, player, ref, store), world);
    }

    private void handleCommand(CommandContext ctx, Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        String[] args = getArgs(ctx);
        if (args.length >= 1 && "holograms".equalsIgnoreCase(args[0])) {
            listHolograms(ctx);
            return;
        }
        if (args.length < 1 || !"admin".equalsIgnoreCase(args[0])) {
            player.sendMessage(Message.raw("Usage: /as holograms | /as admin map <create|setstart|setfinish|addwaypoint|clearwaypoints|setreward|setprice|setorder|list> ... | /as admin holo <map|delete> ..."));
            return;
        }
        if (args.length == 1) {
            AscendMapStore mapStore = ParkourAscendPlugin.getInstance().getMapStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (mapStore != null && playerRef != null) {
                player.getPageManager().openCustomPage(ref, store, new AscendAdminPage(playerRef, mapStore));
            }
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Message.raw("Usage: /as admin map <create|setstart|setfinish|addwaypoint|clearwaypoints|setreward|setprice|setorder|list> ... | /as admin holo <map|delete> ..."));
            return;
        }
        String category = args[1].toLowerCase();
        if (!"map".equals(category) && !"holo".equals(category)) {
            player.sendMessage(Message.raw("Unknown admin category. Use: /as admin map ... or /as admin holo ..."));
            return;
        }
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        AscendMapStore mapStore = plugin.getMapStore();
        if (mapStore == null) {
            return;
        }
        if ("holo".equals(category)) {
            handleHoloCommand(player, ref, store, mapStore, args);
            return;
        }
        if (args.length < 3) {
            player.sendMessage(Message.raw("Usage: /as admin map <create|setstart|setfinish|addwaypoint|clearwaypoints|setreward|setprice|setorder|list> ..."));
            return;
        }
        String action = args[2].toLowerCase();
        switch (action) {
            case "create" -> handleCreateMap(player, store, mapStore, args);
            case "setstart" -> handleSetStart(player, ref, store, mapStore, args);
            case "setfinish" -> handleSetFinish(player, ref, store, mapStore, args);
            case "addwaypoint" -> handleAddWaypoint(player, ref, store, mapStore, args);
            case "clearwaypoints" -> handleClearWaypoints(player, mapStore, args);
            case "setreward" -> handleSetReward(player, mapStore, args);
            case "setprice" -> handleSetPrice(player, mapStore, args);
            case "setorder" -> handleSetOrder(player, mapStore, args);
            case "list" -> handleListMaps(player, mapStore);
            default -> player.sendMessage(Message.raw("Unknown action. Use: /as admin map <create|setstart|setfinish|addwaypoint|clearwaypoints|setreward|setprice|setorder|list> ..."));
        }
    }

    private String[] getArgs(CommandContext ctx) {
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

    private void listHolograms(CommandContext ctx) {
        try {
            List<String> names = HylogramsBridge.listHologramNames();
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

    private void handleCreateMap(Player player, Store<EntityStore> store, AscendMapStore mapStore, String[] args) {
        if (args.length < 5) {
            player.sendMessage(Message.raw("Usage: /as admin map create <id> <name> <reward> [price] [order]"));
            return;
        }
        String id = args[3];
        String name = args[4];
        long reward = parseLong(player, args, 5, 0L, "Reward");
        if (reward < 0) {
            return;
        }
        long price = parseLong(player, args, 6, 0L, "Price");
        if (price < 0) {
            return;
        }
        int order = (int) parseLong(player, args, 7, resolveNextOrder(mapStore), "Order");
        if (order < 0) {
            return;
        }
        if (mapStore.getMap(id) != null) {
            player.sendMessage(Message.raw("Map already exists: " + id));
            return;
        }
        AscendMap map = new AscendMap();
        map.setId(id);
        map.setName(name);
        map.setPrice(price);
        map.setRobotPrice(0L);
        map.setBaseReward(reward);
        map.setBaseRunTimeMs(30000L);
        map.setStorageCapacity((int) AscendConstants.DEFAULT_ROBOT_STORAGE);
        World world = store.getExternalData().getWorld();
        map.setWorld(world != null ? world.getName() : "Ascend");
        map.setDisplayOrder(order);
        mapStore.saveMap(map);
        player.sendMessage(Message.raw("Ascend map created: " + id));
    }

    private void handleSetStart(Player player, Ref<EntityStore> ref, Store<EntityStore> store,
                                AscendMapStore mapStore, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Message.raw("Usage: /as admin map setstart <id>"));
            return;
        }
        AscendMap map = mapStore.getMap(args[3]);
        if (map == null) {
            player.sendMessage(Message.raw("Map not found: " + args[3]));
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Unable to read player position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        map.setStartX(pos.getX());
        map.setStartY(pos.getY());
        map.setStartZ(pos.getZ());
        map.setStartRotX(rot.getX());
        map.setStartRotY(rot.getY());
        map.setStartRotZ(rot.getZ());
        World world = store.getExternalData().getWorld();
        map.setWorld(world != null ? world.getName() : map.getWorld());
        mapStore.saveMap(map);
        player.sendMessage(Message.raw("Start set for map: " + map.getId()));
    }

    private void handleSetFinish(Player player, Ref<EntityStore> ref, Store<EntityStore> store,
                                 AscendMapStore mapStore, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Message.raw("Usage: /as admin map setfinish <id>"));
            return;
        }
        AscendMap map = mapStore.getMap(args[3]);
        if (map == null) {
            player.sendMessage(Message.raw("Map not found: " + args[3]));
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Unable to read player position."));
            return;
        }
        Vector3d pos = transform.getPosition();
        map.setFinishX(pos.getX());
        map.setFinishY(pos.getY());
        map.setFinishZ(pos.getZ());
        mapStore.saveMap(map);
        player.sendMessage(Message.raw("Finish set for map: " + map.getId()));
    }

    private void handleAddWaypoint(Player player, Ref<EntityStore> ref, Store<EntityStore> store,
                                   AscendMapStore mapStore, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Message.raw("Usage: /as admin map addwaypoint <id> [jump] [delayMs]"));
            return;
        }
        AscendMap map = mapStore.getMap(args[3]);
        if (map == null) {
            player.sendMessage(Message.raw("Map not found: " + args[3]));
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("Unable to read player position."));
            return;
        }
        boolean jump = args.length >= 5 && Boolean.parseBoolean(args[4]);
        long delayMs = args.length >= 6 ? parseLong(player, args, 5, 0L, "Delay") : 0L;
        if (delayMs < 0) {
            return;
        }
        Vector3d pos = transform.getPosition();
        AscendMap.Waypoint waypoint = new AscendMap.Waypoint(pos.getX(), pos.getY(), pos.getZ(), jump, delayMs);
        map.getWaypoints().add(waypoint);
        mapStore.saveMap(map);
        player.sendMessage(Message.raw("Waypoint added to map: " + map.getId()));
    }

    private void handleClearWaypoints(Player player, AscendMapStore mapStore, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Message.raw("Usage: /as admin map clearwaypoints <id>"));
            return;
        }
        AscendMap map = mapStore.getMap(args[3]);
        if (map == null) {
            player.sendMessage(Message.raw("Map not found: " + args[3]));
            return;
        }
        map.getWaypoints().clear();
        mapStore.saveMap(map);
        player.sendMessage(Message.raw("Cleared waypoints for map: " + map.getId()));
    }

    private void handleSetReward(Player player, AscendMapStore mapStore, String[] args) {
        if (args.length < 5) {
            player.sendMessage(Message.raw("Usage: /as admin map setreward <id> <reward>"));
            return;
        }
        AscendMap map = mapStore.getMap(args[3]);
        if (map == null) {
            player.sendMessage(Message.raw("Map not found: " + args[3]));
            return;
        }
        long reward = parseLong(player, args, 4, map.getBaseReward(), "Reward");
        if (reward < 0) {
            return;
        }
        map.setBaseReward(reward);
        mapStore.saveMap(map);
        player.sendMessage(Message.raw("Reward updated for map: " + map.getId()));
    }

    private void handleSetPrice(Player player, AscendMapStore mapStore, String[] args) {
        if (args.length < 5) {
            player.sendMessage(Message.raw("Usage: /as admin map setprice <id> <price>"));
            return;
        }
        AscendMap map = mapStore.getMap(args[3]);
        if (map == null) {
            player.sendMessage(Message.raw("Map not found: " + args[3]));
            return;
        }
        long price = parseLong(player, args, 4, map.getPrice(), "Price");
        if (price < 0) {
            return;
        }
        map.setPrice(price);
        mapStore.saveMap(map);
        player.sendMessage(Message.raw("Price updated for map: " + map.getId()));
    }

    private void handleSetOrder(Player player, AscendMapStore mapStore, String[] args) {
        if (args.length < 5) {
            player.sendMessage(Message.raw("Usage: /as admin map setorder <id> <order>"));
            return;
        }
        AscendMap map = mapStore.getMap(args[3]);
        if (map == null) {
            player.sendMessage(Message.raw("Map not found: " + args[3]));
            return;
        }
        int order = (int) parseLong(player, args, 4, map.getDisplayOrder(), "Order");
        if (order < 0) {
            return;
        }
        map.setDisplayOrder(order);
        mapStore.saveMap(map);
        player.sendMessage(Message.raw("Order updated for map: " + map.getId()));
    }

    private void handleListMaps(Player player, AscendMapStore mapStore) {
        List<AscendMap> maps = mapStore.listMaps();
        if (maps.isEmpty()) {
            player.sendMessage(Message.raw("No Ascend maps created yet."));
            return;
        }
        player.sendMessage(Message.raw("Ascend maps:"));
        for (AscendMap map : maps) {
            String name = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
            player.sendMessage(Message.raw("- " + map.getId() + " (" + name + "), reward " + map.getBaseReward()));
        }
    }

    private long parseLong(Player player, String[] args, int index, long fallback, String label) {
        if (index >= args.length) {
            return fallback;
        }
        try {
            return Long.parseLong(args[index]);
        } catch (NumberFormatException e) {
            player.sendMessage(Message.raw(label + " must be a number."));
            return -1L;
        }
    }

    private int resolveNextOrder(AscendMapStore mapStore) {
        int max = 0;
        for (AscendMap map : mapStore.listMaps()) {
            max = Math.max(max, map.getDisplayOrder());
        }
        return max + 1;
    }

    private void handleHoloCommand(Player player, Ref<EntityStore> ref, Store<EntityStore> store,
                                   AscendMapStore mapStore, String[] args) {
        if (!HylogramsBridge.isAvailable()) {
            player.sendMessage(Message.raw("Hylograms plugin not available."));
            return;
        }
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        AscendHologramManager manager = plugin != null ? plugin.getHologramManager() : null;
        if (manager == null) {
            player.sendMessage(Message.raw("Hologram manager not available."));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(Message.raw("Usage: /as admin holo <map|delete> ..."));
            return;
        }
        String action = args[2].toLowerCase();
        switch (action) {
            case "map" -> handleHoloMap(player, ref, store, mapStore, manager, args);
            case "delete" -> handleHoloDelete(player, store, manager, args);
            default -> player.sendMessage(Message.raw("Usage: /as admin holo <map|delete> ..."));
        }
    }

    private void handleHoloMap(Player player, Ref<EntityStore> ref, Store<EntityStore> store,
                               AscendMapStore mapStore, AscendHologramManager manager, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Message.raw("Usage: /as admin holo map <id>"));
            return;
        }
        AscendMap map = mapStore.getMap(args[3]);
        if (map == null) {
            player.sendMessage(Message.raw("Map not found: " + args[3]));
            return;
        }
        Vector3d pos = resolvePlayerPosition(player, ref, store);
        if (pos == null) {
            return;
        }
        String worldName = resolveWorldName(store);
        boolean updated = manager.createOrUpdateMapInfoHolo(map, store, pos, worldName);
        if (updated) {
            player.sendMessage(Message.raw("Map hologram saved for: " + map.getId()));
        } else {
            player.sendMessage(Message.raw("Failed to save map hologram."));
        }
    }

    private void handleHoloDelete(Player player, Store<EntityStore> store,
                                  AscendHologramManager manager, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Message.raw("Usage: /as admin holo delete <map> <id>"));
            return;
        }
        String type = args[3].toLowerCase();
        switch (type) {
            case "map" -> {
                if (args.length < 5) {
                    player.sendMessage(Message.raw("Usage: /as admin holo delete " + type + " <id>"));
                    return;
                }
                boolean removed = manager.deleteMapInfoHolo(args[4], store);
                if (removed) {
                    player.sendMessage(Message.raw("Deleted map hologram for map: " + args[4]));
                } else {
                    player.sendMessage(Message.raw("No map hologram found for map: " + args[4]));
                }
            }
            default -> player.sendMessage(Message.raw("Usage: /as admin holo delete <map> <id>"));
        }
    }

    private Vector3d resolvePlayerPosition(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            player.sendMessage(Message.raw("Could not read your position."));
            return null;
        }
        return transform.getPosition();
    }

    private String resolveWorldName(Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        return world != null ? world.getName() : null;
    }
}
