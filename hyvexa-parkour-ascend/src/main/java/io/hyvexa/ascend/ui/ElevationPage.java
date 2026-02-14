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
import io.hyvexa.ascend.AscendConstants.ElevationPurchaseResult;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.hud.ToastType;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ElevationPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_ELEVATE = "Elevate";
    private static final long REFRESH_INTERVAL_MS = 200L;

    private final AscendPlayerStore playerStore;
    private ScheduledFuture<?> refreshTask;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final AtomicBoolean refreshRequested = new AtomicBoolean(false);

    public ElevationPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Elevation.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ElevateButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ELEVATE), false);

        updateDisplay(ref, store, commandBuilder);
        startAutoRefresh(ref, store);
    }

    @Override
    public void close() {
        stopAutoRefresh();
        super.close();
    }

    @Override
    protected void stopBackgroundTasks() {
        stopAutoRefresh();
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

        if (BUTTON_ELEVATE.equals(data.getButton())) {
            handleElevate(ref, store);
        }
    }

    private void handleElevate(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        BigNumber accumulatedVexa = playerStore.getElevationAccumulatedVexa(playerId);
        int currentElevation = playerStore.getElevationLevel(playerId);

        double costMultiplier = 1.0;

        // Calculate how many levels can be purchased based on accumulated vexa
        BigNumber costMultiplierBN = BigNumber.fromDouble(costMultiplier);
        ElevationPurchaseResult purchase = AscendConstants.calculateElevationPurchase(currentElevation, accumulatedVexa, costMultiplierBN);

        if (purchase.levels <= 0) {
            BigNumber nextCost = AscendConstants.getElevationLevelUpCost(currentElevation, BigNumber.fromDouble(costMultiplier));
            player.sendMessage(Message.raw("[Ascend] You need " + FormatUtils.formatBigNumber(nextCost) + " accumulated vexa to elevate.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Set new elevation and reset vexa/accumulators atomically
        int newElevation = currentElevation + purchase.levels;
        playerStore.atomicSetElevationAndResetVexa(playerId, newElevation);

        showToast(playerId, ToastType.ECONOMY, "Elevation: "
            + AscendConstants.formatElevationMultiplier(currentElevation) + " -> "
            + AscendConstants.formatElevationMultiplier(newElevation));

        // Reset all progress (vexa, map unlocks, runners). Best times are preserved.
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null) {
            AscendMapStore mapStore = plugin.getMapStore();
            RobotManager robotManager = plugin.getRobotManager();

            // Get first map ID
            String firstMapId = null;
            if (mapStore != null) {
                List<AscendMap> maps = mapStore.listMapsSorted();
                if (!maps.isEmpty()) {
                    firstMapId = maps.get(0).getId();
                }
            }

            // Reset progress and get list of maps with runners for despawn
            List<String> mapsWithRunners = playerStore.resetProgressForElevation(playerId, firstMapId);

            // Despawn all runners (player loses them on elevation)
            if (robotManager != null && !mapsWithRunners.isEmpty()) {
                for (String mapId : mapsWithRunners) {
                    robotManager.despawnRobot(playerId, mapId);
                }
            }

            // Check achievements
            if (plugin.getAchievementManager() != null) {
                plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
            }
        }

        // Refresh display only if still current page
        if (!isCurrentPage()) {
            return;
        }
        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateDisplay(ref, store, updateBuilder);
        try {
            sendUpdate(updateBuilder, null, false);
        } catch (Exception e) {
            // UI was replaced - ignore silently
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
        refreshTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            // Quick check - if not current page, stop refreshing
            if (!isCurrentPage()) {
                stopAutoRefresh();
                return;
            }
            if (ref == null || !ref.isValid()) {
                stopAutoRefresh();
                return;
            }
            PageRefreshScheduler.requestRefresh(
                world,
                refreshInFlight,
                refreshRequested,
                () -> refreshDisplay(ref, store),
                this::stopAutoRefresh,
                "ElevationPage"
            );
        }, REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        refreshRequested.set(false);
    }

    private void refreshDisplay(Ref<EntityStore> ref, Store<EntityStore> store) {
        // Don't send updates if this page is no longer current
        if (!isCurrentPage()) {
            stopAutoRefresh();
            return;
        }
        UICommandBuilder commandBuilder = new UICommandBuilder();
        updateDisplay(ref, store, commandBuilder);
        // Final check before sending
        if (!isCurrentPage()) {
            return;
        }
        try {
            sendUpdate(commandBuilder, null, false);
        } catch (Exception e) {
            // UI was replaced by external dialog (e.g., NPCDialog) - stop refreshing
            stopAutoRefresh();
        }
    }

    private void updateDisplay(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        BigNumber accumulatedVexa = playerStore.getElevationAccumulatedVexa(playerId);
        int currentElevation = playerStore.getElevationLevel(playerId);

        double costMultiplier = 1.0;

        // Calculate purchase info based on accumulated vexa
        BigNumber costMultiplierBN = BigNumber.fromDouble(costMultiplier);
        ElevationPurchaseResult purchase = AscendConstants.calculateElevationPurchase(currentElevation, accumulatedVexa, costMultiplierBN);
        int newElevation = currentElevation + purchase.levels;
        BigNumber nextCost = AscendConstants.getElevationLevelUpCost(currentElevation, costMultiplierBN);

        // Calculate cost for the next level beyond current affordable amount
        BigNumber nextLevelAfterPurchaseCost = AscendConstants.getElevationLevelUpCost(newElevation, costMultiplierBN);

        // Update progression display (show progress toward next level after potential elevation)
        int targetElevation = newElevation + 1;
        BigNumber targetCost = purchase.cost.add(nextLevelAfterPurchaseCost);
        String costText = "Progress to " + AscendConstants.formatElevationMultiplier(targetElevation) + ": " +
                         FormatUtils.formatBigNumber(accumulatedVexa) + " / " +
                         FormatUtils.formatBigNumber(targetCost) + " accumulated vexa";
        if (costMultiplier < 1.0) {
            costText += " (-" + Math.round((1.0 - costMultiplier) * 100) + "%)";
        }
        commandBuilder.set("#ConversionRate.Text", costText);

        // Update current elevation display
        commandBuilder.set("#MultiplierValue.Text", AscendConstants.formatElevationMultiplier(currentElevation));
        BigNumber leftoverVexa = accumulatedVexa.subtract(purchase.cost);
        BigNumber amountNeededForNextLevel = nextLevelAfterPurchaseCost.subtract(leftoverVexa).max(BigNumber.ZERO);

        // Update new elevation display and gain
        if (purchase.levels > 0) {
            commandBuilder.set("#NewMultiplierValue.Text", AscendConstants.formatElevationMultiplier(newElevation));
            commandBuilder.set("#NewMultiplierValue.Style.TextColor", "#4ade80");
            commandBuilder.set("#ArrowLabel.Style.TextColor", "#4ade80");
            commandBuilder.set("#ElevateButton.Text", "ELEVATE NOW");
        } else {
            commandBuilder.set("#NewMultiplierValue.Text", AscendConstants.formatElevationMultiplier(currentElevation));
            commandBuilder.set("#NewMultiplierValue.Style.TextColor", "#6b7280");
            commandBuilder.set("#ArrowLabel.Style.TextColor", "#6b7280");
            commandBuilder.set("#ElevateButton.Text", "NEED MORE VEXA");
        }

        // Always show how much more is needed for the next level
        commandBuilder.set("#GainValue.Text", "Need " + FormatUtils.formatBigNumber(amountNeededForNextLevel) + " more");
        commandBuilder.set("#GainValue.Style.TextColor", "#9ca3af");
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
}
