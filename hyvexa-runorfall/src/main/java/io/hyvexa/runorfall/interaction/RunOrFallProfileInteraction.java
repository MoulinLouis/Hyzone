package io.hyvexa.runorfall.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.runorfall.manager.RunOrFallStatsStore;
import io.hyvexa.runorfall.ui.RunOrFallProfilePage;

import javax.annotation.Nonnull;

public class RunOrFallProfileInteraction extends SimpleInteraction {

    public static final BuilderCodec<RunOrFallProfileInteraction> CODEC =
            BuilderCodec.builder(RunOrFallProfileInteraction.class, RunOrFallProfileInteraction::new).build();

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        RunOrFallInteractionBridge.Services services = RunOrFallInteractionBridge.get();
        if (services == null || services.statsStore() == null) {
            return;
        }
        RunOrFallStatsStore statsStore = services.statsStore();
        var store = ref.getStore();
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (!ModeGate.isRunOrFallWorld(world)) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new RunOrFallProfilePage(playerRef, statsStore));
    }
}
