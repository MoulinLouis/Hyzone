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
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ElevationPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_ELEVATE = "Elevate";
    private static final long REFRESH_INTERVAL_MS = 1000L;

    private final AscendPlayerStore playerStore;
    private ScheduledFuture<?> refreshTask;

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
        double coins = playerStore.getCoins(playerId);
        int currentElevation = playerStore.getElevationLevel(playerId);

        // Get cost multiplier from skill tree
        double costMultiplier = getCostMultiplier(playerId);

        // Calculate how many levels can be purchased
        ElevationPurchaseResult purchase = AscendConstants.calculateElevationPurchase(currentElevation, coins, costMultiplier);

        if (purchase.levels <= 0) {
            long nextCost = AscendConstants.getElevationLevelUpCost(currentElevation, costMultiplier);
            player.sendMessage(Message.raw("[Ascend] You need " + FormatUtils.formatCoinsForHud(nextCost) + " coins to elevate.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Deduct the cost and add elevation
        playerStore.setCoins(playerId, coins - purchase.cost);
        int newElevation = playerStore.addElevationLevel(playerId, purchase.levels);

        player.sendMessage(Message.raw("[Ascend] Elevation +" + purchase.levels + " (x" + newElevation + ")!")
            .color(SystemMessageUtils.SUCCESS));

        // Reset all progress (coins, map unlocks, best times, runners)
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
        sendUpdate(updateBuilder, null, false);
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
            try {
                CompletableFuture.runAsync(() -> refreshDisplay(ref, store), world);
            } catch (Exception e) {
                stopAutoRefresh();
            }
        }, REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
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
        sendUpdate(commandBuilder, null, false);
    }

    private void updateDisplay(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        double coins = playerStore.getCoins(playerId);
        int currentElevation = playerStore.getElevationLevel(playerId);

        // Get cost multiplier from skill tree
        double costMultiplier = getCostMultiplier(playerId);

        // Calculate purchase info
        ElevationPurchaseResult purchase = AscendConstants.calculateElevationPurchase(currentElevation, coins, costMultiplier);
        int newElevation = currentElevation + purchase.levels;
        long nextCost = AscendConstants.getElevationLevelUpCost(currentElevation, costMultiplier);

        // Update coin display
        commandBuilder.set("#CoinsValue.Text", FormatUtils.formatCoinsForHudDecimal(coins));

        // Update conversion rate display (show cost for next elevation)
        String costText = "Next: " + FormatUtils.formatCoinsForHud(nextCost) + " coins";
        if (costMultiplier < 1.0) {
            costText += " (-" + Math.round((1.0 - costMultiplier) * 100) + "%)";
        }
        commandBuilder.set("#ConversionRate.Text", costText);

        // Update current elevation display
        commandBuilder.set("#MultiplierValue.Text", "x" + currentElevation);

        // Update new elevation display and gain
        if (purchase.levels > 0) {
            commandBuilder.set("#NewMultiplierValue.Text", "x" + newElevation);
            commandBuilder.set("#NewMultiplierValue.Style.TextColor", "#4ade80");
            commandBuilder.set("#GainValue.Text", "+" + purchase.levels);
            commandBuilder.set("#GainValue.Style.TextColor", "#4ade80");
            commandBuilder.set("#ArrowLabel.Style.TextColor", "#4ade80");
            commandBuilder.set("#ElevateButton.Text", "ELEVATE NOW");
        } else {
            commandBuilder.set("#NewMultiplierValue.Text", "x" + currentElevation);
            commandBuilder.set("#NewMultiplierValue.Style.TextColor", "#6b7280");
            commandBuilder.set("#GainValue.Text", "Need " + FormatUtils.formatCoinsForHudDecimal(nextCost - coins) + " more");
            commandBuilder.set("#GainValue.Style.TextColor", "#6b7280");
            commandBuilder.set("#ArrowLabel.Style.TextColor", "#6b7280");
            commandBuilder.set("#ElevateButton.Text", "NEED MORE COINS");
        }
    }

    private double getCostMultiplier(UUID playerId) {
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        AscensionManager ascensionManager = plugin != null ? plugin.getAscensionManager() : null;
        return ascensionManager != null ? ascensionManager.getElevationCostMultiplier(playerId) : 1.0;
    }
}
