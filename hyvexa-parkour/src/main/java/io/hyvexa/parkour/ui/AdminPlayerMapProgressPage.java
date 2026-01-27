package io.hyvexa.parkour.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.parkour.data.Map;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.util.ParkourUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class AdminPlayerMapProgressPage extends BaseParkourPage {

    private static final String BUTTON_BACK = "Back";
    private static final String BUTTON_SELECT_PREFIX = "Map:";

    private final MapStore mapStore;
    private final ProgressStore progressStore;
    private final UUID targetId;

    public AdminPlayerMapProgressPage(@Nonnull PlayerRef playerRef, MapStore mapStore, ProgressStore progressStore,
                                      @Nonnull UUID targetId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.mapStore = mapStore;
        this.progressStore = progressStore;
        this.targetId = targetId;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Parkour_AdminPlayerMapProgress.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        buildMapList(uiCommandBuilder, uiEventBuilder);
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
            if (player == null || playerRef == null) {
                return;
            }
            player.getPageManager().openCustomPage(ref, store,
                    new AdminPlayerStatsPage(playerRef, mapStore, progressStore, targetId));
            return;
        }
        if (!data.getButton().startsWith(BUTTON_SELECT_PREFIX)) {
            return;
        }
        String mapId = data.getButton().substring(BUTTON_SELECT_PREFIX.length());
        boolean removed = progressStore.clearPlayerMapProgress(targetId, mapId);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            if (removed) {
                String mapName = resolveMapName(mapId);
                player.sendMessage(Message.raw("Cleared progress for " + mapName + "."));
            } else {
                player.sendMessage(Message.raw("No progress found for that map."));
            }
        }
        sendRefresh();
    }

    private void buildMapList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#MapCards");
        if (mapStore == null) {
            commandBuilder.set("#EmptyText.Text", "Maps unavailable.");
            return;
        }
        List<Map> maps = new ArrayList<>(mapStore.listMaps());
        maps.sort(Comparator.comparingInt(Map::getOrder)
                .thenComparing(map -> map.getName() != null ? map.getName() : map.getId(),
                        String.CASE_INSENSITIVE_ORDER));
        int index = 0;
        for (Map map : maps) {
            commandBuilder.append("#MapCards", "Pages/Parkour_AdminPlayerMapProgressEntry.ui");
            commandBuilder.set("#MapCards[" + index + "] #MapName.Text", ParkourUtils.formatMapName(map));
            boolean completed = progressStore.isMapCompleted(targetId, map.getId());
            commandBuilder.set("#MapCards[" + index + "] #MapStatus.Text", completed ? "Completed" : "Not completed");
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#MapCards[" + index + "]",
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + map.getId()), false);
            index++;
        }
        if (maps.isEmpty()) {
            commandBuilder.set("#EmptyText.Text", "No maps available.");
        } else {
            commandBuilder.set("#EmptyText.Text", "");
        }
    }

    private void sendRefresh() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BACK), false);
        buildMapList(commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }

    private String resolveMapName(String mapId) {
        Map map = mapStore != null ? mapStore.getMap(mapId) : null;
        if (map == null) {
            return mapId != null ? mapId : "map";
        }
        return ParkourUtils.formatMapName(map);
    }
}
