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
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.SummitCategory;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;

public class SummitPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_COIN_FLOW = "SummitCoinFlow";
    private static final String BUTTON_RUNNER_SPEED = "SummitRunnerSpeed";
    private static final String BUTTON_MANUAL_MASTERY = "SummitManualMastery";

    private final AscendPlayerStore playerStore;
    private final SummitManager summitManager;

    public SummitPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, SummitManager summitManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.summitManager = summitManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Summit.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CoinFlowButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_COIN_FLOW), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RunnerSpeedButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_RUNNER_SPEED), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ManualMasteryButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_MANUAL_MASTERY), false);

        updateDisplay(ref, store, commandBuilder);
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

        SummitCategory category = switch (data.getButton()) {
            case BUTTON_COIN_FLOW -> SummitCategory.COIN_FLOW;
            case BUTTON_RUNNER_SPEED -> SummitCategory.RUNNER_SPEED;
            case BUTTON_MANUAL_MASTERY -> SummitCategory.MANUAL_MASTERY;
            default -> null;
        };

        if (category != null) {
            handleSummit(ref, store, category);
        }
    }

    private void handleSummit(Ref<EntityStore> ref, Store<EntityStore> store, SummitCategory category) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        if (!summitManager.canSummit(playerId)) {
            long coins = playerStore.getCoins(playerId);
            player.sendMessage(Message.raw("[Summit] Need " + AscendConstants.SUMMIT_MIN_COINS
                + " coins to Summit. You have: " + coins)
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        SummitManager.SummitPreview preview = summitManager.previewSummit(playerId, category);
        if (!preview.hasGain()) {
            player.sendMessage(Message.raw("[Summit] Insufficient coins for level gain.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        int newLevel = summitManager.performSummit(playerId, category);
        if (newLevel < 0) {
            player.sendMessage(Message.raw("[Summit] Summit failed.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        player.sendMessage(Message.raw("[Summit] " + category.getDisplayName() + " Lv." + preview.currentLevel()
            + " → Lv." + newLevel + " (+" + preview.levelGain() + ")")
            .color(SystemMessageUtils.SUCCESS));
        player.sendMessage(Message.raw("[Summit] Bonus: " + formatPercent(preview.currentBonus())
            + " → " + formatPercent(preview.newBonus()))
            .color(SystemMessageUtils.SUCCESS));

        // Check achievements
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
        }

        // Refresh display
        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateDisplay(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }

    private void updateDisplay(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        long coins = playerStore.getCoins(playerId);

        // Update coins display
        commandBuilder.set("#CoinsValue.Text", FormatUtils.formatCoinsForHud(coins));

        // Update each category level
        int coinFlowLevel = playerStore.getSummitLevel(playerId, SummitCategory.COIN_FLOW);
        int runnerSpeedLevel = playerStore.getSummitLevel(playerId, SummitCategory.RUNNER_SPEED);
        int manualMasteryLevel = playerStore.getSummitLevel(playerId, SummitCategory.MANUAL_MASTERY);

        commandBuilder.set("#CoinFlowLevel.Text", "Lv." + coinFlowLevel);
        commandBuilder.set("#RunnerSpeedLevel.Text", "Lv." + runnerSpeedLevel);
        commandBuilder.set("#ManualMasteryLevel.Text", "Lv." + manualMasteryLevel);
    }

    private String formatPercent(double value) {
        return String.format(Locale.US, "+%.0f%%", value * 100);
    }
}
