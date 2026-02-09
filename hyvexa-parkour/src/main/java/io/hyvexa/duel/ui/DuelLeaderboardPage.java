package io.hyvexa.duel.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.HyvexaPlugin;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.duel.data.DuelStats;
import io.hyvexa.duel.data.DuelStatsStore;
import io.hyvexa.common.ui.PaginationState;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;

public class DuelLeaderboardPage extends InteractiveCustomUIPage<DuelLeaderboardPage.LeaderboardData> {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_PREV = "PrevPage";
    private static final String BUTTON_NEXT = "NextPage";

    private final PaginationState pagination = new PaginationState(50);
    private String searchText = "";

    public DuelLeaderboardPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, LeaderboardData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Duel_Leaderboard.ui");
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
        if (BUTTON_BACK.equals(data.getButton())) {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && playerRef != null) {
                player.getPageManager().openCustomPage(ref, store, new DuelMenuPage(playerRef));
            }
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
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
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
        HyvexaPlugin plugin = HyvexaPlugin.getInstance();
        DuelStatsStore statsStore = plugin != null && plugin.getDuelTracker() != null
                ? plugin.getDuelTracker().getStatsStore()
                : null;
        if (statsStore == null) {
            commandBuilder.set("#EmptyText.Text", "Duel stats unavailable.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }
        List<DuelStats> stats = statsStore.listStats();
        if (stats.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No duel stats yet.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }
        String filter = searchText != null ? searchText.trim().toLowerCase() : "";
        List<LeaderboardRow> rows = stats.stream()
                .sorted(Comparator.comparingInt(DuelStats::getWins).reversed()
                        .thenComparingInt(DuelStats::getLosses)
                        .thenComparing(statsRow -> {
                            String name = statsRow.getPlayerName();
                            return name != null ? name.toLowerCase() : "";
                        }))
                .map(LeaderboardRow::new)
                .toList();
        List<LeaderboardRow> filtered = new java.util.ArrayList<>();
        int rank = 1;
        for (LeaderboardRow row : rows) {
            row.rank = rank++;
            if (!filter.isEmpty()) {
                if (!row.name.toLowerCase().startsWith(filter)) {
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
            commandBuilder.append("#LeaderboardCards", "Pages/Parkour_LeaderboardEntry.ui");
            commandBuilder.set("#LeaderboardCards[" + index + "] #Rank.Text", String.valueOf(row.rank));
            commandBuilder.set("#LeaderboardCards[" + index + "] #PlayerName.Text", row.name);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Completion.Text",
                    row.wins + "W/" + row.losses + "L");
            index++;
        }
        commandBuilder.set("#PageLabel.Text", slice.getLabel());
    }

    private static final class LeaderboardRow {
        private int rank;
        private final String name;
        private final int wins;
        private final int losses;

        private LeaderboardRow(DuelStats stats) {
            String rawName = stats.getPlayerName();
            this.name = rawName != null ? rawName : "Player";
            this.wins = stats.getWins();
            this.losses = stats.getLosses();
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
