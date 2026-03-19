package io.hyvexa.ascend.interaction;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.ui.ConveyorChestPage;

public class ConveyorChestInteraction extends AbstractAscendPageInteraction {

    public static final BuilderCodec<ConveyorChestInteraction> CODEC =
        BuilderCodec.builder(ConveyorChestInteraction.class, ConveyorChestInteraction::new).build();

    @Override
    protected boolean validateDependencies(ParkourAscendPlugin plugin, Player player) {
        return plugin.getMinePlayerStore() != null;
    }

    @Override
    protected InteractiveCustomUIPage<?> createPage(Ref<EntityStore> ref, Store<EntityStore> store,
                                                     PlayerRef playerRef, ParkourAscendPlugin plugin) {
        MinePlayerProgress progress = plugin.getMinePlayerStore().getOrCreatePlayer(playerRef.getUuid());
        if (progress == null) return null;
        if (!progress.hasConveyorItems()) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.sendMessage(Message.raw("The chest is empty."));
            }
            return null;
        }
        return new ConveyorChestPage(playerRef, progress, plugin.getMinePlayerStore());
    }
}
