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
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.ui.TranscendencePage;
import io.hyvexa.common.util.SystemMessageUtils;

import java.util.UUID;

public class AscendTranscendenceInteraction extends AbstractAscendPageInteraction {

    public static final BuilderCodec<AscendTranscendenceInteraction> CODEC =
        BuilderCodec.builder(AscendTranscendenceInteraction.class, AscendTranscendenceInteraction::new).build();

    @Override
    protected boolean validateDependencies(ParkourAscendPlugin plugin, Player player) {
        if (plugin.getPlayerStore() == null || plugin.getTranscendenceManager() == null) {
            player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            return false;
        }

        // Gate: must have completed all challenges
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return false;
        }

        UUID playerId = playerRef.getUuid();
        AscendPlayerProgress progress = plugin.getPlayerStore().getPlayer(playerId);
        if (progress == null || !progress.hasAllChallengeRewards()) {
            player.sendMessage(Message.raw("[Transcendence] Complete all challenges to unlock Transcendence.")
                .color(SystemMessageUtils.SECONDARY));
            return false;
        }

        return true;
    }

    @Override
    protected InteractiveCustomUIPage<?> createPage(Ref<EntityStore> ref, Store<EntityStore> store,
                                                    PlayerRef playerRef, ParkourAscendPlugin plugin) {
        return new TranscendencePage(playerRef, plugin.getPlayerStore(), plugin.getTranscendenceManager());
    }
}
