package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.AscendPlayerStore.LeaderboardEntry;
import io.hyvexa.common.ui.AbstractSearchablePaginatedPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.ui.PaginationState;
import io.hyvexa.common.util.FormatUtils;

import javax.annotation.Nonnull;
import io.hyvexa.common.math.BigNumber;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AscendLeaderboardPage extends AbstractSearchablePaginatedPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TAB_VOLT = "TabVolt";
    private static final String BUTTON_TAB_ASCENSIONS = "TabAscensions";
    private static final String BUTTON_TAB_RUNS = "TabRuns";
    private static final String BUTTON_TAB_FASTEST = "TabFastest";

    private final AscendPlayerStore playerStore;
    private LeaderboardCategory currentCategory = LeaderboardCategory.VOLT;

    public AscendLeaderboardPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore) {
        super(playerRef, 50);
        this.playerStore = playerStore;
    }

    @Override
    protected String getPagePath() {
        return "Pages/Ascend_Leaderboard.ui";
    }

    @Override
    protected String getSearchFieldId() {
        return "#SearchField";
    }

    @Override
    protected void bindCustomEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabVolt",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_VOLT), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabAscensions",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_ASCENSIONS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabRuns",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_RUNS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabFastest",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_FASTEST), false);
    }

    @Override
    protected void buildContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        buildLeaderboard(commandBuilder);
    }

    @Override
    protected void handleCustomButton(String button, Ref<EntityStore> ref, Store<EntityStore> store) {
        switch (button) {
            case BUTTON_CLOSE -> this.close();
            case BUTTON_TAB_VOLT -> switchCategory(LeaderboardCategory.VOLT);
            case BUTTON_TAB_ASCENSIONS -> switchCategory(LeaderboardCategory.ASCENSIONS);
            case BUTTON_TAB_RUNS -> switchCategory(LeaderboardCategory.MANUAL_RUNS);
            case BUTTON_TAB_FASTEST -> switchCategory(LeaderboardCategory.FASTEST_ASCENSION);
        }
    }

    private void switchCategory(LeaderboardCategory category) {
        if (currentCategory == category) {
            return;
        }
        currentCategory = category;
        getPagination().reset();
        sendRefresh();
    }

    private void buildLeaderboard(UICommandBuilder commandBuilder) {
        commandBuilder.clear("#LeaderboardCards");
        commandBuilder.set("#SearchField.Value", getSearchText());

        updateTabStyles(commandBuilder);

        List<LeaderboardEntry> entries = playerStore.getLeaderboardEntries();
        if (entries.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No players yet.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }

        List<LeaderboardRow> sorted = getSortedEntries(entries);
        if (sorted.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No data available.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }

        String filter = getSearchText().toLowerCase();
        List<LeaderboardRow> filtered = new ArrayList<>();
        for (LeaderboardRow row : sorted) {
            if (!filter.isEmpty()) {
                String safeName = row.name != null ? row.name : "";
                if (!safeName.toLowerCase().startsWith(filter)) {
                    continue;
                }
            }
            filtered.add(row);
        }

        if (filtered.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No matches.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }

        commandBuilder.set("#EmptyText.Text", "");
        PaginationState.PageSlice slice = getPagination().slice(filtered.size());
        int start = slice.startIndex;
        int end = slice.endIndex;
        int index = 0;

        for (int i = start; i < end; i++) {
            LeaderboardRow row = filtered.get(i);
            commandBuilder.append("#LeaderboardCards", "Pages/Ascend_LeaderboardEntry.ui");
            String accentColor = AscendUIUtils.getRankAccentColor(row.rank);
            commandBuilder.set("#LeaderboardCards[" + index + "] #AccentBar.Background", accentColor);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Rank.Text", "#" + row.rank);
            commandBuilder.set("#LeaderboardCards[" + index + "] #PlayerName.Text", row.name);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Value.Text", row.formattedValue);
            index++;
        }

        commandBuilder.set("#PageLabel.Text", slice.getLabel());
    }

    private void updateTabStyles(UICommandBuilder commandBuilder) {
        setTabActive(commandBuilder, "TabVolt", currentCategory == LeaderboardCategory.VOLT);
        setTabActive(commandBuilder, "TabAscensions", currentCategory == LeaderboardCategory.ASCENSIONS);
        setTabActive(commandBuilder, "TabRuns", currentCategory == LeaderboardCategory.MANUAL_RUNS);
        setTabActive(commandBuilder, "TabFastest", currentCategory == LeaderboardCategory.FASTEST_ASCENSION);
    }

    private void setTabActive(UICommandBuilder commandBuilder, String tabId, boolean active) {
        String wrapPath = "#" + tabId + "Wrap";
        String accentPath = "#" + tabId + "Accent";
        commandBuilder.set(wrapPath + " #" + tabId + "ActiveBg.Visible", active);
        commandBuilder.set(wrapPath + " #" + tabId + "InactiveBg.Visible", !active);
        commandBuilder.set(wrapPath + " " + accentPath + " #" + tabId + "AccentActive.Visible", active);
        commandBuilder.set(wrapPath + " " + accentPath + " #" + tabId + "AccentInactive.Visible", !active);
    }

    private List<LeaderboardRow> getSortedEntries(List<LeaderboardEntry> entries) {
        List<LeaderboardRow> rows = new ArrayList<>();

        List<LeaderboardEntry> sorted = new ArrayList<>(entries);

        switch (currentCategory) {
            case VOLT -> {
                sorted.removeIf(e -> e.totalVoltEarnedExp10() == 0 && e.totalVoltEarnedMantissa() == 0);
                sorted.sort((a, b) -> {
                    int cmp = Integer.compare(b.totalVoltEarnedExp10(), a.totalVoltEarnedExp10());
                    if (cmp != 0) return cmp;
                    return Double.compare(b.totalVoltEarnedMantissa(), a.totalVoltEarnedMantissa());
                });
            }
            case ASCENSIONS -> {
                sorted.removeIf(e -> e.ascensionCount() == 0);
                sorted.sort((a, b) ->
                        Integer.compare(b.ascensionCount(), a.ascensionCount()));
            }
            case MANUAL_RUNS -> {
                sorted.removeIf(e -> e.totalManualRuns() == 0);
                sorted.sort((a, b) ->
                        Integer.compare(b.totalManualRuns(), a.totalManualRuns()));
            }
            case FASTEST_ASCENSION -> {
                sorted.removeIf(e -> e.fastestAscensionMs() == null);
                sorted.sort((a, b) ->
                        Long.compare(a.fastestAscensionMs(), b.fastestAscensionMs()));
            }
        }

        int rank = 1;
        for (LeaderboardEntry entry : sorted) {
            String name = resolveName(entry);
            String formattedValue = formatValue(entry);

            rows.add(new LeaderboardRow(rank, entry.playerId(), name, formattedValue));
            rank++;
        }

        return rows;
    }

    private String resolveName(LeaderboardEntry entry) {
        if (entry.playerName() != null && !entry.playerName().isEmpty()) {
            return entry.playerName();
        }
        // Try in-memory name cache (survives disconnect within same session)
        String cachedName = playerStore.getPlayerName(entry.playerId());
        if (cachedName != null) {
            return cachedName;
        }
        PlayerRef playerRef = Universe.get().getPlayer(entry.playerId());
        if (playerRef != null) {
            return playerRef.getUsername();
        }
        return entry.playerId().toString().substring(0, 8) + "...";
    }

    private String formatValue(LeaderboardEntry entry) {
        return switch (currentCategory) {
            case VOLT -> FormatUtils.formatBigNumber(BigNumber.of(entry.totalVoltEarnedMantissa(), entry.totalVoltEarnedExp10()));
            case ASCENSIONS -> String.valueOf(entry.ascensionCount());
            case MANUAL_RUNS -> String.valueOf(entry.totalManualRuns());
            case FASTEST_ASCENSION -> entry.fastestAscensionMs() != null
                    ? FormatUtils.formatDurationLong(entry.fastestAscensionMs())
                    : "-";
        };
    }

    public enum LeaderboardCategory {
        VOLT,
        ASCENSIONS,
        MANUAL_RUNS,
        FASTEST_ASCENSION
    }

    private record LeaderboardRow(int rank, UUID playerId, String name, String formattedValue) {
    }
}
