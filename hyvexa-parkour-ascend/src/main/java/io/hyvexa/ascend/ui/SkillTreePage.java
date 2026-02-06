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
import io.hyvexa.ascend.AscendConstants.SkillTreeNode;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;

public class SkillTreePage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_NODE = "Node_AUTO_RUNNERS";

    private static final String COLOR_ACCENT = "#f59e0b";
    private static final String COLOR_LOCKED = "#4b5563";
    private static final String COLOR_AVAILABLE_TEXT = "#fbbf24";
    private static final String COLOR_UNLOCKED_TEXT = "#4ade80";
    private static final String COLOR_LOCKED_TEXT = "#6b7280";

    private final AscendPlayerStore playerStore;
    private final AscensionManager ascensionManager;

    public SkillTreePage(@Nonnull PlayerRef playerRef, AscendPlayerStore playerStore, AscensionManager ascensionManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.playerStore = playerStore;
        this.ascensionManager = ascensionManager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/Ascend_SkillTree.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_CLOSE), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NodeAutoRunners",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NODE), false);

        updateNodeState(ref, store, commandBuilder);
    }

    private void updateNodeState(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        var summary = ascensionManager.getSkillTreeSummary(playerId);
        Set<SkillTreeNode> unlockedNodes = summary.unlockedNodes();

        // Update points display
        commandBuilder.set("#AvailablePoints.Text", String.valueOf(summary.availablePoints()));
        commandBuilder.set("#TotalPoints.Text", summary.totalPoints() + " total");

        boolean isUnlocked = unlockedNodes.contains(SkillTreeNode.AUTO_RUNNERS);
        boolean canUnlock = ascensionManager.canUnlockSkillNode(playerId, SkillTreeNode.AUTO_RUNNERS);

        // Update border color
        String borderColor = (isUnlocked || canUnlock) ? COLOR_ACCENT : COLOR_LOCKED;
        commandBuilder.set("#BorderAutoRunners.Background", borderColor);

        // Update status text and color
        if (isUnlocked) {
            commandBuilder.set("#StatusAutoRunners.Text", "UNLOCKED");
            commandBuilder.set("#StatusAutoRunners.Style.TextColor", COLOR_UNLOCKED_TEXT);
        } else if (canUnlock) {
            commandBuilder.set("#StatusAutoRunners.Text", "AVAILABLE");
            commandBuilder.set("#StatusAutoRunners.Style.TextColor", COLOR_AVAILABLE_TEXT);
        } else {
            commandBuilder.set("#StatusAutoRunners.Text", "LOCKED");
            commandBuilder.set("#StatusAutoRunners.Style.TextColor", COLOR_LOCKED_TEXT);
        }
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

        if (BUTTON_NODE.equals(data.getButton())) {
            handleNodeClick(ref, store);
        }
    }

    private void handleNodeClick(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        var summary = ascensionManager.getSkillTreeSummary(playerId);
        SkillTreeNode node = SkillTreeNode.AUTO_RUNNERS;

        // Already unlocked - show info
        if (summary.unlockedNodes().contains(node)) {
            player.sendMessage(Message.raw("[Skill Tree] " + node.getName())
                .color(SystemMessageUtils.PRIMARY_TEXT));
            player.sendMessage(Message.raw("  " + node.getDescription())
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Can unlock - attempt to unlock
        if (ascensionManager.canUnlockSkillNode(playerId, node)) {
            boolean success = ascensionManager.tryUnlockSkillNode(playerId, node);
            if (success) {
                player.sendMessage(Message.raw("[Skill Tree] Unlocked: " + node.getName() + "!")
                    .color(SystemMessageUtils.SUCCESS));
                player.sendMessage(Message.raw("  " + node.getDescription())
                    .color(SystemMessageUtils.SECONDARY));

                // Refresh the page
                refreshNodeState(ref, store);
            } else {
                player.sendMessage(Message.raw("[Skill Tree] Failed to unlock skill.")
                    .color(SystemMessageUtils.SECONDARY));
            }
            return;
        }

        // Cannot unlock - show requirements
        if (summary.availablePoints() <= 0) {
            player.sendMessage(Message.raw("[Skill Tree] " + node.getName() + " - LOCKED")
                .color(SystemMessageUtils.SECONDARY));
            player.sendMessage(Message.raw("  No skill points. Ascend to earn more!")
                .color(SystemMessageUtils.SECONDARY));
        }
    }

    private void refreshNodeState(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateNodeState(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }
}
