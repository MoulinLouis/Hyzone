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
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
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
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class AutomationPage extends InteractiveCustomUIPage<AutomationPage.AutomationData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TOGGLE = "Toggle";
    private static final String BUTTON_TOGGLE_EVOLUTION = "ToggleEvolution";
    private static final String BUTTON_TOGGLE_ASCEND = "ToggleAscend";
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
    private static final SummitCategory[] SUMMIT_CATEGORY_VALUES = SummitCategory.values();
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

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AscToggleButton",
            EventData.of(AutomationData.KEY_BUTTON, BUTTON_TOGGLE_ASCEND), false);

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
        renderToggleSection(commandBuilder, "Upgrade", "",
            ascensionManager.hasAutoRunners(playerId), playerStore.isAutoUpgradeEnabled(playerId));

        // Auto-Evolution section
        renderToggleSection(commandBuilder, "Evo", "Evo",
            ascensionManager.hasAutoEvolution(playerId), playerStore.isAutoEvolutionEnabled(playerId));

        // Auto-Ascend section
        renderToggleSection(commandBuilder, "Asc", "Asc",
            ascensionManager.hasAutoAscend(playerId), playerStore.isAutoAscendEnabled(playerId));

        // Auto-Elevation section
        renderToggleSection(commandBuilder, "Elev", "Elev",
            ascensionManager.hasAutoElevation(playerId), playerStore.isAutoElevationEnabled(playerId));

        // Timer field
        commandBuilder.set("#ElevTimerField.Value", String.valueOf(playerStore.getAutoElevationTimerSeconds(playerId)));

        List<Long> targets = playerStore.getAutoElevationTargets(playerId);
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
        renderToggleSection(commandBuilder, "Sum", "Sum",
            ascensionManager.hasAutoSummit(playerId), playerStore.isAutoSummitEnabled(playerId));

        // Summit timer
        commandBuilder.set("#SumTimerField.Value", String.valueOf(playerStore.getAutoSummitTimerSeconds(playerId)));

        // Summit categories
        List<AscendPlayerProgress.AutoSummitCategoryConfig> sumConfig = playerStore.getAutoSummitConfig(playerId);

        int activeCount = 0;
        int reachedCount = 0;

        for (int i = 0; i < SUMMIT_CATEGORIES; i++) {
            AscendPlayerProgress.AutoSummitCategoryConfig catConfig =
                i < sumConfig.size() ? sumConfig.get(i) : new AscendPlayerProgress.AutoSummitCategoryConfig(false, 0);

            // Level display
            int level = 0;
            if (i < SUMMIT_CATEGORY_VALUES.length) {
                level = playerStore.getSummitLevel(playerId, SUMMIT_CATEGORY_VALUES[i]);
            }

            int targetLevel = catConfig.getTargetLevel();
            boolean reached = targetLevel > 0 && level >= targetLevel;

            if (catConfig.isEnabled() && targetLevel > 0) {
                activeCount++;
                if (reached) reachedCount++;
            }

            // Show current level and target status
            String levelText = reached && targetLevel > 0
                ? "Lv " + level + " (Reached!)"
                : "Lv " + level;
            commandBuilder.set("#SumCatLevel" + i + ".Text", levelText);

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

            // Target level field value
            commandBuilder.set("#SumIncField" + i + ".Value", String.valueOf(catConfig.getTargetLevel()));
        }

        // Target summary
        if (activeCount == 0) {
            commandBuilder.set("#SumNextLabel.Text", "No categories enabled.");
        } else if (reachedCount >= activeCount) {
            commandBuilder.set("#SumNextLabel.Text", "All targets reached!");
        } else {
            commandBuilder.set("#SumNextLabel.Text",
                (activeCount - reachedCount) + " target" + (activeCount - reachedCount > 1 ? "s" : "") + " pending.");
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

        if (BUTTON_TOGGLE_ASCEND.equals(data.button)) {
            handleToggleAscend(ref, store);
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
        handleGenericToggle(ref, store,
            id -> ascensionManager.hasAutoRunners(id),
            id -> playerStore.isAutoUpgradeEnabled(id),
            (id, state) -> playerStore.setAutoUpgradeEnabled(id, state),
            "Auto-Upgrade", "Auto-upgrade");
    }

    private void handleToggleEvolution(Ref<EntityStore> ref, Store<EntityStore> store) {
        handleGenericToggle(ref, store,
            id -> ascensionManager.hasAutoEvolution(id),
            id -> playerStore.isAutoEvolutionEnabled(id),
            (id, state) -> playerStore.setAutoEvolutionEnabled(id, state),
            "Auto-Evolution", "Auto-evolution");
    }

    private void handleToggleAscend(Ref<EntityStore> ref, Store<EntityStore> store) {
        handleGenericToggle(ref, store,
            id -> ascensionManager.hasAutoAscend(id),
            id -> playerStore.isAutoAscendEnabled(id),
            (id, state) -> playerStore.setAutoAscendEnabled(id, state),
            "Auto Ascend", "Auto ascend");
    }

    private void handleToggleElevation(Ref<EntityStore> ref, Store<EntityStore> store) {
        handleGenericToggle(ref, store,
            id -> ascensionManager.hasAutoElevation(id),
            id -> playerStore.isAutoElevationEnabled(id),
            (id, state) -> playerStore.setAutoElevationEnabled(id, state),
            "Auto-Elevation", "Auto-elevation");
    }

    private void handleGenericToggle(Ref<EntityStore> ref, Store<EntityStore> store,
                                     Predicate<UUID> hasSkill, Predicate<UUID> isEnabled,
                                     BiConsumer<UUID, Boolean> setEnabled,
                                     String skillName, String featureName) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        if (!hasSkill.test(playerId)) {
            player.sendMessage(Message.raw("[Automation] Unlock '" + skillName + "' in the Ascendancy Tree first.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        boolean newState = !isEnabled.test(playerId);
        setEnabled.accept(playerId, newState);

        if (newState) {
            player.sendMessage(Message.raw("[Automation] " + featureName + " enabled.")
                .color(SystemMessageUtils.SUCCESS));
        } else {
            player.sendMessage(Message.raw("[Automation] " + featureName + " disabled.")
                .color(SystemMessageUtils.SECONDARY));
        }

        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateState(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }

    /**
     * Renders the locked/unlocked toggle UI for a single automation section.
     *
     * @param contentPrefix prefix for Content/LockedOverlay elements (e.g. "Upgrade", "Evo")
     * @param togglePrefix  prefix for ToggleBorder/ToggleText/StatusLabel elements (e.g. "" for Upgrade, "Evo" for Evolution)
     */
    private void renderToggleSection(UICommandBuilder commandBuilder,
                                     String contentPrefix, String togglePrefix,
                                     boolean hasSkill, boolean isEnabled) {
        if (!hasSkill) {
            commandBuilder.set("#" + contentPrefix + "Content.Visible", false);
            commandBuilder.set("#" + contentPrefix + "LockedOverlay.Visible", true);
        } else {
            commandBuilder.set("#" + contentPrefix + "Content.Visible", true);
            commandBuilder.set("#" + contentPrefix + "LockedOverlay.Visible", false);

            if (isEnabled) {
                commandBuilder.set("#" + togglePrefix + "ToggleBorder.Background", COLOR_ON);
                commandBuilder.set("#" + togglePrefix + "ToggleText.Text", "Disable");
                commandBuilder.set("#" + togglePrefix + "ToggleText.Style.TextColor", COLOR_ON);
                commandBuilder.set("#" + togglePrefix + "StatusLabel.Text", "Status: ON");
                commandBuilder.set("#" + togglePrefix + "StatusLabel.Style.TextColor", COLOR_ON);
            } else {
                commandBuilder.set("#" + togglePrefix + "ToggleBorder.Background", COLOR_ACCENT);
                commandBuilder.set("#" + togglePrefix + "ToggleText.Text", "Enable");
                commandBuilder.set("#" + togglePrefix + "ToggleText.Style.TextColor", COLOR_ACCENT);
                commandBuilder.set("#" + togglePrefix + "StatusLabel.Text", "Status: OFF");
                commandBuilder.set("#" + togglePrefix + "StatusLabel.Style.TextColor", COLOR_OFF);
            }
        }
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
            long currentActualMultiplier = Math.round(AscendConstants.getElevationMultiplier(currentLevel));
            if (value <= currentActualMultiplier) {
                player.sendMessage(Message.raw("[Automation] Target must be higher than your current elevation (" + AscendConstants.formatElevationMultiplier(currentLevel) + ").")
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
        long currentActualMultiplier = Math.round(AscendConstants.getElevationMultiplier(currentLevel));
        int newIndex = 0;
        while (newIndex < targets.size() && targets.get(newIndex) <= currentActualMultiplier) {
            newIndex++;
        }
        int currentIndex = playerStore.getAutoElevationTargetIndex(playerId);
        if (newIndex != currentIndex) {
            playerStore.setAutoElevationTargetIndex(playerId, newIndex);
        }
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
        handleGenericToggle(ref, store,
            id -> ascensionManager.hasAutoSummit(id),
            id -> playerStore.isAutoSummitEnabled(id),
            (id, state) -> playerStore.setAutoSummitEnabled(id, state),
            "Auto-Summit", "Auto-summit");
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
        config.set(index, new AscendPlayerProgress.AutoSummitCategoryConfig(!catConfig.isEnabled(), catConfig.getTargetLevel()));
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
            int targetLevel = (int) Double.parseDouble(input);
            targetLevel = Math.max(0, Math.min(1000, targetLevel));

            UUID playerId = playerRef.getUuid();
            List<AscendPlayerProgress.AutoSummitCategoryConfig> config =
                new ArrayList<>(playerStore.getAutoSummitConfig(playerId));

            while (config.size() <= index) {
                config.add(new AscendPlayerProgress.AutoSummitCategoryConfig(false, 0));
            }

            AscendPlayerProgress.AutoSummitCategoryConfig catConfig = config.get(index);
            config.set(index, new AscendPlayerProgress.AutoSummitCategoryConfig(catConfig.isEnabled(), targetLevel));
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
