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
import io.hyvexa.ascend.ui.AscendProfilePage;

public class AscendDevSilkInteraction extends AbstractAscendPageInteraction {

    public static final BuilderCodec<AscendDevSilkInteraction> CODEC =
        BuilderCodec.builder(AscendDevSilkInteraction.class, AscendDevSilkInteraction::new).build();

    @Override
    protected boolean validateDependencies(ParkourAscendPlugin plugin, Player player) {
        if (plugin.getPlayerStore() == null || plugin.getRobotManager() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return false;
        }
        return true;
    }

    @Override
    protected InteractiveCustomUIPage<?> createPage(Ref<EntityStore> ref, Store<EntityStore> store,
                                                    PlayerRef playerRef, ParkourAscendPlugin plugin) {
        return new AscendProfilePage(playerRef, plugin.getPlayerStore(), plugin.getRobotManager());
    }
}
