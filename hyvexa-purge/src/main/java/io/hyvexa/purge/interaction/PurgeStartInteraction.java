package io.hyvexa.purge.interaction;

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
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.purge.HyvexaPurgePlugin;
import io.hyvexa.purge.ui.PurgePartyMenuPage;

import javax.annotation.Nonnull;
import java.util.UUID;

public class PurgeStartInteraction extends SimpleInteraction {

    public static final BuilderCodec<PurgeStartInteraction> CODEC =
            BuilderCodec.builder(PurgeStartInteraction.class, PurgeStartInteraction::new).build();

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
        if (!ModeGate.isPurgeWorld(world)) {
            player.sendMessage(Message.raw("You must be in the Purge world."));
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        HyvexaPurgePlugin plugin = HyvexaPurgePlugin.getInstance();
        if (plugin == null) {
            return;
        }
        if (plugin.getSessionManager().hasActiveSession(playerId)) {
            player.sendMessage(Message.raw("You already have an active Purge session."));
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new PurgePartyMenuPage(playerRef, playerId,
                        plugin.getPartyManager(), plugin.getSessionManager()));
    }
}
