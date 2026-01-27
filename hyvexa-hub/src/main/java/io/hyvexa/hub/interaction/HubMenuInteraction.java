package io.hyvexa.hub.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.hub.HyvexaHubPlugin;
import io.hyvexa.hub.ui.HubMenuPage;

import javax.annotation.Nonnull;

public class HubMenuInteraction extends SimpleInteraction {

    private static final String HUB_WORLD_NAME = "Hub";

    public static final BuilderCodec<HubMenuInteraction> CODEC =
            BuilderCodec.builder(HubMenuInteraction.class, HubMenuInteraction::new).build();

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        var plugin = HyvexaHubPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        var router = plugin.getRouter();
        if (router == null) {
            return;
        }
        var store = ref.getStore();
        var player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world != null && HUB_WORLD_NAME.equalsIgnoreCase(world.getName())) {
            player.getPageManager().openCustomPage(ref, store, new HubMenuPage(playerRef, router));
            return;
        }
        router.routeToHub(playerRef);
    }
}
