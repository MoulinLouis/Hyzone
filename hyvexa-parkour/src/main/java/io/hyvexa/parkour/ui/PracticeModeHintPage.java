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
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.util.InventoryUtils;

import javax.annotation.Nonnull;

public class PracticeModeHintPage extends BaseParkourPage {

    private final RunTracker runTracker;

    private static final String BUTTON_ENABLE_PRACTICE = "EnablePractice";
    private static final String BUTTON_NOT_NOW = "NotNow";

    public PracticeModeHintPage(@Nonnull PlayerRef playerRef, RunTracker runTracker) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.runTracker = runTracker;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_PracticeModeHint.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#EnablePracticeButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ENABLE_PRACTICE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NotNowButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NOT_NOW), false);
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

        if (BUTTON_ENABLE_PRACTICE.equals(data.getButton())) {
            if (runTracker != null) {
                boolean enabled = runTracker.enablePractice(ref, store, playerRef);
                if (enabled) {
                    String mapId = runTracker.getActiveMapId(playerRef.getUuid());
                    HyvexaPlugin plugin = HyvexaPlugin.getInstance();
                    Map map = mapId != null && plugin != null && plugin.getMapStore() != null
                            ? plugin.getMapStore().getMap(mapId)
                            : null;
                    InventoryUtils.clearAllItems(player);
                    InventoryUtils.giveRunItems(player, map, true);
                }
            }
            this.close();
        } else if (BUTTON_NOT_NOW.equals(data.getButton())) {
            this.close();
        }
    }
}
