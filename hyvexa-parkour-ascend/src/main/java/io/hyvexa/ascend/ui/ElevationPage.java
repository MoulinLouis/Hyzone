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
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
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
        long coins = playerStore.getCoins(playerId);

        // Get elevation cost (may be modified by skill tree)
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        AscensionManager ascensionManager = plugin != null ? plugin.getAscensionManager() : null;
        int elevationCost = ascensionManager != null
            ? ascensionManager.getElevationCost(playerId)
            : 1000;

        int gain = (int) (coins / elevationCost);
        if (gain <= 0) {
            player.sendMessage(Message.raw("[Ascend] You need " + elevationCost + " coins to elevate.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        playerStore.setCoins(playerId, 0L);
        int newMultiplier = playerStore.addElevationMultiplier(playerId, gain);

        player.sendMessage(Message.raw("[Ascend] Elevation +" + gain + " (x" + newMultiplier + ")!")
            .color(SystemMessageUtils.SUCCESS));

        // Check achievements
        if (plugin != null && plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
        }

        // Refresh display
        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateDisplay(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }

    private void updateDisplay(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        long coins = playerStore.getCoins(playerId);
        int currentMultiplier = playerStore.getElevationMultiplier(playerId);

        // Get elevation cost (may be modified by skill tree)
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        AscensionManager ascensionManager = plugin != null ? plugin.getAscensionManager() : null;
        int elevationCost = ascensionManager != null
            ? ascensionManager.getElevationCost(playerId)
            : 1000;

        int gain = (int) (coins / elevationCost);
        int newMultiplier = currentMultiplier + gain;

        // Update coin display
        commandBuilder.set("#CoinsValue.Text", FormatUtils.formatCoinsForHud(coins));

        // Update conversion rate display
        commandBuilder.set("#ConversionRate.Text", "Conversion: " + elevationCost + " coins = +1 elevation");

        // Update current multiplier
        commandBuilder.set("#MultiplierValue.Text", "x" + currentMultiplier);

        // Update gain preview
        if (gain > 0) {
            commandBuilder.set("#GainValue.Text", "+" + gain);
            commandBuilder.set("#GainValue.Style.TextColor", "#4ade80");
        } else {
            commandBuilder.set("#GainValue.Text", "+0");
            commandBuilder.set("#GainValue.Style.TextColor", "#6b7280");
        }

        // Update button zone labels
        commandBuilder.set("#CurrentMultiplierLabel.Text", "x" + currentMultiplier);

        if (gain > 0) {
            // Show the conversion and make it attractive
            commandBuilder.set("#NewMultiplierLabel.Text", "x" + newMultiplier);
            commandBuilder.set("#NewMultiplierLabel.Style.TextColor", "#4ade80");
            commandBuilder.set("#ArrowLabel.Visible", true);
            commandBuilder.set("#ArrowLabel.Style.TextColor", "#4ade80");
            commandBuilder.set("#ButtonText.Text", "ELEVATE NOW");
            commandBuilder.set("#ButtonText.Style.TextColor", "#4ade80");
            commandBuilder.set("#GainPreview.Text", "(+" + gain + " boost!)");
            commandBuilder.set("#GainPreview.Visible", true);
        } else {
            commandBuilder.set("#NewMultiplierLabel.Text", "");
            commandBuilder.set("#ArrowLabel.Visible", false);
            commandBuilder.set("#ButtonText.Text", "NEED " + elevationCost);
            commandBuilder.set("#ButtonText.Style.TextColor", "#9fb0ba");
            commandBuilder.set("#GainPreview.Visible", false);
        }
    }
}
