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
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.ui.ButtonEventData;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AscendLeaderboardPage extends InteractiveCustomUIPage<AscendLeaderboardPage.LeaderboardData> {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_PREV = "PrevPage";
    private static final String BUTTON_NEXT = "NextPage";
    private static final String BUTTON_TAB_COINS = "TabCoins";
    private static final String BUTTON_TAB_ASCENSIONS = "TabAscensions";
    private static final String BUTTON_TAB_RUNS = "TabRuns";

    private static final String COLOR_RANK_1 = "#ffd700";
    private static final String COLOR_RANK_2 = "#c0c0c0";
    private static final String COLOR_RANK_3 = "#cd7f32";
    private static final String COLOR_RANK_DEFAULT = "#9fb0ba";

    private static final String COLOR_TAB_ACTIVE = "#2d3f50";
    private static final String COLOR_TAB_INACTIVE = "#1a2530(0.6)";
    private static final String COLOR_TAB_COINS = "#f59e0b";
    private static final String COLOR_TAB_ASCENSIONS = "#8b5cf6";
    private static final String COLOR_TAB_RUNS = "#10b981";

    private final AscendPlayerStore playerStore;
    private final PaginationState pagination = new PaginationState(50);
    private LeaderboardCategory currentCategory = LeaderboardCategory.COINS;
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
            case BUTTON_TAB_COINS -> switchCategory(LeaderboardCategory.COINS);
            case BUTTON_TAB_ASCENSIONS -> switchCategory(LeaderboardCategory.ASCENSIONS);
            case BUTTON_TAB_RUNS -> switchCategory(LeaderboardCategory.MANUAL_RUNS);
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
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabCoins",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_COINS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabAscensions",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_ASCENSIONS), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabRuns",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TAB_RUNS), false);
    }

    private void buildLeaderboard(UICommandBuilder commandBuilder) {
        commandBuilder.clear("#LeaderboardCards");
        commandBuilder.set("#SearchField.Value", searchText);

        updateTabStyles(commandBuilder);

        Map<UUID, AscendPlayerProgress> playersSnapshot = playerStore.getPlayersSnapshot();
        if (playersSnapshot.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No players yet.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }

        List<LeaderboardRow> sorted = getSortedEntries(playersSnapshot);
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
        commandBuilder.set("#TabCoinsWrap.Background",
                currentCategory == LeaderboardCategory.COINS ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
        commandBuilder.set("#TabAscensionsWrap.Background",
                currentCategory == LeaderboardCategory.ASCENSIONS ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
        commandBuilder.set("#TabRunsWrap.Background",
                currentCategory == LeaderboardCategory.MANUAL_RUNS ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);

        commandBuilder.set("#TabCoinsAccent.Background",
                currentCategory == LeaderboardCategory.COINS ? COLOR_TAB_COINS : COLOR_TAB_INACTIVE);
        commandBuilder.set("#TabAscensionsAccent.Background",
                currentCategory == LeaderboardCategory.ASCENSIONS ? COLOR_TAB_ASCENSIONS : COLOR_TAB_INACTIVE);
        commandBuilder.set("#TabRunsAccent.Background",
                currentCategory == LeaderboardCategory.MANUAL_RUNS ? COLOR_TAB_RUNS : COLOR_TAB_INACTIVE);
    }

    private List<LeaderboardRow> getSortedEntries(Map<UUID, AscendPlayerProgress> players) {
        List<LeaderboardRow> rows = new ArrayList<>();

        List<Map.Entry<UUID, AscendPlayerProgress>> entries = new ArrayList<>(players.entrySet());

        switch (currentCategory) {
            case COINS -> entries.sort((a, b) ->
                    b.getValue().getTotalCoinsEarned().compareTo(a.getValue().getTotalCoinsEarned()));
            case ASCENSIONS -> entries.sort((a, b) ->
                    Integer.compare(b.getValue().getAscensionCount(), a.getValue().getAscensionCount()));
            case MANUAL_RUNS -> entries.sort((a, b) ->
                    Integer.compare(b.getValue().getTotalManualRuns(), a.getValue().getTotalManualRuns()));
        }

        int rank = 1;
        for (Map.Entry<UUID, AscendPlayerProgress> entry : entries) {
            UUID playerId = entry.getKey();
            AscendPlayerProgress progress = entry.getValue();

            String name = resolveName(playerId);
            String formattedValue = formatValue(progress);

            rows.add(new LeaderboardRow(rank, playerId, name, formattedValue));
            rank++;
        }

        return rows;
    }

    private String resolveName(UUID uuid) {
        PlayerRef playerRef = Universe.get().getPlayer(uuid);
        if (playerRef != null) {
            return playerRef.getUsername();
        }
        return uuid.toString().substring(0, 8) + "...";
    }

    private String formatValue(AscendPlayerProgress progress) {
        return switch (currentCategory) {
            case COINS -> formatCoins(progress.getTotalCoinsEarned());
            case ASCENSIONS -> String.valueOf(progress.getAscensionCount());
            case MANUAL_RUNS -> String.valueOf(progress.getTotalManualRuns());
        };
    }

    private String formatCoins(BigDecimal coins) {
        if (coins == null) {
            return "0";
        }
        double value = coins.doubleValue();
        if (value >= 1_000_000_000_000L) {
            return String.format("%.1fT", value / 1_000_000_000_000.0);
        } else if (value >= 1_000_000_000L) {
            return String.format("%.1fB", value / 1_000_000_000.0);
        } else if (value >= 1_000_000L) {
            return String.format("%.1fM", value / 1_000_000.0);
        } else if (value >= 1_000L) {
            return String.format("%.1fK", value / 1_000.0);
        } else {
            return String.format("%.0f", value);
        }
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
        COINS("Coins", "#f59e0b"),
        ASCENSIONS("Ascensions", "#8b5cf6"),
        MANUAL_RUNS("Manual Runs", "#10b981");

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
