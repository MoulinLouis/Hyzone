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
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.ui.ButtonEventData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class AscendMapSelectPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_SELECT_PREFIX = "Select:";
    private static final String BUTTON_BUY_ROBOT_PREFIX = "BuyRobot:";

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
        if (data.getButton().startsWith(BUTTON_BUY_ROBOT_PREFIX)) {
            handleBuyRobot(ref, store, data.getButton().substring(BUTTON_BUY_ROBOT_PREFIX.length()));
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
        List<AscendMap> maps = new ArrayList<>(mapStore.listMapsSorted());
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
                int digit = playerStore.getMapMultiplierValue(playerRef.getUuid(), map.getId());
                long payout = playerStore.getCompletionPayout(playerRef.getUuid(), maps,
                    AscendConstants.MULTIPLIER_SLOTS, map.getId());
                status = "Digit: " + digit + " | Payout: " + payout + " coins";
                if (mapProgress != null && mapProgress.isCompletedManually()) {
                    status = "Completed | " + status;
                }
            }
            int robotCount = mapProgress != null ? Math.max(0, mapProgress.getRobotCount()) : 0;
            long robotPrice = Math.max(0L, map.getRobotPrice());
            String robotPriceText = robotPrice > 0 ? (FormatUtils.formatCoinsForHud(robotPrice) + " coins") : "Free";
            commandBuilder.set("#MapCards[" + index + "] #MapName.Text", mapName);
            commandBuilder.set("#MapCards[" + index + "] #MapStatus.Text", status);
            commandBuilder.set("#MapCards[" + index + "] #RobotCountText.Text", "Robots: " + robotCount);
            commandBuilder.set("#MapCards[" + index + "] #RobotPriceText.Text", "Cost: " + robotPriceText);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#MapCards[" + index + "] #SelectButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + map.getId()), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#MapCards[" + index + "] #RobotBuyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BUY_ROBOT_PREFIX + map.getId()), false);
            index++;
        }
    }

    private void handleBuyRobot(Ref<EntityStore> ref, Store<EntityStore> store, String mapId) {
        AscendMap map = mapStore.getMap(mapId);
        if (map == null) {
            sendMessage(store, ref, "Map not found.");
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerRef.getUuid());
        AscendPlayerProgress.MapProgress mapProgress = progress.getOrCreateMapProgress(mapId);
        boolean unlocked = mapProgress.isUnlocked() || map.getPrice() <= 0;
        if (!unlocked) {
            sendMessage(store, ref, "[Ascend] Unlock the map before buying a robot.");
            return;
        }
        long price = Math.max(0L, map.getRobotPrice());
        if (price > 0 && !playerStore.spendCoins(playerRef.getUuid(), price)) {
            sendMessage(store, ref, "[Ascend] Not enough coins to buy a robot.");
            return;
        }
        int newCount = playerStore.addRobotCount(playerRef.getUuid(), mapId, 1);
        sendMessage(store, ref, "[Ascend] Robot purchased for " + price + " coins. Robots: " + newCount);
        updateRobotRow(ref, store, mapId, newCount, price);
    }

    private void updateRobotRow(Ref<EntityStore> ref, Store<EntityStore> store, String mapId, int robotCount, long price) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        List<AscendMap> maps = new ArrayList<>(mapStore.listMapsSorted());
        int index = -1;
        for (int i = 0; i < maps.size(); i++) {
            AscendMap map = maps.get(i);
            if (map != null && mapId.equals(map.getId())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        String robotPriceText = price > 0 ? (FormatUtils.formatCoinsForHud(price) + " coins") : "Free";
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#MapCards[" + index + "] #RobotCountText.Text", "Robots: " + Math.max(0, robotCount));
        commandBuilder.set("#MapCards[" + index + "] #RobotPriceText.Text", "Cost: " + robotPriceText);
        sendUpdate(commandBuilder, null, false);
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
