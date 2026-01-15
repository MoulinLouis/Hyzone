package io.hyvexa.parkour.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.parkour.data.Map;

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
        World world = store.getExternalData().getWorld();
        if (world == null) {
            player.sendMessage(Message.raw("World not available."));
            return;
        }
        CompletableFuture.runAsync(() -> handleReset(ref, store, player, playerRef, plugin, world), world);
    }

    private void handleReset(Ref<EntityStore> ref, Store<EntityStore> store, Player player, PlayerRef playerRef,
                             HyvexaPlugin plugin, World world) {
        String mapId = plugin.getRunTracker().getActiveMapId(playerRef.getUuid());
        if (mapId == null) {
            player.sendMessage(Message.raw("No active map to reset."));
            return;
        }
        Map map = plugin.getMapStore().getMap(mapId);
        if (map == null || map.getStart() == null) {
            player.sendMessage(Message.raw("Map start not available."));
            return;
        }
        plugin.getRunTracker().setActiveMap(playerRef.getUuid(), mapId);
        Vector3d position = new Vector3d(map.getStart().getX(), map.getStart().getY(), map.getStart().getZ());
        Vector3f rotation = new Vector3f(map.getStart().getRotX(), map.getStart().getRotY(),
                map.getStart().getRotZ());
        store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, position, rotation));
        player.sendMessage(Message.raw("Run reset."));
    }
}
