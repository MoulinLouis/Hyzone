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
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.transcendence.TranscendenceManager;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.UUID;

public class TranscendencePage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_TRANSCEND = "Transcend";

    private final AscendPlayerStore playerStore;
    private final TranscendenceManager transcendenceManager;

    public TranscendencePage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore,
                             TranscendenceManager transcendenceManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.transcendenceManager = transcendenceManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_Transcendence.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TranscendButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_TRANSCEND), false);

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

        if (BUTTON_TRANSCEND.equals(data.getButton())) {
            handleTranscend(ref, store);
        }
    }

    private void handleTranscend(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        if (!transcendenceManager.isEligible(playerId)) {
            BigNumber volt = playerStore.getVolt(playerId);
            player.sendMessage(Message.raw("[Transcendence] Need " + FormatUtils.formatBigNumber(AscendConstants.TRANSCENDENCE_VOLT_THRESHOLD)
                + " volt with BREAK_ASCENSION active. You have: " + FormatUtils.formatBigNumber(volt))
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Despawn all robots before resetting data to prevent completions with pre-reset multipliers
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getRobotManager() != null) {
            plugin.getRobotManager().despawnRobotsForPlayer(playerId);
        }

        int newCount = transcendenceManager.performTranscendence(playerId);
        if (newCount < 0) {
            player.sendMessage(Message.raw("[Transcendence] Transcendence failed.")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        player.sendMessage(Message.raw("[Transcendence] You have Transcended! (x" + newCount + ")")
            .color(SystemMessageUtils.SUCCESS));
        player.sendMessage(Message.raw("[Transcendence] Everything has been reset. Begin anew.")
            .color(SystemMessageUtils.SUCCESS));

        if (newCount == 1) {
            player.sendMessage(Message.raw("[Transcendence] Milestone 1 unlocked: Map 6 is now available!")
                .color(SystemMessageUtils.SUCCESS));
        }

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
        BigNumber volt = playerStore.getVolt(playerId);
        int transcendenceCount = playerStore.getTranscendenceCount(playerId);
        boolean eligible = transcendenceManager.isEligible(playerId);

        commandBuilder.set("#CurrentVolt.Text", FormatUtils.formatBigNumber(volt));
        commandBuilder.set("#RequiredVolt.Text", FormatUtils.formatBigNumber(AscendConstants.TRANSCENDENCE_VOLT_THRESHOLD));
        commandBuilder.set("#TranscendenceCountValue.Text", "x" + transcendenceCount);

        // Milestone 1 status
        boolean milestone1 = transcendenceCount >= 1;
        commandBuilder.set("#Milestone1Status.Text", milestone1 ? "UNLOCKED" : "LOCKED");
        commandBuilder.set("#Milestone1Status.Style.TextColor", milestone1 ? "#4ade80" : "#6b7280");

        // Button text
        if (eligible) {
            commandBuilder.set("#TranscendButton.Text", "TRANSCEND");
        } else {
            commandBuilder.set("#TranscendButton.Text", "NEED " + FormatUtils.formatBigNumber(AscendConstants.TRANSCENDENCE_VOLT_THRESHOLD));
        }
    }
}
