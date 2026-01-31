package io.hyvexa.hub.routing;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.hub.HubConstants;
import io.hyvexa.hub.hud.HubHud;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class HubRouter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String HUB_WORLD_NAME = "Hub";
    private static final String PARKOUR_WORLD_NAME = "Parkour";
    private static final String ASCEND_WORLD_NAME = "Ascend";

    private final ConcurrentHashMap<UUID, HubHud> hubHuds = new ConcurrentHashMap<>();

    public HubRouter() {
    }

    public void routeToHub(PlayerRef playerRef) {
        routeToWorld(playerRef, HUB_WORLD_NAME, true);
    }

    public void routeToParkour(PlayerRef playerRef) {
        routeToWorld(playerRef, PARKOUR_WORLD_NAME, false);
    }

    public void routeToAscend(PlayerRef playerRef) {
        routeToWorld(playerRef, ASCEND_WORLD_NAME, false);
    }

    private void routeToWorld(PlayerRef playerRef, String targetWorldName, boolean isHub) {
        if (playerRef == null || targetWorldName == null) {
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
            World targetWorld = resolveWorld(targetWorldName);
            if (targetWorld == null) {
                return;
            }
            // Skip teleport if already in target world
            String currentWorldName = currentWorld.getName();
            if (currentWorldName != null && currentWorldName.equalsIgnoreCase(targetWorldName)) {
                return;
            }
            Teleport teleport = createTeleport(targetWorld, playerId);
            if (teleport != null) {
                store.addComponent(ref, Teleport.getComponentType(), teleport);
            }
            clearInventory(player);
            if (isHub) {
                giveHubItems(player);
                attachHubHud(playerRef, player);
            } else if (ASCEND_WORLD_NAME.equalsIgnoreCase(targetWorldName)) {
                giveAscendItems(player);
            }
        }, currentWorld);
    }

    private Teleport createTeleport(World targetWorld, UUID playerId) {
        if (targetWorld == null) {
            return null;
        }
        Transform spawn = resolveSpawnTransform(targetWorld, playerId);
        Vector3d position = spawn != null ? spawn.getPosition() : new Vector3d(0, 64, 0);
        Vector3f rotation = spawn != null ? spawn.getRotation() : new Vector3f(0f, 0f, 0f);
        return new Teleport(targetWorld, position, rotation);
    }

    private World resolveWorld(String worldName) {
        if (worldName == null) {
            return null;
        }
        World world = Universe.get().getWorld(worldName);
        if (world == null) {
            world = findWorldByName(worldName);
        }
        if (world == null) {
            try {
                Universe.get().loadWorld(worldName);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Failed to load world '" + worldName + "': " + e.getMessage());
            }
            world = Universe.get().getWorld(worldName);
            if (world == null) {
                world = findWorldByName(worldName);
            }
        }
        if (world == null) {
            LOGGER.at(Level.WARNING).log("World '" + worldName + "' not found, using default world.");
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

    private void attachHubHud(PlayerRef playerRef, Player player) {
        if (playerRef == null || player == null) {
            return;
        }
        HubHud hud = hubHuds.computeIfAbsent(playerRef.getUuid(), ignored -> new HubHud(playerRef));
        player.getHudManager().setCustomHud(playerRef, hud);
        player.getHudManager().hideHudComponents(playerRef, HudComponent.Compass);
        hud.show();
        hud.applyStaticText();
    }

    private static void giveAscendItems(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null || inventory.getHotbar() == null) {
            return;
        }
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar.getCapacity() <= 0) {
            return;
        }
        short slot = (short) (hotbar.getCapacity() - 1);
        hotbar.setItemStackForSlot(slot, new ItemStack(HubConstants.ITEM_SERVER_SELECTOR, 1), false);
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
}
