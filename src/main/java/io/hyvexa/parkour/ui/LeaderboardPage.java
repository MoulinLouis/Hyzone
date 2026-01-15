package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.util.ParkourUtils;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LeaderboardPage extends BaseParkourPage {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final RunTracker runTracker;
    private static final String BUTTON_BACK = "Back";

    public LeaderboardPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                  ProgressStore progressStore, RunTracker runTracker) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.runTracker = runTracker;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_Leaderboard.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        buildLeaderboard(uiCommandBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
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

    private void buildLeaderboard(UICommandBuilder commandBuilder) {
        commandBuilder.clear("#LeaderboardCards");
        int totalMaps = mapStore.listMaps().size();
        if (totalMaps == 0) {
            commandBuilder.set("#EmptyText.Text", "No maps available.");
            return;
        }
        Map<UUID, Integer> counts = progressStore.getMapCompletionCounts();
        if (counts.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No completions yet.");
            return;
        }
        commandBuilder.set("#EmptyText.Text", "");
        List<Map.Entry<UUID, Integer>> sorted = counts.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .toList();
        int index = 0;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            String name = ParkourUtils.resolveName(entry.getKey(), progressStore);
            commandBuilder.append("#LeaderboardCards", "Pages/Parkour_LeaderboardEntry.ui");
            commandBuilder.set("#LeaderboardCards[" + index + "] #Rank.Text", String.valueOf(index + 1));
            commandBuilder.set("#LeaderboardCards[" + index + "] #PlayerName.Text", name);
            commandBuilder.set("#LeaderboardCards[" + index + "] #Completion.Text",
                    entry.getValue() + "/" + totalMaps);
            index++;
        }
    }
}
