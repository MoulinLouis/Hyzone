package io.hyvexa.hub.routing;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class HubRouter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String HUB_WORLD_NAME = "Hub";
    private static final String PARKOUR_WORLD_NAME = "Parkour";
    private static final String ASCEND_WORLD_NAME = "Ascend";

    public void routeToHub(PlayerRef playerRef) {
        routeToWorld(playerRef, HUB_WORLD_NAME);
    }

    public void routeToParkour(PlayerRef playerRef) {
        routeToWorld(playerRef, PARKOUR_WORLD_NAME);
    }

    public void routeToAscend(PlayerRef playerRef) {
        routeToWorld(playerRef, ASCEND_WORLD_NAME);
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
        Vector3d position = spawn != null ? spawn.getPosition() : new Vector3d(0, 64, 0);
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
