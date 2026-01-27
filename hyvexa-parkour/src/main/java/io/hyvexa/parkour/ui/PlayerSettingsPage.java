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
import io.hyvexa.common.util.InventoryUtils;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.util.PlayerSettingsStore;
import io.hyvexa.parkour.visibility.PlayerVisibilityManager;

import javax.annotation.Nonnull;
import java.util.UUID;

public class PlayerSettingsPage extends BaseParkourPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_HIDE_ALL = "HideAll";
    private static final String BUTTON_SHOW_ALL = "ShowAll";
    private static final String BUTTON_HIDE_HUD = "HideHud";
    private static final String BUTTON_SHOW_HUD = "ShowHud";
    private static final String BUTTON_MUSIC = "Music";
    private static final String BUTTON_SPEED_X1 = "SpeedX1";
    private static final String BUTTON_SPEED_X2 = "SpeedX2";
    private static final String BUTTON_SPEED_X4 = "SpeedX4";
    private static final String BUTTON_TOGGLE_RESET_ITEM = "ToggleResetItem";
    private static final String RESET_ITEM_BUTTON_SELECTOR = "#ResetItemButton";
    private static final String RESET_ITEM_LABEL_DISABLE = "No reset item: Off";
    private static final String RESET_ITEM_LABEL_ENABLE = "No reset item: On";

    public PlayerSettingsPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_PlayerSettings.ui");
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        boolean showSpeedBoost = false;
        if (plugin != null && plugin.getProgressStore() != null) {
            var playerId = playerRef.getUuid();
            boolean isVipOrFounder = playerId != null
                    && (plugin.getProgressStore().isVip(playerId) || plugin.getProgressStore().isFounder(playerId));
            boolean inMap = playerId != null && plugin.getRunTracker() != null
                    && plugin.getRunTracker().getActiveMapId(playerId) != null;
            showSpeedBoost = isVipOrFounder && !inMap;
        }
        uiCommandBuilder.set("#VipSpeedLabel.Visible", showSpeedBoost);
        uiCommandBuilder.set("#VipSpeedRow.Visible", showSpeedBoost);
        uiCommandBuilder.set(RESET_ITEM_BUTTON_SELECTOR + ".Text", getResetItemLabel(playerRef.getUuid()));
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
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MusicButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_MUSIC), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#VipSpeedX1Button",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SPEED_X1), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#VipSpeedX2Button",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SPEED_X2), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#VipSpeedX4Button",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SPEED_X4), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetItemButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TOGGLE_RESET_ITEM), false);
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
            return;
        }
        if (BUTTON_MUSIC.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store, new PlayerMusicPage(playerRef));
            return;
        }
        if (BUTTON_TOGGLE_RESET_ITEM.equals(data.getButton())) {
            boolean enabled = PlayerSettingsStore.toggleResetItemEnabled(playerRef.getUuid());
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin != null && plugin.getRunTracker() != null) {
                String mapId = plugin.getRunTracker().getActiveMapId(playerRef.getUuid());
                if (mapId != null && plugin.getMapStore() != null) {
                    Map map = plugin.getMapStore().getMap(mapId);
                    InventoryUtils.giveRunItems(player, map);
                }
            }
            player.sendMessage(Message.raw(enabled ? "Reset item enabled." : "Reset item disabled."));
            player.getPageManager().openCustomPage(ref, store, new PlayerSettingsPage(playerRef));
            return;
        }
        if (BUTTON_SPEED_X1.equals(data.getButton())
                || BUTTON_SPEED_X2.equals(data.getButton())
                || BUTTON_SPEED_X4.equals(data.getButton())) {
            HyvexaPlugin plugin = HyvexaPlugin.getInstance();
            if (plugin == null || plugin.getProgressStore() == null) {
                player.sendMessage(Message.raw("Speed boost unavailable right now."));
                return;
            }
            UUID playerId = playerRef.getUuid();
            boolean isVipOrFounder = playerId != null
                    && (plugin.getProgressStore().isVip(playerId) || plugin.getProgressStore().isFounder(playerId));
            if (!isVipOrFounder) {
                player.sendMessage(Message.raw("Speed boost is VIP/Founder only."));
                return;
            }
            boolean inMap = playerId != null && plugin.getRunTracker() != null
                    && plugin.getRunTracker().getActiveMapId(playerId) != null;
            if (inMap) {
                player.sendMessage(Message.raw("Speed boost is only available outside runs."));
                return;
            }
            float multiplier = 1.0f;
            if (BUTTON_SPEED_X2.equals(data.getButton())) {
                multiplier = 2.0f;
            } else if (BUTTON_SPEED_X4.equals(data.getButton())) {
                multiplier = 4.0f;
            }
            World world = store.getExternalData().getWorld();
            if (world == null) {
                return;
            }
            float finalMultiplier = multiplier;
            world.execute(() -> {
                if (!ref.isValid() || !playerRef.isValid()) {
                    return;
                }
                plugin.applyVipSpeedMultiplier(ref, store, playerRef, finalMultiplier, true);
                Player playerEntity = store.getComponent(ref, Player.getComponentType());
                if (playerEntity != null) {
                    playerEntity.getPageManager().openCustomPage(ref, store, new PlayerSettingsPage(playerRef));
                }
            });
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

    private static String getResetItemLabel(UUID playerId) {
        return PlayerSettingsStore.isResetItemEnabled(playerId) ? RESET_ITEM_LABEL_DISABLE : RESET_ITEM_LABEL_ENABLE;
    }

}
