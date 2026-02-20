package io.hyvexa.runorfall.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.ui.PaginationState;
import io.hyvexa.runorfall.data.RunOrFallPlayerStats;
import io.hyvexa.runorfall.manager.RunOrFallStatsStore;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class RunOrFallLeaderboardPage extends InteractiveCustomUIPage<RunOrFallLeaderboardPage.LeaderboardData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_PREV = "PrevPage";
    private static final String BUTTON_NEXT = "NextPage";

    private final RunOrFallStatsStore statsStore;
    private final PaginationState pagination = new PaginationState(50);
    private String searchText = "";

    public RunOrFallLeaderboardPage(@Nonnull PlayerRef playerRef, RunOrFallStatsStore statsStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, LeaderboardData.CODEC);
        this.statsStore = statsStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/RunOrFall_Leaderboard.ui");
        bindEvents(uiEventBuilder);
        buildLeaderboard(uiCommandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull LeaderboardData data) {
        super.handleDataEvent(ref, store, data);
        String previousSearch = searchText;
        if (data.search != null) {
            searchText = data.search.trim();
        }
        if (data.getButton() == null) {
            if (!previousSearch.equals(searchText)) {
                pagination.reset();
                sendRefresh();
            }
            return;
        }
        if (BUTTON_PREV.equals(data.getButton())) {
            pagination.previous();
            sendRefresh();
            return;
        }
        if (BUTTON_NEXT.equals(data.getButton())) {
            pagination.next();
            sendRefresh();
            return;
        }
        if (BUTTON_CLOSE.equals(data.getButton())) {
            this.close();
        }
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindEvents(eventBuilder);
        buildLeaderboard(commandBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#LeaderboardSearchField",
                EventData.of(LeaderboardData.KEY_SEARCH, "#LeaderboardSearchField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PREV), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NEXT), false);
    }

    private void buildLeaderboard(UICommandBuilder commandBuilder) {
        commandBuilder.clear("#LeaderboardCards");
        commandBuilder.set("#LeaderboardSearchField.Value", searchText);
        if (statsStore == null) {
            commandBuilder.set("#EmptyText.Text", "RunOrFall stats unavailable.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }

        List<RunOrFallPlayerStats> stats = statsStore.listStats();
        if (stats.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No RunOrFall stats yet.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }

        String filter = searchText != null ? searchText.trim().toLowerCase(Locale.ROOT) : "";
        List<LeaderboardRow> rows = stats.stream()
                .map(LeaderboardRow::new)
                .sorted(Comparator.comparingInt(LeaderboardRow::wins).reversed()
                        .thenComparing(Comparator.comparingDouble(LeaderboardRow::winRatePercent).reversed())
                        .thenComparingInt(LeaderboardRow::losses)
                        .thenComparing(row -> row.name.toLowerCase(Locale.ROOT)))
                .toList();

        List<LeaderboardRow> filtered = new java.util.ArrayList<>();
        int rank = 1;
        for (LeaderboardRow row : rows) {
            row.rank = rank++;
            if (!filter.isEmpty() && !row.name.toLowerCase(Locale.ROOT).startsWith(filter)) {
                continue;
            }
            filtered.add(row);
        }

        if (filtered.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No matches.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }

        commandBuilder.set("#EmptyText.Text", "");
        PaginationState.PageSlice slice = pagination.slice(filtered.size());
        int index = 0;
        for (int i = slice.startIndex; i < slice.endIndex; i++) {
            LeaderboardRow row = filtered.get(i);
            commandBuilder.append("#LeaderboardCards", "Pages/RunOrFall_LeaderboardEntry.ui");
            commandBuilder.set("#LeaderboardCards[" + index + "] #Rank.Text", String.valueOf(row.rank));
            commandBuilder.set("#LeaderboardCards[" + index + "] #PlayerName.Text", row.name);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Stats.Text",
                    row.wins + "W  " + row.losses + "L  " + String.format(Locale.US, "%.2f%%", row.winRatePercent));
            index++;
        }
        commandBuilder.set("#PageLabel.Text", slice.getLabel());
    }

    private static final class LeaderboardRow {
        private int rank;
        private final String name;
        private final int wins;
        private final int losses;
        private final double winRatePercent;

        private LeaderboardRow(RunOrFallPlayerStats stats) {
            String rawName = stats != null ? stats.getPlayerName() : null;
            this.name = (rawName == null || rawName.isBlank()) ? "Player" : rawName;
            this.wins = stats != null ? stats.getWins() : 0;
            this.losses = stats != null ? stats.getLosses() : 0;
            this.winRatePercent = stats != null ? stats.getWinRatePercent() : 0.0d;
        }

        private int wins() {
            return wins;
        }

        private int losses() {
            return losses;
        }

        private double winRatePercent() {
            return winRatePercent;
        }
    }

    public static class LeaderboardData extends ButtonEventData {
        static final String KEY_SEARCH = "@Search";

        public static final BuilderCodec<LeaderboardData> CODEC = BuilderCodec
                .<LeaderboardData>builder(LeaderboardData.class, LeaderboardData::new)
                .addField(new KeyedCodec<>(ButtonEventData.KEY_BUTTON, Codec.STRING),
                        (data, value) -> data.button = value, data -> data.button)
                .addField(new KeyedCodec<>(KEY_SEARCH, Codec.STRING),
                        (data, value) -> data.search = value, data -> data.search)
                .build();

        private String button;
        private String search;

        @Override
        public String getButton() {
            return button;
        }
    }
}
