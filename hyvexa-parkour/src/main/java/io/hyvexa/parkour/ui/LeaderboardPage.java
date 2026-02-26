package io.hyvexa.parkour.ui;

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
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.ui.PaginationState;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.MedalStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.util.ParkourUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public class LeaderboardPage extends InteractiveCustomUIPage<LeaderboardPage.LeaderboardData> {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final RunTracker runTracker;
    private final PaginationState pagination = new PaginationState(50);
    private String searchText = "";
    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_PREV = "PrevPage";
    private static final String BUTTON_NEXT = "NextPage";

    public LeaderboardPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                  ProgressStore progressStore, RunTracker runTracker) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, LeaderboardData.CODEC);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.runTracker = runTracker;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_Leaderboard.ui");
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
                player.getPageManager().openCustomPage(ref, store,
                        new LeaderboardMenuPage(playerRef, mapStore, progressStore, runTracker));
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
        List<MedalStore.MedalScoreEntry> snapshot = MedalStore.getInstance().getLeaderboardSnapshot();
        if (snapshot.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No medals earned yet.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }
        String filter = searchText != null ? searchText.trim().toLowerCase() : "";
        List<LeaderboardRow> filtered = new java.util.ArrayList<>();
        for (int i = 0; i < snapshot.size(); i++) {
            MedalStore.MedalScoreEntry entry = snapshot.get(i);
            String name = ParkourUtils.resolveName(entry.getPlayerId(), progressStore);
            if (!filter.isEmpty()) {
                String safeName = name != null ? name : "";
                if (!safeName.toLowerCase().startsWith(filter)) {
                    continue;
                }
            }
            filtered.add(new LeaderboardRow(i + 1, entry, name));
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
            String accentColor = UIColorUtils.getRankAccentColor(row.rank);
            commandBuilder.set("#LeaderboardCards[" + index + "] #AccentBar.Background", accentColor);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Rank.Text", "#" + row.rank);
            commandBuilder.set("#LeaderboardCards[" + index + "] #PlayerName.Text", row.name);
            commandBuilder.set("#LeaderboardCards[" + index + "] #BronzeCount.Text", String.valueOf(row.entry.getBronzeCount()));
            commandBuilder.set("#LeaderboardCards[" + index + "] #SilverCount.Text", String.valueOf(row.entry.getSilverCount()));
            commandBuilder.set("#LeaderboardCards[" + index + "] #GoldCount.Text", String.valueOf(row.entry.getGoldCount()));
            commandBuilder.set("#LeaderboardCards[" + index + "] #AuthorCount.Text", String.valueOf(row.entry.getAuthorCount()));
            commandBuilder.set("#LeaderboardCards[" + index + "] #TotalScore.Text", String.valueOf(row.entry.getTotalScore()));
            index++;
        }
        commandBuilder.set("#PageLabel.Text", slice.getLabel());
    }

    private static final class LeaderboardRow {
        private final int rank;
        private final MedalStore.MedalScoreEntry entry;
        private final String name;

        private LeaderboardRow(int rank, MedalStore.MedalScoreEntry entry, String name) {
            this.rank = rank;
            this.entry = entry;
            this.name = name != null ? name : "";
        }
    }

    public static class LeaderboardData extends ButtonEventData {
        static final String KEY_SEARCH = "@Search";

        public static final BuilderCodec<LeaderboardData> CODEC = BuilderCodec.<LeaderboardData>builder(LeaderboardData.class,
                        LeaderboardData::new)
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
