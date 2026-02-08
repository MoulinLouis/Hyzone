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
    private static final String BUTTON_NODE_PREFIX = "Node_";

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

        // Bind all node buttons dynamically
        for (SkillTreeNode node : SkillTreeNode.values()) {
            String uiId = "#Node" + toPascalCase(node.name());
            String buttonData = BUTTON_NODE_PREFIX + node.name();
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, uiId,
                EventData.of(ButtonEventData.KEY_BUTTON, buttonData), false);
        }

        updateAllNodeStates(ref, store, commandBuilder);
    }

    private void updateAllNodeStates(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
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

        // Update each node
        for (SkillTreeNode node : SkillTreeNode.values()) {
            String pascalName = toPascalCase(node.name());
            boolean isUnlocked = unlockedNodes.contains(node);
            boolean prereqsMet = node.hasPrerequisitesSatisfied(unlockedNodes);
            boolean canUnlock = ascensionManager.canUnlockSkillNode(playerId, node);

            // Toggle revealed/locked content
            commandBuilder.set("#Revealed" + pascalName + ".Visible", prereqsMet);
            commandBuilder.set("#Locked" + pascalName + ".Visible", !prereqsMet);

            // Border color (locked nodes stay gray)
            String borderColor = !prereqsMet ? COLOR_LOCKED
                : (isUnlocked || canUnlock) ? COLOR_ACCENT : COLOR_LOCKED;
            commandBuilder.set("#Border" + pascalName + ".Background", borderColor);

            // Status text and color (only meaningful when revealed)
            if (prereqsMet) {
                if (isUnlocked) {
                    commandBuilder.set("#Status" + pascalName + ".Text", "UNLOCKED");
                    commandBuilder.set("#Status" + pascalName + ".Style.TextColor", COLOR_UNLOCKED_TEXT);
                } else if (canUnlock) {
                    commandBuilder.set("#Status" + pascalName + ".Text", "AVAILABLE");
                    commandBuilder.set("#Status" + pascalName + ".Style.TextColor", COLOR_AVAILABLE_TEXT);
                } else {
                    commandBuilder.set("#Status" + pascalName + ".Text", "LOCKED");
                    commandBuilder.set("#Status" + pascalName + ".Style.TextColor", COLOR_LOCKED_TEXT);
                }
            }
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

        if (data.getButton().startsWith(BUTTON_NODE_PREFIX)) {
            String nodeName = data.getButton().substring(BUTTON_NODE_PREFIX.length());
            try {
                SkillTreeNode node = SkillTreeNode.valueOf(nodeName);
                handleNodeClick(ref, store, node);
            } catch (IllegalArgumentException ignored) {
                // Unknown node
            }
        }
    }

    private void handleNodeClick(Ref<EntityStore> ref, Store<EntityStore> store, SkillTreeNode node) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        var summary = ascensionManager.getSkillTreeSummary(playerId);

        // Special handling for ASCENSION_CHALLENGES (teaser)
        if (node == SkillTreeNode.ASCENSION_CHALLENGES) {
            player.sendMessage(Message.raw("[Skill Tree] Ascension Challenges")
                .color(SystemMessageUtils.PRIMARY_TEXT));
            player.sendMessage(Message.raw("  Coming in a future update!")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Already unlocked - show info
        if (summary.unlockedNodes().contains(node)) {
            player.sendMessage(Message.raw("[Skill Tree] " + node.getName())
                .color(SystemMessageUtils.PRIMARY_TEXT));
            player.sendMessage(Message.raw("  " + node.getDescription())
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Check prerequisites
        if (!node.hasPrerequisitesSatisfied(summary.unlockedNodes())) {
            player.sendMessage(Message.raw("[Skill Tree] " + node.getName() + " - LOCKED")
                .color(SystemMessageUtils.SECONDARY));
            StringBuilder reqMsg = new StringBuilder("  Requires: ");
            SkillTreeNode[] prereqs = node.getPrerequisites();
            for (int i = 0; i < prereqs.length; i++) {
                if (i > 0) reqMsg.append(" or ");
                reqMsg.append(prereqs[i].getName());
            }
            player.sendMessage(Message.raw(reqMsg.toString())
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
                refreshNodeStates(ref, store);
            } else {
                player.sendMessage(Message.raw("[Skill Tree] Failed to unlock skill.")
                    .color(SystemMessageUtils.SECONDARY));
            }
            return;
        }

        // Cannot unlock - no points
        if (summary.availablePoints() <= 0) {
            player.sendMessage(Message.raw("[Skill Tree] " + node.getName() + " - LOCKED")
                .color(SystemMessageUtils.SECONDARY));
            player.sendMessage(Message.raw("  No skill points. Ascend to earn more!")
                .color(SystemMessageUtils.SECONDARY));
        }
    }

    private void refreshNodeStates(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateAllNodeStates(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }

    /**
     * Converts ENUM_NAME to PascalCase (e.g. AUTO_RUNNERS → AutoRunners).
     */
    private static String toPascalCase(String enumName) {
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : enumName.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
