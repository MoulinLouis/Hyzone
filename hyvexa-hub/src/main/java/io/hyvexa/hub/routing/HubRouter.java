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

import io.hyvexa.hub.HubConstants;
import io.hyvexa.hub.ui.HubMenuPage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class HubRouter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Vector3d DEFAULT_SPAWN = new Vector3d(0, 64, 0);

    public void openMenuOrRoute(Ref<EntityStore> ref, Store<EntityStore> store,
                                PlayerRef playerRef, World world) {
        if (world != null && HubConstants.WORLD_HUB.equalsIgnoreCase(world.getName())) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store, new HubMenuPage(playerRef, this));
            }
            return;
        }
        routeToHub(playerRef);
    }

    public void routeToHub(PlayerRef playerRef) {
        routeToWorld(playerRef, HubConstants.WORLD_HUB);
    }

    public void routeToParkour(PlayerRef playerRef) {
        routeToWorld(playerRef, HubConstants.WORLD_PARKOUR);
    }

    public void routeToAscend(PlayerRef playerRef) {
        routeToWorld(playerRef, HubConstants.WORLD_ASCEND);
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
        CompletableFuture.runAsync(() -> {
            if (!ref.isValid()) {
                return;
            }
            World targetWorld = resolveWorld(targetWorldName);
            if (targetWorld == null) {
                return;
            }
            String currentWorldName = currentWorld.getName();
            if (currentWorldName != null && currentWorldName.equalsIgnoreCase(targetWorldName)) {
                return;
            }
            Teleport teleport = createTeleport(targetWorld, playerRef.getUuid());
            store.addComponent(ref, Teleport.getComponentType(), teleport);
        }, currentWorld);
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
            LOGGER.at(Level.WARNING).log("Failed to load world '" + worldName + "': " + e.getMessage());
        }
        world = Universe.get().getWorld(worldName);
        if (world != null) {
            return world;
        }
        LOGGER.at(Level.WARNING).log("World '" + worldName + "' not found, using default world.");
        return Universe.get().getDefaultWorld();
    }

    private static Transform resolveSpawnTransform(World world, UUID playerId) {
        var worldConfig = world.getWorldConfig();
        if (worldConfig != null && worldConfig.getSpawnProvider() != null) {
            try {
                return worldConfig.getSpawnProvider().getSpawnPoint(world, playerId);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Failed to resolve spawn point: " + e.getMessage());
            }
        }
        return null;
    }
}
