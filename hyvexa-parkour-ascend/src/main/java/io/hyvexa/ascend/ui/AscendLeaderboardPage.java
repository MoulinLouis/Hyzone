package io.hyvexa.ascend.ui;

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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.data.AscendPlayerStore.LeaderboardEntry;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.ui.PaginationState;
import io.hyvexa.common.util.FormatUtils;

import javax.annotation.Nonnull;
import io.hyvexa.common.math.BigNumber;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AscendLeaderboardPage extends InteractiveCustomUIPage<AscendLeaderboardPage.LeaderboardData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_PREV = "PrevPage";
    private static final String BUTTON_NEXT = "NextPage";
    private static final String BUTTON_TAB_VEXA = "TabVexa";
    private static final String BUTTON_TAB_ASCENSIONS = "TabAscensions";
    private static final String BUTTON_TAB_RUNS = "TabRuns";
    private static final String BUTTON_TAB_FASTEST = "TabFastest";

    private static final String COLOR_RANK_1 = "#ffd700";
    private static final String COLOR_RANK_2 = "#c0c0c0";
    private static final String COLOR_RANK_3 = "#cd7f32";
    private static final String COLOR_RANK_DEFAULT = "#9fb0ba";

    private final AscendPlayerStore playerStore;
    private final PaginationState pagination = new PaginationState(50);
    private LeaderboardCategory currentCategory = LeaderboardCategory.VEXA;
    private String searchText = "";

    public AscendLeaderboardPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, LeaderboardData.CODEC);
        this.playerStore = playerStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Leaderboard.ui");
        bindEvents(eventBuilder);
        buildLeaderboard(commandBuilder);
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

        switch (data.getButton()) {
            case BUTTON_CLOSE -> this.close();
            case BUTTON_PREV -> {
                pagination.previous();
                sendRefresh();
            }
            case BUTTON_NEXT -> {
                pagination.next();
                sendRefresh();
            }
            case BUTTON_TAB_VEXA -> switchCategory(LeaderboardCategory.VEXA);
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
        pagination.reset();
        sendRefresh();
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindEvents(eventBuilder);
        buildLeaderboard(commandBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchField",
                EventData.of(LeaderboardData.KEY_SEARCH, "#SearchField.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PREV), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NEXT), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabVexa",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_VEXA), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabAscensions",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_ASCENSIONS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabRuns",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_RUNS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabFastest",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_FASTEST), false);
    }

    private void buildLeaderboard(UICommandBuilder commandBuilder) {
        commandBuilder.clear("#LeaderboardCards");
        commandBuilder.set("#SearchField.Value", searchText);

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

        String filter = searchText.toLowerCase();
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
        PaginationState.PageSlice slice = pagination.slice(filtered.size());
        int start = slice.startIndex;
        int end = slice.endIndex;
        int index = 0;

        for (int i = start; i < end; i++) {
            LeaderboardRow row = filtered.get(i);
            commandBuilder.append("#LeaderboardCards", "Pages/Ascend_LeaderboardEntry.ui");
            String accentColor = getRankAccentColor(row.rank);
            commandBuilder.set("#LeaderboardCards[" + index + "] #AccentBar.Background", accentColor);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Rank.Text", "#" + row.rank);
            commandBuilder.set("#LeaderboardCards[" + index + "] #PlayerName.Text", row.name);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Value.Text", row.formattedValue);
            index++;
        }

        commandBuilder.set("#PageLabel.Text", slice.getLabel());
    }

    private void updateTabStyles(UICommandBuilder commandBuilder) {
        setTabActive(commandBuilder, "TabVexa", currentCategory == LeaderboardCategory.VEXA);
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
            case VEXA -> {
                sorted.removeIf(e -> e.totalVexaEarnedExp10() == 0 && e.totalVexaEarnedMantissa() == 0);
                sorted.sort((a, b) -> {
                    int cmp = Integer.compare(b.totalVexaEarnedExp10(), a.totalVexaEarnedExp10());
                    if (cmp != 0) return cmp;
                    return Double.compare(b.totalVexaEarnedMantissa(), a.totalVexaEarnedMantissa());
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
            case VEXA -> formatVexa(BigNumber.of(entry.totalVexaEarnedMantissa(), entry.totalVexaEarnedExp10()));
            case ASCENSIONS -> String.valueOf(entry.ascensionCount());
            case MANUAL_RUNS -> String.valueOf(entry.totalManualRuns());
            case FASTEST_ASCENSION -> entry.fastestAscensionMs() != null
                    ? FormatUtils.formatDurationLong(entry.fastestAscensionMs())
                    : "-";
        };
    }

    private String formatVexa(BigNumber vexa) {
        return FormatUtils.formatBigNumber(vexa);
    }

    private String getRankAccentColor(int rank) {
        return switch (rank) {
            case 1 -> COLOR_RANK_1;
            case 2 -> COLOR_RANK_2;
            case 3 -> COLOR_RANK_3;
            default -> COLOR_RANK_DEFAULT;
        };
    }

    public enum LeaderboardCategory {
        VEXA("Vexa", "#f59e0b"),
        ASCENSIONS("Ascensions", "#8b5cf6"),
        MANUAL_RUNS("Manual Runs", "#10b981"),
        FASTEST_ASCENSION("Fastest Ascension", "#ef4444");

        private final String label;
        private final String color;

        LeaderboardCategory(String label, String color) {
            this.label = label;
            this.color = color;
        }

        public String getLabel() {
            return label;
        }

        public String getColor() {
            return color;
        }
    }

    private record LeaderboardRow(int rank, UUID playerId, String name, String formattedValue) {
    }

    public static class LeaderboardData extends ButtonEventData {
        static final String KEY_SEARCH = "@Search";

        public static final BuilderCodec<LeaderboardData> CODEC = BuilderCodec.<LeaderboardData>builder(
                        LeaderboardData.class, LeaderboardData::new)
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
