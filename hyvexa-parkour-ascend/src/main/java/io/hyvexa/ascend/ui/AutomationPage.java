package io.hyvexa.ascend.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
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
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AutomationPage extends InteractiveCustomUIPage<AutomationPage.AutomationData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TOGGLE = "Toggle";
    private static final String BUTTON_TOGGLE_EVOLUTION = "ToggleEvolution";
    private static final String BUTTON_TOGGLE_ELEVATION = "ToggleElevation";
    private static final String BUTTON_ELEV_ADD = "ElevAdd";
    private static final String BUTTON_ELEV_CLEAR = "ElevClear";
    private static final String BUTTON_ELEV_REMOVE_PREFIX = "ElevRemove";

    private static final int MAX_TARGETS = 5;

    private static final String COLOR_ON = "#4ade80";
    private static final String COLOR_OFF = "#6b7280";
    private static final String COLOR_ACCENT = "#f59e0b";
    private static final String COLOR_LOCKED_BORDER = "#4b5563";

    private final AscendPlayerStore playerStore;
    private final AscensionManager ascensionManager;

    private String timerInput;
    private String addValueInput;

    public AutomationPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, AscensionManager ascensionManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, AutomationData.CODEC);
        this.playerStore = playerStore;
        this.ascensionManager = ascensionManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Automation.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(AutomationData.KEY_BUTTON, BUTTON_CLOSE), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleButton",
            EventData.of(AutomationData.KEY_BUTTON, BUTTON_TOGGLE), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#EvoToggleButton",
            EventData.of(AutomationData.KEY_BUTTON, BUTTON_TOGGLE_EVOLUTION), false);

        // Auto-Elevation bindings
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ElevToggleButton",
            EventData.of(AutomationData.KEY_BUTTON, BUTTON_TOGGLE_ELEVATION), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ElevAddButton",
            EventData.of(AutomationData.KEY_BUTTON, BUTTON_ELEV_ADD), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClearAllButton",
            EventData.of(AutomationData.KEY_BUTTON, BUTTON_ELEV_CLEAR), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ElevTimerField",
            EventData.of(AutomationData.KEY_TIMER, "#ElevTimerField.Value"), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ElevAddField",
            EventData.of(AutomationData.KEY_ADD_VALUE, "#ElevAddField.Value"), false);

        for (int i = 0; i < MAX_TARGETS; i++) {
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ElevRemove" + i,
                EventData.of(AutomationData.KEY_BUTTON, BUTTON_ELEV_REMOVE_PREFIX + i), false);
        }

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

        // Auto-Upgrade section
        boolean hasSkill = ascensionManager.hasAutoRunners(playerId);
        boolean isEnabled = playerStore.isAutoUpgradeEnabled(playerId);

        if (!hasSkill) {
            commandBuilder.set("#UpgradeContent.Visible", false);
            commandBuilder.set("#UpgradeLockedOverlay.Visible", true);
        } else {
            commandBuilder.set("#UpgradeContent.Visible", true);
            commandBuilder.set("#UpgradeLockedOverlay.Visible", false);

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

        // Auto-Evolution section
        boolean hasEvoSkill = ascensionManager.hasAutoEvolution(playerId);
        boolean isEvoEnabled = playerStore.isAutoEvolutionEnabled(playerId);

        if (!hasEvoSkill) {
            commandBuilder.set("#EvoContent.Visible", false);
            commandBuilder.set("#EvoLockedOverlay.Visible", true);
        } else {
            commandBuilder.set("#EvoContent.Visible", true);
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

        // Auto-Elevation section
        boolean hasElevSkill = ascensionManager.hasAutoElevation(playerId);
        boolean isElevEnabled = playerStore.isAutoElevationEnabled(playerId);

        if (!hasElevSkill) {
            commandBuilder.set("#ElevContent.Visible", false);
            commandBuilder.set("#ElevLockedOverlay.Visible", true);
        } else {
            commandBuilder.set("#ElevContent.Visible", true);
            commandBuilder.set("#ElevLockedOverlay.Visible", false);

            if (isElevEnabled) {
                commandBuilder.set("#ElevToggleBorder.Background", COLOR_ON);
                commandBuilder.set("#ElevToggleText.Text", "Disable");
                commandBuilder.set("#ElevToggleText.Style.TextColor", COLOR_ON);
                commandBuilder.set("#ElevStatusLabel.Text", "Status: ON");
                commandBuilder.set("#ElevStatusLabel.Style.TextColor", COLOR_ON);
            } else {
                commandBuilder.set("#ElevToggleBorder.Background", COLOR_ACCENT);
                commandBuilder.set("#ElevToggleText.Text", "Enable");
                commandBuilder.set("#ElevToggleText.Style.TextColor", COLOR_ACCENT);
                commandBuilder.set("#ElevStatusLabel.Text", "Status: OFF");
                commandBuilder.set("#ElevStatusLabel.Style.TextColor", COLOR_OFF);
            }
        }

        // Timer field
        commandBuilder.set("#ElevTimerField.Value", String.valueOf(playerStore.getAutoElevationTimerSeconds(playerId)));

        // Recalculate targetIndex based on current multiplier before displaying
        List<Long> targets = playerStore.getAutoElevationTargets(playerId);
        recalculateTargetIndex(playerId, targets);
        int targetIndex = playerStore.getAutoElevationTargetIndex(playerId);
        for (int i = 0; i < MAX_TARGETS; i++) {
            if (i < targets.size()) {
                commandBuilder.set("#ElevTarget" + i + ".Visible", true);
                String prefix = (i == targetIndex) ? "-> " : "   ";
                commandBuilder.set("#ElevTargetLabel" + i + ".Text", prefix + (i + 1) + ". x" + targets.get(i));
            } else {
                commandBuilder.set("#ElevTarget" + i + ".Visible", false);
            }
        }

        // Next target info
        if (targets.isEmpty()) {
            commandBuilder.set("#ElevNextTarget.Text", "No targets configured.");
        } else if (targetIndex >= targets.size()) {
            commandBuilder.set("#ElevNextTarget.Text", "All targets reached!");
        } else {
            commandBuilder.set("#ElevNextTarget.Text",
                "Next target: x" + targets.get(targetIndex) + " (" + (targetIndex + 1) + "/" + targets.size() + ")");
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull AutomationData data) {
        super.handleDataEvent(ref, store, data);

        // Capture ValueChanged fields
        if (data.timerValue != null) {
            timerInput = data.timerValue;
            handleTimerChanged(ref, store);
        }
        if (data.addValue != null) {
            addValueInput = data.addValue;
        }

        if (data.button == null) {
            return;
        }

        if (BUTTON_CLOSE.equals(data.button)) {
            this.close();
            return;
        }

        if (BUTTON_TOGGLE.equals(data.button)) {
            handleToggle(ref, store);
            return;
        }

        if (BUTTON_TOGGLE_EVOLUTION.equals(data.button)) {
            handleToggleEvolution(ref, store);
            return;
        }

        if (BUTTON_TOGGLE_ELEVATION.equals(data.button)) {
            handleToggleElevation(ref, store);
            return;
        }

        if (BUTTON_ELEV_ADD.equals(data.button)) {
            handleAddTarget(ref, store);
            return;
        }

        if (BUTTON_ELEV_CLEAR.equals(data.button)) {
            handleClearAll(ref, store);
            return;
        }

        if (data.button.startsWith(BUTTON_ELEV_REMOVE_PREFIX)) {
            String indexStr = data.button.substring(BUTTON_ELEV_REMOVE_PREFIX.length());
            try {
                int index = Integer.parseInt(indexStr);
                handleRemoveTarget(ref, store, index);
            } catch (NumberFormatException ignored) {
            }
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
            player.sendMessage(Message.raw("[Automation] Unlock 'Auto-Upgrade' in the Ascendancy Tree first.")
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

    private void handleToggleElevation(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        if (!ascensionManager.hasAutoElevation(playerId)) {
            player.sendMessage(Message.raw("[Automation] Unlock 'Auto-Elevation' in the Ascendancy Tree first.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        boolean current = playerStore.isAutoElevationEnabled(playerId);
        boolean newState = !current;
        playerStore.setAutoElevationEnabled(playerId, newState);

        if (newState) {
            player.sendMessage(Message.raw("[Automation] Auto-elevation enabled.")
                .color(SystemMessageUtils.SUCCESS));
        } else {
            player.sendMessage(Message.raw("[Automation] Auto-elevation disabled.")
                .color(SystemMessageUtils.SECONDARY));
        }

        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateState(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }

    private void handleTimerChanged(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || timerInput == null) {
            return;
        }

        try {
            int timer = (int) Double.parseDouble(timerInput);
            timer = Math.max(0, Math.min(86400, timer));
            playerStore.setAutoElevationTimerSeconds(playerRef.getUuid(), timer);
        } catch (NumberFormatException ignored) {
        }
    }

    private void handleAddTarget(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        List<Long> targets = new ArrayList<>(playerStore.getAutoElevationTargets(playerId));

        if (targets.size() >= MAX_TARGETS) {
            player.sendMessage(Message.raw("[Automation] Maximum " + MAX_TARGETS + " targets allowed.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        if (addValueInput == null || addValueInput.isBlank()) {
            return;
        }

        try {
            long value = (long) Double.parseDouble(addValueInput);
            if (value < 1) {
                return;
            }
            int currentMultiplier = playerStore.getOrCreatePlayer(playerId).getElevationMultiplier();
            if (value <= currentMultiplier) {
                player.sendMessage(Message.raw("[Automation] Target must be higher than your current elevation (x" + currentMultiplier + ").")
                    .color(SystemMessageUtils.ERROR));
                return;
            }
            if (!targets.isEmpty() && value <= targets.get(targets.size() - 1)) {
                player.sendMessage(Message.raw("[Automation] Target must be higher than x" + targets.get(targets.size() - 1) + ".")
                    .color(SystemMessageUtils.ERROR));
                return;
            }
            targets.add(value);
            playerStore.setAutoElevationTargets(playerId, targets);
            recalculateTargetIndex(playerId, targets);
        } catch (NumberFormatException ignored) {
            return;
        }

        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateState(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }

    private void handleRemoveTarget(Ref<EntityStore> ref, Store<EntityStore> store, int index) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        List<Long> targets = new ArrayList<>(playerStore.getAutoElevationTargets(playerId));
        if (index < 0 || index >= targets.size()) {
            return;
        }

        targets.remove(index);
        playerStore.setAutoElevationTargets(playerId, targets);
        recalculateTargetIndex(playerId, targets);

        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateState(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }

    /**
     * Recalculates targetIndex so it points to the first target strictly greater than
     * the player's current elevation multiplier. Already-surpassed targets are skipped.
     */
    private void recalculateTargetIndex(UUID playerId, List<Long> targets) {
        int currentMultiplier = playerStore.getOrCreatePlayer(playerId).getElevationMultiplier();
        int newIndex = 0;
        while (newIndex < targets.size() && targets.get(newIndex) <= currentMultiplier) {
            newIndex++;
        }
        playerStore.setAutoElevationTargetIndex(playerId, newIndex);
    }

    private void handleClearAll(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        playerStore.setAutoElevationTargets(playerId, List.of());
        recalculateTargetIndex(playerId, List.of());

        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateState(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }

    public static class AutomationData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_TIMER = "@TimerValue";
        static final String KEY_ADD_VALUE = "@AddValue";

        public static final BuilderCodec<AutomationData> CODEC =
            BuilderCodec.<AutomationData>builder(AutomationData.class, AutomationData::new)
                .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING),
                    (data, value) -> data.button = value, data -> data.button)
                .addField(new KeyedCodec<>(KEY_TIMER, Codec.STRING),
                    (data, value) -> data.timerValue = value, data -> data.timerValue)
                .addField(new KeyedCodec<>(KEY_ADD_VALUE, Codec.STRING),
                    (data, value) -> data.addValue = value, data -> data.addValue)
                .build();

        String button;
        String timerValue;
        String addValue;
    }
}
