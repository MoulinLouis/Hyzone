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
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.MedalStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.util.ParkourUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardPage extends AbstractSearchablePaginatedPage {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final MedalStore medalStore;
    private static final String BUTTON_BACK = "Back";

    public LeaderboardPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                  ProgressStore progressStore, MedalStore medalStore) {
        super(playerRef, 50);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.medalStore = medalStore;
    }

    @Override
    protected String getPagePath() {
        return "Pages/Parkour_Leaderboard.ui";
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
                        new LeaderboardMenuPage(playerRef, mapStore, progressStore, medalStore));
            }
        }
    }

    @Override
    protected void buildContent(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#LeaderboardCards");
        commandBuilder.set("#LeaderboardSearchField.Value", getSearchText());
        List<MedalStore.MedalScoreEntry> snapshot = medalStore.getLeaderboardSnapshot();
        if (snapshot.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No medals earned yet.");
            commandBuilder.set("#PageLabel.Text", "");
            return;
        }
        String filter = getSearchText().trim().toLowerCase();
        List<LeaderboardRow> filtered = new ArrayList<>();
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
        PaginationState.PageSlice slice = getPagination().slice(filtered.size());
        int start = slice.startIndex;
        int end = slice.endIndex;
        int index = 0;
        for (int i = start; i < end; i++) {
            LeaderboardRow row = filtered.get(i);
            commandBuilder.append("#LeaderboardCards", "Pages/Parkour_LeaderboardEntry.ui");
            String accentColor = UIColorUtils.getRankAccentColor(row.rank);
            AccentOverlayUtils.applyAccent(commandBuilder, "#LeaderboardCards[" + index + "] #AccentBar",
                    accentColor, AccentOverlayUtils.RANK_ACCENTS);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Rank.Text", "#" + row.rank);
            commandBuilder.set("#LeaderboardCards[" + index + "] #PlayerName.Text", row.name);
            commandBuilder.set("#LeaderboardCards[" + index + "] #BronzeCount.Text", String.valueOf(row.entry.getBronzeCount()));
            commandBuilder.set("#LeaderboardCards[" + index + "] #SilverCount.Text", String.valueOf(row.entry.getSilverCount()));
            commandBuilder.set("#LeaderboardCards[" + index + "] #GoldCount.Text", String.valueOf(row.entry.getGoldCount()));
            commandBuilder.set("#LeaderboardCards[" + index + "] #EmeraldCount.Text", String.valueOf(row.entry.getEmeraldCount()));
            commandBuilder.set("#LeaderboardCards[" + index + "] #InsaneCount.Text", String.valueOf(row.entry.getInsaneCount()));
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
}
