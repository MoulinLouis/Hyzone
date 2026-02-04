package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.common.ui.ButtonEventData;

import javax.annotation.Nonnull;

public class AscendAdminPanelPage extends BaseAscendPage {

    private static final String BUTTON_MAPS = "Maps";
    private static final String BUTTON_ADMIN = "AdminPanel";
    private static final String BUTTON_WHITELIST = "Whitelist";
    private static final String BUTTON_CLOSE = "Close";

    public AscendAdminPanelPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Ascend_AdminPanel.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MapsButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_MAPS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AdminButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ADMIN), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#WhitelistButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_WHITELIST), false);
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
        switch (data.getButton()) {
            case BUTTON_CLOSE -> this.close();
            case BUTTON_MAPS -> openMaps(ref, store);
            case BUTTON_ADMIN -> openAdminPanel(ref, store);
            case BUTTON_WHITELIST -> openWhitelist(ref, store);
            default -> {
            }
        }
    }

    private void openMaps(Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        AscendMapStore mapStore = plugin.getMapStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (mapStore == null || playerRef == null) {
            if (player != null) {
                player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading."));
            }
            return;
        }
        if (player != null) {
            player.getPageManager().openCustomPage(ref, store, new AscendAdminPage(playerRef, mapStore));
        }
    }

    private void openAdminPanel(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new AscendAdminCoinsPage(playerRef));
    }

    private void openWhitelist(Ref<EntityStore> ref, Store<EntityStore> store) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
            new AscendWhitelistPage(playerRef, plugin.getWhitelistManager()));
    }
}
