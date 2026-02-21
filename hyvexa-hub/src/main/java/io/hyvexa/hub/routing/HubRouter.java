package io.hyvexa.hub.routing;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.server.core.Message;
import io.hyvexa.common.util.AsyncExecutionHelper;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.hub.ui.HubMenuPage;

import java.util.UUID;


public class HubRouter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Vector3d DEFAULT_SPAWN = new Vector3d(0, 64, 0);

    public void openMenuOrRoute(Ref<EntityStore> ref, Store<EntityStore> store,
                                PlayerRef playerRef, World world) {
        if (world != null && WorldConstants.WORLD_HUB.equalsIgnoreCase(world.getName())) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store, new HubMenuPage(playerRef, this));
            }
            return;
        }
        routeToHub(playerRef);
    }

    public void routeToHub(PlayerRef playerRef) {
        routeToWorld(playerRef, WorldConstants.WORLD_HUB);
    }

    public void routeToParkour(PlayerRef playerRef) {
        routeToWorld(playerRef, WorldConstants.WORLD_PARKOUR);
    }

    public void routeToAscend(PlayerRef playerRef) {
        routeToWorld(playerRef, WorldConstants.WORLD_ASCEND);
    }

    public void routeToPurge(PlayerRef playerRef) {
        routeToWorld(playerRef, WorldConstants.WORLD_PURGE);
    }

    public void routeToRunOrFall(PlayerRef playerRef) {
        routeToWorld(playerRef, WorldConstants.WORLD_RUN_OR_FALL);
    }

    private void routeToWorld(PlayerRef playerRef, String targetWorldName) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World currentWorld = store.getExternalData().getWorld();
        if (currentWorld == null) {
            return;
        }
        String playerIdText = playerRef.getUuid() != null ? playerRef.getUuid().toString() : "unknown";
        String sourceWorldName = currentWorld.getName() != null ? currentWorld.getName() : "unknown";
        AsyncExecutionHelper.runBestEffort(currentWorld, () -> {
            if (!ref.isValid()) {
                return;
            }
            World targetWorld = resolveWorld(targetWorldName);
            if (targetWorld == null) {
                LOGGER.atWarning().log("Failed to resolve world '" + targetWorldName
                        + "' for player " + playerIdText);
                Player p = store.getComponent(ref, Player.getComponentType());
                if (p != null) {
                    p.sendMessage(Message.raw("Failed to teleport: world '" + targetWorldName + "' is unavailable."));
                }
                return;
            }
            if (sourceWorldName.equalsIgnoreCase(targetWorldName)) {
                return;
            }
            try {
                io.hyvexa.core.analytics.AnalyticsStore.getInstance().logEvent(
                        playerRef.getUuid(), "mode_switch",
                        "{\"to\":\"" + targetWorldName + "\"}");
            } catch (Exception e) { /* silent */ }
            Teleport teleport = createTeleport(targetWorld, playerRef.getUuid());
            store.addComponent(ref, Teleport.getComponentType(), teleport);
        }, "hub.route.world", "hub route to world",
                "player=" + playerIdText + ", from=" + sourceWorldName + ", target=" + targetWorldName);
    }

    private Teleport createTeleport(World targetWorld, UUID playerId) {
        Transform spawn = resolveSpawnTransform(targetWorld, playerId);
        Vector3d position = spawn != null ? spawn.getPosition() : DEFAULT_SPAWN;
        Vector3f rotation = spawn != null ? spawn.getRotation() : new Vector3f(0f, 0f, 0f);
        return new Teleport(targetWorld, position, rotation);
    }

    private World resolveWorld(String worldName) {
        World world = Universe.get().getWorld(worldName);
        if (world != null) {
            return world;
        }
        try {
            Universe.get().loadWorld(worldName);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to load world '" + worldName + "': " + e.getMessage());
        }
        world = Universe.get().getWorld(worldName);
        if (world != null) {
            return world;
        }
        LOGGER.atWarning().log("World '" + worldName + "' not found after load attempt.");
        return null;
    }

    private static Transform resolveSpawnTransform(World world, UUID playerId) {
        var worldConfig = world.getWorldConfig();
        if (worldConfig != null && worldConfig.getSpawnProvider() != null) {
            try {
                return worldConfig.getSpawnProvider().getSpawnPoint(world, playerId);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to resolve spawn point: " + e.getMessage());
            }
        }
        return null;
    }
}
