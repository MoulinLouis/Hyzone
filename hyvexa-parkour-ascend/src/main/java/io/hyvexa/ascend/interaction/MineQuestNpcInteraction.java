package io.hyvexa.ascend.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.mine.quest.MineQuestManager;

import javax.annotation.Nonnull;

public class MineQuestNpcInteraction extends SimpleInteraction {

    public static final BuilderCodec<MineQuestNpcInteraction> CODEC =
            BuilderCodec.builder(MineQuestNpcInteraction.class, MineQuestNpcInteraction::new).build();

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);

        var store = ref.getStore();
        var player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        AscendInteractionBridge.Services services = AscendInteractionBridge.get();
        if (services == null) return;

        MineQuestManager questManager = services.mineQuestManager();
        if (questManager == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        questManager.handleNpcInteraction(playerRef.getUuid(), "miner", player);
    }
}
