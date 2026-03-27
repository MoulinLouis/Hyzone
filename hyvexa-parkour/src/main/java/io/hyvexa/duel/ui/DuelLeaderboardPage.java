package io.hyvexa.duel.ui;

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
import io.hyvexa.duel.DuelTracker;
import io.hyvexa.duel.data.DuelPreferenceStore;
import io.hyvexa.duel.data.DuelStats;
import io.hyvexa.duel.data.DuelStatsStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.tracker.RunTracker;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DuelLeaderboardPage extends AbstractLeaderboardPage {

    private static final String BUTTON_BACK = "Back";

    private final DuelTracker duelTracker;
    private final RunTracker runTracker;
    private final ProgressStore progressStore;
    private final DuelPreferenceStore duelPreferenceStore;
    private DuelRowData[] duelDataByRank;

    public DuelLeaderboardPage(@Nonnull PlayerRef playerRef, DuelTracker duelTracker, RunTracker runTracker,
                               ProgressStore progressStore, DuelPreferenceStore duelPreferenceStore) {
        super(playerRef, 50);
        this.duelTracker = duelTracker;
        this.runTracker = runTracker;
        this.progressStore = progressStore;
        this.duelPreferenceStore = duelPreferenceStore;
    }

    @Override
    protected String getPagePath() {
        return "Pages/Duel_Leaderboard.ui";
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
    protected String getNoDataMessage() {
        return duelTracker == null || duelTracker.getStatsStore() == null
                ? "Duel stats unavailable." : "No duel stats yet.";
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
                        new DuelMenuPage(playerRef, duelTracker, runTracker, progressStore, duelPreferenceStore));
            }
        }
    }

    @Override
    protected List<LeaderboardRow> loadRows() {
        DuelStatsStore statsStore = duelTracker != null ? duelTracker.getStatsStore() : null;
        if (statsStore == null) {
            return null;
        }
        List<DuelStats> stats = statsStore.listStats();
        if (stats.isEmpty()) {
            return null;
        }

        List<DuelStats> sorted = new ArrayList<>(stats);
        sorted.sort(Comparator.comparingInt(DuelStats::getWins).reversed()
                .thenComparingInt(DuelStats::getLosses)
                .thenComparing(s -> {
                    String name = s.getPlayerName();
                    return name != null ? name.toLowerCase() : "";
                }));

        duelDataByRank = new DuelRowData[sorted.size() + 1]; // 1-indexed
        List<LeaderboardRow> rows = new ArrayList<>();
        int rank = 1;
        for (DuelStats s : sorted) {
            String name = s.getPlayerName() != null ? s.getPlayerName() : "Player";
            duelDataByRank[rank] = new DuelRowData(s.getWins(), s.getLosses());
            rows.add(new LeaderboardRow(rank, null, name, ""));
            rank++;
        }
        return rows;
    }

    @Override
    protected void renderRow(UICommandBuilder cmd, String cardPrefix, LeaderboardRow row) {
        cmd.set(cardPrefix + " #Rank.Text", String.valueOf(row.rank()));
        cmd.set(cardPrefix + " #PlayerName.Text", row.name());
        DuelRowData data = (duelDataByRank != null && row.rank() > 0 && row.rank() < duelDataByRank.length)
                ? duelDataByRank[row.rank()] : null;
        if (data != null) {
            cmd.set(cardPrefix + " #TotalScore.Text", data.wins + "W/" + data.losses + "L");
        }
    }

    private record DuelRowData(int wins, int losses) {
    }
}
