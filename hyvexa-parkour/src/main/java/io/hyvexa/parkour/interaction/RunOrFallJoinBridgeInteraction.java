package io.hyvexa.parkour.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.ModeGate;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RunOrFallJoinBridgeInteraction extends SimpleInteraction {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RUN_OR_FALL_PLUGIN_CLASS = "io.hyvexa.runorfall.HyvexaRunOrFallPlugin";

    public static final BuilderCodec<RunOrFallJoinBridgeInteraction> CODEC =
            BuilderCodec.builder(RunOrFallJoinBridgeInteraction.class, RunOrFallJoinBridgeInteraction::new).build();

    @Override
    public void handle(@Nonnull Ref<EntityStore> ref, boolean firstRun, float time,
                       @Nonnull InteractionType type, @Nonnull InteractionContext interactionContext) {
        super.handle(ref, firstRun, time, type, interactionContext);
        var store = ref.getStore();
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (!ModeGate.isRunOrFallWorld(world)) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;
        if (playerId == null) {
            return;
        }
        CompletableFuture.runAsync(() -> joinRunOrFallLobby(playerId, world), world);
    }

    private void joinRunOrFallLobby(UUID playerId, World world) {
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
            gameManager.getClass().getMethod("joinLobby", UUID.class, World.class)
                    .invoke(gameManager, playerId, world);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e)
                    .log("Failed to route Ingredient_Life_Essence to RunOrFall join.");
        }
    }
}
