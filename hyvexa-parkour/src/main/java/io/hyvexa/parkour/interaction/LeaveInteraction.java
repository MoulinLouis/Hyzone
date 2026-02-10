package io.hyvexa.parkour.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.parkour.util.InventoryUtils;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.TransformData;
import javax.annotation.Nonnull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class LeaveInteraction extends SimpleInteraction {

    public static final BuilderCodec<LeaveInteraction> CODEC =
            BuilderCodec.builder(LeaveInteraction.class, LeaveInteraction::new).build();
    private static final long CONFIRM_WINDOW_MS = 10000L;
    private static final ConcurrentHashMap<UUID, PendingLeave> PENDING_LEAVES = new ConcurrentHashMap<>();

    public static void clearPendingLeave(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PENDING_LEAVES.remove(playerId);
    }

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        var plugin = HyvexaPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        var store = ref.getStore();
        var player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            player.sendMessage(Message.raw("World not available."));
            return;
        }
        String mapId = plugin.getRunTracker().getActiveMapId(playerRef.getUuid());
        if (mapId == null) {
            PENDING_LEAVES.remove(playerRef.getUuid());
            player.sendMessage(Message.raw("No active map to leave."));
            return;
        }
        if (!confirmLeave(player, playerRef.getUuid(), mapId)) {
            return;
        }
        CompletableFuture.runAsync(() -> handleLeave(ref, store, player, playerRef, plugin), world);
    }

    private void handleLeave(Ref<EntityStore> ref, Store<EntityStore> store, Player player, PlayerRef playerRef,
                             HyvexaPlugin plugin) {
        String mapId = plugin.getRunTracker().getActiveMapId(playerRef.getUuid());
        if (mapId == null) {
            player.sendMessage(Message.raw("No active map to leave."));
            return;
        }
        plugin.getRunTracker().clearActiveMap(playerRef.getUuid());
        Map map = plugin.getMapStore().getMap(mapId);
        TransformData leaveTeleport = map != null ? map.getLeaveTeleport() : null;
        if (leaveTeleport != null) {
            Vector3d position = new Vector3d(leaveTeleport.getX(), leaveTeleport.getY(), leaveTeleport.getZ());
            Vector3f rotation = new Vector3f(leaveTeleport.getRotX(), leaveTeleport.getRotY(), leaveTeleport.getRotZ());
            store.addComponent(ref, Teleport.getComponentType(),
                    new Teleport(store.getExternalData().getWorld(), position, rotation));
        } else {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            plugin.getRunTracker().teleportToSpawn(ref, store, transform);
        }
        InventoryUtils.giveMenuItems(player);
        String mapName = map != null && map.getName() != null && !map.getName().isBlank()
                ? map.getName()
                : mapId;
        player.sendMessage(SystemMessageUtils.withParkourPrefix(
                Message.raw("Run ended: ").color(SystemMessageUtils.SECONDARY),
                Message.raw(mapName != null ? mapName : "Map").color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(".").color(SystemMessageUtils.SECONDARY)
        ));
    }

    private boolean confirmLeave(Player player, UUID playerId, String mapId) {
        long now = System.currentTimeMillis();
        PendingLeave pending = PENDING_LEAVES.get(playerId);
        if (pending == null || !pending.mapId.equals(mapId) || now - pending.requestedAt > CONFIRM_WINDOW_MS) {
            PENDING_LEAVES.put(playerId, new PendingLeave(mapId, now));
            player.sendMessage(SystemMessageUtils.parkourWarn("Confirm leave: right-click again."));
            return false;
        }
        PENDING_LEAVES.remove(playerId);
        return true;
    }

    private static final class PendingLeave {
        private final String mapId;
        private final long requestedAt;

        private PendingLeave(String mapId, long requestedAt) {
            this.mapId = mapId;
            this.requestedAt = requestedAt;
        }
    }
}
