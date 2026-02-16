package io.hyvexa.ascend.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants.SkillTreeNode;
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.ascension.AscensionManager;
import io.hyvexa.ascend.ascension.AscensionManager.SkillTreeSummary;
import io.hyvexa.ascend.util.AscendModeGate;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /skill [coord] - Unlock a skill tree node via chat command (bypass for UI lag).
 * Uses the coordinate system visible on the skill tree (e.g. /skill 1_1, /skill 3_2).
 * Without arguments, lists available skills with their coordinates and status.
 * Equivalent to clicking Unlock on the /ascend skills page.
 */
public class SkillCommand extends AbstractAsyncCommand {

    public SkillCommand() {
        super("skill", "Unlock a skill tree node");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            ctx.sendMessage(Message.raw("This command can only be used by players."));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            if (AscendModeGate.denyIfNotAscend(ctx, world)) {
                return;
            }

            ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
            if (plugin == null || plugin.getAscensionManager() == null) {
                player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            AscensionManager ascensionManager = plugin.getAscensionManager();
            UUID playerId = playerRef.getUuid();

            String[] args = CommandUtils.getArgs(ctx);
            if (args.length == 0) {
                showSkillList(player, ascensionManager, playerId);
                return;
            }

            String input = args[0];
            SkillTreeNode node = parseCoord(input);
            if (node == null) {
                player.sendMessage(Message.raw("[Skill] Unknown coordinate '" + input + "'. Use /skill to see the list.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            SkillTreeSummary summary = ascensionManager.getSkillTreeSummary(playerId);

            if (summary.unlockedNodes().contains(node)) {
                player.sendMessage(Message.raw("[Skill] " + node.getName() + " is already unlocked.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            if (!node.hasPrerequisitesSatisfied(summary.unlockedNodes())) {
                player.sendMessage(Message.raw("[Skill] " + node.getName() + " - prerequisites not met.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            if (summary.availablePoints() < node.getCost()) {
                player.sendMessage(Message.raw("[Skill] Not enough AP. Need " + node.getCost()
                    + " AP, you have " + summary.availablePoints() + " AP.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            boolean success = ascensionManager.tryUnlockSkillNode(playerId, node);
            if (!success) {
                player.sendMessage(Message.raw("[Skill] Failed to unlock " + node.getName() + ".")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            SkillTreeSummary updated = ascensionManager.getSkillTreeSummary(playerId);
            player.sendMessage(Message.raw("[Skill] Unlocked: " + node.getName()
                + " (" + node.getCost() + " AP) - " + node.getDescription()
                + " | AP remaining: " + updated.availablePoints())
                .color(SystemMessageUtils.SUCCESS));
        }, world);
    }

    private void showSkillList(Player player, AscensionManager ascensionManager, UUID playerId) {
        SkillTreeSummary summary = ascensionManager.getSkillTreeSummary(playerId);
        Set<SkillTreeNode> unlocked = summary.unlockedNodes();

        player.sendMessage(Message.raw("[Skill] AP: " + summary.availablePoints()
            + " / " + summary.totalPoints() + " (spent: " + summary.spentPoints() + ")")
            .color(SystemMessageUtils.INFO));
        player.sendMessage(Message.raw("[Skill] Usage: /skill <coord> (e.g. /skill 1_1)")
            .color(SystemMessageUtils.SECONDARY));

        for (SkillTreeNode node : SkillTreeNode.values()) {
            String status;
            String color;
            if (unlocked.contains(node)) {
                status = "[UNLOCKED]";
                color = SystemMessageUtils.SUCCESS;
            } else if (node.hasPrerequisitesSatisfied(unlocked)
                    && summary.availablePoints() >= node.getCost()) {
                status = "[AVAILABLE]";
                color = SystemMessageUtils.WARN;
            } else if (node.hasPrerequisitesSatisfied(unlocked)) {
                status = "[" + node.getCost() + " AP]";
                color = SystemMessageUtils.SECONDARY;
            } else {
                status = "[LOCKED]";
                color = SystemMessageUtils.SECONDARY;
            }

            String coord = getCoord(node);
            player.sendMessage(Message.raw("  " + coord + " " + status + " " + node.getName()
                + " (" + node.getCost() + " AP) - " + node.getDescription())
                .color(color));
        }
    }

    /**
     * Parses a coordinate string (e.g. "1_1", "3:2", "3-2") to the corresponding skill tree node.
     * Coordinates match those displayed on the /ascend skills UI.
     */
    private static SkillTreeNode parseCoord(String input) {
        // Normalize separators: accept _ : - .
        String normalized = input.replace(':', '_').replace('-', '_').replace('.', '_');
        return switch (normalized) {
            case "1_1" -> SkillTreeNode.AUTO_RUNNERS;
            case "2_1" -> SkillTreeNode.AUTO_EVOLUTION;
            case "3_1" -> SkillTreeNode.RUNNER_SPEED;
            case "3_2" -> SkillTreeNode.EVOLUTION_POWER;
            case "4_1" -> SkillTreeNode.RUNNER_SPEED_2;
            case "5_1" -> SkillTreeNode.AUTO_SUMMIT;
            case "5_2" -> SkillTreeNode.AUTO_ELEVATION;
            case "6_1" -> SkillTreeNode.ASCENSION_CHALLENGES;
            case "7_1" -> SkillTreeNode.MOMENTUM_SURGE;
            case "7_2" -> SkillTreeNode.MOMENTUM_ENDURANCE;
            case "8_1" -> SkillTreeNode.MULTIPLIER_BOOST;
            case "9_1" -> SkillTreeNode.RUNNER_SPEED_3;
            case "9_2" -> SkillTreeNode.EVOLUTION_POWER_2;
            default -> null;
        };
    }

    private static String getCoord(SkillTreeNode node) {
        return switch (node) {
            case AUTO_RUNNERS -> "1:1";
            case AUTO_EVOLUTION -> "2:1";
            case RUNNER_SPEED -> "3:1";
            case EVOLUTION_POWER -> "3:2";
            case RUNNER_SPEED_2 -> "4:1";
            case AUTO_SUMMIT -> "5:1";
            case AUTO_ELEVATION -> "5:2";
            case ASCENSION_CHALLENGES -> "6:1";
            case MOMENTUM_SURGE -> "7:1";
            case MOMENTUM_ENDURANCE -> "7:2";
            case MULTIPLIER_BOOST -> "8:1";
            case RUNNER_SPEED_3 -> "9:1";
            case EVOLUTION_POWER_2 -> "9:2";
        };
    }
}
