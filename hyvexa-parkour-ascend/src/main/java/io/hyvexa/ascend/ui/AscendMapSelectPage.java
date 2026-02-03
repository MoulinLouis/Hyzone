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
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.ghost.GhostRecording;
import io.hyvexa.ascend.ghost.GhostStore;
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
    private static final String BUTTON_BUY_ALL = "BuyAll";
    private static final String BUTTON_EVOLVE_ALL = "EvolveAll";
    private static final String BUTTON_STATS = "Stats";
    private static final int MAX_SPEED_LEVEL = 20;

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final AscendRunTracker runTracker;
    private final RobotManager robotManager;
    private final GhostStore ghostStore;
    private ScheduledFuture<?> refreshTask;
    private int lastMapCount;

    public AscendMapSelectPage(@Nonnull PlayerRef playerRef, AscendMapStore mapStore,
                               AscendPlayerStore playerStore, AscendRunTracker runTracker,
                               RobotManager robotManager, GhostStore ghostStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.mapStore = mapStore;
        this.playerStore = playerStore;
        this.runTracker = runTracker;
        this.robotManager = robotManager;
        this.ghostStore = ghostStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Ascend_MapSelect.ui");
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ActionButton1",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BUY_ALL), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ActionButton2",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_EVOLVE_ALL), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ActionButton3",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_STATS), false);
        buildMapList(ref, store, uiCommandBuilder, uiEventBuilder);
        // Start affordability color updates (coins change frequently from passive income)
        startAffordabilityUpdates(ref, store);
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
        if (BUTTON_BUY_ALL.equals(data.getButton())) {
            handleBuyAll(ref, store);
            return;
        }
        if (BUTTON_EVOLVE_ALL.equals(data.getButton())) {
            handleEvolveAll(ref, store);
            return;
        }
        if (BUTTON_STATS.equals(data.getButton())) {
            handleOpenStats(ref, store);
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
            player.sendMessage(Message.raw("[Ascend] Ready: " + mapName + " - Move to start!"));
            // Give run items (reset + leave)
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin != null) {
                plugin.giveRunItems(player);
            }
        }
        this.close();
    }

    @Override
    public void close() {
        stopAffordabilityUpdates();
        super.close();
    }

    private void buildMapList(Ref<EntityStore> ref, Store<EntityStore> store,
                              UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#MapCards");
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        // Filter maps to only include unlocked ones
        List<AscendMap> allMaps = new ArrayList<>(mapStore.listMapsSorted());
        List<AscendMap> maps = new ArrayList<>();
        for (AscendMap map : allMaps) {
            MapUnlockHelper.UnlockResult unlockResult = MapUnlockHelper.checkAndEnsureUnlock(
                playerRef.getUuid(), map, playerStore, mapStore);
            if (unlockResult.unlocked) {
                maps.add(map);
            }
        }
        lastMapCount = maps.size();
        int index = 0;
        for (AscendMap map : maps) {
            commandBuilder.append("#MapCards", "Pages/Ascend_MapSelectEntry.ui");
            String accentColor = resolveMapAccentColor(index);
            String mapName = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();

            // All maps in this list are already unlocked (filtered above)
            AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerRef.getUuid(), map.getId());
            boolean hasRobot = mapProgress != null && mapProgress.hasRobot();
            boolean completedManually = mapProgress != null && mapProgress.isCompletedManually();
            int speedLevel = mapProgress != null ? mapProgress.getRobotSpeedLevel() : 0;
            int stars = mapProgress != null ? mapProgress.getRobotStars() : 0;

            // Apply accent color to left accent bar
            commandBuilder.set("#MapCards[" + index + "] #AccentBar.Background", accentColor);

            // Apply accent color to progress bar segments (always use accent color)
            for (int seg = 1; seg <= MAX_SPEED_LEVEL; seg++) {
                commandBuilder.set("#MapCards[" + index + "] #Seg" + seg + ".Background", accentColor);
            }

            // Update progress bar visibility based on speed level
            updateProgressBar(commandBuilder, index, speedLevel);

            // Update star visibility
            updateStarDisplay(commandBuilder, index, stars);

            // Level text for button zone
            String levelText = buildLevelText(stars, speedLevel);

            // Map name
            commandBuilder.set("#MapCards[" + index + "] #MapName.Text", mapName);

            // Status text (all displayed maps are unlocked)
            String status = "Runs/sec: " + formatRunsPerSecond(map, hasRobot, speedLevel, playerRef.getUuid());

            // Add personal best time if available
            if (mapProgress != null && mapProgress.getBestTimeMs() != null) {
                long bestTimeMs = mapProgress.getBestTimeMs();
                double bestTimeSec = bestTimeMs / 1000.0;
                status += " | PB: " + String.format("%.2fs", bestTimeSec);
            }

            commandBuilder.set("#MapCards[" + index + "] #MapStatus.Text", status);

            // Runner status and button text
            String runnerStatusText;
            String runnerButtonText;
            long actionPrice;
            if (!hasRobot) {
                runnerStatusText = "No Runner";
                if (!completedManually) {
                    runnerButtonText = "Complete First";
                    actionPrice = 0L;
                } else {
                    runnerButtonText = "Buy Runner";
                    actionPrice = 0L; // Buying a runner is now free
                }
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
                    actionPrice = computeUpgradeCost(speedLevel, map.getDisplayOrder());
                }
            }

            // Combine button text and price for "Upgrade" case to save vertical space
            String displayButtonText;
            String displayPriceText;
            if (runnerButtonText.equals("Upgrade") && actionPrice > 0) {
                displayButtonText = "Cost:";
                displayPriceText = FormatUtils.formatCoinsForHudDecimal(actionPrice) + " coins";
            } else {
                displayButtonText = runnerButtonText;
                // No price text for Buy Runner, Complete First, Maxed, or Evolve
                displayPriceText = "";
            }

            // Determine color for level text and button accent bar based on affordability
            String levelColor;
            boolean isUpgrade = runnerButtonText.equals("Upgrade") && actionPrice > 0;
            if (isUpgrade) {
                double currentCoins = playerStore.getCoins(playerRef.getUuid());
                boolean canAfford = currentCoins >= actionPrice;
                levelColor = canAfford ? accentColor : "#ffffff";
            } else {
                levelColor = accentColor;
            }

            // Apply color to button accent bar and level text
            commandBuilder.set("#MapCards[" + index + "] #ButtonAccent.Background", levelColor);
            commandBuilder.set("#MapCards[" + index + "] #RunnerLevel.Text", levelText);
            commandBuilder.set("#MapCards[" + index + "] #RunnerLevel.Style.TextColor", levelColor);
            commandBuilder.set("#MapCards[" + index + "] #RunnerStatus.Text", runnerStatusText);
            commandBuilder.set("#MapCards[" + index + "] #RobotBuyText.Text", displayButtonText);
            commandBuilder.set("#MapCards[" + index + "] #RobotPriceText.Text", displayPriceText);

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

    private void updateStarDisplay(UICommandBuilder cmd, int cardIndex, int stars) {
        // Show/hide star images based on evolution level (max 5 stars)
        for (int i = 1; i <= AscendConstants.MAX_ROBOT_STARS; i++) {
            boolean visible = i <= stars;
            cmd.set("#MapCards[" + cardIndex + "] #Star" + i + ".Visible", visible);
        }
    }

    private String buildLevelText(int stars, int speedLevel) {
        if (stars >= AscendConstants.MAX_ROBOT_STARS && speedLevel >= MAX_SPEED_LEVEL) {
            return "MAX";
        }
        return "Lv." + speedLevel;
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

    private String formatRunsPerSecond(AscendMap map, boolean hasRobot, int speedLevel, java.util.UUID playerId) {
        if (map == null || !hasRobot || playerId == null) {
            return "0";
        }
        // Use player's PB time as base (from ghost recording)
        GhostRecording ghost = ghostStore.getRecording(playerId, map.getId());
        if (ghost == null) {
            return "0";
        }
        long base = ghost.getCompletionTimeMs();
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

    /**
     * Compute runner speed upgrade cost using obscure quick-ramp formula with map-based scaling.
     * Formula: baseCost(level + mapOffset) × mapMultiplier
     * Where baseCost(L) = round(10 × 2.4^L + L × 6)
     */
    private long computeUpgradeCost(int currentLevel, int mapDisplayOrder) {
        // Get map-specific scaling parameters
        int offset = AscendConstants.getMapUpgradeOffset(mapDisplayOrder);
        double multiplier = AscendConstants.getMapUpgradeMultiplier(mapDisplayOrder);

        // Apply offset to level for base cost calculation
        int effectiveLevel = currentLevel + offset;

        // Obscure quick-ramp formula: 10 × 2.4^level + level × 6
        double baseCost = 10.0 * Math.pow(2.4, effectiveLevel) + (effectiveLevel * 6);

        // Apply map multiplier and round to long
        return Math.round(baseCost * multiplier);
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
            // Check if map has been completed manually
            if (!mapProgress.isCompletedManually()) {
                sendMessage(store, ref, "[Ascend] Complete the map manually before buying a runner!");
                return;
            }

            // Check if ghost recording exists
            GhostRecording ghost = ghostStore.getRecording(playerRef.getUuid(), mapId);
            if (ghost == null) {
                sendMessage(store, ref, "[Ascend] No ghost recording found. Complete the map again.");
                return;
            }

            // Buying a runner is now free
            playerStore.setHasRobot(playerRef.getUuid(), mapId, true);
            sendMessage(store, ref, "[Ascend] Runner purchased!");
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
            long upgradeCost = computeUpgradeCost(currentLevel, map.getDisplayOrder());
            if (!playerStore.spendCoins(playerRef.getUuid(), upgradeCost)) {
                sendMessage(store, ref, "[Ascend] Not enough coins to upgrade speed.");
                return;
            }
            int newLevel = playerStore.incrementRobotSpeedLevel(playerRef.getUuid(), mapId);
            int newSpeedPercent = newLevel * 10;
            sendMessage(store, ref, "[Ascend] Runner speed upgraded to +" + newSpeedPercent + "%!");

            // Check if new level unlocks next map
            if (newLevel == AscendConstants.MAP_UNLOCK_REQUIRED_RUNNER_LEVEL) {
                List<String> unlockedMapIds = playerStore.checkAndUnlockEligibleMaps(playerRef.getUuid(), mapStore);
                for (String unlockedMapId : unlockedMapIds) {
                    AscendMap unlockedMap = mapStore.getMap(unlockedMapId);
                    if (unlockedMap != null) {
                        String mapName = unlockedMap.getName() != null && !unlockedMap.getName().isBlank()
                            ? unlockedMap.getName()
                            : unlockedMap.getId();
                        Player player = store.getComponent(ref, Player.getComponentType());
                        if (player != null) {
                            player.sendMessage(Message.raw("🎉 New map unlocked: " + mapName + "!"));
                        }

                        // Add map to UI immediately
                        if (isCurrentPage() && ref.isValid()) {
                            addMapToUI(ref, store, unlockedMap);
                        }
                    }
                }
            }

            updateRobotRow(ref, store, mapId);
        }
    }

    private void startAffordabilityUpdates(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (refreshTask != null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        // Update affordability colors every 500ms to reflect coin changes
        refreshTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            if (!isCurrentPage()) {
                stopAffordabilityUpdates();
                return;
            }
            if (ref == null || !ref.isValid()) {
                stopAffordabilityUpdates();
                return;
            }
            try {
                CompletableFuture.runAsync(() -> updateAffordabilityColors(ref, store), world);
            } catch (Exception e) {
                stopAffordabilityUpdates();
            }
        }, 500L, 500L, TimeUnit.MILLISECONDS);
    }

    private void stopAffordabilityUpdates() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
    }

    private void updateAffordabilityColors(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!isCurrentPage()) {
            stopAffordabilityUpdates();
            return;
        }
        try {
            doUpdateAffordabilityColors(ref, store);
        } catch (Exception e) {
            // Page was replaced, stop refreshing
            stopAffordabilityUpdates();
        }
    }

    private void doUpdateAffordabilityColors(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        // Filter maps to only include unlocked ones
        List<AscendMap> allMaps = new ArrayList<>(mapStore.listMapsSorted());
        List<AscendMap> maps = new ArrayList<>();
        for (AscendMap map : allMaps) {
            MapUnlockHelper.UnlockResult unlockResult = MapUnlockHelper.checkAndEnsureUnlock(
                playerRef.getUuid(), map, playerStore, mapStore);
            if (unlockResult.unlocked) {
                maps.add(map);
            }
        }

        // Don't send updates if no maps exist (UI elements wouldn't exist)
        if (maps.isEmpty() || maps.size() != lastMapCount) {
            return;
        }

        double currentCoins = playerStore.getCoins(playerRef.getUuid());
        UICommandBuilder commandBuilder = new UICommandBuilder();

        for (int i = 0; i < maps.size(); i++) {
            AscendMap map = maps.get(i);
            if (map == null) {
                continue;
            }

            String accentColor = resolveMapAccentColor(i);
            AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerRef.getUuid(), map.getId());
            boolean hasRobot = mapProgress != null && mapProgress.hasRobot();
            int speedLevel = mapProgress != null ? mapProgress.getRobotSpeedLevel() : 0;
            int stars = mapProgress != null ? mapProgress.getRobotStars() : 0;

            // Only update affordability for maps with runners that can be upgraded
            if (!hasRobot) {
                continue; // No runner = no upgrade button
            }
            if (speedLevel >= MAX_SPEED_LEVEL && stars >= AscendConstants.MAX_ROBOT_STARS) {
                continue; // Maxed out = no upgrade button
            }
            if (speedLevel >= MAX_SPEED_LEVEL) {
                continue; // At max speed but not max stars = evolve button, not upgrade
            }

            // This map has an upgrade button, check affordability
            long upgradeCost = computeUpgradeCost(speedLevel, map.getDisplayOrder());
            boolean canAfford = currentCoins >= upgradeCost;
            String levelColor = canAfford ? accentColor : "#ffffff";

            // Update only the color properties
            commandBuilder.set("#MapCards[" + i + "] #ButtonAccent.Background", levelColor);
            commandBuilder.set("#MapCards[" + i + "] #RunnerLevel.Style.TextColor", levelColor);
        }

        if (!isCurrentPage()) {
            return;
        }
        sendUpdate(commandBuilder, null, false);
    }

    private void updateRobotRow(Ref<EntityStore> ref, Store<EntityStore> store, String mapId) {
        if (!isCurrentPage()) {
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
        // All displayed maps are unlocked (filtered in buildMapList)
        AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerRef.getUuid(), selectedMap.getId());
        boolean hasRobot = mapProgress != null && mapProgress.hasRobot();
        boolean completedManually = mapProgress != null && mapProgress.isCompletedManually();
        int speedLevel = mapProgress != null ? mapProgress.getRobotSpeedLevel() : 0;
        int stars = mapProgress != null ? mapProgress.getRobotStars() : 0;

        String status = "Runs/sec: " + formatRunsPerSecond(selectedMap, hasRobot, speedLevel, playerRef.getUuid());

        // Add personal best time if available
        if (mapProgress != null && mapProgress.getBestTimeMs() != null) {
            long bestTimeMs = mapProgress.getBestTimeMs();
            double bestTimeSec = bestTimeMs / 1000.0;
            status += " | PB: " + String.format("%.2fs", bestTimeSec);
        }

        String runnerStatusText;
        String runnerButtonText;
        long actionPrice;
        if (!hasRobot) {
            runnerStatusText = "No Runner";
            if (!completedManually) {
                runnerButtonText = "Complete First";
                actionPrice = 0L;
            } else {
                runnerButtonText = "Buy Runner";
                actionPrice = 0L; // Buying a runner is now free
            }
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
                actionPrice = computeUpgradeCost(speedLevel, selectedMap.getDisplayOrder());
            }
        }

        // Combine button text and price for "Upgrade" case to save vertical space
        String displayButtonText;
        String displayPriceText;
        if (runnerButtonText.equals("Upgrade") && actionPrice > 0) {
            displayButtonText = "Cost:";
            displayPriceText = FormatUtils.formatCoinsForHudDecimal(actionPrice) + " coins";
        } else {
            displayButtonText = runnerButtonText;
            // No price text for Buy Runner, Complete First, Maxed, or Evolve
            displayPriceText = "";
        }

        UICommandBuilder commandBuilder = new UICommandBuilder();

        // Update progress bar
        updateProgressBar(commandBuilder, index, speedLevel);

        // Update star visibility
        updateStarDisplay(commandBuilder, index, stars);

        // Update level text in button zone
        String levelText = buildLevelText(stars, speedLevel);

        // Determine color for level text and button accent bar based on affordability
        String accentColor = resolveMapAccentColor(index);
        String levelColor;
        boolean isUpgrade = runnerButtonText.equals("Upgrade") && actionPrice > 0;
        if (isUpgrade) {
            double currentCoins = playerStore.getCoins(playerRef.getUuid());
            boolean canAfford = currentCoins >= actionPrice;
            levelColor = canAfford ? accentColor : "#ffffff";
        } else {
            levelColor = accentColor;
        }

        // Apply accent color to progress bar segments (always use accent color)
        for (int seg = 1; seg <= MAX_SPEED_LEVEL; seg++) {
            commandBuilder.set("#MapCards[" + index + "] #Seg" + seg + ".Background", accentColor);
        }

        // Apply color to button accent bar and level text
        commandBuilder.set("#MapCards[" + index + "] #ButtonAccent.Background", levelColor);
        commandBuilder.set("#MapCards[" + index + "] #RunnerLevel.Text", levelText);
        commandBuilder.set("#MapCards[" + index + "] #RunnerLevel.Style.TextColor", levelColor);

        // Update text fields
        commandBuilder.set("#MapCards[" + index + "] #RunnerStatus.Text", runnerStatusText);
        commandBuilder.set("#MapCards[" + index + "] #RobotBuyText.Text", displayButtonText);
        commandBuilder.set("#MapCards[" + index + "] #RobotPriceText.Text", displayPriceText);
        commandBuilder.set("#MapCards[" + index + "] #MapStatus.Text", status);

        if (!isCurrentPage()) {
            return;
        }
        sendUpdate(commandBuilder, null, false);
    }

    /**
     * Adds a newly unlocked map to the UI dynamically.
     * This method is called when a runner reaches level 3 and unlocks the next map.
     */
    private void addMapToUI(Ref<EntityStore> ref, Store<EntityStore> store, AscendMap map) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();

        // Use current lastMapCount as the index for the new map
        int index = lastMapCount;
        String mapName = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();
        String accentColor = resolveMapAccentColor(index);

        // Get map progress (newly unlocked, so might be minimal data)
        AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerRef.getUuid(), map.getId());
        boolean hasRobot = mapProgress != null && mapProgress.hasRobot();
        boolean completedManually = mapProgress != null && mapProgress.isCompletedManually();
        int speedLevel = mapProgress != null ? mapProgress.getRobotSpeedLevel() : 0;
        int stars = mapProgress != null ? mapProgress.getRobotStars() : 0;

        // Append new map card
        commandBuilder.append("#MapCards", "Pages/Ascend_MapSelectEntry.ui");

        // Apply accent color to left accent bar
        commandBuilder.set("#MapCards[" + index + "] #AccentBar.Background", accentColor);

        // Apply accent color to progress bar segments (always use accent color)
        for (int seg = 1; seg <= MAX_SPEED_LEVEL; seg++) {
            commandBuilder.set("#MapCards[" + index + "] #Seg" + seg + ".Background", accentColor);
        }

        // Update progress bar visibility
        updateProgressBar(commandBuilder, index, speedLevel);

        // Update star visibility
        updateStarDisplay(commandBuilder, index, stars);

        // Level text for button zone
        String levelText = buildLevelText(stars, speedLevel);

        // Map name
        commandBuilder.set("#MapCards[" + index + "] #MapName.Text", mapName);

        // Status text
        String status = "Runs/sec: " + formatRunsPerSecond(map, hasRobot, speedLevel, playerRef.getUuid());
        if (mapProgress != null && mapProgress.getBestTimeMs() != null) {
            long bestTimeMs = mapProgress.getBestTimeMs();
            double bestTimeSec = bestTimeMs / 1000.0;
            status += " | PB: " + String.format("%.2fs", bestTimeSec);
        }
        commandBuilder.set("#MapCards[" + index + "] #MapStatus.Text", status);

        // Runner status and button text
        String runnerStatusText;
        String runnerButtonText;
        long actionPrice;
        if (!hasRobot) {
            runnerStatusText = "No Runner";
            if (!completedManually) {
                runnerButtonText = "Complete First";
                actionPrice = 0L;
            } else {
                runnerButtonText = "Buy Runner";
                actionPrice = 0L; // Buying a runner is now free
            }
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
                actionPrice = computeUpgradeCost(speedLevel, map.getDisplayOrder());
            }
        }

        // Combine button text and price for "Upgrade" case to save vertical space
        String displayButtonText;
        String displayPriceText;
        if (runnerButtonText.equals("Upgrade") && actionPrice > 0) {
            displayButtonText = "Cost:";
            displayPriceText = FormatUtils.formatCoinsForHudDecimal(actionPrice) + " coins";
        } else {
            displayButtonText = runnerButtonText;
            // No price text for Buy Runner, Complete First, Maxed, or Evolve
            displayPriceText = "";
        }

        // Determine color for level text and button accent bar based on affordability
        String levelColor;
        boolean isUpgrade = runnerButtonText.equals("Upgrade") && actionPrice > 0;
        if (isUpgrade) {
            double currentCoins = playerStore.getCoins(playerRef.getUuid());
            boolean canAfford = currentCoins >= actionPrice;
            levelColor = canAfford ? accentColor : "#ffffff";
        } else {
            levelColor = accentColor;
        }

        // Apply color to button accent bar and level text
        commandBuilder.set("#MapCards[" + index + "] #ButtonAccent.Background", levelColor);
        commandBuilder.set("#MapCards[" + index + "] #RunnerLevel.Text", levelText);
        commandBuilder.set("#MapCards[" + index + "] #RunnerLevel.Style.TextColor", levelColor);
        commandBuilder.set("#MapCards[" + index + "] #RunnerStatus.Text", runnerStatusText);
        commandBuilder.set("#MapCards[" + index + "] #RobotBuyText.Text", displayButtonText);
        commandBuilder.set("#MapCards[" + index + "] #RobotPriceText.Text", displayPriceText);

        // Event bindings
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
            "#MapCards[" + index + "] #SelectButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + map.getId()), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
            "#MapCards[" + index + "] #RobotBuyButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ROBOT_PREFIX + map.getId()), false);

        // Increment map count
        lastMapCount++;

        // Send update to client
        if (isCurrentPage()) {
            sendUpdate(commandBuilder, eventBuilder, false);
        }
    }

    private void sendMessage(Store<EntityStore> store, Ref<EntityStore> ref, String text) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(text));
        }
    }

    private void handleBuyAll(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        // Collect all affordable purchases sorted by price (cheapest first)
        List<PurchaseOption> options = new ArrayList<>();
        List<AscendMap> maps = mapStore.listMapsSorted();

        for (AscendMap map : maps) {
            MapUnlockHelper.UnlockResult unlockResult = MapUnlockHelper.checkAndEnsureUnlock(
                playerRef.getUuid(), map, playerStore, mapStore);
            if (!unlockResult.unlocked) {
                continue;
            }

            AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerRef.getUuid(), map.getId());
            boolean hasRobot = mapProgress != null && mapProgress.hasRobot();
            boolean completedManually = mapProgress != null && mapProgress.isCompletedManually();
            int speedLevel = mapProgress != null ? mapProgress.getRobotSpeedLevel() : 0;
            int stars = mapProgress != null ? mapProgress.getRobotStars() : 0;

            if (!hasRobot) {
                // Can buy robot if completed manually and has ghost
                if (completedManually) {
                    GhostRecording ghost = ghostStore.getRecording(playerRef.getUuid(), map.getId());
                    if (ghost != null) {
                        // Buying a runner is now free
                        options.add(new PurchaseOption(map.getId(), PurchaseType.BUY_ROBOT, 0L));
                    }
                }
            } else {
                // Can upgrade speed if not at max level
                if (speedLevel >= MAX_SPEED_LEVEL) {
                    // At max speed level, skip (evolution handled by Evolve All)
                    continue;
                }
                long upgradeCost = computeUpgradeCost(speedLevel);
                options.add(new PurchaseOption(map.getId(), PurchaseType.UPGRADE_SPEED, upgradeCost));
            }
        }

        if (options.isEmpty()) {
            sendMessage(store, ref, "[Ascend] No upgrades available.");
            return;
        }

        // Sort by price (cheapest first) to maximize number of purchases
        options.sort((a, b) -> Long.compare(a.price, b.price));

        // Process purchases
        int purchased = 0;
        long totalSpent = 0;
        List<String> updatedMapIds = new ArrayList<>();

        for (PurchaseOption option : options) {
            double currentCoins = playerStore.getCoins(playerRef.getUuid());
            if (option.price > currentCoins) {
                continue;
            }

            boolean success = false;
            switch (option.type) {
                case BUY_ROBOT -> {
                    if (option.price > 0) {
                        if (!playerStore.spendCoins(playerRef.getUuid(), option.price)) {
                            continue;
                        }
                    }
                    playerStore.setHasRobot(playerRef.getUuid(), option.mapId, true);
                    success = true;
                }
                case UPGRADE_SPEED -> {
                    if (!playerStore.spendCoins(playerRef.getUuid(), option.price)) {
                        continue;
                    }
                    int newLevel = playerStore.incrementRobotSpeedLevel(playerRef.getUuid(), option.mapId);
                    // Check if new level unlocks next map
                    if (newLevel == AscendConstants.MAP_UNLOCK_REQUIRED_RUNNER_LEVEL) {
                        playerStore.checkAndUnlockEligibleMaps(playerRef.getUuid(), mapStore);
                    }
                    success = true;
                }
            }

            if (success) {
                purchased++;
                totalSpent += option.price;
                if (!updatedMapIds.contains(option.mapId)) {
                    updatedMapIds.add(option.mapId);
                }
            }
        }

        if (purchased == 0) {
            sendMessage(store, ref, "[Ascend] Not enough coins for any upgrade.");
            return;
        }

        String costText = totalSpent > 0 ? " for " + FormatUtils.formatCoinsForHud(totalSpent) + " coins" : "";
        sendMessage(store, ref, "[Ascend] Purchased " + purchased + " upgrade" + (purchased > 1 ? "s" : "") + costText + "!");

        // Update UI for all affected maps
        for (String mapId : updatedMapIds) {
            updateRobotRow(ref, store, mapId);
        }
    }

    private enum PurchaseType {
        BUY_ROBOT,
        UPGRADE_SPEED
    }

    private record PurchaseOption(String mapId, PurchaseType type, long price) {}

    private void handleEvolveAll(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        // Find all runners that can be evolved (max speed level but not max stars)
        List<String> eligibleMapIds = new ArrayList<>();
        List<AscendMap> maps = mapStore.listMapsSorted();

        for (AscendMap map : maps) {
            MapUnlockHelper.UnlockResult unlockResult = MapUnlockHelper.checkAndEnsureUnlock(
                playerRef.getUuid(), map, playerStore, mapStore);
            if (!unlockResult.unlocked) {
                continue;
            }

            AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerRef.getUuid(), map.getId());
            if (mapProgress == null || !mapProgress.hasRobot()) {
                continue;
            }

            int speedLevel = mapProgress.getRobotSpeedLevel();
            int stars = mapProgress.getRobotStars();

            // Can evolve if at max speed level but not at max stars
            if (speedLevel >= MAX_SPEED_LEVEL && stars < AscendConstants.MAX_ROBOT_STARS) {
                eligibleMapIds.add(map.getId());
            }
        }

        if (eligibleMapIds.isEmpty()) {
            sendMessage(store, ref, "[Ascend] No runners ready for evolution.");
            return;
        }

        // Evolve all eligible runners
        int evolved = 0;
        for (String mapId : eligibleMapIds) {
            int newStars = playerStore.evolveRobot(playerRef.getUuid(), mapId);
            if (robotManager != null) {
                robotManager.respawnRobot(playerRef.getUuid(), mapId, newStars);
            }
            evolved++;
            updateRobotRow(ref, store, mapId);
        }

        sendMessage(store, ref, "[Ascend] Evolved " + evolved + " runner" + (evolved > 1 ? "s" : "") + "!");
    }

    private void handleOpenStats(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
            new StatsPage(playerRef, playerStore, mapStore, ghostStore));
    }

    private boolean ensureUnlocked(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef,
                                   String mapId, AscendMap map) {
        // All displayed maps are already unlocked due to filtering in buildMapList
        AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerRef.getUuid());
        AscendPlayerProgress.MapProgress mapProgress = progress.getOrCreateMapProgress(mapId);

        // Safety check: if not unlocked, mark as unlocked (should not happen)
        if (!mapProgress.isUnlocked()) {
            mapProgress.setUnlocked(true);
            playerStore.markDirty(playerRef.getUuid());
        }
        return true;
    }
}
