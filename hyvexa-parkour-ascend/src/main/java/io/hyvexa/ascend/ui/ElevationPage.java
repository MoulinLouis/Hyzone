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
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
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
    private volatile boolean active;

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

        active = true;
        updateDisplay(ref, store, commandBuilder);
        startAutoRefresh(ref, store);
    }

    @Override
    public void close() {
        active = false;
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
        long coins = playerStore.getCoins(playerId);
        int currentLevel = playerStore.getElevationLevel(playerId);

        // Get cost multiplier from skill tree
        double costMultiplier = getCostMultiplier(playerId);

        // Calculate how many levels can be purchased
        ElevationPurchaseResult purchase = AscendConstants.calculateElevationPurchase(currentLevel, coins, costMultiplier);

        if (purchase.levels <= 0) {
            long nextCost = AscendConstants.getElevationLevelUpCost(currentLevel, costMultiplier);
            player.sendMessage(Message.raw("[Ascend] You need " + FormatUtils.formatCoinsForHud(nextCost) + " coins to elevate.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Deduct the cost and add levels
        playerStore.setCoins(playerId, coins - purchase.cost);
        int newLevel = playerStore.addElevationLevel(playerId, purchase.levels);
        double newMultiplier = AscendConstants.calculateElevationMultiplier(newLevel);

        player.sendMessage(Message.raw("[Ascend] Elevation +" + purchase.levels + " (Lv." + newLevel + " = x" + formatMultiplier(newMultiplier) + ")!")
            .color(SystemMessageUtils.SUCCESS));

        // Check achievements
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
        }

        // Refresh display
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
            if (!active || ref == null || !ref.isValid()) {
                stopAutoRefresh();
                return;
            }
            CompletableFuture.runAsync(() -> refreshDisplay(ref, store), world);
        }, REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
    }

    private void refreshDisplay(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!active) {
            return;
        }
        UICommandBuilder commandBuilder = new UICommandBuilder();
        updateDisplay(ref, store, commandBuilder);
        sendUpdate(commandBuilder, null, false);
    }

    private void updateDisplay(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        long coins = playerStore.getCoins(playerId);
        int currentLevel = playerStore.getElevationLevel(playerId);
        double currentMultiplier = AscendConstants.calculateElevationMultiplier(currentLevel);

        // Get cost multiplier from skill tree
        double costMultiplier = getCostMultiplier(playerId);

        // Calculate purchase info
        ElevationPurchaseResult purchase = AscendConstants.calculateElevationPurchase(currentLevel, coins, costMultiplier);
        int newLevel = currentLevel + purchase.levels;
        double newMultiplier = AscendConstants.calculateElevationMultiplier(newLevel);
        long nextLevelCost = AscendConstants.getElevationLevelUpCost(currentLevel, costMultiplier);

        // Update coin display
        commandBuilder.set("#CoinsValue.Text", FormatUtils.formatCoinsForHud(coins));

        // Update conversion rate display (show cost for next level)
        String costText = "Next level: " + FormatUtils.formatCoinsForHud(nextLevelCost) + " coins";
        if (costMultiplier < 1.0) {
            costText += " (-" + Math.round((1.0 - costMultiplier) * 100) + "%)";
        }
        commandBuilder.set("#ConversionRate.Text", costText);

        // Update current multiplier display (show level and multiplier)
        commandBuilder.set("#MultiplierValue.Text", "Lv." + currentLevel + " (x" + formatMultiplier(currentMultiplier) + ")");

        // Update new multiplier display and gain
        if (purchase.levels > 0) {
            commandBuilder.set("#NewMultiplierValue.Text", "Lv." + newLevel + " (x" + formatMultiplier(newMultiplier) + ")");
            commandBuilder.set("#NewMultiplierValue.Style.TextColor", "#4ade80");
            commandBuilder.set("#GainValue.Text", "+" + purchase.levels + " level" + (purchase.levels > 1 ? "s" : ""));
            commandBuilder.set("#GainValue.Style.TextColor", "#4ade80");
            commandBuilder.set("#ArrowLabel.Style.TextColor", "#4ade80");
            commandBuilder.set("#ElevateButton.Text", "ELEVATE NOW");
        } else {
            commandBuilder.set("#NewMultiplierValue.Text", "Lv." + currentLevel);
            commandBuilder.set("#NewMultiplierValue.Style.TextColor", "#6b7280");
            commandBuilder.set("#GainValue.Text", "Need " + FormatUtils.formatCoinsForHud(nextLevelCost - coins) + " more");
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

    private String formatMultiplier(double multiplier) {
        if (multiplier >= 10.0) {
            return String.format("%.1f", multiplier);
        }
        return String.format("%.2f", multiplier);
    }
}
