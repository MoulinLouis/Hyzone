package io.hyvexa.runorfall.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.ui.AbstractLeaderboardPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.runorfall.data.RunOrFallPlayerStats;
import io.hyvexa.runorfall.manager.RunOrFallStatsStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class RunOrFallLeaderboardPage extends AbstractLeaderboardPage {

    private static final String COLOR_STATS_WINS = "#34d399";
    private static final String COLOR_STATS_LOSSES = "#f87171";
    private static final String COLOR_STATS_WINRATE = "#60a5fa";
    private static final String COLOR_STATS_BEST_STREAK = "#fbbf24";
    private static final String COLOR_STATS_LONGEST_TIME = "#c084fc";
    private static final String COLOR_STATS_DEFAULT = "#9fb0ba";

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_CATEGORY_PREFIX = "Category:";
    private static final String BUTTON_CATEGORY_TOTAL_WINS = BUTTON_CATEGORY_PREFIX + "TotalWins";
    private static final String BUTTON_CATEGORY_BEST_STREAK = BUTTON_CATEGORY_PREFIX + "BestStreak";
    private static final String BUTTON_CATEGORY_LONGEST_SURVIVED = BUTTON_CATEGORY_PREFIX + "LongestSurvived";

    private final RunOrFallStatsStore statsStore;
    private LeaderboardCategory selectedCategory = LeaderboardCategory.TOTAL_WINS;
    private StatsData[] statsDataByRank;

    public RunOrFallLeaderboardPage(@Nonnull PlayerRef playerRef, RunOrFallStatsStore statsStore) {
        super(playerRef, 50);
        this.statsStore = statsStore;
    }

    @Override
    protected String getPagePath() {
        return "Pages/RunOrFall_Leaderboard.ui";
    }

    @Override
    protected String getSearchFieldId() {
        return "#SearchField";
    }

    @Override
    protected String getCardTemplatePath() {
        return "Pages/RunOrFall_LeaderboardEntry.ui";
    }

    @Override
    protected String getNoDataMessage() {
        return statsStore == null ? "RunOrFall stats unavailable." : "No RunOrFall stats yet.";
    }

    @Override
    protected void bindCustomEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabWins",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CATEGORY_TOTAL_WINS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabStreak",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CATEGORY_BEST_STREAK), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabLongest",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CATEGORY_LONGEST_SURVIVED), false);
    }

    @Override
    protected void handleCustomButton(String button, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (BUTTON_CLOSE.equals(button)) {
            this.close();
            return;
        }
        LeaderboardCategory nextCategory = LeaderboardCategory.fromButton(button);
        if (nextCategory != null && selectedCategory != nextCategory) {
            selectedCategory = nextCategory;
            getPagination().reset();
            sendRefresh();
        }
    }

    @Override
    protected void onBuildLeaderboard(UICommandBuilder cmd) {
        applyCategoryUi(cmd);
    }

    @Override
    protected List<LeaderboardRow> loadRows() {
        if (statsStore == null) {
            return null;
        }

        List<RunOrFallPlayerStats> stats = statsStore.listStats();
        if (stats.isEmpty()) {
            return null;
        }

        List<StatsData> sortable = new ArrayList<>();
        for (RunOrFallPlayerStats s : stats) {
            String rawName = s.getPlayerName();
            String name = (rawName == null || rawName.isBlank()) ? "Player" : rawName;
            sortable.add(new StatsData(name, s.getWins(), s.getLosses(), s.getWinRatePercent(),
                    s.getBestWinStreak(), s.getLongestSurvivedMs()));
        }

        sortable.sort(selectedCategory.comparator());

        statsDataByRank = new StatsData[sortable.size() + 1]; // 1-indexed
        List<LeaderboardRow> rows = new ArrayList<>();
        int rank = 1;
        for (StatsData data : sortable) {
            statsDataByRank[rank] = data;
            rows.add(new LeaderboardRow(rank, null, data.name, ""));
            rank++;
        }
        return rows;
    }

    @Override
    protected void renderRow(UICommandBuilder cmd, String cardPrefix, LeaderboardRow row) {
        cmd.set(cardPrefix + " #Rank.Text", "#" + row.rank());
        cmd.set(cardPrefix + " #PlayerName.Text", row.name());
        StatsData data = (statsDataByRank != null && row.rank() > 0 && row.rank() < statsDataByRank.length)
                ? statsDataByRank[row.rank()] : null;
        if (data == null) return;

        boolean totalWinsCategory = selectedCategory == LeaderboardCategory.TOTAL_WINS;
        cmd.set(cardPrefix + " #StatsGeneric.Visible", !totalWinsCategory);
        cmd.set(cardPrefix + " #StatsTotalWins.Visible", totalWinsCategory);
        if (totalWinsCategory) {
            cmd.set(cardPrefix + " #StatsWins.Text", data.wins + "W");
            cmd.set(cardPrefix + " #StatsLosses.Text", data.losses + "L");
            cmd.set(cardPrefix + " #StatsWinrate.Text",
                    String.format(Locale.US, "%.2f%%", data.winRatePercent));
            cmd.set(cardPrefix + " #StatsWins.Style.TextColor", COLOR_STATS_WINS);
            cmd.set(cardPrefix + " #StatsLosses.Style.TextColor", COLOR_STATS_LOSSES);
            cmd.set(cardPrefix + " #StatsWinrate.Style.TextColor", COLOR_STATS_WINRATE);
        } else {
            cmd.set(cardPrefix + " #StatsGeneric.Text", selectedCategory.formatStats(data));
            cmd.set(cardPrefix + " #StatsGeneric.Style.TextColor", selectedCategory.genericTextColor());
        }
    }

    private void applyCategoryUi(UICommandBuilder cmd) {
        boolean isWins = selectedCategory == LeaderboardCategory.TOTAL_WINS;
        boolean isStreak = selectedCategory == LeaderboardCategory.BEST_WIN_STREAK;
        boolean isTime = selectedCategory == LeaderboardCategory.LONGEST_TIME_SURVIVED;

        cmd.set("#TabWinsActiveBg.Visible", isWins);
        cmd.set("#TabWinsInactiveBg.Visible", !isWins);
        cmd.set("#TabWinsAccentActive.Visible", isWins);
        cmd.set("#TabWinsAccentInactive.Visible", !isWins);
        cmd.set("#TabWinsLabel.Style.TextColor", isWins ? "#f0f4f8" : "#9fb0ba");

        cmd.set("#TabStreakActiveBg.Visible", isStreak);
        cmd.set("#TabStreakInactiveBg.Visible", !isStreak);
        cmd.set("#TabStreakAccentActive.Visible", isStreak);
        cmd.set("#TabStreakAccentInactive.Visible", !isStreak);
        cmd.set("#TabStreakLabel.Style.TextColor", isStreak ? "#f0f4f8" : "#9fb0ba");

        cmd.set("#TabLongestActiveBg.Visible", isTime);
        cmd.set("#TabLongestInactiveBg.Visible", !isTime);
        cmd.set("#TabLongestAccentActive.Visible", isTime);
        cmd.set("#TabLongestAccentInactive.Visible", !isTime);
        cmd.set("#TabLongestLabel.Style.TextColor", isTime ? "#f0f4f8" : "#9fb0ba");
    }

    // --- Stats data for side-array rendering ---

    private record StatsData(String name, int wins, int losses, double winRatePercent,
                             int bestWinStreak, long longestSurvivedMs) {
        String nameLower() {
            return name.toLowerCase(Locale.ROOT);
        }
    }

    private enum LeaderboardCategory {
        TOTAL_WINS(BUTTON_CATEGORY_TOTAL_WINS) {
            @Override
            Comparator<StatsData> comparator() {
                return Comparator.<StatsData>comparingInt(d -> d.wins).reversed()
                        .thenComparing(Comparator.<StatsData>comparingDouble(d -> d.winRatePercent).reversed())
                        .thenComparingInt(d -> d.losses)
                        .thenComparing(StatsData::nameLower);
            }

            @Override
            String formatStats(StatsData data) {
                return data.wins + "W  " + data.losses + "L  "
                        + String.format(Locale.US, "%.2f%%", data.winRatePercent);
            }

            @Override
            String genericTextColor() {
                return COLOR_STATS_DEFAULT;
            }
        },
        BEST_WIN_STREAK(BUTTON_CATEGORY_BEST_STREAK) {
            @Override
            Comparator<StatsData> comparator() {
                return Comparator.<StatsData>comparingInt(d -> d.bestWinStreak).reversed()
                        .thenComparing(Comparator.<StatsData>comparingInt(d -> d.wins).reversed())
                        .thenComparing(Comparator.<StatsData>comparingDouble(d -> d.winRatePercent).reversed())
                        .thenComparingInt(d -> d.losses)
                        .thenComparing(StatsData::nameLower);
            }

            @Override
            String formatStats(StatsData data) {
                return String.valueOf(data.bestWinStreak);
            }

            @Override
            String genericTextColor() {
                return COLOR_STATS_BEST_STREAK;
            }
        },
        LONGEST_TIME_SURVIVED(BUTTON_CATEGORY_LONGEST_SURVIVED) {
            @Override
            Comparator<StatsData> comparator() {
                return Comparator.<StatsData>comparingLong(d -> d.longestSurvivedMs).reversed()
                        .thenComparing(Comparator.<StatsData>comparingInt(d -> d.wins).reversed())
                        .thenComparing(Comparator.<StatsData>comparingInt(d -> d.bestWinStreak).reversed())
                        .thenComparing(StatsData::nameLower);
            }

            @Override
            String formatStats(StatsData data) {
                return FormatUtils.formatDurationPadded(data.longestSurvivedMs);
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

        abstract Comparator<StatsData> comparator();

        abstract String formatStats(StatsData data);

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
}
