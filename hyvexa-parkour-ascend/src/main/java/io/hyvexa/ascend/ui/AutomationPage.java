package io.hyvexa.ascend.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutomationPage extends InteractiveCustomUIPage<AutomationPage.AutomationData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TOGGLE = "Toggle";
    private static final String BUTTON_TOGGLE_EVOLUTION = "ToggleEvolution";
    private static final String BUTTON_TOGGLE_ELEVATION = "ToggleElevation";
    private static final String BUTTON_ELEV_ADD = "ElevAdd";
    private static final String BUTTON_ELEV_CLEAR = "ElevClear";
    private static final String BUTTON_ELEV_REMOVE_PREFIX = "ElevRemove";
    private static final String BUTTON_TOGGLE_SUMMIT = "ToggleSummit";
    private static final String BUTTON_SUM_CAT_TOGGLE_PREFIX = "SumCatToggle";

    private static final int MAX_TARGETS = 5;
    private static final int SUMMIT_CATEGORIES = 3;

    private static final String COLOR_ON = "#4ade80";
    private static final String COLOR_OFF = "#6b7280";
    private static final String COLOR_ACCENT = "#f59e0b";
    private static final String COLOR_LOCKED_BORDER = "#4b5563";
    private static final long REFRESH_INTERVAL_MS = 1000L;

    private final AscendPlayerStore playerStore;
    private final AscensionManager ascensionManager;

    private String timerInput;
    private String addValueInput;
    private String sumTimerInput;
    private final String[] sumIncrementInput = new String[SUMMIT_CATEGORIES];

    private volatile ScheduledFuture<?> refreshTask;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final AtomicBoolean refreshRequested = new AtomicBoolean(false);
    private volatile boolean dismissed = false;

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

        // Auto-Summit bindings
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SumToggleButton",
            EventData.of(AutomationData.KEY_BUTTON, BUTTON_TOGGLE_SUMMIT), false);

        for (int i = 0; i < SUMMIT_CATEGORIES; i++) {
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SumCatToggle" + i,
                EventData.of(AutomationData.KEY_BUTTON, BUTTON_SUM_CAT_TOGGLE_PREFIX + i), false);
        }

        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SumTimerField",
            EventData.of(AutomationData.KEY_SUM_TIMER, "#SumTimerField.Value"), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SumIncField0",
            EventData.of(AutomationData.KEY_SUM_INC_0, "#SumIncField0.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SumIncField1",
            EventData.of(AutomationData.KEY_SUM_INC_1, "#SumIncField1.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SumIncField2",
            EventData.of(AutomationData.KEY_SUM_INC_2, "#SumIncField2.Value"), false);

        updateState(ref, store, commandBuilder);
        startAutoRefresh(ref, store);
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

        // Auto-Summit section
        boolean hasSumSkill = ascensionManager.hasAutoSummit(playerId);
        boolean isSumEnabled = playerStore.isAutoSummitEnabled(playerId);

        if (!hasSumSkill) {
            commandBuilder.set("#SumContent.Visible", false);
            commandBuilder.set("#SumLockedOverlay.Visible", true);
        } else {
            commandBuilder.set("#SumContent.Visible", true);
            commandBuilder.set("#SumLockedOverlay.Visible", false);

            if (isSumEnabled) {
                commandBuilder.set("#SumToggleBorder.Background", COLOR_ON);
                commandBuilder.set("#SumToggleText.Text", "Disable");
                commandBuilder.set("#SumToggleText.Style.TextColor", COLOR_ON);
                commandBuilder.set("#SumStatusLabel.Text", "Status: ON");
                commandBuilder.set("#SumStatusLabel.Style.TextColor", COLOR_ON);
            } else {
                commandBuilder.set("#SumToggleBorder.Background", COLOR_ACCENT);
                commandBuilder.set("#SumToggleText.Text", "Enable");
                commandBuilder.set("#SumToggleText.Style.TextColor", COLOR_ACCENT);
                commandBuilder.set("#SumStatusLabel.Text", "Status: OFF");
                commandBuilder.set("#SumStatusLabel.Style.TextColor", COLOR_OFF);
            }
        }

        // Summit timer
        commandBuilder.set("#SumTimerField.Value", String.valueOf(playerStore.getAutoSummitTimerSeconds(playerId)));

        // Summit categories
        List<AscendPlayerProgress.AutoSummitCategoryConfig> sumConfig = playerStore.getAutoSummitConfig(playerId);
        io.hyvexa.ascend.AscendConstants.SummitCategory[] categories = io.hyvexa.ascend.AscendConstants.SummitCategory.values();
        int rotationIndex = playerStore.getAutoSummitRotationIndex(playerId);

        for (int i = 0; i < SUMMIT_CATEGORIES; i++) {
            AscendPlayerProgress.AutoSummitCategoryConfig catConfig =
                i < sumConfig.size() ? sumConfig.get(i) : new AscendPlayerProgress.AutoSummitCategoryConfig(false, 0);

            // Level display
            int level = 0;
            if (i < categories.length) {
                level = playerStore.getSummitLevel(playerId, categories[i]);
            }
            commandBuilder.set("#SumCatLevel" + i + ".Text", "Lv " + level);

            // Toggle state
            if (catConfig.isEnabled()) {
                commandBuilder.set("#SumCatBorder" + i + ".Background", COLOR_ON);
                commandBuilder.set("#SumCatToggleText" + i + ".Text", "ON");
                commandBuilder.set("#SumCatToggleText" + i + ".Style.TextColor", COLOR_ON);
            } else {
                commandBuilder.set("#SumCatBorder" + i + ".Background", COLOR_LOCKED_BORDER);
                commandBuilder.set("#SumCatToggleText" + i + ".Text", "OFF");
                commandBuilder.set("#SumCatToggleText" + i + ".Style.TextColor", COLOR_OFF);
            }

            // Increment field value
            commandBuilder.set("#SumIncField" + i + ".Value", String.valueOf(catConfig.getIncrement()));
        }

        // Next category info
        String nextCatName = null;
        for (int i = 0; i < SUMMIT_CATEGORIES; i++) {
            int idx = (rotationIndex + i) % SUMMIT_CATEGORIES;
            if (idx < sumConfig.size() && sumConfig.get(idx).isEnabled() && idx < categories.length) {
                nextCatName = categories[idx].getDisplayName();
                break;
            }
        }
        if (nextCatName != null) {
            commandBuilder.set("#SumNextLabel.Text", "Next: " + nextCatName);
        } else {
            commandBuilder.set("#SumNextLabel.Text", "No categories enabled.");
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        dismissed = true;
        stopAutoRefresh();
        super.onDismiss(ref, store);
    }

    private void startAutoRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (refreshTask != null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        refreshTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            if (dismissed) {
                stopAutoRefresh();
                return;
            }
            if (ref == null || !ref.isValid()) {
                stopAutoRefresh();
                return;
            }
            PageRefreshScheduler.requestRefresh(
                world,
                refreshInFlight,
                refreshRequested,
                () -> refreshDisplay(ref, store),
                this::stopAutoRefresh,
                "AutomationPage"
            );
        }, REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        refreshRequested.set(false);
    }

    private void refreshDisplay(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (dismissed || ref == null || !ref.isValid()) {
            return;
        }
        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateState(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
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
        if (data.sumTimerValue != null) {
            sumTimerInput = data.sumTimerValue;
            handleSumTimerChanged(ref, store);
        }
        if (data.sumInc0 != null) {
            sumIncrementInput[0] = data.sumInc0;
            handleSumIncrementChanged(ref, store, 0);
        }
        if (data.sumInc1 != null) {
            sumIncrementInput[1] = data.sumInc1;
            handleSumIncrementChanged(ref, store, 1);
        }
        if (data.sumInc2 != null) {
            sumIncrementInput[2] = data.sumInc2;
            handleSumIncrementChanged(ref, store, 2);
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
            return;
        }

        if (BUTTON_TOGGLE_SUMMIT.equals(data.button)) {
            handleToggleSummit(ref, store);
            return;
        }

        if (data.button.startsWith(BUTTON_SUM_CAT_TOGGLE_PREFIX)) {
            String indexStr = data.button.substring(BUTTON_SUM_CAT_TOGGLE_PREFIX.length());
            try {
                int index = Integer.parseInt(indexStr);
                handleSumCategoryToggle(ref, store, index);
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
            int currentLevel = playerStore.getOrCreatePlayer(playerId).getElevationMultiplier();
            long currentActualMultiplier = Math.round(io.hyvexa.ascend.AscendConstants.getElevationMultiplier(currentLevel));
            if (value <= currentActualMultiplier) {
                player.sendMessage(Message.raw("[Automation] Target must be higher than your current elevation (" + io.hyvexa.ascend.AscendConstants.formatElevationMultiplier(currentLevel) + ").")
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
        int currentLevel = playerStore.getOrCreatePlayer(playerId).getElevationMultiplier();
        long currentActualMultiplier = Math.round(io.hyvexa.ascend.AscendConstants.getElevationMultiplier(currentLevel));
        int newIndex = 0;
        while (newIndex < targets.size() && targets.get(newIndex) <= currentActualMultiplier) {
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

    private void handleToggleSummit(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        if (!ascensionManager.hasAutoSummit(playerId)) {
            player.sendMessage(Message.raw("[Automation] Unlock 'Auto-Summit' in the Ascendancy Tree first.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        boolean current = playerStore.isAutoSummitEnabled(playerId);
        boolean newState = !current;
        playerStore.setAutoSummitEnabled(playerId, newState);

        if (newState) {
            player.sendMessage(Message.raw("[Automation] Auto-summit enabled.")
                .color(SystemMessageUtils.SUCCESS));
        } else {
            player.sendMessage(Message.raw("[Automation] Auto-summit disabled.")
                .color(SystemMessageUtils.SECONDARY));
        }

        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateState(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }

    private void handleSumTimerChanged(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || sumTimerInput == null) {
            return;
        }

        try {
            int timer = (int) Double.parseDouble(sumTimerInput);
            timer = Math.max(0, Math.min(86400, timer));
            playerStore.setAutoSummitTimerSeconds(playerRef.getUuid(), timer);
        } catch (NumberFormatException ignored) {
        }
    }

    private void handleSumCategoryToggle(Ref<EntityStore> ref, Store<EntityStore> store, int index) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || index < 0 || index >= SUMMIT_CATEGORIES) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        List<AscendPlayerProgress.AutoSummitCategoryConfig> config =
            new ArrayList<>(playerStore.getAutoSummitConfig(playerId));

        while (config.size() <= index) {
            config.add(new AscendPlayerProgress.AutoSummitCategoryConfig(false, 0));
        }

        AscendPlayerProgress.AutoSummitCategoryConfig catConfig = config.get(index);
        config.set(index, new AscendPlayerProgress.AutoSummitCategoryConfig(!catConfig.isEnabled(), catConfig.getIncrement()));
        playerStore.setAutoSummitConfig(playerId, config);

        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateState(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }

    private void handleSumIncrementChanged(Ref<EntityStore> ref, Store<EntityStore> store, int index) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || index < 0 || index >= SUMMIT_CATEGORIES) {
            return;
        }
        String input = sumIncrementInput[index];
        if (input == null) {
            return;
        }

        try {
            int increment = (int) Double.parseDouble(input);
            increment = Math.max(0, Math.min(1000, increment));

            UUID playerId = playerRef.getUuid();
            List<AscendPlayerProgress.AutoSummitCategoryConfig> config =
                new ArrayList<>(playerStore.getAutoSummitConfig(playerId));

            while (config.size() <= index) {
                config.add(new AscendPlayerProgress.AutoSummitCategoryConfig(false, 0));
            }

            AscendPlayerProgress.AutoSummitCategoryConfig catConfig = config.get(index);
            config.set(index, new AscendPlayerProgress.AutoSummitCategoryConfig(catConfig.isEnabled(), increment));
            playerStore.setAutoSummitConfig(playerId, config);
        } catch (NumberFormatException ignored) {
        }
    }

    public static class AutomationData {
        static final String KEY_BUTTON = "Button";
        static final String KEY_TIMER = "@TimerValue";
        static final String KEY_ADD_VALUE = "@AddValue";
        static final String KEY_SUM_TIMER = "@SumTimerValue";
        static final String KEY_SUM_INC_0 = "@SumInc0";
        static final String KEY_SUM_INC_1 = "@SumInc1";
        static final String KEY_SUM_INC_2 = "@SumInc2";

        public static final BuilderCodec<AutomationData> CODEC =
            BuilderCodec.<AutomationData>builder(AutomationData.class, AutomationData::new)
                .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING),
                    (data, value) -> data.button = value, data -> data.button)
                .addField(new KeyedCodec<>(KEY_TIMER, Codec.STRING),
                    (data, value) -> data.timerValue = value, data -> data.timerValue)
                .addField(new KeyedCodec<>(KEY_ADD_VALUE, Codec.STRING),
                    (data, value) -> data.addValue = value, data -> data.addValue)
                .addField(new KeyedCodec<>(KEY_SUM_TIMER, Codec.STRING),
                    (data, value) -> data.sumTimerValue = value, data -> data.sumTimerValue)
                .addField(new KeyedCodec<>(KEY_SUM_INC_0, Codec.STRING),
                    (data, value) -> data.sumInc0 = value, data -> data.sumInc0)
                .addField(new KeyedCodec<>(KEY_SUM_INC_1, Codec.STRING),
                    (data, value) -> data.sumInc1 = value, data -> data.sumInc1)
                .addField(new KeyedCodec<>(KEY_SUM_INC_2, Codec.STRING),
                    (data, value) -> data.sumInc2 = value, data -> data.sumInc2)
                .build();

        String button;
        String timerValue;
        String addValue;
        String sumTimerValue;
        String sumInc0;
        String sumInc1;
        String sumInc2;
    }
}
