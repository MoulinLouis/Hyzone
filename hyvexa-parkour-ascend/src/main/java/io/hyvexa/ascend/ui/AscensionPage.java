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
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.UUID;

public class AscensionPage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_ASCEND = "Ascend";

    private final AscendPlayerStore playerStore;
    private final AscensionManager ascensionManager;

    public AscensionPage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, AscensionManager ascensionManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.ascensionManager = ascensionManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Ascension.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AscendButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_ASCEND), false);

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

        if (BUTTON_ASCEND.equals(data.getButton())) {
            handleAscend(ref, store);
        }
    }

    private void handleAscend(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        if (!ascensionManager.canAscend(playerId)) {
            BigNumber vexa = playerStore.getVexa(playerId);
            player.sendMessage(Message.raw("[Ascension] Need " + FormatUtils.formatBigNumber(AscendConstants.ASCENSION_VEXA_THRESHOLD)
                + " vexa to Ascend. You have: " + FormatUtils.formatBigNumber(vexa))
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        int newCount = ascensionManager.performAscension(playerId);
        if (newCount < 0) {
            player.sendMessage(Message.raw("[Ascension] Ascension failed.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        player.sendMessage(Message.raw("[Ascension] You have Ascended! (x" + newCount + ")")
            .color(SystemMessageUtils.SUCCESS));
        player.sendMessage(Message.raw("[Ascension] +1 Skill Tree Point. All progress has been reset.")
            .color(SystemMessageUtils.SUCCESS));
        player.sendMessage(Message.raw("[Ascension] Use /ascend skills to unlock abilities.")
            .color(SystemMessageUtils.SECONDARY));

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

    private void updateDisplay(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        BigNumber vexa = playerStore.getVexa(playerId);
        int ascensionCount = playerStore.getAscensionCount(playerId);
        int availablePoints = playerStore.getAvailableSkillPoints(playerId);
        boolean canAscend = ascensionManager.canAscend(playerId);

        // Update vexa values
        commandBuilder.set("#CurrentVexa.Text", FormatUtils.formatBigNumber(vexa));
        commandBuilder.set("#RequiredVexa.Text", FormatUtils.formatBigNumber(AscendConstants.ASCENSION_VEXA_THRESHOLD));

        // Update current stats
        commandBuilder.set("#AscensionCountValue.Text", "x" + ascensionCount);
        commandBuilder.set("#SkillPointsValue.Text", String.valueOf(availablePoints));

        // Update button text based on status
        if (canAscend) {
            commandBuilder.set("#AscendButton.Text", "ASCEND");
        } else {
            commandBuilder.set("#AscendButton.Text", "NEED " + FormatUtils.formatBigNumber(AscendConstants.ASCENSION_VEXA_THRESHOLD));
        }
    }
}
