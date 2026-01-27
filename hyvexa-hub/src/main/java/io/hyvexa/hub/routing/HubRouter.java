package io.hyvexa.hub.routing;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.hub.HubConstants;
import io.hyvexa.core.event.ModeEnterEvent;
import io.hyvexa.core.event.ModeExitEvent;
import io.hyvexa.core.state.PlayerMode;
import io.hyvexa.core.state.PlayerModeState;
import io.hyvexa.core.state.PlayerModeStateStore;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class HubRouter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String HUB_WORLD_NAME = "Hub";
    private static final String PARKOUR_WORLD_NAME = "Parkour";
    private static final String ASCEND_WORLD_NAME = "Ascend";

    private final PlayerModeStateStore stateStore;
    private final EventBus eventBus;

    public HubRouter(PlayerModeStateStore stateStore, EventBus eventBus) {
        this.stateStore = stateStore;
        this.eventBus = eventBus;
    }

    public PlayerMode getCurrentMode(UUID playerId) {
        return stateStore.getCurrentMode(playerId);
    }

    public void routeToHub(PlayerRef playerRef) {
        routeToMode(playerRef, PlayerMode.HUB);
    }

    public void routeToParkour(PlayerRef playerRef) {
        routeToMode(playerRef, PlayerMode.PARKOUR);
    }

    public void routeToAscend(PlayerRef playerRef) {
        routeToMode(playerRef, PlayerMode.ASCEND);
    }

    private void routeToMode(PlayerRef playerRef, PlayerMode targetMode) {
        if (playerRef == null || targetMode == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
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
            Player player = store.getComponent(ref, Player.getComponentType());
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (player == null || transform == null) {
                return;
            }
            PlayerModeState state = stateStore.getOrCreate(playerId);
            PlayerMode previousMode = state.getCurrentMode();
            if (previousMode == targetMode) {
                return;
            }
            if (targetMode == PlayerMode.PARKOUR) {
                state.setParkourReturnLocation(captureLocation(currentWorld, transform));
            }
            if (targetMode == PlayerMode.ASCEND) {
                state.setAscendReturnLocation(captureLocation(currentWorld, transform));
            }
            fireModeExit(playerRef, previousMode, targetMode);
            state.setCurrentMode(targetMode);
            stateStore.saveState(playerId, state);
            Teleport teleport = resolveTeleport(playerId, targetMode, state);
            if (teleport != null) {
                store.addComponent(ref, Teleport.getComponentType(), teleport);
            }
            if (targetMode == PlayerMode.HUB) {
                clearInventory(player);
                giveHubItems(player);
            }
            fireModeEnter(playerRef, targetMode, previousMode);
        }, currentWorld);
    }

    private Teleport resolveTeleport(UUID playerId, PlayerMode targetMode, PlayerModeState state) {
        Location returnLocation = null;
        String worldName = HUB_WORLD_NAME;
        if (targetMode == PlayerMode.PARKOUR) {
            worldName = PARKOUR_WORLD_NAME;
        } else if (targetMode == PlayerMode.ASCEND) {
            worldName = ASCEND_WORLD_NAME;
        } else if (targetMode == PlayerMode.HUB) {
            worldName = HUB_WORLD_NAME;
        }
        World targetWorld = resolveWorld(returnLocation, worldName);
        if (targetWorld == null) {
            return null;
        }
        if (returnLocation != null && returnLocation.getPosition() != null) {
            Vector3d position = returnLocation.getPosition();
            Vector3f rotation = returnLocation.getRotation();
            if (rotation == null) {
                rotation = new Vector3f(0f, 0f, 0f);
            }
            return new Teleport(targetWorld, position, rotation);
        }
        Transform spawn = resolveSpawnTransform(targetWorld, playerId);
        Vector3d position = spawn != null ? spawn.getPosition() : new Vector3d(0, 64, 0);
        Vector3f rotation = spawn != null ? spawn.getRotation() : new Vector3f(0f, 0f, 0f);
        return new Teleport(targetWorld, position, rotation);
    }

    private World resolveWorld(Location returnLocation, String fallbackName) {
        World world = null;
        if (returnLocation != null && returnLocation.getWorld() != null) {
            world = Universe.get().getWorld(returnLocation.getWorld());
            if (world == null) {
                world = findWorldByName(returnLocation.getWorld());
            }
        }
        if (world == null) {
            world = Universe.get().getWorld(fallbackName);
            if (world == null) {
                world = findWorldByName(fallbackName);
            }
        }
        if (world == null && fallbackName != null) {
            try {
                Universe.get().loadWorld(fallbackName);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Failed to load world '" + fallbackName + "': " + e.getMessage());
            }
            world = Universe.get().getWorld(fallbackName);
            if (world == null) {
                world = findWorldByName(fallbackName);
            }
        }
        if (world == null) {
            LOGGER.at(Level.WARNING).log("World '" + fallbackName + "' not found, using default world.");
            world = Universe.get().getDefaultWorld();
        }
        return world;
    }

    private World findWorldByName(String name) {
        if (name == null) {
            return null;
        }
        for (World world : Universe.get().getWorlds().values()) {
            if (world != null && name.equalsIgnoreCase(world.getName())) {
                return world;
            }
        }
        return null;
    }

    private static Location captureLocation(World world, TransformComponent transform) {
        if (world == null || transform == null) {
            return null;
        }
        Vector3d position = transform.getPosition();
        Vector3f rotation = transform.getRotation();
        if (position == null) {
            return null;
        }
        String worldName = world.getName();
        float rotX = rotation != null ? rotation.getX() : 0f;
        float rotY = rotation != null ? rotation.getY() : 0f;
        float rotZ = rotation != null ? rotation.getZ() : 0f;
        return new Location(worldName, position.getX(), position.getY(), position.getZ(), rotX, rotY, rotZ);
    }

    private static Transform resolveSpawnTransform(World world, UUID playerId) {
        if (world == null) {
            return null;
        }
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

    private static void clearInventory(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        clearContainer(inventory.getHotbar());
        clearContainer(inventory.getStorage());
        clearContainer(inventory.getBackpack());
        clearContainer(inventory.getTools());
        clearContainer(inventory.getUtility());
        clearContainer(inventory.getArmor());
    }

    private static void giveHubItems(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        inventory.getHotbar().setItemStackForSlot((short) 0, new ItemStack(HubConstants.ITEM_SERVER_SELECTOR, 1), false);
    }

    private static void clearContainer(ItemContainer container) {
        if (container == null) {
            return;
        }
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            container.setItemStackForSlot(slot, ItemStack.EMPTY, false);
        }
    }

    private void fireModeExit(PlayerRef playerRef, PlayerMode mode, PlayerMode nextMode) {
        if (eventBus == null || mode == null || mode == PlayerMode.NONE) {
            return;
        }
        try {
            eventBus.dispatchFor(ModeExitEvent.class, null).dispatch(new ModeExitEvent(playerRef, mode, nextMode));
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to dispatch ModeExitEvent: " + e.getMessage());
        }
    }

    private void fireModeEnter(PlayerRef playerRef, PlayerMode mode, PlayerMode previousMode) {
        if (eventBus == null || mode == null || mode == PlayerMode.NONE) {
            return;
        }
        try {
            eventBus.dispatchFor(ModeEnterEvent.class, null).dispatch(new ModeEnterEvent(playerRef, mode, previousMode));
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to dispatch ModeEnterEvent: " + e.getMessage());
        }
    }
}
