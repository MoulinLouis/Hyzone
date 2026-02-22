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

public class ElevationPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_ELEVATE = "Elevate";

    private final AscendPlayerStore playerStore;

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

        // Block elevation during active challenge
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getChallengeManager() != null
                && plugin.getChallengeManager().isElevationBlocked(playerId)) {
            player.sendMessage(Message.raw("[Ascend] Elevation is blocked during this challenge.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        BigNumber accumulatedVolt = playerStore.getElevationAccumulatedVolt(playerId);
        int currentElevation = playerStore.getElevationLevel(playerId);

        // Calculate how many levels can be purchased based on accumulated volt
        ElevationPurchaseResult purchase = AscendConstants.calculateElevationPurchase(currentElevation, accumulatedVolt, BigNumber.ONE);

        if (purchase.levels <= 0) {
            BigNumber nextCost = AscendConstants.getElevationLevelUpCost(currentElevation, BigNumber.ONE);
            player.sendMessage(Message.raw("[Ascend] You need " + FormatUtils.formatBigNumber(nextCost) + " accumulated volt to elevate.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Despawn all robots before resetting data to prevent completions with pre-reset multipliers
        if (plugin != null) {
            RobotManager robotManager = plugin.getRobotManager();
            if (robotManager != null) {
                robotManager.despawnRobotsForPlayer(playerId);
            }
        }

        // Set new elevation and reset volt/accumulators atomically
        int newElevation = currentElevation + purchase.levels;
        playerStore.atomicSetElevationAndResetVolt(playerId, newElevation);

        showToast(playerId, ToastType.ECONOMY, "Elevation: "
            + AscendConstants.formatElevationMultiplier(currentElevation) + " -> "
            + AscendConstants.formatElevationMultiplier(newElevation));

        // Reset all progress (volt, map unlocks, runners). Best times are preserved.
        if (plugin != null) {
            AscendMapStore mapStore = plugin.getMapStore();

            // Get first map ID
            String firstMapId = null;
            if (mapStore != null) {
                List<AscendMap> maps = mapStore.listMapsSorted();
                if (!maps.isEmpty()) {
                    firstMapId = maps.get(0).getId();
                }
            }

            playerStore.resetProgressForElevation(playerId, firstMapId);

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

    private void updateDisplay(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        int currentElevation = playerStore.getElevationLevel(playerId);

        // Show blocked state during active challenge
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getChallengeManager() != null
                && plugin.getChallengeManager().isElevationBlocked(playerId)) {
            commandBuilder.set("#ConversionRate.Text", "Elevation is blocked during this challenge");
            commandBuilder.set("#MultiplierValue.Text", AscendConstants.formatElevationMultiplier(currentElevation));
            commandBuilder.set("#NewMultiplierValue.Text", "BLOCKED");
            commandBuilder.set("#NewMultiplierValue.Style.TextColor", "#ef4444");
            commandBuilder.set("#ArrowLabel.Style.TextColor", "#ef4444");
            commandBuilder.set("#ElevateButton.Text", "BLOCKED");
            commandBuilder.set("#GainValue.Text", "Complete the challenge to elevate");
            commandBuilder.set("#GainValue.Style.TextColor", "#ef4444");
            return;
        }

        BigNumber accumulatedVolt = playerStore.getElevationAccumulatedVolt(playerId);

        // Calculate purchase info based on accumulated volt
        ElevationPurchaseResult purchase = AscendConstants.calculateElevationPurchase(currentElevation, accumulatedVolt, BigNumber.ONE);
        int newElevation = currentElevation + purchase.levels;
        BigNumber nextCost = AscendConstants.getElevationLevelUpCost(currentElevation, BigNumber.ONE);

        // Calculate cost for the next level beyond current affordable amount
        BigNumber nextLevelAfterPurchaseCost = AscendConstants.getElevationLevelUpCost(newElevation, BigNumber.ONE);

        // Update progression display (show progress toward next level after potential elevation)
        int targetElevation = newElevation + 1;
        BigNumber targetCost = purchase.cost.add(nextLevelAfterPurchaseCost);
        String costText = "Progress to " + AscendConstants.formatElevationMultiplier(targetElevation) + ": " +
                         FormatUtils.formatBigNumber(accumulatedVolt) + " / " +
                         FormatUtils.formatBigNumber(targetCost) + " accumulated volt";
        commandBuilder.set("#ConversionRate.Text", costText);

        // Update current elevation display
        commandBuilder.set("#MultiplierValue.Text", AscendConstants.formatElevationMultiplier(currentElevation));
        BigNumber leftoverVolt = accumulatedVolt.subtract(purchase.cost);
        BigNumber amountNeededForNextLevel = nextLevelAfterPurchaseCost.subtract(leftoverVolt).max(BigNumber.ZERO);

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
            commandBuilder.set("#ElevateButton.Text", "NEED MORE VOLT");
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
