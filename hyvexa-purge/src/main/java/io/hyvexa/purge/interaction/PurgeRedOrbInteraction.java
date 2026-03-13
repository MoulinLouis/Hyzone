package io.hyvexa.purge.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.manager.PurgeSessionManager;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class PurgeRedOrbInteraction extends SimpleInteraction {

    public static final BuilderCodec<PurgeRedOrbInteraction> CODEC =
            BuilderCodec.builder(PurgeRedOrbInteraction.class, PurgeRedOrbInteraction::new).build();

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        PurgeInteractionContext ctx = PurgeInteractionContext.resolve(ref);
        if (ctx == null) {
            return;
        }
        PurgeSessionManager sessionManager = ctx.plugin().getSessionManager();
        if (sessionManager.getSessionByPlayer(ctx.playerId()) == null) {
            ctx.player().sendMessage(Message.raw("No active Purge session."));
            return;
        }
        if (ctx.store().getExternalData() == null) {
            ctx.player().sendMessage(Message.raw("Could not resolve your world."));
            return;
        }
        var world = ctx.store().getExternalData().getWorld();
        if (world == null) {
            ctx.player().sendMessage(Message.raw("Could not resolve your world."));
            return;
        }
        CompletableFuture.runAsync(() -> sessionManager.leaveSession(ctx.playerId(), "voluntary stop"), world);
    }
}
