package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.tracker.RunTracker;

import javax.annotation.Nonnull;

public class WelcomeTutorialScreen2Page extends BaseParkourPage {

    private static final String BUTTON_SHOW_MAPS = "ShowMaps";
    private static final String BUTTON_BACK = "Back";

    public WelcomeTutorialScreen2Page(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_WelcomeTutorial_Screen2.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ShowMapsButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SHOW_MAPS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

        if (player == null || playerRef == null) {
            return;
        }

        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (plugin == null) {
            return;
        }

        MapStore mapStore = plugin.getMapStore();
        ProgressStore progressStore = plugin.getProgressStore();
        RunTracker runTracker = plugin.getRunTracker();

        if (mapStore == null || progressStore == null || runTracker == null) {
            return;
        }

        if (BUTTON_SHOW_MAPS.equals(data.getButton())) {
            progressStore.markWelcomeShown(playerRef.getUuid(), playerRef.getUsername());
            player.getPageManager().openCustomPage(ref, store,
                    new CategorySelectPage(playerRef, mapStore, progressStore, runTracker));
        } else if (BUTTON_BACK.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store,
                    new WelcomeTutorialScreen1Page(playerRef));
        }
    }
}
