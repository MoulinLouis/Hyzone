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
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.UUID;

public class AscendSettingsPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TOGGLE = "Toggle";

    private static final String COLOR_ON = "#4ade80";
    private static final String COLOR_OFF = "#6b7280";
    private static final String COLOR_ACCENT = "#f59e0b";

    private final AscendPlayerStore playerStore;
    private final RobotManager robotManager;

    public AscendSettingsPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, RobotManager robotManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.robotManager = robotManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Settings.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TOGGLE), false);

        updateState(ref, store, commandBuilder);
    }

    private void updateState(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        boolean isEnabled = playerStore.isHideOtherRunners(playerId);

        if (isEnabled) {
            commandBuilder.set("#ToggleBorder.Background", COLOR_ON);
            commandBuilder.set("#ToggleText.Text", "Disable");
            commandBuilder.set("#ToggleText.Style.TextColor", COLOR_ON);
            commandBuilder.set("#StatusLabel.Text", "Status: ON");
            commandBuilder.set("#StatusLabel.Style.TextColor", COLOR_ON);
        } else {
            commandBuilder.set("#ToggleBorder.Background", COLOR_ACCENT);
            commandBuilder.set("#ToggleText.Text", "Enable");
            commandBuilder.set("#ToggleText.Style.TextColor", COLOR_ACCENT);
            commandBuilder.set("#StatusLabel.Text", "Status: OFF");
            commandBuilder.set("#StatusLabel.Style.TextColor", COLOR_OFF);
        }
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

        if (BUTTON_TOGGLE.equals(data.getButton())) {
            handleToggle(ref, store);
        }
    }

    private void handleToggle(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        boolean current = playerStore.isHideOtherRunners(playerId);
        boolean newState = !current;
        playerStore.setHideOtherRunners(playerId, newState);

        if (robotManager != null) {
            robotManager.applyRunnerVisibility(playerId);
        }

        if (newState) {
            player.sendMessage(Message.raw("[Settings] Other players' runners are now hidden.")
                .color(SystemMessageUtils.SUCCESS));
        } else {
            player.sendMessage(Message.raw("[Settings] Other players' runners are now visible.")
                .color(SystemMessageUtils.SECONDARY));
        }

        // Refresh UI
        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateState(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }
}
