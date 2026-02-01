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
import io.hyvexa.ascend.robot.RobotManager;
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
    private static final String BUTTON_ROBOT_PREFIX = "Robot:";
    private static final int MAX_SPEED_LEVEL = 20;

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final AscendRunTracker runTracker;
    private final RobotManager robotManager;
    private ScheduledFuture<?> refreshTask;
    private volatile boolean active;
    private int lastMapCount;

    public AscendMapSelectPage(@Nonnull PlayerRef playerRef, AscendMapStore mapStore,
                               AscendPlayerStore playerStore, AscendRunTracker runTracker,
                               RobotManager robotManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.mapStore = mapStore;
        this.playerStore = playerStore;
        this.runTracker = runTracker;
        this.robotManager = robotManager;
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
        if (data.getButton().startsWith(BUTTON_ROBOT_PREFIX)) {
            handleRobotAction(ref, store, data.getButton().substring(BUTTON_ROBOT_PREFIX.length()));
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
            String accentColor = resolveMapAccentColor(index);
            String mapName = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();

            MapUnlockHelper.UnlockResult unlockResult = MapUnlockHelper.checkAndEnsureUnlock(
                playerRef.getUuid(), map, playerStore, mapStore);
            boolean unlocked = unlockResult.unlocked;
            AscendPlayerProgress.MapProgress mapProgress = unlockResult.mapProgress;
            boolean hasRobot = mapProgress != null && mapProgress.hasRobot();
            int speedLevel = mapProgress != null ? mapProgress.getRobotSpeedLevel() : 0;
            int stars = mapProgress != null ? mapProgress.getRobotStars() : 0;

            // Apply accent color to accent bars (left and button top)
            commandBuilder.set("#MapCards[" + index + "] #AccentBar.Background", accentColor);
            commandBuilder.set("#MapCards[" + index + "] #ButtonAccent.Background", accentColor);

            // Apply accent color to progress bar segments
            for (int seg = 1; seg <= MAX_SPEED_LEVEL; seg++) {
                commandBuilder.set("#MapCards[" + index + "] #Seg" + seg + ".Background", accentColor);
            }

            // Update progress bar visibility based on speed level
            updateProgressBar(commandBuilder, index, speedLevel);

            // Level text for button zone with star display
            String levelText = buildStarDisplay(stars, speedLevel);

            // Map name
            commandBuilder.set("#MapCards[" + index + "] #MapName.Text", mapName);

            // Status text
            String status;
            if (!unlocked) {
                status = "Locked | Price: " + map.getEffectivePrice() + " coins";
            } else {
                status = "Runs/sec: " + formatRunsPerSecond(map, hasRobot, speedLevel);
            }
            commandBuilder.set("#MapCards[" + index + "] #MapStatus.Text", status);

            // Runner status and button text
            String runnerStatusText;
            String runnerButtonText;
            long actionPrice;
            if (!hasRobot) {
                runnerStatusText = "No Runner";
                runnerButtonText = "Buy Runner";
                actionPrice = Math.max(0L, map.getEffectiveRobotPrice());
            } else {
                int speedPercent = speedLevel * 10;
                runnerStatusText = "Speed: +" + speedPercent + "%";
                if (speedLevel >= MAX_SPEED_LEVEL && stars < AscendConstants.MAX_ROBOT_STARS) {
                    runnerButtonText = "Evolve";
                    actionPrice = 0L;
                } else if (stars >= AscendConstants.MAX_ROBOT_STARS && speedLevel >= MAX_SPEED_LEVEL) {
                    runnerButtonText = "Maxed!";
                    actionPrice = 0L;
                } else {
                    runnerButtonText = "Upgrade";
                    actionPrice = computeUpgradeCost(speedLevel);
                }
            }

            String priceText = actionPrice > 0 ? (FormatUtils.formatCoinsForHud(actionPrice) + " coins") : "Free";
            if ((speedLevel >= MAX_SPEED_LEVEL && hasRobot) || runnerButtonText.equals("Maxed!")) {
                priceText = "";
            }

            commandBuilder.set("#MapCards[" + index + "] #RunnerLevel.Text", levelText);
            commandBuilder.set("#MapCards[" + index + "] #RunnerLevel.Style.TextColor", accentColor);
            commandBuilder.set("#MapCards[" + index + "] #RunnerStatus.Text", runnerStatusText);
            commandBuilder.set("#MapCards[" + index + "] #RobotBuyText.Text", runnerButtonText);
            commandBuilder.set("#MapCards[" + index + "] #RobotPriceText.Text", priceText);

            // Event bindings
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#MapCards[" + index + "] #SelectButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + map.getId()), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#MapCards[" + index + "] #RobotBuyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ROBOT_PREFIX + map.getId()), false);
            index++;
        }
    }

    private void updateProgressBar(UICommandBuilder cmd, int cardIndex, int speedLevel) {
        int filledSegments = Math.min(speedLevel, MAX_SPEED_LEVEL);
        for (int seg = 1; seg <= MAX_SPEED_LEVEL; seg++) {
            boolean visible = seg <= filledSegments;
            cmd.set("#MapCards[" + cardIndex + "] #Seg" + seg + ".Visible", visible);
        }
    }

    private String buildStarDisplay(int stars, int speedLevel) {
        if (stars >= AscendConstants.MAX_ROBOT_STARS && speedLevel >= MAX_SPEED_LEVEL) {
            return "MAX";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stars; i++) {
            sb.append('\u2605');  // Unicode filled star
        }
        if (stars > 0) {
            sb.append(' ');
        }
        sb.append("Lv.").append(speedLevel);
        return sb.toString();
    }

    private String resolveMapAccentColor(int index) {
        return switch (index) {
            case 0 -> "#7c3aed";  // Violet
            case 1 -> "#3b82f6";  // Blue
            case 2 -> "#06b6d4";  // Cyan
            case 3 -> "#f59e0b";  // Amber
            default -> "#ef4444"; // Red
        };
    }

    private String formatRunsPerSecond(AscendMap map, boolean hasRobot, int speedLevel) {
        if (map == null || !hasRobot) {
            return "0";
        }
        long base = Math.max(0L, map.getEffectiveBaseRunTimeMs());
        if (base <= 0L) {
            return "0";
        }
        double speedMultiplier = 1.0 + (speedLevel * AscendConstants.SPEED_UPGRADE_MULTIPLIER);
        long interval = (long) (base / speedMultiplier);
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

    private long computeUpgradeCost(int currentLevel) {
        return 100L * (long) Math.pow(2, currentLevel);
    }

    private void handleRobotAction(Ref<EntityStore> ref, Store<EntityStore> store, String mapId) {
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
        boolean unlocked = mapProgress.isUnlocked() || map.getEffectivePrice() <= 0;
        if (!unlocked) {
            sendMessage(store, ref, "[Ascend] Unlock the map before buying a runner.");
            return;
        }
        if (!mapProgress.hasRobot()) {
            long price = Math.max(0L, map.getEffectiveRobotPrice());
            if (price > 0 && !playerStore.spendCoins(playerRef.getUuid(), price)) {
                sendMessage(store, ref, "[Ascend] Not enough coins to buy a runner.");
                return;
            }
            playerStore.setHasRobot(playerRef.getUuid(), mapId, true);
            sendMessage(store, ref, "[Ascend] Runner purchased for " + price + " coins!");
            updateRobotRow(ref, store, mapId);
        } else {
            int currentLevel = mapProgress.getRobotSpeedLevel();
            int currentStars = mapProgress.getRobotStars();

            // Check if fully maxed (max stars and max level)
            if (currentStars >= AscendConstants.MAX_ROBOT_STARS && currentLevel >= MAX_SPEED_LEVEL) {
                sendMessage(store, ref, "[Ascend] Runner is fully evolved and at maximum speed!");
                return;
            }

            // Check if at max level and can evolve
            if (currentLevel >= MAX_SPEED_LEVEL && currentStars < AscendConstants.MAX_ROBOT_STARS) {
                int newStars = playerStore.evolveRobot(playerRef.getUuid(), mapId);
                if (robotManager != null) {
                    robotManager.respawnRobot(playerRef.getUuid(), mapId, newStars);
                }
                sendMessage(store, ref, "[Ascend] Runner evolved! Now at " + newStars + " star" + (newStars > 1 ? "s" : "") + "!");
                updateRobotRow(ref, store, mapId);
                return;
            }

            // Normal speed upgrade
            long upgradeCost = computeUpgradeCost(currentLevel);
            if (!playerStore.spendCoins(playerRef.getUuid(), upgradeCost)) {
                sendMessage(store, ref, "[Ascend] Not enough coins to upgrade speed.");
                return;
            }
            int newLevel = playerStore.incrementRobotSpeedLevel(playerRef.getUuid(), mapId);
            int newSpeedPercent = newLevel * 10;
            sendMessage(store, ref, "[Ascend] Runner speed upgraded to +" + newSpeedPercent + "%!");
            updateRobotRow(ref, store, mapId);
        }
    }

    private void startAutoRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (refreshTask != null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        // Initial delay of 2 seconds to let client fully build the UI before sending updates
        refreshTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            if (!active || ref == null || !ref.isValid()) {
                stopAutoRefresh();
                return;
            }
            CompletableFuture.runAsync(() -> refreshRunRates(ref, store), world);
        }, 2000L, 1000L, TimeUnit.MILLISECONDS);
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
        // Don't send updates if no maps exist (UI elements wouldn't exist)
        if (maps.isEmpty()) {
            return;
        }
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
                playerRef.getUuid(), map, playerStore, mapStore);
            boolean unlocked = unlockResult.unlocked;
            AscendPlayerProgress.MapProgress mapProgress = unlockResult.mapProgress;
            boolean hasRobot = mapProgress != null && mapProgress.hasRobot();
            int speedLevel = mapProgress != null ? mapProgress.getRobotSpeedLevel() : 0;
            String status = unlocked
                ? "Runs/sec: " + formatRunsPerSecond(map, hasRobot, speedLevel)
                : "Locked | Price: " + map.getEffectivePrice() + " coins";
            commandBuilder.set("#MapCards[" + i + "] #MapStatus.Text", status);
        }
        sendUpdate(commandBuilder, null, false);
    }

    private void updateRobotRow(Ref<EntityStore> ref, Store<EntityStore> store, String mapId) {
        if (!active) {
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
            playerRef.getUuid(), selectedMap, playerStore, mapStore);
        boolean unlocked = unlockResult.unlocked;
        AscendPlayerProgress.MapProgress mapProgress = unlockResult.mapProgress;
        boolean hasRobot = mapProgress != null && mapProgress.hasRobot();
        int speedLevel = mapProgress != null ? mapProgress.getRobotSpeedLevel() : 0;
        int stars = mapProgress != null ? mapProgress.getRobotStars() : 0;

        String status;
        if (!unlocked) {
            status = "Locked | Price: " + selectedMap.getEffectivePrice() + " coins";
        } else {
            status = "Runs/sec: " + formatRunsPerSecond(selectedMap, hasRobot, speedLevel);
        }

        String runnerStatusText;
        String runnerButtonText;
        long actionPrice;
        if (!hasRobot) {
            runnerStatusText = "No Runner";
            runnerButtonText = "Buy Runner";
            actionPrice = Math.max(0L, selectedMap.getEffectiveRobotPrice());
        } else {
            int speedPercent = speedLevel * 10;
            runnerStatusText = "Speed: +" + speedPercent + "%";
            if (speedLevel >= MAX_SPEED_LEVEL && stars < AscendConstants.MAX_ROBOT_STARS) {
                runnerButtonText = "Evolve";
                actionPrice = 0L;
            } else if (stars >= AscendConstants.MAX_ROBOT_STARS && speedLevel >= MAX_SPEED_LEVEL) {
                runnerButtonText = "Maxed!";
                actionPrice = 0L;
            } else {
                runnerButtonText = "Upgrade";
                actionPrice = computeUpgradeCost(speedLevel);
            }
        }

        String priceText = actionPrice > 0 ? (FormatUtils.formatCoinsForHud(actionPrice) + " coins") : "Free";
        if ((speedLevel >= MAX_SPEED_LEVEL && hasRobot) || runnerButtonText.equals("Maxed!")) {
            priceText = "";
        }

        UICommandBuilder commandBuilder = new UICommandBuilder();

        // Update progress bar
        updateProgressBar(commandBuilder, index, speedLevel);

        // Update level text in button zone with star display
        String levelText = buildStarDisplay(stars, speedLevel);
        commandBuilder.set("#MapCards[" + index + "] #RunnerLevel.Text", levelText);

        // Update text fields
        commandBuilder.set("#MapCards[" + index + "] #RunnerStatus.Text", runnerStatusText);
        commandBuilder.set("#MapCards[" + index + "] #RobotBuyText.Text", runnerButtonText);
        commandBuilder.set("#MapCards[" + index + "] #RobotPriceText.Text", priceText);
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
        if (mapProgress.isUnlocked() || map.getEffectivePrice() <= 0) {
            if (!mapProgress.isUnlocked()) {
                mapProgress.setUnlocked(true);
                playerStore.markDirty(playerRef.getUuid());
            }
            return true;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerStore.spendCoins(playerRef.getUuid(), map.getEffectivePrice())) {
            mapProgress.setUnlocked(true);
            playerStore.markDirty(playerRef.getUuid());
            if (player != null) {
                player.sendMessage(Message.raw("[Ascend] Map unlocked for " + map.getEffectivePrice() + " coins."));
            }
            return true;
        }
        if (player != null) {
            player.sendMessage(Message.raw("[Ascend] Map locked. Need " + map.getEffectivePrice() + " coins."));
        }
        return false;
    }
}
