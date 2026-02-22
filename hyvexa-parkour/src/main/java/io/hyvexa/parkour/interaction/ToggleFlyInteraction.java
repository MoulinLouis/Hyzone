package io.hyvexa.parkour.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ToggleFlyInteraction extends SimpleInteraction {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RUN_OR_FALL_PLUGIN_CLASS = "io.hyvexa.runorfall.HyvexaRunOrFallPlugin";

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
        if (ModeGate.isRunOrFallWorld(world)) {
            leaveRunOrFallLobby(playerRef, world);
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

    private void leaveRunOrFallLobby(PlayerRef playerRef, World world) {
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;
        if (playerId == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Class<?> pluginClass = Class.forName(RUN_OR_FALL_PLUGIN_CLASS);
                Object runOrFallPlugin = pluginClass.getMethod("getInstance").invoke(null);
                if (runOrFallPlugin == null) {
                    return;
                }
                Object gameManager = pluginClass.getMethod("getGameManager").invoke(runOrFallPlugin);
                if (gameManager == null) {
                    return;
                }
                gameManager.getClass().getMethod("leaveLobby", UUID.class, boolean.class)
                        .invoke(gameManager, playerId, true);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e)
                        .log("Failed to route Ingredient_Earth_Essence to RunOrFall leave.");
            }
        }, world);
    }
}
