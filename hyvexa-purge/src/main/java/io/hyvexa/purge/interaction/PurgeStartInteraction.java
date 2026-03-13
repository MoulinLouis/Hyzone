package io.hyvexa.purge.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.purge.ui.PurgePartyMenuPage;

import javax.annotation.Nonnull;

public class PurgeStartInteraction extends SimpleInteraction {

    public static final BuilderCodec<PurgeStartInteraction> CODEC =
            BuilderCodec.builder(PurgeStartInteraction.class, PurgeStartInteraction::new).build();

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        PurgeInteractionContext ctx = PurgeInteractionContext.resolve(ref);
        if (ctx == null) {
            return;
        }
        if (ctx.store().getExternalData() == null) {
            ctx.player().sendMessage(Message.raw("Could not resolve your world."));
            return;
        }
        World world = ctx.store().getExternalData().getWorld();
        if (world == null) {
            ctx.player().sendMessage(Message.raw("Could not resolve your world."));
            return;
        }
        if (!ModeGate.isPurgeWorld(world)) {
            ctx.player().sendMessage(Message.raw("You must be in the Purge world."));
            return;
        }
        if (ctx.plugin().getSessionManager().hasActiveSession(ctx.playerId())) {
            ctx.player().sendMessage(Message.raw("You already have an active Purge session."));
            return;
        }
        ctx.player().getPageManager().openCustomPage(ref, ctx.store(),
                new PurgePartyMenuPage(ctx.playerRef(), ctx.playerId(),
                        ctx.plugin().getPartyManager(), ctx.plugin().getSessionManager()));
    }
}
