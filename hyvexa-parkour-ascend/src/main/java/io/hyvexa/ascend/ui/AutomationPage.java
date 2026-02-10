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
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.UUID;

public class AutomationPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TOGGLE = "Toggle";
    private static final String BUTTON_TOGGLE_EVOLUTION = "ToggleEvolution";

    private static final String COLOR_ON = "#4ade80";
    private static final String COLOR_OFF = "#6b7280";
    private static final String COLOR_ACCENT = "#f59e0b";
    private static final String COLOR_LOCKED_BORDER = "#4b5563";

    private final AscendPlayerStore playerStore;
    private final AscensionManager ascensionManager;

    public AutomationPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, AscensionManager ascensionManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.ascensionManager = ascensionManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Automation.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TOGGLE), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#EvoToggleButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TOGGLE_EVOLUTION), false);

        updateState(ref, store, commandBuilder);
    }

    private void updateState(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        // Show disclaimer if player hasn't ascended yet
        AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerId);
        boolean showDisclaimer = progress.getAscensionCount() == 0;
        commandBuilder.set("#AscensionDisclaimer.Visible", showDisclaimer);
        commandBuilder.set("#DisclaimerSpacer.Visible", showDisclaimer);

        boolean hasSkill = ascensionManager.hasAutoRunners(playerId);
        boolean isEnabled = playerStore.isAutoUpgradeEnabled(playerId);

        if (!hasSkill) {
            // Skill not unlocked — show locked state
            commandBuilder.set("#ToggleButton.Disabled", true);
            commandBuilder.set("#ToggleBorder.Background", COLOR_LOCKED_BORDER);
            commandBuilder.set("#ToggleText.Text", "Locked");
            commandBuilder.set("#ToggleText.Style.TextColor", COLOR_OFF);
            commandBuilder.set("#StatusLabel.Text", "Status: LOCKED");
            commandBuilder.set("#StatusLabel.Style.TextColor", COLOR_OFF);
            commandBuilder.set("#LockedOverlay.Visible", true);
        } else {
            // Skill unlocked — show toggle state
            commandBuilder.set("#ToggleButton.Disabled", false);
            commandBuilder.set("#LockedOverlay.Visible", false);

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

        // Auto-Evolution toggle
        boolean hasEvoSkill = ascensionManager.hasAutoEvolution(playerId);
        boolean isEvoEnabled = playerStore.isAutoEvolutionEnabled(playerId);

        if (!hasEvoSkill) {
            commandBuilder.set("#EvoToggleButton.Disabled", true);
            commandBuilder.set("#EvoToggleBorder.Background", COLOR_LOCKED_BORDER);
            commandBuilder.set("#EvoToggleText.Text", "Locked");
            commandBuilder.set("#EvoToggleText.Style.TextColor", COLOR_OFF);
            commandBuilder.set("#EvoStatusLabel.Text", "Status: LOCKED");
            commandBuilder.set("#EvoStatusLabel.Style.TextColor", COLOR_OFF);
            commandBuilder.set("#EvoLockedOverlay.Visible", true);
        } else {
            commandBuilder.set("#EvoToggleButton.Disabled", false);
            commandBuilder.set("#EvoLockedOverlay.Visible", false);

            if (isEvoEnabled) {
                commandBuilder.set("#EvoToggleBorder.Background", COLOR_ON);
                commandBuilder.set("#EvoToggleText.Text", "Disable");
                commandBuilder.set("#EvoToggleText.Style.TextColor", COLOR_ON);
                commandBuilder.set("#EvoStatusLabel.Text", "Status: ON");
                commandBuilder.set("#EvoStatusLabel.Style.TextColor", COLOR_ON);
            } else {
                commandBuilder.set("#EvoToggleBorder.Background", COLOR_ACCENT);
                commandBuilder.set("#EvoToggleText.Text", "Enable");
                commandBuilder.set("#EvoToggleText.Style.TextColor", COLOR_ACCENT);
                commandBuilder.set("#EvoStatusLabel.Text", "Status: OFF");
                commandBuilder.set("#EvoStatusLabel.Style.TextColor", COLOR_OFF);
            }
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
            return;
        }

        if (BUTTON_TOGGLE_EVOLUTION.equals(data.getButton())) {
            handleToggleEvolution(ref, store);
        }
    }

    private void handleToggle(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        if (!ascensionManager.hasAutoRunners(playerId)) {
            player.sendMessage(Message.raw("[Automation] Unlock 'Auto-Upgrade + Momentum' in the Ascendancy Tree first.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        boolean current = playerStore.isAutoUpgradeEnabled(playerId);
        boolean newState = !current;
        playerStore.setAutoUpgradeEnabled(playerId, newState);

        if (newState) {
            player.sendMessage(Message.raw("[Automation] Auto-upgrade enabled.")
                .color(SystemMessageUtils.SUCCESS));
        } else {
            player.sendMessage(Message.raw("[Automation] Auto-upgrade disabled.")
                .color(SystemMessageUtils.SECONDARY));
        }

        // Refresh UI
        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateState(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }

    private void handleToggleEvolution(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        if (!ascensionManager.hasAutoEvolution(playerId)) {
            player.sendMessage(Message.raw("[Automation] Unlock 'Auto-Evolution' in the Ascendancy Tree first.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        boolean current = playerStore.isAutoEvolutionEnabled(playerId);
        boolean newState = !current;
        playerStore.setAutoEvolutionEnabled(playerId, newState);

        if (newState) {
            player.sendMessage(Message.raw("[Automation] Auto-evolution enabled.")
                .color(SystemMessageUtils.SUCCESS));
        } else {
            player.sendMessage(Message.raw("[Automation] Auto-evolution disabled.")
                .color(SystemMessageUtils.SECONDARY));
        }

        // Refresh UI
        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateState(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }
}
