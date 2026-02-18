package io.hyvexa.parkour.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class PracticeCheckpointInteraction extends SimpleInteraction {

    public static final BuilderCodec<PracticeCheckpointInteraction> CODEC =
            BuilderCodec.builder(PracticeCheckpointInteraction.class, PracticeCheckpointInteraction::new).build();

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        var plugin = HyvexaPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        var store = ref.getStore();
        var player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            if (plugin.getDuelTracker() != null && plugin.getDuelTracker().isInMatch(playerRef.getUuid())) {
                player.sendMessage(SystemMessageUtils.parkourWarn("Practice is unavailable during duels."));
                return;
            }
            if (plugin.getRunTracker() == null) {
                return;
            }
            if (!plugin.getRunTracker().isPracticeEnabled(playerRef.getUuid())) {
                player.sendMessage(SystemMessageUtils.parkourWarn("Practice mode must be enabled to set a checkpoint."));
                return;
            }
            boolean checkpointSet = plugin.getRunTracker().setPracticeCheckpoint(ref, store, playerRef);
            if (checkpointSet) {
                player.sendMessage(SystemMessageUtils.parkourInfo("Practice checkpoint set."));
            } else {
                player.sendMessage(SystemMessageUtils.parkourWarn("Unable to set a practice checkpoint here."));
            }
        }, world);
    }
}
