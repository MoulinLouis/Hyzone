package io.hyvexa.ascend.ui;

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
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.ui.ButtonEventData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AscendMapSelectPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_SELECT_PREFIX = "Select:";

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final AscendRunTracker runTracker;

    public AscendMapSelectPage(@Nonnull PlayerRef playerRef, AscendMapStore mapStore,
                               AscendPlayerStore playerStore, AscendRunTracker runTracker) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.mapStore = mapStore;
        this.playerStore = playerStore;
        this.runTracker = runTracker;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Ascend_MapSelect.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        buildMapList(ref, store, uiCommandBuilder, uiEventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ButtonEventData data) {
        super.handleDataEvent(ref, store, data);
        if (data.getButton() == null) {
            return;
        }
        if (BUTTON_CLOSE.equals(data.getButton())) {
            this.close();
            return;
        }
        if (!data.getButton().startsWith(BUTTON_SELECT_PREFIX)) {
            return;
        }
        String mapId = data.getButton().substring(BUTTON_SELECT_PREFIX.length());
        AscendMap map = mapStore.getMap(mapId);
        if (map == null) {
            sendMessage(store, ref, "Map not found.");
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        if (!ensureUnlocked(store, ref, playerRef, mapId, map)) {
            return;
        }
        if (map.getStartX() == 0 && map.getStartY() == 0 && map.getStartZ() == 0) {
            sendMessage(store, ref, "Map '" + mapId + "' has no start set.");
            return;
        }
        if (runTracker != null) {
            runTracker.teleportToMapStart(ref, store, playerRef, mapId);
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            String mapName = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
            player.sendMessage(Message.raw("[Ascend] Run started: " + mapName));
        }
        this.close();
    }

    private void buildMapList(Ref<EntityStore> ref, Store<EntityStore> store,
                              UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#MapCards");
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        List<AscendMap> maps = new ArrayList<>(mapStore.listMaps());
        maps.sort(Comparator.comparingInt(AscendMap::getDisplayOrder)
            .thenComparing(map -> map.getName() != null ? map.getName() : map.getId(),
                String.CASE_INSENSITIVE_ORDER));
        AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerRef.getUuid());
        int index = 0;
        for (AscendMap map : maps) {
            commandBuilder.append("#MapCards", "Pages/Ascend_MapSelectEntry.ui");
            String mapName = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
            AscendPlayerProgress.MapProgress mapProgress = progress != null
                ? progress.getMapProgress().get(map.getId())
                : null;
            boolean unlocked = map.getPrice() <= 0;
            if (mapProgress != null && mapProgress.isUnlocked()) {
                unlocked = true;
            }
            if (map.getPrice() <= 0 && (mapProgress == null || !mapProgress.isUnlocked())) {
                playerStore.setMapUnlocked(playerRef.getUuid(), map.getId(), true);
                mapProgress = playerStore.getMapProgress(playerRef.getUuid(), map.getId());
                unlocked = true;
            }
            String status;
            if (!unlocked) {
                status = "Locked | Price: " + map.getPrice() + " coins";
            } else {
                status = "Reward: " + map.getBaseReward() + " coins";
                if (mapProgress != null && mapProgress.getPendingCoins() > 0) {
                    status += " | Pending: " + mapProgress.getPendingCoins();
                }
                if (mapProgress != null && mapProgress.isCompletedManually()) {
                    status = "Completed | " + status;
                }
            }
            commandBuilder.set("#MapCards[" + index + "] #MapName.Text", mapName);
            commandBuilder.set("#MapCards[" + index + "] #MapStatus.Text", status);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#MapCards[" + index + "]",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + map.getId()), false);
            index++;
        }
    }

    private void sendMessage(Store<EntityStore> store, Ref<EntityStore> ref, String text) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(text));
        }
    }

    private boolean ensureUnlocked(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef,
                                   String mapId, AscendMap map) {
        AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerRef.getUuid());
        AscendPlayerProgress.MapProgress mapProgress = progress.getOrCreateMapProgress(mapId);
        if (mapProgress.isUnlocked() || map.getPrice() <= 0) {
            if (!mapProgress.isUnlocked()) {
                mapProgress.setUnlocked(true);
                playerStore.markDirty(playerRef.getUuid());
            }
            return true;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerStore.spendCoins(playerRef.getUuid(), map.getPrice())) {
            mapProgress.setUnlocked(true);
            playerStore.markDirty(playerRef.getUuid());
            if (player != null) {
                player.sendMessage(Message.raw("[Ascend] Map unlocked for " + map.getPrice() + " coins."));
            }
            return true;
        }
        if (player != null) {
            player.sendMessage(Message.raw("[Ascend] Map locked. Need " + map.getPrice() + " coins."));
        }
        return false;
    }
}
