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
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.util.InventoryUtils;

import javax.annotation.Nonnull;

public class MapRecommendationPage extends BaseParkourPage {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final RunTracker runTracker;
    private final String currentMapId;
    private final String category;

    private static final String BUTTON_TRY_DIFFERENT = "TryDifferent";
    private static final String BUTTON_PRACTICE_MODE = "PracticeMode";
    private static final String BUTTON_CONTINUE = "Continue";

    public MapRecommendationPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                ProgressStore progressStore, RunTracker runTracker,
                                String currentMapId, String category) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.runTracker = runTracker;
        this.currentMapId = currentMapId;
        this.category = category;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_MapRecommendation.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TryDifferentButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TRY_DIFFERENT), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PracticeModeButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PRACTICE_MODE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ContinueButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CONTINUE), false);
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

        if (BUTTON_TRY_DIFFERENT.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store,
                    new MapSelectPage(playerRef, mapStore, progressStore, runTracker, category));
        } else if (BUTTON_PRACTICE_MODE.equals(data.getButton())) {
            if (runTracker != null) {
                boolean enabled = runTracker.enablePractice(ref, store, playerRef);
                if (enabled) {
                    Map map = mapStore != null ? mapStore.getMap(currentMapId) : null;
                    InventoryUtils.clearAllItems(player);
                    InventoryUtils.giveRunItems(player, map, true);
                }
            }
            this.close();
        } else if (BUTTON_CONTINUE.equals(data.getButton())) {
            this.close();
        }
    }
}
