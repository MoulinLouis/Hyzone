package io.hyvexa.parkour.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.parkour.ui.StatsPage;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class StatsInteraction extends SimpleInteraction {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RUN_OR_FALL_PLUGIN_CLASS = "io.hyvexa.runorfall.HyvexaRunOrFallPlugin";
    private static final String RUN_OR_FALL_STATS_PAGE_CLASS = "io.hyvexa.runorfall.ui.RunOrFallStatsPage";

    public static final BuilderCodec<StatsInteraction> CODEC =
            BuilderCodec.builder(StatsInteraction.class, StatsInteraction::new).build();

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

        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (ModeGate.isRunOrFallWorld(world) && tryOpenRunOrFallStats(ref, store, player, playerRef)) {
            return;
        }

        player.getPageManager().openCustomPage(ref, store,
                new StatsPage(playerRef, plugin.getProgressStore(), plugin.getMapStore()));
    }

    private boolean tryOpenRunOrFallStats(Ref<EntityStore> ref,
                                          com.hypixel.hytale.component.Store<EntityStore> store,
                                          Player player,
                                          PlayerRef playerRef) {
        try {
            Class<?> pluginClass = Class.forName(RUN_OR_FALL_PLUGIN_CLASS);
            Method getInstance = pluginClass.getMethod("getInstance");
            Object runOrFallPlugin = getInstance.invoke(null);
            if (runOrFallPlugin == null) {
                return false;
            }

            Method getStatsStore = pluginClass.getMethod("getStatsStore");
            Object statsStore = getStatsStore.invoke(runOrFallPlugin);
            if (statsStore == null) {
                return false;
            }

            Class<?> pageClass = Class.forName(RUN_OR_FALL_STATS_PAGE_CLASS);
            Constructor<?> constructor = null;
            for (Constructor<?> candidate : pageClass.getConstructors()) {
                Class<?>[] params = candidate.getParameterTypes();
                if (params.length == 2 && params[0] == PlayerRef.class) {
                    constructor = candidate;
                    break;
                }
            }
            if (constructor == null) {
                return false;
            }

            Object page = constructor.newInstance(playerRef, statsStore);
            if (!(page instanceof InteractiveCustomUIPage<?>)) {
                return false;
            }
            player.getPageManager().openCustomPage(ref, store, (InteractiveCustomUIPage<?>) page);
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to open RunOrFall stats page from Food_Candy_Cane interaction.");
            return false;
        }
    }
}
