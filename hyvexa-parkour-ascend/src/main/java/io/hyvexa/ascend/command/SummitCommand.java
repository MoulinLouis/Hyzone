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
import io.hyvexa.ascend.SummitConstants;
import io.hyvexa.ascend.SummitConstants.SummitCategory;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.interaction.AbstractAscendPageInteraction;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.hud.ToastType;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.summit.SummitManager;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.core.state.ModeMessages;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /summit <category> - Performs summit directly via chat command (bypass for UI lag).
 * Categories: multiplier, speed, evolution
 * Equivalent to clicking a Summit button on the Summit page.
 */
public class SummitCommand extends AbstractAsyncCommand {
    private final SummitManager summitManager;
    private final AscendPlayerStore playerStore;
    private final ChallengeManager challengeManager;
    private final RobotManager robotManager;
    private final AscendHudManager hudManager;
    private final AchievementManager achievementManager;

    public SummitCommand(SummitManager summitManager, AscendPlayerStore playerStore,
                         ChallengeManager challengeManager, RobotManager robotManager,
                         AscendHudManager hudManager, AchievementManager achievementManager) {
        super("summit", "Summit into a category");
        this.summitManager = summitManager;
        this.playerStore = playerStore;
        this.challengeManager = challengeManager;
        this.robotManager = robotManager;
        this.hudManager = hudManager;
        this.achievementManager = achievementManager;
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
        if (store.getExternalData() == null) return CompletableFuture.completedFuture(null);
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            if (ModeGate.denyIfNot(ctx, world, WorldConstants.WORLD_ASCEND, ModeMessages.MESSAGE_ENTER_ASCEND)) {
                return;
            }

            String[] args = CommandUtils.tokenize(ctx);
            if (args.length == 0) {
                player.sendMessage(Message.raw("[Summit] Usage: /summit <multiplier|speed|evolution>")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            SummitCategory category = parseCategory(args[0]);
            if (category == null) {
                player.sendMessage(Message.raw("[Summit] Unknown category '" + args[0]
                    + "'. Use: multiplier, speed, evolution")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            if (summitManager == null || playerStore == null) {
                player.sendMessage(AbstractAscendPageInteraction.LOADING_MESSAGE);
                return;
            }

            UUID playerId = playerRef.getUuid();

            // Check challenge block
            if (challengeManager != null && challengeManager.isSummitBlocked(playerId, category)) {
                player.sendMessage(Message.raw("[Summit] " + category.getDisplayName()
                    + " is locked during your active challenge.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            if (!summitManager.canSummit(playerId)) {
                BigNumber volt = playerStore.volt().getVolt(playerId);
                String minVolt = FormatUtils.formatBigNumber(
                    BigNumber.fromLong(SummitConstants.SUMMIT_MIN_VOLT));
                player.sendMessage(Message.raw("[Summit] Need " + minVolt
                    + " volt to Summit. You have: " + FormatUtils.formatBigNumber(volt))
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            SummitManager.SummitPreview preview = summitManager.previewSummit(playerId, category);
            if (!preview.hasGain()) {
                player.sendMessage(Message.raw("[Summit] Insufficient volt for level gain.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            // Despawn all robots before resetting data to prevent completions with pre-reset multipliers
            if (robotManager != null) {
                robotManager.despawnRobotsForPlayer(playerId);
            }

            // Perform summit
            SummitManager.SummitResult result = summitManager.performSummit(playerId, category);
            if (!result.succeeded()) {
                player.sendMessage(Message.raw("[Summit] Summit failed.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            // Toast
            if (hudManager != null) {
                hudManager.showToast(playerId, ToastType.EVOLUTION,
                    category.getDisplayName() + " Lv." + FormatUtils.formatLong(preview.currentLevel())
                    + " -> Lv." + FormatUtils.formatLong(result.newLevel())
                    + " | " + formatBonus(category, preview.currentBonus())
                    + " -> " + formatBonus(category, preview.newBonus()));
            }

            player.sendMessage(Message.raw("[Summit] " + category.getDisplayName()
                + " Lv." + FormatUtils.formatLong(preview.currentLevel())
                + " -> Lv." + FormatUtils.formatLong(result.newLevel())
                + " | " + formatBonus(category, preview.currentBonus())
                + " -> " + formatBonus(category, preview.newBonus()))
                .color(SystemMessageUtils.SUCCESS));
            player.sendMessage(Message.raw("[Summit] Progress reset: volt, elevation, multipliers, runners, map unlocks")
                .color(SystemMessageUtils.SECONDARY));

            // Achievements
            if (achievementManager != null) {
                achievementManager.checkAndUnlockAchievements(playerId, player);
            }
        }, world);
    }

    private static SummitCategory parseCategory(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "multiplier", "mult", "mg" -> SummitCategory.MULTIPLIER_GAIN;
            case "speed", "runner", "rs" -> SummitCategory.RUNNER_SPEED;
            case "evolution", "evo", "ep" -> SummitCategory.EVOLUTION_POWER;
            default -> null;
        };
    }

    private static String formatBonus(SummitCategory category, double value) {
        return String.format(Locale.US, "x%.2f", value);
    }
}
