package io.hyvexa.parkour.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.parkour.util.InventoryUtils;
import io.hyvexa.parkour.data.Map;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class RestartCheckpointInteraction extends SimpleInteraction {

    public static final BuilderCodec<RestartCheckpointInteraction> CODEC =
            BuilderCodec.builder(RestartCheckpointInteraction.class, RestartCheckpointInteraction::new).build();

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        var services = ParkourInteractionBridge.get();
        if (services == null) {
            return;
        }
        var store = ref.getStore();
        var player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (store.getExternalData() == null) return;
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            InventoryUtils.clearAllItems(player);
            if (services.duelTracker() != null && services.duelTracker().isInMatch(playerRef.getUuid())) {
                Map map = services.duelTracker().getActiveMap(playerRef.getUuid());
                InventoryUtils.giveDuelItems(player, map);
                boolean teleported = services.duelTracker().teleportToLastCheckpoint(ref, store, playerRef);
                if (!teleported) {
                    services.duelTracker().resetRunToStart(ref, store, player, playerRef);
                }
                return;
            }
            Map map = null;
            if (services.runTracker() != null && services.mapStore() != null) {
                String mapId = services.runTracker().getActiveMapId(playerRef.getUuid());
                if (mapId != null) {
                    map = services.mapStore().getMap(mapId);
                }
            }
            boolean practiceEnabled = services.runTracker() != null
                    && services.runTracker().isPracticeEnabled(playerRef.getUuid());
            InventoryUtils.giveRunItems(player, map, practiceEnabled);
            boolean teleported = services.runTracker().teleportToLastCheckpoint(ref, store, playerRef);
            if (!teleported) {
                if (practiceEnabled) {
                    player.sendMessage(SystemMessageUtils.parkourWarn(
                            "No checkpoint available. Use Set checkpoint first."));
                    return;
                }
                services.runTracker().resetRunToStart(ref, store, player, playerRef);
            }
        }, world);
    }
}
