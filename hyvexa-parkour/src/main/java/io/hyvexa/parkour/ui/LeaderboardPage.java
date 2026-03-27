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
import io.hyvexa.common.ui.AbstractLeaderboardPage;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.MedalStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.util.ParkourUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LeaderboardPage extends AbstractLeaderboardPage {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final MedalStore medalStore;
    private static final String BUTTON_BACK = "Back";

    private Map<UUID, MedalStore.MedalScoreEntry> medalData;

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
    protected String getCardTemplatePath() {
        return "Pages/Parkour_LeaderboardEntry.ui";
    }

    @Override
    protected String getRankAccentColor(int rank) {
        return UIColorUtils.getRankAccentColor(rank);
    }

    @Override
    protected String getNoDataMessage() {
        return "No medals earned yet.";
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
    protected List<LeaderboardRow> loadRows() {
        List<MedalStore.MedalScoreEntry> snapshot = medalStore.getLeaderboardSnapshot();
        if (snapshot.isEmpty()) {
            return null;
        }

        medalData = new HashMap<>();
        List<LeaderboardRow> rows = new ArrayList<>();
        for (int i = 0; i < snapshot.size(); i++) {
            MedalStore.MedalScoreEntry entry = snapshot.get(i);
            UUID playerId = entry.getPlayerId();
            String name = ParkourUtils.resolveName(playerId, progressStore);
            medalData.put(playerId, entry);
            rows.add(new LeaderboardRow(i + 1, playerId, name != null ? name : "", ""));
        }
        return rows;
    }

    @Override
    protected void renderRow(UICommandBuilder cmd, String cardPrefix, LeaderboardRow row) {
        cmd.set(cardPrefix + " #Rank.Text", "#" + row.rank());
        cmd.set(cardPrefix + " #PlayerName.Text", row.name());
        MedalStore.MedalScoreEntry entry = medalData != null ? medalData.get(row.playerId()) : null;
        if (entry != null) {
            cmd.set(cardPrefix + " #BronzeCount.Text", String.valueOf(entry.getBronzeCount()));
            cmd.set(cardPrefix + " #SilverCount.Text", String.valueOf(entry.getSilverCount()));
            cmd.set(cardPrefix + " #GoldCount.Text", String.valueOf(entry.getGoldCount()));
            cmd.set(cardPrefix + " #EmeraldCount.Text", String.valueOf(entry.getEmeraldCount()));
            cmd.set(cardPrefix + " #InsaneCount.Text", String.valueOf(entry.getInsaneCount()));
            cmd.set(cardPrefix + " #TotalScore.Text", String.valueOf(entry.getTotalScore()));
        }
    }
}
