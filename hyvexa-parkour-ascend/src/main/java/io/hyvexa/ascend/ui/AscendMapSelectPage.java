package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.util.MapUnlockHelper;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.ui.ButtonEventData;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AscendMapSelectPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_SELECT_PREFIX = "Select:";
    private static final String BUTTON_BUY_ROBOT_PREFIX = "BuyRobot:";

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final AscendRunTracker runTracker;
    private ScheduledFuture<?> refreshTask;
    private volatile boolean active;
    private int lastMapCount;

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
        active = true;
        buildMapList(ref, store, uiCommandBuilder, uiEventBuilder);
        startAutoRefresh(ref, store);
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

    @Override
    public void close() {
        active = false;
        stopAutoRefresh();
        super.close();
    }

    private void buildMapList(Ref<EntityStore> ref, Store<EntityStore> store,
                              UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#MapCards");
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        List<AscendMap> maps = new ArrayList<>(mapStore.listMapsSorted());
        lastMapCount = maps.size();
        int index = 0;
        for (AscendMap map : maps) {
            commandBuilder.append("#MapCards", "Pages/Ascend_MapSelectEntry.ui");
            String cardColor = resolveMapCardColor(index);
            String mapName = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
            String textColor = resolveMapTextColor(index);
            String hoverColor = adjustColor(cardColor, 0.12);
            String pressedColor = adjustColor(cardColor, -0.12);
            commandBuilder.set("#MapCards[" + index + "] #SelectButton.Background", cardColor);
            commandBuilder.set("#MapCards[" + index + "] #SelectButton.Style.Default.Background", cardColor);
            commandBuilder.set("#MapCards[" + index + "] #SelectButton.Style.Hovered.Background", hoverColor);
            commandBuilder.set("#MapCards[" + index + "] #SelectButton.Style.Pressed.Background", pressedColor);
            commandBuilder.set("#MapCards[" + index + "] #RobotBuyButton.Background", cardColor);
            commandBuilder.set("#MapCards[" + index + "] #RobotBuyButton.Style.Default.Background", cardColor);
            commandBuilder.set("#MapCards[" + index + "] #RobotBuyButton.Style.Hovered.Background", hoverColor);
            commandBuilder.set("#MapCards[" + index + "] #RobotBuyButton.Style.Pressed.Background", pressedColor);
            commandBuilder.set("#MapCards[" + index + "] #MapName.Style.TextColor", textColor);
            commandBuilder.set("#MapCards[" + index + "] #MapStatus.Style.TextColor", textColor);
            commandBuilder.set("#MapCards[" + index + "] #RobotCountText.Style.TextColor", textColor);
            commandBuilder.set("#MapCards[" + index + "] #RobotPriceText.Style.TextColor", textColor);
            commandBuilder.set("#MapCards[" + index + "] #RobotBuyText.Style.TextColor", textColor);
            MapUnlockHelper.UnlockResult unlockResult = MapUnlockHelper.checkAndEnsureUnlock(
                playerRef.getUuid(), map, playerStore);
            boolean unlocked = unlockResult.unlocked;
            AscendPlayerProgress.MapProgress mapProgress = unlockResult.mapProgress;
            String status;
            if (!unlocked) {
                status = "Locked | Price: " + map.getPrice() + " coins";
            } else {
                int robotCount = mapProgress != null ? Math.max(0, mapProgress.getRobotCount()) : 0;
                status = "Runs/sec: " + formatRunsPerSecond(map, robotCount);
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

    private String resolveMapCardColor(int index) {
        return switch (index) {
            case 0 -> "#c0392b";
            case 1 -> "#d35400";
            case 2 -> "#f1c40f";
            case 3 -> "#27ae60";
            default -> "#2980b9";
        };
    }

    private String resolveMapTextColor(int index) {
        return "#1b1b1b";
    }

    private String adjustColor(String hex, double factor) {
        if (hex == null || !hex.startsWith("#") || (hex.length() != 7)) {
            return "#000000";
        }
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            r = adjustChannel(r, factor);
            g = adjustChannel(g, factor);
            b = adjustChannel(b, factor);
            return String.format("#%02x%02x%02x", r, g, b);
        } catch (NumberFormatException e) {
            return "#000000";
        }
    }

    private int adjustChannel(int value, double factor) {
        double result;
        if (factor >= 0) {
            result = value + (255 - value) * factor;
        } else {
            result = value * (1.0 + factor);
        }
        int clamped = (int) Math.round(result);
        return Math.max(0, Math.min(255, clamped));
    }

    private String formatRunsPerSecond(AscendMap map, int robotCount) {
        if (map == null || robotCount <= 0) {
            return "0";
        }
        long base = Math.max(0L, map.getBaseRunTimeMs());
        long reduction = Math.max(0L, map.getRobotTimeReductionMs());
        if (base <= 0L) {
            return "0";
        }
        long interval = base - (reduction * Math.max(0, robotCount - 1));
        interval = Math.max(1L, interval);
        double perSecond = 1000.0 / interval;
        String text = String.format(Locale.US, "%.2f", perSecond);
        if (text.endsWith(".00")) {
            return text.substring(0, text.length() - 3);
        }
        if (text.endsWith("0")) {
            return text.substring(0, text.length() - 1);
        }
        return text;
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

    private void startAutoRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (refreshTask != null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        refreshTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            if (!active || ref == null || !ref.isValid()) {
                stopAutoRefresh();
                return;
            }
            CompletableFuture.runAsync(() -> refreshRunRates(ref, store), world);
        }, 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
    }

    private void refreshRunRates(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!active) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        List<AscendMap> maps = new ArrayList<>(mapStore.listMapsSorted());
        if (maps.size() != lastMapCount) {
            UICommandBuilder rebuild = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            buildMapList(ref, store, rebuild, events);
            sendUpdate(rebuild, events, false);
            return;
        }
        UICommandBuilder commandBuilder = new UICommandBuilder();
        for (int i = 0; i < maps.size(); i++) {
            AscendMap map = maps.get(i);
            if (map == null) {
                continue;
            }
            MapUnlockHelper.UnlockResult unlockResult = MapUnlockHelper.checkAndEnsureUnlock(
                playerRef.getUuid(), map, playerStore);
            boolean unlocked = unlockResult.unlocked;
            AscendPlayerProgress.MapProgress mapProgress = unlockResult.mapProgress;
            int robotCount = mapProgress != null ? Math.max(0, mapProgress.getRobotCount()) : 0;
            String status = unlocked
                ? "Runs/sec: " + formatRunsPerSecond(map, robotCount)
                : "Locked | Price: " + map.getPrice() + " coins";
            commandBuilder.set("#MapCards[" + i + "] #MapStatus.Text", status);
        }
        sendUpdate(commandBuilder, null, false);
    }

    private void updateRobotRow(Ref<EntityStore> ref, Store<EntityStore> store, String mapId, int robotCount, long price) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        List<AscendMap> maps = new ArrayList<>(mapStore.listMapsSorted());
        int index = -1;
        AscendMap selectedMap = null;
        for (int i = 0; i < maps.size(); i++) {
            AscendMap map = maps.get(i);
            if (map != null && mapId.equals(map.getId())) {
                index = i;
                selectedMap = map;
                break;
            }
        }
        if (index < 0 || selectedMap == null) {
            return;
        }
        MapUnlockHelper.UnlockResult unlockResult = MapUnlockHelper.checkAndEnsureUnlock(
            playerRef.getUuid(), selectedMap, playerStore);
        boolean unlocked = unlockResult.unlocked;
        AscendPlayerProgress.MapProgress mapProgress = unlockResult.mapProgress;
        String status;
        if (!unlocked) {
            status = "Locked | Price: " + selectedMap.getPrice() + " coins";
        } else {
            status = "Runs/sec: " + formatRunsPerSecond(selectedMap, robotCount);
        }
        String robotPriceText = price > 0 ? (FormatUtils.formatCoinsForHud(price) + " coins") : "Free";
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#MapCards[" + index + "] #RobotCountText.Text", "Robots: " + Math.max(0, robotCount));
        commandBuilder.set("#MapCards[" + index + "] #RobotPriceText.Text", "Cost: " + robotPriceText);
        commandBuilder.set("#MapCards[" + index + "] #MapStatus.Text", status);
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
