package io.hyvexa.runorfall.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.runorfall.HyvexaRunOrFallPlugin;
import io.hyvexa.runorfall.manager.RunOrFallStatsStore;

import javax.annotation.Nonnull;
import java.util.UUID;

public class RunOrFallSettingsPage extends InteractiveCustomUIPage<ButtonEventData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_MUSIC = "Music";
    private static final String BUTTON_HIDE_HUD = "HideHud";
    private static final String BUTTON_SHOW_HUD = "ShowHud";

    private final RunOrFallStatsStore statsStore;
    private final boolean fromProfile;

    public RunOrFallSettingsPage(@Nonnull PlayerRef playerRef, RunOrFallStatsStore statsStore, boolean fromProfile) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ButtonEventData.CODEC);
        this.statsStore = statsStore;
        this.fromProfile = fromProfile;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/RunOrFall_Settings.ui");
        if (fromProfile) {
            commandBuilder.set("#CloseButton.Text", "Back");
        }
        PlayerRef currentPlayer = store.getComponent(ref, PlayerRef.getComponentType());
        UUID playerId = currentPlayer != null ? currentPlayer.getUuid() : null;
        applyIndicators(commandBuilder, playerId);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MusicButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_MUSIC), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#HideHudButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_HIDE_HUD), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ShowHudButton",
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
            if (!fromProfile) {
                this.close();
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || playerRef == null) {
                this.close();
                return;
            }
            player.getPageManager().openCustomPage(ref, store, new RunOrFallProfilePage(playerRef, statsStore));
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null || playerRef.getUuid() == null) {
            return;
        }
        HyvexaRunOrFallPlugin plugin = HyvexaRunOrFallPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        if (BUTTON_MUSIC.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store,
                    new RunOrFallMusicPage(playerRef, statsStore, fromProfile));
            return;
        }
        if (BUTTON_HIDE_HUD.equals(data.getButton())) {
            plugin.hideHud(playerRef.getUuid());
            player.sendMessage(Message.raw("HUD hidden."));
            player.getPageManager().openCustomPage(ref, store,
                    new RunOrFallSettingsPage(playerRef, statsStore, fromProfile));
            return;
        }
        if (BUTTON_SHOW_HUD.equals(data.getButton())) {
            plugin.showHud(playerRef.getUuid());
            player.sendMessage(Message.raw("HUD shown."));
            player.getPageManager().openCustomPage(ref, store,
                    new RunOrFallSettingsPage(playerRef, statsStore, fromProfile));
        }
    }

    private static void applyIndicators(UICommandBuilder commandBuilder, UUID playerId) {
        HyvexaRunOrFallPlugin plugin = HyvexaRunOrFallPlugin.getInstance();
        boolean hudHidden = plugin != null && plugin.isHudHidden(playerId);
        commandBuilder.set("#HideHudIndicator.Visible", hudHidden);
        commandBuilder.set("#ShowHudIndicator.Visible", !hudHidden);
    }
}
