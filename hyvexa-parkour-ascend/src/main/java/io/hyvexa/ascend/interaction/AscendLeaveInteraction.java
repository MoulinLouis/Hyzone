package io.hyvexa.ascend.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendSettingsStore;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.util.AscendInventoryUtils;
import io.hyvexa.ascend.util.AscendModeGate;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.core.state.ModeMessages;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AscendLeaveInteraction extends SimpleInteraction {

    public static final BuilderCodec<AscendLeaveInteraction> CODEC =
        BuilderCodec.builder(AscendLeaveInteraction.class, AscendLeaveInteraction::new).build();

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
        var store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (!AscendModeGate.isAscendWorld(world)) {
            player.sendMessage(ModeMessages.MESSAGE_ENTER_ASCEND);
            return;
        }
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        AscendRunTracker runTracker = plugin.getRunTracker();
        if (runTracker == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        String mapId = runTracker.getActiveMapId(playerRef.getUuid());
        if (mapId == null) {
            PENDING_LEAVES.remove(playerRef.getUuid());
            player.sendMessage(Message.raw("[Ascend] No active map to leave."));
            return;
        }
        if (!confirmLeave(player, playerRef.getUuid(), mapId)) {
            return;
        }
        CompletableFuture.runAsync(() -> handleLeave(ref, store, player, playerRef, plugin), world);
    }

    private void handleLeave(Ref<EntityStore> ref, com.hypixel.hytale.component.Store<EntityStore> store,
                             Player player, PlayerRef playerRef, ParkourAscendPlugin plugin) {
        AscendRunTracker runTracker = plugin.getRunTracker();
        String mapId = runTracker.getActiveMapId(playerRef.getUuid());
        if (mapId == null) {
            player.sendMessage(Message.raw("[Ascend] No active map to leave."));
            return;
        }

        // Cancel the run
        runTracker.cancelRun(playerRef.getUuid());

        // Teleport to spawn
        AscendSettingsStore settingsStore = plugin.getSettingsStore();
        World world = store.getExternalData().getWorld();
        if (settingsStore != null && settingsStore.hasSpawnPosition() && world != null) {
            Vector3d pos = settingsStore.getSpawnPosition();
            Vector3f rot = settingsStore.getSpawnRotation();
            store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, pos, rot));
        }

        // Give back menu items
        AscendInventoryUtils.giveMenuItems(player);
    }

    private boolean confirmLeave(Player player, UUID playerId, String mapId) {
        long now = System.currentTimeMillis();
        PendingLeave pending = PENDING_LEAVES.get(playerId);
        if (pending == null || !pending.mapId.equals(mapId) || now - pending.requestedAt > CONFIRM_WINDOW_MS) {
            PENDING_LEAVES.put(playerId, new PendingLeave(mapId, now));
            player.sendMessage(Message.raw("[Ascend] Confirm leave: right-click again.")
                .color(SystemMessageUtils.WARN));
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
