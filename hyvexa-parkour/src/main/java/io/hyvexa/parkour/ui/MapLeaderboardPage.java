package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.AbstractSearchablePaginatedPage;
import io.hyvexa.common.ui.AccentOverlayUtils;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.ui.PaginationState;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.util.ParkourUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MapLeaderboardPage extends AbstractSearchablePaginatedPage {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final String mapId;
    private final String category;
    private static final String BUTTON_BACK = "Back";

    public MapLeaderboardPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                     ProgressStore progressStore,
                                     String mapId, String category) {
        super(playerRef, 50);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.mapId = mapId;
        this.category = category;
    }

    @Override
    protected String getPagePath() {
        return "Pages/Parkour_MapLeaderboard.ui";
    }

    @Override
    protected String getSearchFieldId() {
        return "#LeaderboardSearchField";
    }

    @Override
    protected void bindCustomEvents(UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
    }

    @Override
    protected void handleCustomButton(String button, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (BUTTON_BACK.equals(button)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && playerRef != null) {
                player.getPageManager().openCustomPage(ref, store,
                        new LeaderboardMapSelectPage(playerRef, mapStore, progressStore, category));
            }
        }
    }

    @Override
    protected void buildContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        var map = mapStore.getMap(mapId);
        if (map != null) {
            commandBuilder.set("#MapTitle.Text", "Best times for " + ParkourUtils.formatMapName(map));
        }
        commandBuilder.clear("#LeaderboardCards");
        commandBuilder.set("#LeaderboardSearchField.Value", getSearchText());
        Map<UUID, Long> times = progressStore.getBestTimesForMap(mapId);
        if (times.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No completions yet.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }
        String filter = getSearchText().trim().toLowerCase();
        List<Map.Entry<UUID, Long>> sorted = times.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .toList();
        List<LeaderboardRow> filtered = new ArrayList<>();
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
        PaginationState.PageSlice slice = getPagination().slice(filtered.size());
        int start = slice.startIndex;
        int end = slice.endIndex;
        int index = 0;
        for (int i = start; i < end; i++) {
            LeaderboardRow row = filtered.get(i);
            commandBuilder.append("#LeaderboardCards", "Pages/Parkour_MapLeaderboardEntry.ui");
            String accentColor = UIColorUtils.getRankAccentColor(row.rank);
            AccentOverlayUtils.applyAccent(commandBuilder, "#LeaderboardCards[" + index + "] #AccentBar",
                    accentColor, AccentOverlayUtils.RANK_ACCENTS);
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
}
