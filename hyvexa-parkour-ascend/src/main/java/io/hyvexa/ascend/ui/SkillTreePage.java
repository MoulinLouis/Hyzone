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
import io.hyvexa.ascend.AscendConstants.SkillTreeNode;
import io.hyvexa.ascend.AscendConstants.SkillTreePath;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.common.ui.ButtonEventData;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SkillTreePage extends BaseAscendPage {

    private static final String BUTTON_CLOSE = "Close";
    private static final String BUTTON_NODE_PREFIX = "Node_";

    // Path colors
    private static final String COLOR_COIN = "#ffd166";
    private static final String COLOR_SPEED = "#3b82f6";
    private static final String COLOR_MANUAL = "#7c3aed";
    private static final String COLOR_HYBRID = "#14b8a6";
    private static final String COLOR_ULTIMATE = "#f43f5e";
    private static final String COLOR_LOCKED = "#4b5563";

    // Node state colors
    private static final String COLOR_AVAILABLE_TEXT = "#fbbf24";
    private static final String COLOR_UNLOCKED_TEXT = "#4ade80";
    private static final String COLOR_LOCKED_TEXT = "#6b7280";

    private final AscendPlayerStore playerStore;
    private final AscensionManager ascensionManager;

    // Maps enum nodes to UI element IDs (camelCase, no underscores)
    private static final Map<SkillTreeNode, String> NODE_UI_IDS = new HashMap<>();
    static {
        NODE_UI_IDS.put(SkillTreeNode.COIN_T1_STARTING_COINS, "CoinT1");
        NODE_UI_IDS.put(SkillTreeNode.COIN_T2_BASE_REWARD, "CoinT2");
        NODE_UI_IDS.put(SkillTreeNode.COIN_T3_ELEVATION_COST, "CoinT3");
        NODE_UI_IDS.put(SkillTreeNode.COIN_T4_SUMMIT_COST, "CoinT4");
        NODE_UI_IDS.put(SkillTreeNode.COIN_T5_AUTO_ELEVATION, "CoinT5");
        NODE_UI_IDS.put(SkillTreeNode.SPEED_T1_BASE_SPEED, "SpeedT1");
        NODE_UI_IDS.put(SkillTreeNode.SPEED_T2_MAX_LEVEL, "SpeedT2");
        NODE_UI_IDS.put(SkillTreeNode.SPEED_T3_EVOLUTION_COST, "SpeedT3");
        NODE_UI_IDS.put(SkillTreeNode.SPEED_T4_DOUBLE_LAP, "SpeedT4");
        NODE_UI_IDS.put(SkillTreeNode.SPEED_T5_INSTANT_EVOLVE, "SpeedT5");
        NODE_UI_IDS.put(SkillTreeNode.MANUAL_T1_MULTIPLIER, "ManualT1");
        NODE_UI_IDS.put(SkillTreeNode.MANUAL_T2_CHAIN_BONUS, "ManualT2");
        NODE_UI_IDS.put(SkillTreeNode.MANUAL_T3_SESSION_BONUS, "ManualT3");
        NODE_UI_IDS.put(SkillTreeNode.MANUAL_T4_RUNNER_BOOST, "ManualT4");
        NODE_UI_IDS.put(SkillTreeNode.MANUAL_T5_PERSONAL_BEST, "ManualT5");
        NODE_UI_IDS.put(SkillTreeNode.HYBRID_OFFLINE_EARNINGS, "Hybrid1");
        NODE_UI_IDS.put(SkillTreeNode.HYBRID_SUMMIT_PERSIST, "Hybrid2");
        NODE_UI_IDS.put(SkillTreeNode.ULTIMATE_GLOBAL_BOOST, "Ultimate");
    }

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

        // Bind events for all nodes
        for (SkillTreeNode node : SkillTreeNode.values()) {
            String uiId = NODE_UI_IDS.get(node);
            if (uiId != null) {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#Node" + uiId,
                    EventData.of(ButtonEventData.KEY_BUTTON, BUTTON_NODE_PREFIX + node.name()), false);
            }
        }

        updateNodeStates(ref, store, commandBuilder);
    }

    private void updateNodeStates(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder commandBuilder) {
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
            String uiId = NODE_UI_IDS.get(node);
            if (uiId == null) {
                continue;
            }

            boolean isUnlocked = unlockedNodes.contains(node);
            boolean canUnlock = ascensionManager.canUnlockSkillNode(playerId, node);

            // Get path color
            String pathColor = getPathColor(node.getPath());

            // Update border color
            String borderColor = (isUnlocked || canUnlock) ? pathColor : COLOR_LOCKED;
            commandBuilder.set("#Border" + uiId + ".Background", borderColor);

            // Update status text and color
            if (isUnlocked) {
                commandBuilder.set("#Status" + uiId + ".Text", "UNLOCKED");
                commandBuilder.set("#Status" + uiId + ".Style.TextColor", COLOR_UNLOCKED_TEXT);
            } else if (canUnlock) {
                commandBuilder.set("#Status" + uiId + ".Text", "AVAILABLE");
                commandBuilder.set("#Status" + uiId + ".Style.TextColor", COLOR_AVAILABLE_TEXT);
            } else {
                commandBuilder.set("#Status" + uiId + ".Text", "LOCKED");
                commandBuilder.set("#Status" + uiId + ".Style.TextColor", COLOR_LOCKED_TEXT);
            }
        }

        // Update connection line colors based on unlock status
        updateLineColors(commandBuilder, unlockedNodes);
    }

    private void updateLineColors(UICommandBuilder commandBuilder, Set<SkillTreeNode> unlockedNodes) {
        // COIN path lines
        updateLine(commandBuilder, "LineCoinT1T2", unlockedNodes.contains(SkillTreeNode.COIN_T1_STARTING_COINS), COLOR_COIN);
        updateLine(commandBuilder, "LineCoinT2T3", unlockedNodes.contains(SkillTreeNode.COIN_T2_BASE_REWARD), COLOR_COIN);
        updateLine(commandBuilder, "LineCoinT3T4", unlockedNodes.contains(SkillTreeNode.COIN_T3_ELEVATION_COST), COLOR_COIN);
        updateLine(commandBuilder, "LineCoinT4T5", unlockedNodes.contains(SkillTreeNode.COIN_T4_SUMMIT_COST), COLOR_COIN);

        // SPEED path lines
        updateLine(commandBuilder, "LineSpeedT1T2", unlockedNodes.contains(SkillTreeNode.SPEED_T1_BASE_SPEED), COLOR_SPEED);
        updateLine(commandBuilder, "LineSpeedT2T3", unlockedNodes.contains(SkillTreeNode.SPEED_T2_MAX_LEVEL), COLOR_SPEED);
        updateLine(commandBuilder, "LineSpeedT3T4", unlockedNodes.contains(SkillTreeNode.SPEED_T3_EVOLUTION_COST), COLOR_SPEED);
        updateLine(commandBuilder, "LineSpeedT4T5", unlockedNodes.contains(SkillTreeNode.SPEED_T4_DOUBLE_LAP), COLOR_SPEED);

        // MANUAL path lines
        updateLine(commandBuilder, "LineManualT1T2", unlockedNodes.contains(SkillTreeNode.MANUAL_T1_MULTIPLIER), COLOR_MANUAL);
        updateLine(commandBuilder, "LineManualT2T3", unlockedNodes.contains(SkillTreeNode.MANUAL_T2_CHAIN_BONUS), COLOR_MANUAL);
        updateLine(commandBuilder, "LineManualT3T4", unlockedNodes.contains(SkillTreeNode.MANUAL_T3_SESSION_BONUS), COLOR_MANUAL);
        updateLine(commandBuilder, "LineManualT4T5", unlockedNodes.contains(SkillTreeNode.MANUAL_T4_RUNNER_BOOST), COLOR_MANUAL);

        // HYBRID connection lines - light up when T5 is unlocked
        updateLine(commandBuilder, "LineCoinHybrid1", unlockedNodes.contains(SkillTreeNode.COIN_T5_AUTO_ELEVATION), COLOR_COIN);
        updateLine(commandBuilder, "LineSpeedHybrid1", unlockedNodes.contains(SkillTreeNode.SPEED_T5_INSTANT_EVOLVE), COLOR_SPEED);
        updateLine(commandBuilder, "LineSpeedHybrid2", unlockedNodes.contains(SkillTreeNode.SPEED_T5_INSTANT_EVOLVE), COLOR_SPEED);
        updateLine(commandBuilder, "LineManualHybrid2", unlockedNodes.contains(SkillTreeNode.MANUAL_T5_PERSONAL_BEST), COLOR_MANUAL);

        // ULTIMATE connection lines
        boolean hybrid1Unlocked = unlockedNodes.contains(SkillTreeNode.HYBRID_OFFLINE_EARNINGS);
        boolean hybrid2Unlocked = unlockedNodes.contains(SkillTreeNode.HYBRID_SUMMIT_PERSIST);
        updateLine(commandBuilder, "LineHybrid1Ult", hybrid1Unlocked, COLOR_HYBRID);
        updateLine(commandBuilder, "LineHybrid2Ult", hybrid2Unlocked, COLOR_HYBRID);
        updateLine(commandBuilder, "LineUltH", hybrid1Unlocked || hybrid2Unlocked, COLOR_ULTIMATE);
        updateLine(commandBuilder, "LineUltV", hybrid1Unlocked || hybrid2Unlocked, COLOR_ULTIMATE);
    }

    private void updateLine(UICommandBuilder commandBuilder, String lineId, boolean active, String activeColor) {
        commandBuilder.set("#" + lineId + ".Background", active ? activeColor : COLOR_LOCKED);
    }

    private String getPathColor(SkillTreePath path) {
        return switch (path) {
            case COIN -> COLOR_COIN;
            case SPEED -> COLOR_SPEED;
            case MANUAL -> COLOR_MANUAL;
            case HYBRID -> COLOR_HYBRID;
            case ULTIMATE -> COLOR_ULTIMATE;
        };
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
            } catch (IllegalArgumentException e) {
                // Invalid node name
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
                refreshNodeStates(ref, store);
            } else {
                player.sendMessage(Message.raw("[Skill Tree] Failed to unlock skill.")
                    .color(SystemMessageUtils.SECONDARY));
            }
            return;
        }

        // Cannot unlock - show requirements
        showRequirements(player, node, summary);
    }

    private void showRequirements(Player player, SkillTreeNode node, AscensionManager.SkillTreeSummary summary) {
        player.sendMessage(Message.raw("[Skill Tree] " + node.getName() + " - LOCKED")
            .color(SystemMessageUtils.SECONDARY));

        // Check skill points
        if (summary.availablePoints() <= 0) {
            player.sendMessage(Message.raw("  No skill points. Ascend to earn more!")
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Check prerequisite
        SkillTreeNode prereq = node.getPrerequisite();
        if (prereq != null && !summary.unlockedNodes().contains(prereq)) {
            player.sendMessage(Message.raw("  Requires: " + prereq.getName())
                .color(SystemMessageUtils.SECONDARY));
            return;
        }

        // Check hybrid requirements
        if (node.getPath() == SkillTreePath.HYBRID) {
            int coinPoints = countPointsInPath(summary, SkillTreePath.COIN);
            int speedPoints = countPointsInPath(summary, SkillTreePath.SPEED);
            int manualPoints = countPointsInPath(summary, SkillTreePath.MANUAL);

            if (node == SkillTreeNode.HYBRID_OFFLINE_EARNINGS) {
                player.sendMessage(Message.raw("  Need " + AscendConstants.HYBRID_PATH_REQUIREMENT + " in COIN + SPEED")
                    .color(SystemMessageUtils.SECONDARY));
                player.sendMessage(Message.raw("  Current: COIN=" + coinPoints + ", SPEED=" + speedPoints)
                    .color(SystemMessageUtils.SECONDARY));
            } else if (node == SkillTreeNode.HYBRID_SUMMIT_PERSIST) {
                player.sendMessage(Message.raw("  Need " + AscendConstants.HYBRID_PATH_REQUIREMENT + " in any 2 paths")
                    .color(SystemMessageUtils.SECONDARY));
                player.sendMessage(Message.raw("  Current: COIN=" + coinPoints + ", SPEED=" + speedPoints + ", MANUAL=" + manualPoints)
                    .color(SystemMessageUtils.SECONDARY));
            }
            return;
        }

        // Check ultimate requirements
        if (node.getPath() == SkillTreePath.ULTIMATE) {
            int totalSpent = summary.spentPoints();
            player.sendMessage(Message.raw("  Need " + AscendConstants.ULTIMATE_TOTAL_REQUIREMENT + " total points spent")
                .color(SystemMessageUtils.SECONDARY));
            player.sendMessage(Message.raw("  Current: " + totalSpent + " spent")
                .color(SystemMessageUtils.SECONDARY));
        }
    }

    private int countPointsInPath(AscensionManager.SkillTreeSummary summary, SkillTreePath path) {
        int count = 0;
        for (SkillTreeNode node : summary.unlockedNodes()) {
            if (node.getPath() == path) {
                count++;
            }
        }
        return count;
    }

    private void refreshNodeStates(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder updateBuilder = new UICommandBuilder();
        updateNodeStates(ref, store, updateBuilder);
        sendUpdate(updateBuilder, null, false);
    }
}
