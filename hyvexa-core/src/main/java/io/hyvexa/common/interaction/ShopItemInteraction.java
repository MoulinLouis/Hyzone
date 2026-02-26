package io.hyvexa.common.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class ShopItemInteraction extends SimpleInteraction {

    public static final BuilderCodec<ShopItemInteraction> CODEC =
            BuilderCodec.builder(ShopItemInteraction.class, ShopItemInteraction::new).build();

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        var store = ref.getStore();
        var player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        CommandManager.get().handleCommand(player, "shop");
    }
}
