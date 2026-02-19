package io.hyvexa.runorfall.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.runorfall.data.RunOrFallPlayerStats;
import io.hyvexa.runorfall.manager.RunOrFallStatsStore;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;

public class RunOrFallStatsPage extends InteractiveCustomUIPage<ButtonEventData> {
    private static final String BUTTON_CLOSE = "Close";

    private final RunOrFallStatsStore statsStore;

    public RunOrFallStatsPage(@Nonnull PlayerRef playerRef, RunOrFallStatsStore statsStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ButtonEventData.CODEC);
        this.statsStore = statsStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/RunOrFall_Stats.ui");
        populateFields(ref, store, commandBuilder);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
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
        }
    }

    private void populateFields(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        if (ref == null || !ref.isValid() || store == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || playerRef.getUuid() == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        String playerName = playerRef.getUsername();
        RunOrFallPlayerStats stats = statsStore.getStats(playerId, playerName);

        commandBuilder.set("#StatsPlayerName.Text", stats.getPlayerName());
        commandBuilder.set("#StatsWinsValue.Text", String.valueOf(stats.getWins()));
        commandBuilder.set("#StatsLooseValue.Text", String.valueOf(stats.getLosses()));
        commandBuilder.set("#StatsWinrateValue.Text",
                String.format(Locale.US, "%.2f%%", stats.getWinRatePercent()));
        commandBuilder.set("#StatsBestStreakValue.Text", String.valueOf(stats.getBestWinStreak()));
        commandBuilder.set("#StatsLongestTimeValue.Text", formatDuration(stats.getLongestSurvivedMs()));
    }

    private static String formatDuration(long millis) {
        long safeMillis = Math.max(0L, millis);
        long totalSeconds = safeMillis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        long centiseconds = (safeMillis % 1000L) / 10L;
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centiseconds);
    }
}
