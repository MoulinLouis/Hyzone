package io.hyvexa.ascend.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
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

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class SkillTreePage extends BaseAscendPage {

    private static final Map<SkillTreeNode, String> NODE_COORDINATES = new EnumMap<>(SkillTreeNode.class);
    static {
        NODE_COORDINATES.put(SkillTreeNode.AUTO_RUNNERS, "1:1");
        NODE_COORDINATES.put(SkillTreeNode.AUTO_EVOLUTION, "2:1");
        NODE_COORDINATES.put(SkillTreeNode.RUNNER_SPEED, "3:1");
        NODE_COORDINATES.put(SkillTreeNode.EVOLUTION_POWER, "3:2");
        NODE_COORDINATES.put(SkillTreeNode.RUNNER_SPEED_2, "4:1");
        NODE_COORDINATES.put(SkillTreeNode.ASCENSION_CHALLENGES, "5:1");
    }

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_NODE_PREFIX = "Node_";
    private static final String BUTTON_UNLOCK = "Unlock";
    private static final String BUTTON_DETAIL_CLOSE = "DetailClose";

    private static final String COLOR_ACCENT = "#f59e0b";
    private static final String COLOR_LOCKED = "#4b5563";
    private static final String COLOR_AVAILABLE_TEXT = "#fbbf24";
    private static final String COLOR_UNLOCKED_TEXT = "#4ade80";
    private static final String COLOR_LOCKED_TEXT = "#6b7280";

    private final AscendPlayerStore playerStore;
    private final AscensionManager ascensionManager;
    private SkillTreeNode selectedNode;

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

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#UnlockButton",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_UNLOCK), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DetailCloseBtn",
            EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_DETAIL_CLOSE), false);

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

        // Update Ascendance display
        int ascendanceCount = playerStore.getAscensionCount(playerId);
        commandBuilder.set("#AscendanceValue.Text", String.valueOf(ascendanceCount));

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

        if (BUTTON_DETAIL_CLOSE.equals(data.getButton())) {
            selectedNode = null;
            hideDetailPanel();
            return;
        }

        if (BUTTON_UNLOCK.equals(data.getButton())) {
            handleUnlockClick(ref, store);
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
        if (playerRef == null) {
            return;
        }

        // Clicking the same node again closes the panel
        if (node == selectedNode) {
            selectedNode = null;
            hideDetailPanel();
            return;
        }

        selectedNode = node;
        showDetailPanel(playerRef.getUuid(), node);
    }

    private void showDetailPanel(UUID playerId, SkillTreeNode node) {
        UICommandBuilder builder = new UICommandBuilder();
        var summary = ascensionManager.getSkillTreeSummary(playerId);
        boolean isUnlocked = summary.unlockedNodes().contains(node);
        boolean prereqsMet = node.hasPrerequisitesSatisfied(summary.unlockedNodes());
        boolean canUnlock = ascensionManager.canUnlockSkillNode(playerId, node);

        builder.set("#DetailCoord.Text", NODE_COORDINATES.getOrDefault(node, ""));

        // Determine status and button visibility
        if (!prereqsMet) {
            // Locked: hide real info behind mystery
            builder.set("#DetailTitle.Text", "???");
            builder.set("#DetailDesc.Text", "");
            builder.set("#DetailStatus.Text", "");
            builder.set("#DetailStatus.Style.TextColor", COLOR_LOCKED_TEXT);
            builder.set("#UnlockBtnActive.Visible", false);
            builder.set("#UnlockBtnDisabled.Visible", false);
        } else if (isUnlocked) {
            builder.set("#DetailTitle.Text", node.getName());
            builder.set("#DetailDesc.Text", node.getDescription());
            builder.set("#DetailStatus.Text", "Already unlocked");
            builder.set("#DetailStatus.Style.TextColor", COLOR_UNLOCKED_TEXT);
            builder.set("#UnlockBtnActive.Visible", false);
            builder.set("#UnlockBtnDisabled.Visible", false);
        } else {
            // Prerequisites met, not yet unlocked
            builder.set("#DetailTitle.Text", node.getName());
            builder.set("#DetailDesc.Text", node.getDescription());
            if (canUnlock) {
                builder.set("#DetailStatus.Text", "");
                builder.set("#UnlockBtnActive.Visible", true);
                builder.set("#UnlockBtnDisabled.Visible", false);
            } else {
                builder.set("#DetailStatus.Text", "Not enough AP");
                builder.set("#DetailStatus.Style.TextColor", COLOR_LOCKED_TEXT);
                builder.set("#UnlockBtnActive.Visible", false);
                builder.set("#UnlockBtnDisabled.Visible", true);
            }
        }

        builder.set("#DetailPanel.Visible", true);
        sendUpdate(builder, null, false);
    }

    private void hideDetailPanel() {
        UICommandBuilder builder = new UICommandBuilder();
        builder.set("#DetailPanel.Visible", false);
        sendUpdate(builder, null, false);
    }

    private void handleUnlockClick(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (selectedNode == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        SkillTreeNode node = selectedNode;

        if (ascensionManager.canUnlockSkillNode(playerId, node)) {
            boolean success = ascensionManager.tryUnlockSkillNode(playerId, node);
            if (success) {
                // Refresh tree states and update detail panel to show unlocked state
                UICommandBuilder builder = new UICommandBuilder();
                updateAllNodeStates(ref, store, builder);
                sendUpdate(builder, null, false);

                // Re-show the detail panel with updated state
                showDetailPanel(playerId, node);
            }
        }
    }

    private void refreshNodeStates(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateAllNodeStates(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }

    /**
     * Converts ENUM_NAME to PascalCase (e.g. AUTO_RUNNERS -> AutoRunners).
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
