package io.hyvexa.hub.ui;

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
import io.hyvexa.hub.routing.HubRouter;

import javax.annotation.Nonnull;

public class HubMenuPage extends BaseHubPage {

    private static final String BUTTON_PARKOUR = "Parkour";
    private static final String BUTTON_ASCEND = "Parkour Ascend";
    private static final String BUTTON_CLOSE = "Close";

    private final HubRouter router;

    public HubMenuPage(@Nonnull PlayerRef playerRef, @Nonnull HubRouter router) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.router = router;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Hub_Menu.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ParkourButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PARKOUR), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AscendButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ASCEND), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (BUTTON_CLOSE.equals(data.getButton())) {
            this.close();
            return;
        }
        if (BUTTON_PARKOUR.equals(data.getButton())) {
            if (playerRef != null) {
                router.routeToParkour(playerRef);
            }
            this.close();
            return;
        }
        if (BUTTON_ASCEND.equals(data.getButton())) {
            if (playerRef != null) {
                router.routeToAscend(playerRef);
            }
            this.close();
        }
    }
}
