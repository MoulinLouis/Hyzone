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
import io.hyvexa.parkour.util.InventoryUtils;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.parkour.data.Map;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class PracticeInteraction extends SimpleInteraction {

    public static final BuilderCodec<PracticeInteraction> CODEC =
            BuilderCodec.builder(PracticeInteraction.class, PracticeInteraction::new).build();

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
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            if (services.duelTracker() != null && services.duelTracker().isInMatch(playerRef.getUuid())) {
                player.sendMessage(SystemMessageUtils.parkourWarn("Practice is unavailable during duels."));
                return;
            }
            if (services.runTracker() == null) {
                return;
            }
            String mapId = services.runTracker().getActiveMapId(playerRef.getUuid());
            if (mapId == null) {
                player.sendMessage(SystemMessageUtils.parkourWarn("Start a run before enabling practice."));
                return;
            }
            Map map = services.mapStore() != null ? services.mapStore().getMap(mapId) : null;
            if (!services.runTracker().isPracticeEnabled(playerRef.getUuid())) {
                services.runTracker().enablePractice(ref, store, playerRef);
                InventoryUtils.clearAllItems(player);
                InventoryUtils.giveRunItems(player, map, true);
                player.sendMessage(SystemMessageUtils.parkourInfo("Practice mode enabled."));
                return;
            }
            player.sendMessage(SystemMessageUtils.parkourWarn("Practice mode already enabled. Use Leave practice."));
        }, world);
    }
}
