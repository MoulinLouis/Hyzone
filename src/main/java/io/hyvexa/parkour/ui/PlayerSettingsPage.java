package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.parkour.visibility.PlayerVisibilityManager;

import javax.annotation.Nonnull;

public class PlayerSettingsPage extends BaseParkourPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_HIDE_ALL = "HideAll";
    private static final String BUTTON_SHOW_ALL = "ShowAll";
    private static final String BUTTON_HIDE_HUD = "HideHud";
    private static final String BUTTON_SHOW_HUD = "ShowHud";

    public PlayerSettingsPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_PlayerSettings.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#HideAllButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_HIDE_ALL), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ShowAllButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SHOW_ALL), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#HideHudButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_HIDE_HUD), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ShowHudButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SHOW_HUD), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }
        if (BUTTON_CLOSE.equals(data.getButton())) {
            this.close();
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (BUTTON_HIDE_ALL.equals(data.getButton())) {
            hideAllPlayers(playerRef);
            player.sendMessage(Message.raw("All players hidden."));
            return;
        }
        if (BUTTON_SHOW_ALL.equals(data.getButton())) {
            showAllPlayers(playerRef);
            player.sendMessage(Message.raw("All players shown."));
        }
        if (BUTTON_HIDE_HUD.equals(data.getButton())) {
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin != null) {
                plugin.hideRunHud(playerRef);
                player.sendMessage(Message.raw("Server HUD hidden."));
            }
            return;
        }
        if (BUTTON_SHOW_HUD.equals(data.getButton())) {
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin != null) {
                plugin.showRunHud(playerRef);
                player.sendMessage(Message.raw("Server HUD shown."));
            }
        }
    }

    private void hideAllPlayers(@Nonnull PlayerRef viewerRef) {
        Universe.get().getWorlds().forEach((worldId, world) ->
                world.execute(() -> applyHiddenState(viewerRef, world, true)));
    }

    private void showAllPlayers(@Nonnull PlayerRef viewerRef) {
        PlayerVisibilityManager.get().clearHidden(viewerRef.getUuid());
    }

    private void applyHiddenState(@Nonnull PlayerRef viewerRef, @Nonnull World world, boolean hide) {
        if (viewerRef.getReference() == null || !viewerRef.getReference().isValid()) {
            return;
        }
        Store<EntityStore> store = viewerRef.getReference().getStore();
        for (PlayerRef targetRef : world.getPlayerRefs()) {
            if (viewerRef.equals(targetRef)) {
                continue;
            }
            Ref<EntityStore> targetEntityRef = targetRef.getReference();
            if (targetEntityRef == null || !targetEntityRef.isValid()) {
                continue;
            }
            UUIDComponent uuidComponent = store.getComponent(targetEntityRef, UUIDComponent.getComponentType());
            if (uuidComponent == null) {
                continue;
            }
            if (hide) {
                PlayerVisibilityManager.get().hidePlayer(viewerRef.getUuid(), uuidComponent.getUuid());
            } else {
                PlayerVisibilityManager.get().showPlayer(viewerRef.getUuid(), uuidComponent.getUuid());
            }
        }
    }
}
