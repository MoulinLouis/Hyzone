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
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.util.ParkourUtils;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MapLeaderboardPage extends InteractiveCustomUIPage<MapLeaderboardPage.MapLeaderboardData> {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final RunTracker runTracker;
    private final String mapId;
    private final String category;
    private final PaginationState pagination = new PaginationState(50);
    private String searchText = "";
    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_PREV = "PrevPage";
    private static final String BUTTON_NEXT = "NextPage";

    public MapLeaderboardPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                     ProgressStore progressStore, RunTracker runTracker,
                                     String mapId, String category) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, MapLeaderboardData.CODEC);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.runTracker = runTracker;
        this.mapId = mapId;
        this.category = category;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_MapLeaderboard.ui");
        bindEvents(uiEventBuilder);
        var map = mapStore.getMap(mapId);
        if (map != null) {
            uiCommandBuilder.set("#MapTitle.Text", "Best times for " + ParkourUtils.formatMapName(map));
        }
        buildLeaderboard(uiCommandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull MapLeaderboardData data) {
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
                        new LeaderboardMapSelectPage(playerRef, mapStore, progressStore, runTracker, category));
            }
        }
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        bindEvents(eventBuilder);
        var map = mapStore.getMap(mapId);
        if (map != null) {
            commandBuilder.set("#MapTitle.Text", "Best times for " + ParkourUtils.formatMapName(map));
        }
        buildLeaderboard(commandBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void bindEvents(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#LeaderboardSearchField",
                EventData.of(MapLeaderboardData.KEY_SEARCH, "#LeaderboardSearchField.Value"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_PREV), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NEXT), false);
    }

    private void buildLeaderboard(UICommandBuilder commandBuilder) {
        commandBuilder.clear("#LeaderboardCards");
        commandBuilder.set("#LeaderboardSearchField.Value", searchText);
        Map<UUID, Long> times = progressStore.getBestTimesForMap(mapId);
        if (times.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No completions yet.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }
        String filter = searchText != null ? searchText.trim().toLowerCase() : "";
        List<Map.Entry<UUID, Long>> sorted = times.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .toList();
        List<LeaderboardRow> filtered = new java.util.ArrayList<>();
        long lastTime = Long.MIN_VALUE;
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<UUID, Long> entry = sorted.get(i);
            long time = toDisplayedCentiseconds(entry.getValue());
            if (i == 0 || time > lastTime) {
                rank = i + 1;
                lastTime = time;
            }
            String name = ParkourUtils.resolveName(entry.getKey(), progressStore);
            if (!filter.isEmpty()) {
                String safeName = name != null ? name : "";
                if (!safeName.toLowerCase().startsWith(filter)) {
                    continue;
                }
            }
            filtered.add(new LeaderboardRow(rank, entry, name));
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
            commandBuilder.append("#LeaderboardCards", "Pages/Parkour_MapLeaderboardEntry.ui");
            String accentColor = UIColorUtils.getRankAccentColor(row.rank);
            commandBuilder.set("#LeaderboardCards[" + index + "] #AccentBar.Background", accentColor);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Rank.Text", String.valueOf(row.rank));
            commandBuilder.set("#LeaderboardCards[" + index + "] #PlayerName.Text", row.name);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Time.Text",
                    FormatUtils.formatDuration(row.entry.getValue()));
            index++;
        }
        commandBuilder.set("#PageLabel.Text", slice.getLabel());
    }

    private static final class LeaderboardRow {
        private final int rank;
        private final Map.Entry<UUID, Long> entry;
        private final String name;

        private LeaderboardRow(int rank, Map.Entry<UUID, Long> entry, String name) {
            this.rank = rank;
            this.entry = entry;
            this.name = name != null ? name : "";
        }
    }

    private static long toDisplayedCentiseconds(long durationMs) {
        return Math.round(durationMs / 10.0);
    }

    public static class MapLeaderboardData extends ButtonEventData {
        static final String KEY_SEARCH = "@Search";

        public static final BuilderCodec<MapLeaderboardData> CODEC = BuilderCodec.<MapLeaderboardData>builder(MapLeaderboardData.class,
                        MapLeaderboardData::new)
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
