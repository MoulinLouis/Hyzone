package io.hyvexa.ascend.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.ui.AscendMapSelectPage;

public class AscendDevCinderclothInteraction extends AbstractAscendPageInteraction {

    public static final BuilderCodec<AscendDevCinderclothInteraction> CODEC =
        BuilderCodec.builder(AscendDevCinderclothInteraction.class, AscendDevCinderclothInteraction::new).build();

    @Override
    protected boolean validateDependencies(ParkourAscendPlugin plugin, Player player) {
        if (plugin.getMapStore() == null || plugin.getPlayerStore() == null
                || plugin.getRunTracker() == null || plugin.getGhostStore() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return false;
        }
        return true;
    }

    @Override
    protected InteractiveCustomUIPage<?> createPage(Ref<EntityStore> ref, Store<EntityStore> store,
                                                    PlayerRef playerRef, ParkourAscendPlugin plugin) {
        return new AscendMapSelectPage(playerRef, plugin.getMapStore(), plugin.getPlayerStore(),
            plugin.getRunTracker(), plugin.getRobotManager(), plugin.getGhostStore());
    }
}
