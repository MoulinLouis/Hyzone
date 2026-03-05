package io.hyvexa.ascend.mine;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.mine.data.MineConfigStore;
import io.hyvexa.ascend.util.AscendInventoryUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MineGateChecker {

    private static final long COOLDOWN_MS = 2000;

    private final MineConfigStore configStore;
    private final AscendPlayerStore playerStore;
    private final Map<UUID, Long> lastTeleport = new ConcurrentHashMap<>();

    public MineGateChecker(MineConfigStore configStore, AscendPlayerStore playerStore) {
        this.configStore = configStore;
        this.playerStore = playerStore;
    }

    public void checkPlayer(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        Vector3d pos = transform.getPosition();
        if (pos == null) return;

        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();

        if (isOnCooldown(playerId)) return;

        // Entry gate: ascend >= 1 -> teleport inside mine + give pickaxe
        if (configStore.isInsideEntryGate(x, y, z)) {
            AscendPlayerProgress progress = playerStore.getPlayer(playerId);
            if (progress == null || progress.getAscensionCount() < 1) return;

            World world = store.getExternalData().getWorld();
            if (world == null) return;

            teleportPlayer(ref, store, world,
                configStore.getEntryDestX(), configStore.getEntryDestY(), configStore.getEntryDestZ(),
                configStore.getEntryDestRotX(), configStore.getEntryDestRotY(), configStore.getEntryDestRotZ());
            markCooldown(playerId);

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                giveMineItems(player);
            }
            return;
        }

        // Exit gate: teleport outside mine + restore menu items
        if (configStore.isInsideExitGate(x, y, z)) {
            World world = store.getExternalData().getWorld();
            if (world == null) return;

            teleportPlayer(ref, store, world,
                configStore.getExitDestX(), configStore.getExitDestY(), configStore.getExitDestZ(),
                configStore.getExitDestRotX(), configStore.getExitDestRotY(), configStore.getExitDestRotZ());
            markCooldown(playerId);

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                AscendInventoryUtils.giveMenuItems(player);
            }
        }
    }

    private void teleportPlayer(Ref<EntityStore> ref, Store<EntityStore> store, World world,
                                double x, double y, double z,
                                float rotX, float rotY, float rotZ) {
        Vector3d destPos = new Vector3d(x, y, z);
        Vector3f destRot = new Vector3f(rotX, rotY, rotZ);
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, destPos, destRot));
    }

    private void giveMineItems(Player player) {
        if (player.getWorld() == null) return;
        io.hyvexa.common.util.InventoryUtils.clearAllContainers(player);
        Inventory inventory = player.getInventory();
        if (inventory == null) return;
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null || hotbar.getCapacity() <= 0) return;
        hotbar.setItemStackForSlot((short) 0, new ItemStack(AscendConstants.ITEM_MINE_PICKAXE, 1), false);
        hotbar.setItemStackForSlot((short) 1, new ItemStack(AscendConstants.ITEM_MINE_SELECT, 1), false);
        hotbar.setItemStackForSlot((short) 2, new ItemStack(AscendConstants.ITEM_MINE_SELL, 1), false);
        hotbar.setItemStackForSlot((short) 3, new ItemStack(AscendConstants.ITEM_MINE_UPGRADES, 1), false);
        short lastSlot = (short) (hotbar.getCapacity() - 1);
        hotbar.setItemStackForSlot(lastSlot, new ItemStack(AscendConstants.ITEM_MINE_RETURN, 1), false);
    }

    private boolean isOnCooldown(UUID playerId) {
        Long last = lastTeleport.get(playerId);
        return last != null && (System.currentTimeMillis() - last) < COOLDOWN_MS;
    }

    private void markCooldown(UUID playerId) {
        lastTeleport.put(playerId, System.currentTimeMillis());
    }

    public void evict(UUID playerId) {
        lastTeleport.remove(playerId);
    }
}
