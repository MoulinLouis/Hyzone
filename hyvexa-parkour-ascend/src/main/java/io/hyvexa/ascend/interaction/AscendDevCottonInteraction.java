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
import io.hyvexa.ascend.ui.AutomationPage;

public class AscendDevCottonInteraction extends AbstractAscendPageInteraction {

    public static final BuilderCodec<AscendDevCottonInteraction> CODEC =
        BuilderCodec.builder(AscendDevCottonInteraction.class, AscendDevCottonInteraction::new).build();

    @Override
    protected boolean validateDependencies(ParkourAscendPlugin plugin, Player player) {
        if (plugin.getPlayerStore() == null || plugin.getAscensionManager() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return false;
        }
        return true;
    }

    @Override
    protected InteractiveCustomUIPage<?> createPage(Ref<EntityStore> ref, Store<EntityStore> store,
                                                    PlayerRef playerRef, ParkourAscendPlugin plugin) {
        return new AutomationPage(playerRef, plugin.getPlayerStore(), plugin.getAscensionManager());
    }
}
