package io.hyvexa.ascend.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

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
import io.hyvexa.common.ghost.GhostRecording;
import io.hyvexa.common.ghost.GhostStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.ascend.tracker.AscendRunTracker;
import io.hyvexa.ascend.util.AscendInventoryUtils;
import io.hyvexa.ascend.util.MapUnlockHelper;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.hud.ToastType;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;

public class AscendMapSelectPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_SELECT_PREFIX = "Select:";
    private static final String BUTTON_ROBOT_PREFIX = "Robot:";
    private static final String BUTTON_BUY_ALL = "BuyAll";
    private static final String BUTTON_BUY_ALL_DISABLED = "BuyAllDisabled";
    private static final String BUTTON_EVOLVE_ALL = "EvolveAll";
    private static final String BUTTON_EVOLVE_ALL_DISABLED = "EvolveAllDisabled";
    private static final String BUTTON_LEADERBOARD = "Leaderboard";
    private static final String BUTTON_CHALLENGE_TAB = "ChallengeTab";
    private static final int MAX_SPEED_LEVEL = 20;
    private static final long BUY_ALL_COOLDOWN_MS = 100L; // 100ms cooldown to prevent race conditions while allowing satisfying spam clicks

    private static final Map<UUID, Long> lastBuyAllClick = new ConcurrentHashMap<>();

    public static void clearBuyAllCooldown(UUID playerId) {
        lastBuyAllClick.remove(playerId);
    }

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final AscendRunTracker runTracker;
    private final RobotManager robotManager;
    private final GhostStore ghostStore;
    private ScheduledFuture<?> refreshTask;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final AtomicBoolean refreshRequested = new AtomicBoolean(false);
    private int lastMapCount;
    private final List<String> displayedMapIds = new ArrayList<>();
    private final Map<String, int[]> cachedMapState = new HashMap<>(); // mapId -> [speedLevel, stars]


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
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyAllOverlay",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_BUY_ALL_DISABLED), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ActionButton2",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_EVOLVE_ALL), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#EvolveAllOverlay",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_EVOLVE_ALL_DISABLED), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ActionButton3",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_LEADERBOARD), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabLockedA",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CHALLENGE_TAB), false);

        // Check if Challenge tab should be unlocked
        PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (pRef != null) {
            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin != null && plugin.getAscensionManager() != null
                    && plugin.getAscensionManager().hasAscensionChallenges(pRef.getUuid())) {
                uiCommandBuilder.set("#TabUnlockedBg.Visible", true);
                uiCommandBuilder.set("#TabLockedAContent.Visible", false);
                uiCommandBuilder.set("#TabChallengeLabel.Visible", true);
            }
        }

        RefreshSnapshot refreshSnapshot = buildMapList(ref, store, uiCommandBuilder, uiEventBuilder);

        // Set initial button states
        if (refreshSnapshot != null) {
            updateBuyAllButtonState(uiCommandBuilder, refreshSnapshot);
            updateEvolveAllButtonState(uiCommandBuilder, refreshSnapshot);
        }

        // Start affordability color updates (vexa changes frequently from passive income)
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
        if (BUTTON_BUY_ALL_DISABLED.equals(data.getButton())) {
            return;
        }
        if (BUTTON_EVOLVE_ALL.equals(data.getButton())) {
            handleEvolveAll(ref, store);
            return;
        }
        if (BUTTON_EVOLVE_ALL_DISABLED.equals(data.getButton())) {
            return;
        }
        if (BUTTON_LEADERBOARD.equals(data.getButton())) {
            handleOpenLeaderboard(ref, store);
            return;
        }
        if (BUTTON_CHALLENGE_TAB.equals(data.getButton())) {
            handleOpenChallenge(ref, store);
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
        if (!isDisplayedMapId(mapId)) {
            sendMessage(store, ref, "[Ascend] Invalid map selection.");
            return;
        }
        AscendMap map = mapStore.getMap(mapId);
        if (map == null) {
            sendMessage(store, ref, "Map not found.");
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        if (!ensureUnlocked(store, ref, playerRef, map)) {
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
            showToast(playerRef.getUuid(), ToastType.INFO, "Ready: " + mapName + " - Move to start!");
            // Give run items (reset + leave)
            AscendInventoryUtils.giveRunItems(player);
        }
        this.close();
    }

    @Override
    public void close() {
        stopAffordabilityUpdates();
        super.close();
    }

    @Override
    protected void stopBackgroundTasks() {
        stopAffordabilityUpdates();
    }

    private RefreshSnapshot buildMapList(Ref<EntityStore> ref, Store<EntityStore> store,
                                         UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#MapCards");
        displayedMapIds.clear();
        cachedMapState.clear();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return null;
        }

        RefreshSnapshot refreshSnapshot = buildRefreshSnapshot(playerRef.getUuid());
        List<MapRefreshState> maps = refreshSnapshot.mapStates();
        lastMapCount = maps.size();
        BigNumber currentVexa = refreshSnapshot.currentVexa();
        int index = 0;
        for (MapRefreshState mapState : maps) {
            AscendMap map = mapState.map();
            commandBuilder.append("#MapCards", "Pages/Ascend_MapSelectEntry.ui");
            String mapName = map.getName() != null && !map.getName().isBlank() ? map.getName() : map.getId();

            AscendPlayerProgress.MapProgress mapProgress = mapState.mapProgress();

            // Apply accent color to left accent bar
            applyAccentBarVariant(commandBuilder, index, index);

            // Map name
            commandBuilder.set("#MapCards[" + index + "] #MapName.Text", mapName);

            RunnerCardSnapshot snapshot = renderRunnerButton(
                commandBuilder, index, map, mapProgress, playerRef.getUuid(), currentVexa);

            // Event bindings
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#MapCards[" + index + "] #SelectButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + map.getId()), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#MapCards[" + index + "] #RobotBuyButton",
                EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ROBOT_PREFIX + map.getId()), false);

            displayedMapIds.add(map.getId());
            cachedMapState.put(map.getId(), new int[]{snapshot.speedLevel(), snapshot.stars()});
            index++;
        }
        return refreshSnapshot;
    }

    private RefreshSnapshot buildRefreshSnapshot(UUID playerId) {
        BigNumber currentVexa = playerStore.getVexa(playerId);
        List<MapRefreshState> mapStates = new ArrayList<>();
        boolean hasAvailableBuyAll = false;
        boolean hasEligibleEvolution = false;

        for (AscendMap map : mapStore.listMapsSorted()) {
            if (map == null) {
                continue;
            }
            AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerId, map.getId());
            if (!isMapUnlockedForDisplay(playerId, map, mapProgress)) {
                continue;
            }
            boolean hasRobot = mapProgress != null && mapProgress.hasRobot();
            int speedLevel = mapProgress != null ? mapProgress.getRobotSpeedLevel() : 0;
            int stars = mapProgress != null ? mapProgress.getRobotStars() : 0;
            boolean momentumActive = mapProgress != null && mapProgress.isMomentumActive();
            boolean canAffordUpgrade = false;

            if (hasRobot && speedLevel < MAX_SPEED_LEVEL) {
                BigNumber upgradeCost = computeUpgradeCost(speedLevel, map.getDisplayOrder(), stars);
                canAffordUpgrade = currentVexa.gte(upgradeCost);
            }

            boolean canBuyAll = (!hasRobot && ghostStore.getRecording(playerId, map.getId()) != null)
                    || (hasRobot && speedLevel < MAX_SPEED_LEVEL && canAffordUpgrade);
            boolean canEvolve = hasRobot
                    && speedLevel >= MAX_SPEED_LEVEL
                    && stars < AscendConstants.MAX_ROBOT_STARS;

            hasAvailableBuyAll |= canBuyAll;
            hasEligibleEvolution |= canEvolve;

            mapStates.add(new MapRefreshState(
                    map,
                    mapProgress,
                    hasRobot,
                    speedLevel,
                    stars,
                    momentumActive,
                    canAffordUpgrade
            ));
        }

        return new RefreshSnapshot(currentVexa, mapStates, hasAvailableBuyAll, hasEligibleEvolution);
    }

    private boolean isMapUnlockedForDisplay(UUID playerId, AscendMap map, AscendPlayerProgress.MapProgress mapProgress) {
        if (MapUnlockHelper.isUnlocked(mapProgress, map)) {
            return true;
        }
        return MapUnlockHelper.meetsUnlockRequirement(playerId, map, playerStore, mapStore);
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
            case 1 -> "#ef4444";  // Red
            case 2 -> "#f59e0b";  // Orange
            case 3 -> "#10b981";  // Green
            default -> "#3b82f6"; // Blue
        };
    }

    private void applyAccentBarVariant(UICommandBuilder commandBuilder, int cardIndex, int mapIndex) {
        boolean violet = mapIndex == 0;
        boolean red = mapIndex == 1;
        boolean orange = mapIndex == 2;
        boolean green = mapIndex == 3;
        boolean blue = mapIndex >= 4;
        String basePath = "#MapCards[" + cardIndex + "] #AccentBar";
        commandBuilder.set(basePath + " #AccentViolet.Visible", violet);
        commandBuilder.set(basePath + " #AccentRed.Visible", red);
        commandBuilder.set(basePath + " #AccentOrange.Visible", orange);
        commandBuilder.set(basePath + " #AccentGreen.Visible", green);
        commandBuilder.set(basePath + " #AccentBlue.Visible", blue);
    }

    private boolean isDisplayedMapId(String mapId) {
        return mapId != null && displayedMapIds.contains(mapId);
    }

    private String resolveButtonBgElementId(int index) {
        return switch (index) {
            case 0 -> "#ButtonBgViolet";
            case 1 -> "#ButtonBgRed";
            case 2 -> "#ButtonBgOrange";
            case 3 -> "#ButtonBgGreen";
            default -> "#ButtonBgBlue";
        };
    }

    private RunnerCardSnapshot renderRunnerButton(UICommandBuilder commandBuilder, int index, AscendMap map,
                                                  AscendPlayerProgress.MapProgress mapProgress, UUID playerId,
                                                  BigNumber currentVexa) {
        boolean hasRobot = mapProgress != null && mapProgress.hasRobot();
        boolean hasGhostRecording = ghostStore.getRecording(playerId, map.getId()) != null;
        int speedLevel = mapProgress != null ? mapProgress.getRobotSpeedLevel() : 0;
        int stars = mapProgress != null ? mapProgress.getRobotStars() : 0;

        String accentColor = resolveMapAccentColor(index);
        for (int seg = 1; seg <= MAX_SPEED_LEVEL; seg++) {
            commandBuilder.set("#MapCards[" + index + "] #Seg" + seg + ".Background", accentColor);
        }
        updateProgressBar(commandBuilder, index, speedLevel);
        updateStarDisplay(commandBuilder, index, stars);

        boolean momentumActive = mapProgress != null && mapProgress.isMomentumActive();
        commandBuilder.set("#MapCards[" + index + "] #MomentumLabel.Visible", momentumActive);
        if (momentumActive) {
            commandBuilder.set("#MapCards[" + index + "] #MomentumLabel.Text",
                buildMomentumText(mapProgress, playerId));
        }
        commandBuilder.set("#MapCards[" + index + "] " + resolveButtonBgElementId(index) + ".Visible", true);
        commandBuilder.set("#MapCards[" + index + "] #MapStatus.Text",
            buildMapStatusText(map, hasRobot, speedLevel, stars, playerId));

        String levelText = buildLevelText(stars, speedLevel);
        commandBuilder.set("#MapCards[" + index + "] #RunnerLevel.Text", levelText);
        commandBuilder.set("#MapCards[" + index + "] #RunnerLevel.Style.TextColor", "#ffffff");

        RunnerStatusData runnerStatus = resolveRunnerStatusData(map, hasRobot, hasGhostRecording, speedLevel, stars, playerId);
        BigNumber vexa = currentVexa != null
            ? currentVexa
            : (playerId != null ? playerStore.getVexa(playerId) : BigNumber.ZERO);
        boolean canAfford = !runnerStatus.isUpgrade() || vexa.gte(runnerStatus.actionPrice());
        boolean showDisabledOverlay = runnerStatus.isUpgrade() && !canAfford;
        String secondaryTextColor = canAfford ? "#ffffff" : "#9fb0ba";

        commandBuilder.set("#MapCards[" + index + "] #ButtonDisabledOverlay.Visible", showDisabledOverlay);
        commandBuilder.set("#MapCards[" + index + "] #RunnerStatus.Text", runnerStatus.runnerStatusText());
        commandBuilder.set("#MapCards[" + index + "] #RunnerStatus.Style.TextColor", secondaryTextColor);
        commandBuilder.set("#MapCards[" + index + "] #RobotBuyText.Text", runnerStatus.displayButtonText());
        commandBuilder.set("#MapCards[" + index + "] #RobotBuyText.Style.TextColor", secondaryTextColor);
        commandBuilder.set("#MapCards[" + index + "] #RobotPriceText.Text", runnerStatus.displayPriceText());
        commandBuilder.set("#MapCards[" + index + "] #RobotPriceText.Style.TextColor", secondaryTextColor);

        return new RunnerCardSnapshot(speedLevel, stars);
    }

    private RunnerStatusData resolveRunnerStatusData(AscendMap map, boolean hasRobot, boolean hasGhostRecording,
                                                     int speedLevel, int stars, UUID playerId) {
        String runnerStatusText;
        String runnerButtonText;
        BigNumber actionPrice;
        if (!hasRobot) {
            runnerStatusText = "No Runner";
            if (!hasGhostRecording) {
                runnerButtonText = "Complete First";
                actionPrice = BigNumber.ZERO;
            } else {
                runnerButtonText = "Buy Runner";
                actionPrice = BigNumber.ZERO;
            }
        } else {
            int speedGainPercent = (int) (AscendConstants.getMapSpeedMultiplier(map.getDisplayOrder()) * 100);
            runnerStatusText = "+" + speedGainPercent + "% speed/lvl";
            if (speedLevel >= MAX_SPEED_LEVEL && stars < AscendConstants.MAX_ROBOT_STARS) {
                runnerButtonText = "Evolve";
                actionPrice = BigNumber.ZERO;
                runnerStatusText = formatEvolveGain(stars, playerId);
            } else if (stars >= AscendConstants.MAX_ROBOT_STARS && speedLevel >= MAX_SPEED_LEVEL) {
                runnerButtonText = "Maxed!";
                actionPrice = BigNumber.ZERO;
            } else {
                runnerButtonText = "Upgrade";
                actionPrice = computeUpgradeCost(speedLevel, map.getDisplayOrder(), stars);
            }
        }

        boolean isUpgrade = "Upgrade".equals(runnerButtonText) && actionPrice.gt(BigNumber.ZERO);
        String displayButtonText = isUpgrade ? "Cost:" : runnerButtonText;
        String displayPriceText = isUpgrade ? FormatUtils.formatBigNumber(actionPrice) + " vexa" : "";
        return new RunnerStatusData(runnerStatusText, displayButtonText, displayPriceText, actionPrice, isUpgrade);
    }

    private String buildMapStatusText(AscendMap map, boolean hasRobot, int speedLevel, int stars, UUID playerId) {
        String status = "Run: " + formatRunTime(map, hasRobot, speedLevel, playerId);
        if (hasRobot) {
            status += " (" + formatMultiplierGain(stars, playerId) + ")";
        }
        GhostRecording ghostForPb = ghostStore.getRecording(playerId, map.getId());
        if (ghostForPb != null && ghostForPb.getCompletionTimeMs() > 0) {
            long bestTimeMs = ghostForPb.getCompletionTimeMs();
            double bestTimeSec = bestTimeMs / 1000.0;
            status += " | PB: " + String.format("%.2fs", bestTimeSec);
        }
        return status;
    }

    private String formatRunTime(AscendMap map, boolean hasRobot, int speedLevel, java.util.UUID playerId) {
        if (map == null || !hasRobot || playerId == null) {
            return "-";
        }
        // Use player's PB time as base (from ghost recording)
        GhostRecording ghost = ghostStore.getRecording(playerId, map.getId());
        if (ghost == null) {
            return "-";
        }
        long base = ghost.getCompletionTimeMs();
        if (base <= 0L) {
            return "-";
        }
        // Use full speed multiplier calculation (includes Summit and Ascension bonuses)
        double speedMultiplier = RobotManager.calculateSpeedMultiplier(map, speedLevel, playerId);
        long intervalMs = (long) (base / speedMultiplier);
        intervalMs = Math.max(1L, intervalMs);
        double seconds = intervalMs / 1000.0;
        return String.format(Locale.US, "%.2fs", seconds);
    }

    private String formatMultiplierGain(int stars, java.util.UUID playerId) {
        // Get Summit bonuses (Multiplier Gain + Evolution Power)
        double multiplierGainBonus = 1.0;
        double evolutionPowerBonus = 3.0;
        double baseMultiplierBonus = 0.0;
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            SummitManager summitManager = plugin.getSummitManager();
            if (summitManager != null && playerId != null) {
                multiplierGainBonus = summitManager.getMultiplierGainBonus(playerId);
                evolutionPowerBonus = summitManager.getEvolutionPowerBonus(playerId);
                baseMultiplierBonus = summitManager.getBaseMultiplierBonus(playerId);
            }
        }
        BigNumber increment = AscendConstants.getRunnerMultiplierIncrement(stars, multiplierGainBonus, evolutionPowerBonus, baseMultiplierBonus);
        // Format: show 2 decimals for values under 1, 1 decimal for 1-10, integer for 10+
        double val = increment.toDouble();
        if (val < 1.0) {
            return String.format(Locale.US, "+%.2fx", val);
        } else if (val < 10.0) {
            return String.format(Locale.US, "+%.1fx", val);
        } else {
            return String.format(Locale.US, "+%.0fx", val);
        }
    }

    /**
     * Format the evolve multiplier gain to show what the runner will earn after evolving.
     * Shows current → next multiplier increment per run.
     */
    private String formatEvolveGain(int stars, java.util.UUID playerId) {
        double multiplierGainBonus = 1.0;
        double evolutionPowerBonus = 3.0;
        double baseMultiplierBonus = 0.0;
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            SummitManager summitManager = plugin.getSummitManager();
            if (summitManager != null && playerId != null) {
                multiplierGainBonus = summitManager.getMultiplierGainBonus(playerId);
                evolutionPowerBonus = summitManager.getEvolutionPowerBonus(playerId);
                baseMultiplierBonus = summitManager.getBaseMultiplierBonus(playerId);
            }
        }
        BigNumber nextIncrement = AscendConstants.getRunnerMultiplierIncrement(stars + 1, multiplierGainBonus, evolutionPowerBonus, baseMultiplierBonus);
        double val = nextIncrement.toDouble();
        String formatted;
        if (val < 1.0) {
            formatted = String.format(Locale.US, "%.2fx", val);
        } else if (val < 10.0) {
            formatted = String.format(Locale.US, "%.1fx", val);
        } else {
            formatted = String.format(Locale.US, "%.0fx", val);
        }
        return "-> +" + formatted + "/run";
    }

    /**
     * Compute runner speed upgrade cost using obscure quick-ramp formula with map-based scaling.
     * Formula: baseCost(level + mapOffset) × mapMultiplier × starMultiplier
     * Where baseCost(L) = round(20 × 2.4^L + L × 12) [×2 base inflation]
     * Star multiplier: ×2.2 per star (slightly inefficient evolution, ratio 1.1→1.6)
     * Early game boost: levels 0-4 cost ÷4 for smooth first 2-3 minutes
     */
    private BigNumber computeUpgradeCost(int currentLevel, int mapDisplayOrder, int stars) {
        return AscendConstants.getRunnerUpgradeCost(currentLevel, mapDisplayOrder, stars);
    }

    private void handleRobotAction(Ref<EntityStore> ref, Store<EntityStore> store, String mapId) {
        if (!isDisplayedMapId(mapId)) {
            sendMessage(store, ref, "[Ascend] Invalid map selection.");
            return;
        }
        AscendMap map = mapStore.getMap(mapId);
        if (map == null) {
            sendMessage(store, ref, "Map not found.");
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        MapUnlockHelper.UnlockResult unlockResult = MapUnlockHelper.checkAndEnsureUnlock(
            playerRef.getUuid(), map, playerStore, mapStore);
        if (!unlockResult.unlocked) {
            sendMessage(store, ref, "[Ascend] Unlock the map before buying a runner.");
            return;
        }
        AscendPlayerProgress progress = playerStore.getOrCreatePlayer(playerRef.getUuid());
        AscendPlayerProgress.MapProgress mapProgress = progress.getOrCreateMapProgress(mapId);
        if (!mapProgress.hasRobot()) {
            // Check if ghost recording exists (preserves PB across progress reset)
            GhostRecording ghost = ghostStore.getRecording(playerRef.getUuid(), mapId);
            if (ghost == null) {
                sendMessage(store, ref, "[Ascend] Complete the map manually before buying a runner!");
                return;
            }

            // Buying a runner is now free
            playerStore.setHasRobot(playerRef.getUuid(), mapId, true);
            showToast(playerRef.getUuid(), ToastType.SUCCESS, "Runner purchased!");
            updateRobotRow(ref, store, mapId);
        } else {
            int currentLevel = mapProgress.getRobotSpeedLevel();
            int currentStars = mapProgress.getRobotStars();

            // Check if fully maxed (max stars and max level)
            if (currentStars >= AscendConstants.MAX_ROBOT_STARS && currentLevel >= MAX_SPEED_LEVEL) {
                showToast(playerRef.getUuid(), ToastType.INFO, "Runner already maxed!");
                return;
            }

            // Check if at max level and can evolve
            if (currentLevel >= MAX_SPEED_LEVEL && currentStars < AscendConstants.MAX_ROBOT_STARS) {
                int newStars = playerStore.evolveRobot(playerRef.getUuid(), mapId);
                if (robotManager != null) {
                    robotManager.respawnRobot(playerRef.getUuid(), mapId, newStars);
                }
                // Trigger evolution tutorial on first evolution (newStars == 1)
                if (newStars == 1) {
                    ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
                    if (plugin != null && plugin.getTutorialTriggerService() != null) {
                        plugin.getTutorialTriggerService().checkEvolution(playerRef.getUuid(), ref);
                    }
                }
                String evoMsg = "Runner evolved! Now at " + newStars + " star" + (newStars > 1 ? "s" : "") + "!";
                showToast(playerRef.getUuid(), ToastType.EVOLUTION, evoMsg);
                updateRobotRow(ref, store, mapId);
                return;
            }

            // Normal speed upgrade
            BigNumber upgradeCost = computeUpgradeCost(currentLevel, map.getDisplayOrder(), currentStars);
            if (!playerStore.atomicSpendVexa(playerRef.getUuid(), upgradeCost)) {
                return;
            }
            int newLevel = playerStore.incrementRobotSpeedLevel(playerRef.getUuid(), mapId);

            // Check if new level unlocks next map
            if (newLevel == AscendConstants.MAP_UNLOCK_REQUIRED_RUNNER_LEVEL) {
                List<String> unlockedMapIds = playerStore.checkAndUnlockEligibleMaps(playerRef.getUuid(), mapStore);
                if (!unlockedMapIds.isEmpty()) {
                    // Trigger map unlock tutorial
                    ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
                    if (plugin != null && plugin.getTutorialTriggerService() != null) {
                        plugin.getTutorialTriggerService().checkMapUnlock(playerRef.getUuid(), ref);
                    }
                }
                for (String unlockedMapId : unlockedMapIds) {
                    AscendMap unlockedMap = mapStore.getMap(unlockedMapId);
                    if (unlockedMap != null) {
                        String mapName = unlockedMap.getName() != null && !unlockedMap.getName().isBlank()
                            ? unlockedMap.getName()
                            : unlockedMap.getId();
                        Player player = store.getComponent(ref, Player.getComponentType());
                        if (player != null) {
                            player.sendMessage(Message.raw("New map unlocked: " + mapName + "!"));
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
        // Update affordability colors every 500ms to reflect vexa changes
        refreshTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            if (!isCurrentPage()) {
                stopAffordabilityUpdates();
                return;
            }
            if (ref == null || !ref.isValid()) {
                stopAffordabilityUpdates();
                return;
            }
            PageRefreshScheduler.requestRefresh(
                world,
                refreshInFlight,
                refreshRequested,
                () -> updateAffordabilityColors(ref, store),
                this::stopAffordabilityUpdates,
                "AscendMapSelectPage"
            );
        }, 500L, 500L, TimeUnit.MILLISECONDS);
    }

    private void stopAffordabilityUpdates() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        refreshRequested.set(false);
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

        RefreshSnapshot refreshSnapshot = buildRefreshSnapshot(playerRef.getUuid());
        List<MapRefreshState> maps = refreshSnapshot.mapStates();

        // Don't send updates if no maps exist (UI elements wouldn't exist)
        if (maps.isEmpty()) {
            return;
        }

        // Detect new map unlocks (auto-upgrade hit level 3 and unlocked new maps)
        if (maps.size() > lastMapCount) {
            for (int i = lastMapCount; i < maps.size(); i++) {
                AscendMap newMap = maps.get(i).map();
                if (newMap != null) {
                    addMapToUI(ref, store, newMap);
                }
            }
            // addMapToUI sends its own update; continue with regular updates for existing maps
        }

        BigNumber currentVexa = refreshSnapshot.currentVexa();
        UICommandBuilder commandBuilder = new UICommandBuilder();

        int displayCount = Math.min(displayedMapIds.size(), maps.size());
        for (int i = 0; i < displayCount; i++) {
            MapRefreshState mapState = maps.get(i);
            AscendMap map = mapState.map();
            AscendPlayerProgress.MapProgress mapProgress = mapState.mapProgress();
            int speedLevel = mapState.speedLevel();
            int stars = mapState.stars();

            // Update momentum indicator (can expire independently of other data changes)
            commandBuilder.set("#MapCards[" + i + "] #MomentumLabel.Visible", mapState.momentumActive());
            if (mapState.momentumActive() && mapProgress != null) {
                commandBuilder.set("#MapCards[" + i + "] #MomentumLabel.Text",
                    buildMomentumText(mapProgress, playerRef.getUuid()));
            }

            // Check if data changed since last refresh (auto-upgrade happened)
            int[] cached = cachedMapState.get(map.getId());
            boolean dataChanged = cached == null || cached[0] != speedLevel || cached[1] != stars;

            if (dataChanged) {
                RunnerCardSnapshot snapshot = renderRunnerButton(
                    commandBuilder, i, map, mapProgress, playerRef.getUuid(), currentVexa);
                cachedMapState.put(map.getId(), new int[]{snapshot.speedLevel(), snapshot.stars()});
            } else if (mapState.hasRobot() && speedLevel < MAX_SPEED_LEVEL) {
                // No data change — affordability-only update for maps with upgrade buttons
                boolean canAfford = mapState.canAffordUpgrade();
                String secondaryTextColor = canAfford ? "#ffffff" : "#9fb0ba";

                commandBuilder.set("#MapCards[" + i + "] #ButtonDisabledOverlay.Visible", !canAfford);
                commandBuilder.set("#MapCards[" + i + "] #RunnerLevel.Style.TextColor", "#ffffff");
                commandBuilder.set("#MapCards[" + i + "] #RunnerStatus.Style.TextColor", secondaryTextColor);
                commandBuilder.set("#MapCards[" + i + "] #RobotBuyText.Style.TextColor", secondaryTextColor);
                commandBuilder.set("#MapCards[" + i + "] #RobotPriceText.Style.TextColor", secondaryTextColor);
            }
        }

        // Also update action button states
        updateBuyAllButtonState(commandBuilder, refreshSnapshot);
        updateEvolveAllButtonState(commandBuilder, refreshSnapshot);

        if (!isCurrentPage()) {
            return;
        }
        try {
            sendUpdate(commandBuilder, null, false);
        } catch (Exception e) {
            // UI was replaced by external dialog (e.g., NPCDialog) - stop refreshing
            stopAffordabilityUpdates();
        }
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
        AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerRef.getUuid(), selectedMap.getId());
        UICommandBuilder commandBuilder = new UICommandBuilder();
        RunnerCardSnapshot snapshot = renderRunnerButton(
            commandBuilder,
            index,
            selectedMap,
            mapProgress,
            playerRef.getUuid(),
            playerStore.getVexa(playerRef.getUuid())
        );

        // Keep cache in sync so the periodic refresh doesn't see a stale diff
        cachedMapState.put(mapId, new int[]{snapshot.speedLevel(), snapshot.stars()});

        // Also update action button states (runner state may have changed)
        updateBuyAllButtonState(commandBuilder, playerRef.getUuid());
        updateEvolveAllButtonState(commandBuilder, playerRef.getUuid());

        if (!isCurrentPage()) {
            return;
        }
        try {
            sendUpdate(commandBuilder, null, false);
        } catch (Exception e) {
            // UI was replaced - ignore silently
        }
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

        // Get map progress (newly unlocked, so might be minimal data)
        AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerRef.getUuid(), map.getId());

        // Append new map card
        commandBuilder.append("#MapCards", "Pages/Ascend_MapSelectEntry.ui");

        // Apply accent color to left accent bar
        applyAccentBarVariant(commandBuilder, index, index);

        // Map name
        commandBuilder.set("#MapCards[" + index + "] #MapName.Text", mapName);

        RunnerCardSnapshot snapshot = renderRunnerButton(
            commandBuilder,
            index,
            map,
            mapProgress,
            playerRef.getUuid(),
            playerStore.getVexa(playerRef.getUuid())
        );

        // Event bindings
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
            "#MapCards[" + index + "] #SelectButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_SELECT_PREFIX + map.getId()), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
            "#MapCards[" + index + "] #RobotBuyButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ROBOT_PREFIX + map.getId()), false);

        // Track new map in cache
        displayedMapIds.add(map.getId());
        cachedMapState.put(map.getId(), new int[]{snapshot.speedLevel(), snapshot.stars()});
        lastMapCount++;

        // Send update to client
        if (isCurrentPage()) {
            try {
                sendUpdate(commandBuilder, eventBuilder, false);
            } catch (Exception e) {
                // UI was replaced - ignore silently
            }
        }
    }

    private void sendMessage(Store<EntityStore> store, Ref<EntityStore> ref, String text) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(text));
        }
    }

    private void showToast(UUID playerId, ToastType type, String message) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            AscendHudManager hm = plugin.getHudManager();
            if (hm != null) {
                hm.showToast(playerId, type, message);
            }
        }
    }

    private void handleBuyAll(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        // Debounce: prevent spam clicking and race conditions with auto-upgrade
        UUID playerId = playerRef.getUuid();
        long now = System.currentTimeMillis();
        Long lastClick = lastBuyAllClick.get(playerId);
        if (lastClick != null && (now - lastClick) < BUY_ALL_COOLDOWN_MS) {
            return; // Silently ignore rapid clicks
        }
        lastBuyAllClick.put(playerId, now);

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
            int speedLevel = mapProgress != null ? mapProgress.getRobotSpeedLevel() : 0;
            int stars = mapProgress != null ? mapProgress.getRobotStars() : 0;

            if (!hasRobot) {
                // Can buy robot if ghost recording exists (preserves PB across progress reset)
                GhostRecording ghost = ghostStore.getRecording(playerRef.getUuid(), map.getId());
                if (ghost != null) {
                    // Buying a runner is now free
                    options.add(new PurchaseOption(map.getId(), PurchaseType.BUY_ROBOT, BigNumber.ZERO));
                }
            } else {
                // Can upgrade speed if not at max level
                if (speedLevel >= MAX_SPEED_LEVEL) {
                    // At max speed level, skip (evolution handled by Evolve All)
                    continue;
                }
                BigNumber upgradeCost = computeUpgradeCost(speedLevel, map.getDisplayOrder(), stars);
                options.add(new PurchaseOption(map.getId(), PurchaseType.UPGRADE_SPEED, upgradeCost));
            }
        }

        if (options.isEmpty()) {
            return;
        }

        // Sort by price (cheapest first) to maximize number of purchases
        options.sort((a, b) -> a.price.compareTo(b.price));

        // Process purchases
        int purchased = 0;
        List<String> updatedMapIds = new ArrayList<>();

        for (PurchaseOption option : options) {
            BigNumber currentVexa = playerStore.getVexa(playerRef.getUuid());
            if (option.price.gt(currentVexa)) {
                continue;
            }

            boolean success = false;
            switch (option.type) {
                case BUY_ROBOT -> {
                    if (option.price.gt(BigNumber.ZERO)) {
                        if (!playerStore.atomicSpendVexa(playerRef.getUuid(), option.price)) {
                            continue;
                        }
                    }
                    playerStore.setHasRobot(playerRef.getUuid(), option.mapId, true);
                    success = true;
                }
                case UPGRADE_SPEED -> {
                    if (!playerStore.atomicSpendVexa(playerRef.getUuid(), option.price)) {
                        continue;
                    }
                    int newLevel = playerStore.incrementRobotSpeedLevel(playerRef.getUuid(), option.mapId);
                    // Check if new level unlocks next map
                    if (newLevel == AscendConstants.MAP_UNLOCK_REQUIRED_RUNNER_LEVEL) {
                        List<String> unlockedMapIds = playerStore.checkAndUnlockEligibleMaps(playerRef.getUuid(), mapStore);
                        if (!unlockedMapIds.isEmpty()) {
                            ParkourAscendPlugin p = ParkourAscendPlugin.getInstance();
                            if (p != null && p.getTutorialTriggerService() != null) {
                                p.getTutorialTriggerService().checkMapUnlock(playerRef.getUuid(), ref);
                            }
                        }
                        for (String unlockedMapId : unlockedMapIds) {
                            AscendMap unlockedMap = mapStore.getMap(unlockedMapId);
                            if (unlockedMap != null) {
                                String mapName = unlockedMap.getName() != null && !unlockedMap.getName().isBlank()
                                    ? unlockedMap.getName()
                                    : unlockedMap.getId();
                                Player player = store.getComponent(ref, Player.getComponentType());
                                if (player != null) {
                                    player.sendMessage(Message.raw("New map unlocked: " + mapName + "!"));
                                }
                                // Add map to UI immediately to prevent crash when updating non-existent elements
                                if (isCurrentPage() && ref.isValid()) {
                                    addMapToUI(ref, store, unlockedMap);
                                }
                            }
                        }
                    }
                    success = true;
                }
            }

            if (success) {
                purchased++;
                if (!updatedMapIds.contains(option.mapId)) {
                    updatedMapIds.add(option.mapId);
                }
            }
        }

        if (purchased == 0) {
            showToast(playerRef.getUuid(), ToastType.ERROR, "Not enough vexa");
            return;
        }

        String buyAllMsg = "Buy All: " + purchased + " upgrade" + (purchased > 1 ? "s" : "");
        showToast(playerRef.getUuid(), ToastType.BATCH, buyAllMsg);

        // Update UI for all affected maps
        for (String mapId : updatedMapIds) {
            updateRobotRow(ref, store, mapId);
        }
    }

    private enum PurchaseType {
        BUY_ROBOT,
        UPGRADE_SPEED
    }

    private record MapRefreshState(AscendMap map, AscendPlayerProgress.MapProgress mapProgress,
                                   boolean hasRobot, int speedLevel, int stars, boolean momentumActive,
                                   boolean canAffordUpgrade) {}

    private record RefreshSnapshot(BigNumber currentVexa, List<MapRefreshState> mapStates,
                                   boolean hasAvailableBuyAll, boolean hasEligibleEvolution) {}

    private record RunnerCardSnapshot(int speedLevel, int stars) {}

    private String buildMomentumText(AscendPlayerProgress.MapProgress mapProgress, UUID playerId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        boolean hasSurge = plugin != null && plugin.getAscensionManager() != null
                && plugin.getAscensionManager().hasMomentumSurge(playerId);
        String mult = hasSurge ? "x2.5" : "x2";
        long remainingMs = mapProgress.getMomentumExpireTimeMs() - System.currentTimeMillis();
        if (remainingMs <= 0) {
            return "MOMENTUM " + mult;
        }
        long secs = (remainingMs + 999) / 1000; // round up
        return "MOMENTUM " + mult + " (" + secs + "s)";
    }

    private record RunnerStatusData(String runnerStatusText, String displayButtonText, String displayPriceText,
                                    BigNumber actionPrice, boolean isUpgrade) {}

    private record PurchaseOption(String mapId, PurchaseType type, BigNumber price) {}

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

        String evolveAllMsg = "Evolve All: " + evolved + " runner" + (evolved > 1 ? "s" : "");
        showToast(playerRef.getUuid(), ToastType.EVOLUTION, evolveAllMsg);

        // Update action button states (may now be grayed out if no more eligible actions)
        UICommandBuilder buttonUpdateCmd = new UICommandBuilder();
        updateBuyAllButtonState(buttonUpdateCmd, playerRef.getUuid());
        updateEvolveAllButtonState(buttonUpdateCmd, playerRef.getUuid());
        if (isCurrentPage()) {
            try {
                sendUpdate(buttonUpdateCmd, null, false);
            } catch (Exception e) {
                // UI was replaced - ignore
            }
        }
    }

    private void handleOpenLeaderboard(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
            new AscendMapLeaderboardPage(playerRef, playerStore, mapStore, runTracker, robotManager, ghostStore));
    }

    private void handleOpenChallenge(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin == null || plugin.getChallengeManager() == null) {
            return;
        }
        if (plugin.getAscensionManager() == null
                || !plugin.getAscensionManager().hasAscensionChallenges(playerRef.getUuid())) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Ascend] Unlock the Ascension Challenges skill first.")
                .color(io.hyvexa.common.util.SystemMessageUtils.SECONDARY));
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
            new AscendChallengePage(playerRef, playerStore, plugin.getChallengeManager()));
    }

    /**
     * Updates the Buy All button appearance based on whether any upgrade is available.
     */
    private void updateBuyAllButtonState(UICommandBuilder commandBuilder, java.util.UUID playerId) {
        updateBuyAllButtonState(commandBuilder, buildRefreshSnapshot(playerId));
    }

    private void updateBuyAllButtonState(UICommandBuilder commandBuilder, RefreshSnapshot refreshSnapshot) {
        boolean hasAvailable = refreshSnapshot != null && refreshSnapshot.hasAvailableBuyAll();
        commandBuilder.set("#BuyAllOverlay.Visible", !hasAvailable);
    }

    /**
     * Updates the Evolve All button appearance based on whether any runner can be evolved.
     * Shows/hides the gray overlay on the button.
     */
    private void updateEvolveAllButtonState(UICommandBuilder commandBuilder, java.util.UUID playerId) {
        updateEvolveAllButtonState(commandBuilder, buildRefreshSnapshot(playerId));
    }

    private void updateEvolveAllButtonState(UICommandBuilder commandBuilder, RefreshSnapshot refreshSnapshot) {
        boolean hasEligible = refreshSnapshot != null && refreshSnapshot.hasEligibleEvolution();
        commandBuilder.set("#EvolveAllOverlay.Visible", !hasEligible);
    }

    private boolean ensureUnlocked(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef,
                                   AscendMap map) {
        MapUnlockHelper.UnlockResult unlockResult = MapUnlockHelper.checkAndEnsureUnlock(
            playerRef.getUuid(), map, playerStore, mapStore);
        if (!unlockResult.unlocked) {
            sendMessage(store, ref, "[Ascend] Unlock this map first.");
            return false;
        }
        return true;
    }
}
