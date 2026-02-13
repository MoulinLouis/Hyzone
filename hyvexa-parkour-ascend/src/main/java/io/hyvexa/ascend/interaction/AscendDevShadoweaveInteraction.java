package io.hyvexa.ascend.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.ui.AscendHelpPage;

public class AscendDevShadoweaveInteraction extends AbstractAscendPageInteraction {

    public static final BuilderCodec<AscendDevShadoweaveInteraction> CODEC =
        BuilderCodec.builder(AscendDevShadoweaveInteraction.class, AscendDevShadoweaveInteraction::new).build();

    @Override
    protected boolean requiresAscendWorld() {
        return false;
    }

    @Override
    protected boolean requiresPlugin() {
        return false;
    }

    @Override
    protected InteractiveCustomUIPage<?> createPage(Ref<EntityStore> ref, Store<EntityStore> store,
                                                    PlayerRef playerRef, ParkourAscendPlugin plugin) {
        return new AscendHelpPage(playerRef);
    }
}
