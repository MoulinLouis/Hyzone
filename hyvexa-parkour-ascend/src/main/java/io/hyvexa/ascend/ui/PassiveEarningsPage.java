package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.passive.PassiveEarningsManager;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;

import javax.annotation.Nonnull;
import java.util.Map;

public class PassiveEarningsPage extends BaseAscendPage {

    private static final String BUTTON_CLAIM = "Claim";
    private static final String[] MAP_COLORS = {
        "#7c3aed", "#ef4444", "#f59e0b", "#10b981", "#3b82f6"
    };

    private final PassiveEarningsManager.PassiveEarningsResult result;

    public PassiveEarningsPage(@Nonnull PlayerRef playerRef,
                              PassiveEarningsManager.PassiveEarningsResult result) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.result = result;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_PassiveEarnings.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ClaimButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLAIM), false);

        updateSummary(commandBuilder);
        buildRunnerEntries(commandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (BUTTON_CLAIM.equals(data.getButton())) {
            this.close();
        }
    }

    private void updateSummary(UICommandBuilder commandBuilder) {
        // Format away time
        long timeAwayMs = result.timeAwayMs();
        String timeStr = FormatUtils.formatDurationLong(timeAwayMs);
        String awayText = "You were away for " + timeStr;

        // Cap indicator
        if (timeAwayMs >= 24 * 60 * 60 * 1000L) {
            awayText = "You were away for " + timeStr + " (capped at 24 hours)";
        }
        commandBuilder.set("#AwayTimeLabel.Text", awayText);

        // Total volt
        String voltStr = FormatUtils.formatBigNumber(result.totalVolt());
        commandBuilder.set("#TotalVoltLabel.Text", voltStr + " VOLT");

        // Total multiplier
        String multStr = FormatUtils.formatBigNumber(result.totalMultiplier());
        commandBuilder.set("#MultiplierLabel.Text", "+" + multStr + " total multiplier");
    }

    private void buildRunnerEntries(UICommandBuilder commandBuilder) {
        commandBuilder.clear("#RunnerEntries");

        // Sort entries by map ID (parkour1, parkour2, etc.)
        var sortedEntries = result.runnerBreakdown().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .toList();

        int index = 0;
        for (Map.Entry<String, PassiveEarningsManager.PassiveRunnerEarnings> entry : sortedEntries) {
            PassiveEarningsManager.PassiveRunnerEarnings earnings = entry.getValue();

            // Append new entry
            commandBuilder.append("#RunnerEntries", "Pages/Ascend_PassiveEarningsEntry.ui");

            // Use array index syntax to target the specific entry
            String selector = "#RunnerEntries[" + index + "]";

            // Accent bar color
            String color = MAP_COLORS[index % MAP_COLORS.length];
            commandBuilder.set(selector + " #AccentBar.Background", color);

            // Map name
            commandBuilder.set(selector + " #MapName.Text", earnings.mapName());

            // Stars (show star images)
            int stars = earnings.stars();
            for (int i = 1; i <= 5; i++) {
                commandBuilder.set(selector + " #Star" + i + ".Visible", i <= stars);
            }

            // Speed level
            commandBuilder.set(selector + " #SpeedLevel.Text", "Lv." + earnings.speedLevel());

            // Runs completed
            commandBuilder.set(selector + " #Runs.Text", earnings.runsCompleted() + " runs");

            // Multiplier gain
            String multStr = FormatUtils.formatBigNumber(earnings.multiplierGain());
            commandBuilder.set(selector + " #Multiplier.Text", "+" + multStr + " mult");

            // Volt earned
            String voltStr = FormatUtils.formatBigNumber(earnings.voltEarned());
            commandBuilder.set(selector + " #Volt.Text", voltStr);

            index++;
        }
    }

}
