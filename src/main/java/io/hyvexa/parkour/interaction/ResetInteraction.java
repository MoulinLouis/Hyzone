package io.hyvexa.parkour.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.util.InventoryUtils;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.util.PlayerSettingsStore;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class ResetInteraction extends SimpleInteraction {

    public static final BuilderCodec<ResetInteraction> CODEC =
            BuilderCodec.builder(ResetInteraction.class, ResetInteraction::new).build();

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
        if (!PlayerSettingsStore.isResetItemEnabled(playerRef.getUuid())) {
            player.sendMessage(Message.raw("Reset item disabled in settings."));
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            player.sendMessage(Message.raw("World not available."));
            return;
        }
        CompletableFuture.runAsync(() -> {
            InventoryUtils.clearAllItems(player);
            Map map = null;
            if (plugin.getRunTracker() != null && plugin.getMapStore() != null) {
                String mapId = plugin.getRunTracker().getActiveMapId(playerRef.getUuid());
                if (mapId != null) {
                    map = plugin.getMapStore().getMap(mapId);
                }
            }
            InventoryUtils.giveRunItems(player, map);
            plugin.getRunTracker().resetRunToStart(ref, store, player, playerRef);
        }, world);
    }
}
