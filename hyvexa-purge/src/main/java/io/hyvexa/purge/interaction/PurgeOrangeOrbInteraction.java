package io.hyvexa.purge.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.purge.ui.PurgeWeaponSelectPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;

import javax.annotation.Nonnull;

public class PurgeOrangeOrbInteraction extends SimpleInteraction {

    public static final BuilderCodec<PurgeOrangeOrbInteraction> CODEC =
            BuilderCodec.builder(PurgeOrangeOrbInteraction.class, PurgeOrangeOrbInteraction::new).build();

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        PurgeInteractionContext ctx = PurgeInteractionContext.resolve(ref);
        if (ctx == null) {
            return;
        }
        ctx.player().getPageManager().openCustomPage(ref, ctx.store(),
                new PurgeWeaponSelectPage(ctx.playerRef(), PurgeWeaponSelectPage.Mode.LOADOUT, ctx.playerId(),
                        ctx.services().weaponConfigManager(), null, null, null,
                        ctx.services().sessionManager(), ctx.services().loadoutService(),
                        ctx.services().purgeSkinStore()));
    }
}
