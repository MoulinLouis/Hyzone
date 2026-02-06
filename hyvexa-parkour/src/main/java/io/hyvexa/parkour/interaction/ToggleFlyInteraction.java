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

public class ToggleFlyInteraction extends SimpleInteraction {

    public static final BuilderCodec<ToggleFlyInteraction> CODEC =
            BuilderCodec.builder(ToggleFlyInteraction.class, ToggleFlyInteraction::new).build();

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
            if (plugin.getRunTracker() == null) {
                return;
            }
            if (!plugin.getRunTracker().isPracticeEnabled(playerRef.getUuid())) {
                player.sendMessage(SystemMessageUtils.parkourWarn("Practice mode must be enabled to toggle fly."));
                return;
            }
            boolean flyActive = plugin.getRunTracker().toggleFly(playerRef.getUuid());
            if (flyActive) {
                player.sendMessage(SystemMessageUtils.parkourInfo("Fly enabled."));
            } else {
                player.sendMessage(SystemMessageUtils.parkourInfo("Fly disabled."));
            }
        }, world);
    }
}
