package io.hyvexa.ascend.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.util.AscendModeGate;
import io.hyvexa.core.state.ModeMessages;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class AscendResetInteraction extends SimpleInteraction {

    public static final BuilderCodec<AscendResetInteraction> CODEC =
        BuilderCodec.builder(AscendResetInteraction.class, AscendResetInteraction::new).build();

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
        AscendMapStore mapStore = plugin.getMapStore();
        if (runTracker == null || mapStore == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return;
        }
        String mapId = runTracker.getActiveMapId(playerRef.getUuid());
        if (mapId == null) {
            player.sendMessage(Message.raw("[Ascend] No active map to reset."));
            return;
        }
        CompletableFuture.runAsync(() -> {
            AscendMap map = mapStore.getMap(mapId);
            if (map == null) {
                player.sendMessage(Message.raw("[Ascend] Map not found."));
                return;
            }
            // Teleport back to start and set pending run
            runTracker.teleportToMapStart(ref, store, playerRef, mapId);
            player.sendMessage(Message.raw("[Ascend] Ready: " + (map.getName() != null ? map.getName() : mapId) + " - Move to start!"));
        }, world);
    }
}
