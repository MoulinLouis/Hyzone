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
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.tracker.RunTracker;
import io.hyvexa.parkour.util.ParkourUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LeaderboardMapSelectPage extends BaseParkourPage {

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final RunTracker runTracker;
    private final String category;
    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_SELECT_PREFIX = "Select:";

    public LeaderboardMapSelectPage(@Nonnull PlayerRef playerRef, MapStore mapStore,
                                           ProgressStore progressStore, RunTracker runTracker,
                                           String category) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.runTracker = runTracker;
        this.category = category;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_LeaderboardMapSelect.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        buildMapList(ref, store, uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (BUTTON_BACK.equals(data.getButton())) {
            player.getPageManager().openCustomPage(ref, store,
                    new LeaderboardMenuPage(playerRef, mapStore, progressStore, runTracker));
            return;
        }
        if (data.getButton().startsWith(BUTTON_SELECT_PREFIX)) {
            String mapId = data.getButton().substring(BUTTON_SELECT_PREFIX.length());
            player.getPageManager().openCustomPage(ref, store,
                    new MapLeaderboardPage(playerRef, mapStore, progressStore, runTracker, mapId, category));
        }
    }

    private void buildMapList(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder,
                              UIEventBuilder eventBuilder) {
        commandBuilder.clear("#MapCards");
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        List<Map> maps = new ArrayList<>(mapStore.listMaps());
        maps.sort(Comparator.comparingInt((Map map) -> {
                    int difficulty = map.getDifficulty();
                    return difficulty <= 0 ? Integer.MAX_VALUE : difficulty;
                })
                .thenComparing(map -> map.getName() != null ? map.getName() : map.getId(),
                        String.CASE_INSENSITIVE_ORDER));
        String accentColor = UIColorUtils.getCategoryAccentColor(category);
        int index = 0;
        for (Map map : maps) {
            if (!FormatUtils.normalizeCategory(map.getCategory()).equalsIgnoreCase(category)) {
                continue;
            }
            commandBuilder.append("#MapCards", "Pages/Parkour_LeaderboardMapEntry.ui");
            commandBuilder.set("#MapCards[" + index + "] #AccentBar.Background", accentColor);
            commandBuilder.set("#MapCards[" + index + "] #MapName.Text", ParkourUtils.formatMapName(map));
            boolean completed = progressStore.isMapCompleted(playerRef.getUuid(), map.getId());
            if (completed) {
                Long bestTime = progressStore.getBestTimeMs(playerRef.getUuid(), map.getId());
                int position = progressStore.getLeaderboardPosition(map.getId(), playerRef.getUuid());
                StringBuilder status = new StringBuilder();
                if (bestTime != null) {
                    status.append("PB: ").append(FormatUtils.formatDuration(bestTime));
                }
                if (position > 0) {
                    if (status.length() > 0) {
                        status.append(" | ");
                    }
                    status.append("#").append(position);
                }
                commandBuilder.set("#MapCards[" + index + "] #MapStatus.Text", status.toString());
            } else {
                commandBuilder.set("#MapCards[" + index + "] #MapStatus.Text", "");
            }
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#MapCards[" + index + "] #SelectButton",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + map.getId()), false);
            index++;
        }
    }
}
