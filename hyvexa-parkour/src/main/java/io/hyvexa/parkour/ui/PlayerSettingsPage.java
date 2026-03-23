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
import io.hyvexa.common.visibility.EntityVisibilityManager;
import io.hyvexa.manager.HudManager;
import io.hyvexa.parkour.util.InventoryUtils;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.ghost.GhostNpcManager;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.util.PlayerSettingsStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.function.Consumer;

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
    private static final String BUTTON_TOGGLE_GHOST = "ToggleGhost";
    private static final String BUTTON_TOGGLE_ADVANCED_HUD = "ToggleAdvancedHud";
    private static final String RESET_ITEM_BUTTON_SELECTOR = "#ResetItemButton";
    private static final String RESET_ITEM_LABEL_DISABLE = "No reset item: Off";
    private static final String RESET_ITEM_LABEL_ENABLE = "No reset item: On";
    private static final String GHOST_BUTTON_SELECTOR = "#GhostButton";
    private static final String GHOST_LABEL_ON = "PB Ghost: On";
    private static final String GHOST_LABEL_OFF = "PB Ghost: Off";
    private static final String ADVANCED_HUD_BUTTON_SELECTOR = "#AdvancedHudButton";
    private static final String ADVANCED_HUD_LABEL_ON = "Advanced HUD: On";
    private static final String ADVANCED_HUD_LABEL_OFF = "Advanced HUD: Off";

    private final ProgressStore progressStore;
    private final RunTracker runTracker;
    private final MapStore mapStore;
    private final GhostNpcManager ghostNpcManager;
    private final HudManager hudManager;
    private final VipSpeedApplier vipSpeedApplier;

    @FunctionalInterface
    public interface VipSpeedApplier {
        void apply(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
                   float multiplier, boolean notify);
    }

    public PlayerSettingsPage(@Nonnull PlayerRef playerRef, ProgressStore progressStore,
                              RunTracker runTracker, MapStore mapStore,
                              GhostNpcManager ghostNpcManager, HudManager hudManager,
                              VipSpeedApplier vipSpeedApplier) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.progressStore = progressStore;
        this.runTracker = runTracker;
        this.mapStore = mapStore;
        this.ghostNpcManager = ghostNpcManager;
        this.hudManager = hudManager;
        this.vipSpeedApplier = vipSpeedApplier;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_PlayerSettings.ui");
        boolean showSpeedBoost = false;
        if (progressStore != null) {
            var playerId = playerRef.getUuid();
            boolean isVipOrFounder = playerId != null
                    && (progressStore.isVip(playerId) || progressStore.isFounder(playerId));
            boolean inMap = playerId != null && runTracker != null
                    && runTracker.getActiveMapId(playerId) != null;
            showSpeedBoost = isVipOrFounder && !inMap;
        }
        uiCommandBuilder.set("#VipSpeedLabel.Visible", showSpeedBoost);
        uiCommandBuilder.set("#VipSpeedRow.Visible", showSpeedBoost);
        uiCommandBuilder.set(RESET_ITEM_BUTTON_SELECTOR + ".Text", getResetItemLabel(playerRef.getUuid()));
        uiCommandBuilder.set(GHOST_BUTTON_SELECTOR + ".Text", getGhostLabel(playerRef.getUuid()));
        uiCommandBuilder.set(ADVANCED_HUD_BUTTON_SELECTOR + ".Text", getAdvancedHudLabel(playerRef.getUuid()));
        applyToggleIndicators(uiCommandBuilder, playerRef.getUuid(), hudManager);
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
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GhostButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TOGGLE_GHOST), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AdvancedHudButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TOGGLE_ADVANCED_HUD), false);
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
            PlayerSettingsStore.setPlayersHidden(playerRef.getUuid(), true);
            player.sendMessage(Message.raw("All players hidden."));
            player.getPageManager().openCustomPage(ref, store, new PlayerSettingsPage(playerRef));
            return;
        }
        if (BUTTON_SHOW_ALL.equals(data.getButton())) {
            showAllPlayers(playerRef);
            PlayerSettingsStore.setPlayersHidden(playerRef.getUuid(), false);
            player.sendMessage(Message.raw("All players shown."));
            player.getPageManager().openCustomPage(ref, store, new PlayerSettingsPage(playerRef));
            return;
        }
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        if (BUTTON_HIDE_HUD.equals(data.getButton())) {
            if (plugin != null) {
                plugin.hideRunHud(playerRef);
                player.sendMessage(Message.raw("Server HUD hidden."));
                player.getPageManager().openCustomPage(ref, store, new PlayerSettingsPage(playerRef));
            }
            return;
        }
        if (BUTTON_SHOW_HUD.equals(data.getButton())) {
            if (plugin != null) {
                plugin.showRunHud(playerRef);
                player.sendMessage(Message.raw("Server HUD shown."));
                player.getPageManager().openCustomPage(ref, store, new PlayerSettingsPage(playerRef));
            }
            return;
        }
        if (BUTTON_MUSIC.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store, new PlayerMusicPage(playerRef));
            return;
        }
        if (BUTTON_TOGGLE_RESET_ITEM.equals(data.getButton())) {
            boolean enabled = PlayerSettingsStore.toggleResetItemEnabled(playerRef.getUuid());
            if (plugin != null && plugin.getRunTracker() != null) {
                String mapId = plugin.getRunTracker().getActiveMapId(playerRef.getUuid());
                if (mapId != null && plugin.getMapStore() != null) {
                    Map map = plugin.getMapStore().getMap(mapId);
                    boolean practiceEnabled = plugin.getRunTracker().isPracticeEnabled(playerRef.getUuid());
                    InventoryUtils.giveRunItems(player, map, practiceEnabled);
                }
            }
            player.sendMessage(Message.raw(enabled ? "Reset item enabled." : "Reset item disabled."));
            player.getPageManager().openCustomPage(ref, store, new PlayerSettingsPage(playerRef));
            return;
        }
        if (BUTTON_TOGGLE_GHOST.equals(data.getButton())) {
            boolean visible = PlayerSettingsStore.toggleGhostVisible(playerRef.getUuid());
            if (plugin != null && plugin.getRunTracker() != null) {
                String mapId = plugin.getRunTracker().getActiveMapId(playerRef.getUuid());
                if (mapId != null) {
                    GhostNpcManager ghostNpcManager = plugin.getGhostNpcManager();
                    if (ghostNpcManager != null) {
                        if (!visible) {
                            ghostNpcManager.despawnGhost(playerRef.getUuid());
                        } else {
                            ghostNpcManager.spawnGhost(playerRef.getUuid(), mapId);
                        }
                    }
                }
            }
            player.sendMessage(Message.raw(visible ? "PB ghost enabled." : "PB ghost disabled."));
            player.getPageManager().openCustomPage(ref, store, new PlayerSettingsPage(playerRef));
            return;
        }
        if (BUTTON_TOGGLE_ADVANCED_HUD.equals(data.getButton())) {
            boolean enabled = PlayerSettingsStore.toggleAdvancedHud(playerRef.getUuid());
            if (plugin != null && plugin.getHudManager() != null) {
                plugin.getHudManager().setAdvancedHudVisible(playerRef, enabled);
            }
            player.sendMessage(Message.raw(enabled ? "Advanced HUD enabled." : "Advanced HUD disabled."));
            player.getPageManager().openCustomPage(ref, store, new PlayerSettingsPage(playerRef));
            return;
        }
        if (BUTTON_SPEED_X1.equals(data.getButton())
                || BUTTON_SPEED_X2.equals(data.getButton())
                || BUTTON_SPEED_X4.equals(data.getButton())) {
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
                world.execute(() -> applyHiddenState(viewerRef, world)));
    }

    private void showAllPlayers(@Nonnull PlayerRef viewerRef) {
        EntityVisibilityManager.get().clearHidden(viewerRef.getUuid());
    }

    private void applyHiddenState(@Nonnull PlayerRef viewerRef, @Nonnull World world) {
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
            EntityVisibilityManager.get().hideEntity(viewerRef.getUuid(), uuidComponent.getUuid());
        }
    }

    private static void applyToggleIndicators(UICommandBuilder cmd, UUID playerId, HyvexaPlugin plugin) {
        boolean playersHidden = PlayerSettingsStore.isPlayersHidden(playerId);
        cmd.set("#HidePlayersIndicator.Visible", playersHidden);
        cmd.set("#ShowPlayersIndicator.Visible", !playersHidden);

        boolean hudHidden = plugin != null && plugin.getHudManager() != null
                && plugin.getHudManager().isRunHudHidden(playerId);
        cmd.set("#HideHudIndicator.Visible", hudHidden);
        cmd.set("#ShowHudIndicator.Visible", !hudHidden);
    }

    private static String getResetItemLabel(UUID playerId) {
        return PlayerSettingsStore.isResetItemEnabled(playerId) ? RESET_ITEM_LABEL_DISABLE : RESET_ITEM_LABEL_ENABLE;
    }

    private static String getGhostLabel(UUID playerId) {
        return PlayerSettingsStore.isGhostVisible(playerId) ? GHOST_LABEL_ON : GHOST_LABEL_OFF;
    }

    private static String getAdvancedHudLabel(UUID playerId) {
        return PlayerSettingsStore.isAdvancedHudEnabled(playerId) ? ADVANCED_HUD_LABEL_ON : ADVANCED_HUD_LABEL_OFF;
    }

}
