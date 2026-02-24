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
import io.hyvexa.runorfall.util.RunOrFallUtils;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class RunOrFallLeaderboardPage extends InteractiveCustomUIPage<RunOrFallLeaderboardPage.LeaderboardData> {

    private static final String COLOR_STATS_WINS = "#34d399";
    private static final String COLOR_STATS_LOSSES = "#f87171";
    private static final String COLOR_STATS_WINRATE = "#60a5fa";
    private static final String COLOR_STATS_BEST_STREAK = "#fbbf24";
    private static final String COLOR_STATS_LONGEST_TIME = "#c084fc";
    private static final String COLOR_STATS_DEFAULT = "#9fb0ba";

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_PREV = "PrevPage";
    private static final String BUTTON_NEXT = "NextPage";
    private static final String BUTTON_CATEGORY_PREFIX = "Category:";
    private static final String BUTTON_CATEGORY_TOTAL_WINS = BUTTON_CATEGORY_PREFIX + "TotalWins";
    private static final String BUTTON_CATEGORY_BEST_STREAK = BUTTON_CATEGORY_PREFIX + "BestStreak";
    private static final String BUTTON_CATEGORY_LONGEST_SURVIVED = BUTTON_CATEGORY_PREFIX + "LongestSurvived";

    private final RunOrFallStatsStore statsStore;
    private final PaginationState pagination = new PaginationState(50);
    private String searchText = "";
    private LeaderboardCategory selectedCategory = LeaderboardCategory.TOTAL_WINS;

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
        LeaderboardCategory nextCategory = LeaderboardCategory.fromButton(data.getButton());
        if (nextCategory != null) {
            if (selectedCategory != nextCategory) {
                selectedCategory = nextCategory;
                pagination.reset();
                sendRefresh();
            }
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
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchField",
                EventData.of(LeaderboardData.KEY_SEARCH, "#SearchField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PREV), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NEXT), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabWins",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CATEGORY_TOTAL_WINS), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabStreak",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CATEGORY_BEST_STREAK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabLongest",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CATEGORY_LONGEST_SURVIVED), false);
    }

    private void buildLeaderboard(UICommandBuilder commandBuilder) {
        commandBuilder.clear("#LeaderboardCards");
        commandBuilder.set("#SearchField.Value", searchText);
        applyCategoryUi(commandBuilder);
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
                .sorted(selectedCategory.comparator())
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
            String prefix = "#LeaderboardCards[" + index + "]";
            commandBuilder.set(prefix + " #Rank.Text", "#" + row.rank);
            commandBuilder.set(prefix + " #PlayerName.Text", row.name);
            boolean totalWinsCategory = selectedCategory == LeaderboardCategory.TOTAL_WINS;
            commandBuilder.set(prefix + " #StatsGeneric.Visible", !totalWinsCategory);
            commandBuilder.set(prefix + " #StatsTotalWins.Visible", totalWinsCategory);
            if (totalWinsCategory) {
                commandBuilder.set(prefix + " #StatsWins.Text", row.wins() + "W");
                commandBuilder.set(prefix + " #StatsLosses.Text", row.losses() + "L");
                commandBuilder.set(prefix + " #StatsWinrate.Text",
                        String.format(Locale.US, "%.2f%%", row.winRatePercent()));
                commandBuilder.set(prefix + " #StatsWins.Style.TextColor", COLOR_STATS_WINS);
                commandBuilder.set(prefix + " #StatsLosses.Style.TextColor", COLOR_STATS_LOSSES);
                commandBuilder.set(prefix + " #StatsWinrate.Style.TextColor", COLOR_STATS_WINRATE);
            } else {
                commandBuilder.set(prefix + " #StatsGeneric.Text", selectedCategory.formatStats(row));
                commandBuilder.set(prefix + " #StatsGeneric.Style.TextColor", selectedCategory.genericTextColor());
            }
            index++;
        }
        commandBuilder.set("#PageLabel.Text", slice.getLabel());
    }

    private void applyCategoryUi(UICommandBuilder commandBuilder) {
        boolean isWins = selectedCategory == LeaderboardCategory.TOTAL_WINS;
        boolean isStreak = selectedCategory == LeaderboardCategory.BEST_WIN_STREAK;
        boolean isTime = selectedCategory == LeaderboardCategory.LONGEST_TIME_SURVIVED;

        // Total Wins tab
        commandBuilder.set("#TabWinsActiveBg.Visible", isWins);
        commandBuilder.set("#TabWinsInactiveBg.Visible", !isWins);
        commandBuilder.set("#TabWinsAccentActive.Visible", isWins);
        commandBuilder.set("#TabWinsAccentInactive.Visible", !isWins);
        commandBuilder.set("#TabWinsLabel.Style.TextColor", isWins ? "#f0f4f8" : "#9fb0ba");

        // Best Streak tab
        commandBuilder.set("#TabStreakActiveBg.Visible", isStreak);
        commandBuilder.set("#TabStreakInactiveBg.Visible", !isStreak);
        commandBuilder.set("#TabStreakAccentActive.Visible", isStreak);
        commandBuilder.set("#TabStreakAccentInactive.Visible", !isStreak);
        commandBuilder.set("#TabStreakLabel.Style.TextColor", isStreak ? "#f0f4f8" : "#9fb0ba");

        // Longest Survived tab
        commandBuilder.set("#TabLongestActiveBg.Visible", isTime);
        commandBuilder.set("#TabLongestInactiveBg.Visible", !isTime);
        commandBuilder.set("#TabLongestAccentActive.Visible", isTime);
        commandBuilder.set("#TabLongestAccentInactive.Visible", !isTime);
        commandBuilder.set("#TabLongestLabel.Style.TextColor", isTime ? "#f0f4f8" : "#9fb0ba");

    }

    private enum LeaderboardCategory {
        TOTAL_WINS(BUTTON_CATEGORY_TOTAL_WINS) {
            @Override
            Comparator<LeaderboardRow> comparator() {
                return Comparator.comparingInt(LeaderboardRow::wins).reversed()
                        .thenComparing(Comparator.comparingDouble(LeaderboardRow::winRatePercent).reversed())
                        .thenComparingInt(LeaderboardRow::losses)
                        .thenComparing(LeaderboardRow::nameLower);
            }

            @Override
            String formatStats(LeaderboardRow row) {
                return row.wins() + "W  " + row.losses() + "L  "
                        + String.format(Locale.US, "%.2f%%", row.winRatePercent());
            }

            @Override
            String genericTextColor() {
                return COLOR_STATS_DEFAULT;
            }
        },
        BEST_WIN_STREAK(BUTTON_CATEGORY_BEST_STREAK) {
            @Override
            Comparator<LeaderboardRow> comparator() {
                return Comparator.comparingInt(LeaderboardRow::bestWinStreak).reversed()
                        .thenComparing(Comparator.comparingInt(LeaderboardRow::wins).reversed())
                        .thenComparing(Comparator.comparingDouble(LeaderboardRow::winRatePercent).reversed())
                        .thenComparingInt(LeaderboardRow::losses)
                        .thenComparing(LeaderboardRow::nameLower);
            }

            @Override
            String formatStats(LeaderboardRow row) {
                return String.valueOf(row.bestWinStreak());
            }

            @Override
            String genericTextColor() {
                return COLOR_STATS_BEST_STREAK;
            }
        },
        LONGEST_TIME_SURVIVED(BUTTON_CATEGORY_LONGEST_SURVIVED) {
            @Override
            Comparator<LeaderboardRow> comparator() {
                return Comparator.comparingLong(LeaderboardRow::longestSurvivedMs).reversed()
                        .thenComparing(Comparator.comparingInt(LeaderboardRow::wins).reversed())
                        .thenComparing(Comparator.comparingInt(LeaderboardRow::bestWinStreak).reversed())
                        .thenComparing(LeaderboardRow::nameLower);
            }

            @Override
            String formatStats(LeaderboardRow row) {
                return RunOrFallUtils.formatDuration(row.longestSurvivedMs());
            }

            @Override
            String genericTextColor() {
                return COLOR_STATS_LONGEST_TIME;
            }
        };

        private final String buttonId;

        LeaderboardCategory(String buttonId) {
            this.buttonId = buttonId;
        }

        abstract Comparator<LeaderboardRow> comparator();

        abstract String formatStats(LeaderboardRow row);

        abstract String genericTextColor();

        static LeaderboardCategory fromButton(String button) {
            for (LeaderboardCategory category : values()) {
                if (category.buttonId.equals(button)) {
                    return category;
                }
            }
            return null;
        }
    }

    private static final class LeaderboardRow {
        private int rank;
        private final String name;
        private final int wins;
        private final int losses;
        private final double winRatePercent;
        private final int bestWinStreak;
        private final long longestSurvivedMs;

        private LeaderboardRow(RunOrFallPlayerStats stats) {
            String rawName = stats != null ? stats.getPlayerName() : null;
            this.name = (rawName == null || rawName.isBlank()) ? "Player" : rawName;
            this.wins = stats != null ? stats.getWins() : 0;
            this.losses = stats != null ? stats.getLosses() : 0;
            this.winRatePercent = stats != null ? stats.getWinRatePercent() : 0.0d;
            this.bestWinStreak = stats != null ? stats.getBestWinStreak() : 0;
            this.longestSurvivedMs = stats != null ? stats.getLongestSurvivedMs() : 0L;
        }

        private String nameLower() {
            return name.toLowerCase(Locale.ROOT);
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

        private int bestWinStreak() {
            return bestWinStreak;
        }

        private long longestSurvivedMs() {
            return longestSurvivedMs;
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
