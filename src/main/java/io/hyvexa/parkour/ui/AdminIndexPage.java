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
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.data.SettingsStore;
import io.hyvexa.common.ui.ButtonEventData;

import javax.annotation.Nonnull;

public class AdminIndexPage extends BaseParkourPage {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final SettingsStore settingsStore;
    private static final String BUTTON_MAPS = "Maps";
    private static final String BUTTON_PROGRESS = "Progress";
    private static final String BUTTON_SETTINGS = "Settings";

    public AdminIndexPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                 ProgressStore progressStore) {
        this(playerRef, mapStore, progressStore, HyvexaPlugin.getInstance() != null
                ? HyvexaPlugin.getInstance().getSettingsStore()
                : null);
    }

    public AdminIndexPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                 ProgressStore progressStore, SettingsStore settingsStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.settingsStore = settingsStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_AdminIndex.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MapsButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_MAPS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ProgressButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PROGRESS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SettingsButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SETTINGS), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (BUTTON_MAPS.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store, new MapAdminPage(playerRef, mapStore));
            return;
        }
        if (BUTTON_PROGRESS.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store, new ProgressAdminPage(playerRef, progressStore));
            return;
        }
        if (BUTTON_SETTINGS.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store,
                    new SettingsAdminPage(playerRef, settingsStore, mapStore));
        }
    }
}
