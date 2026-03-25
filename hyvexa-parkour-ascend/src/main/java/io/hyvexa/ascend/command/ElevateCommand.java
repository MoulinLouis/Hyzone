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
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.AscendConstants.ElevationPurchaseResult;
import io.hyvexa.ascend.achievement.AchievementManager;
import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.interaction.AbstractAscendPageInteraction;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.hud.ToastType;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.math.BigNumber;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.core.state.ModeMessages;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /elevate - Performs elevation directly via chat command (bypass for UI lag).
 * Equivalent to clicking the Elevate button on the Elevation page.
 */
public class ElevateCommand extends AbstractAsyncCommand {
    private final AscendPlayerStore playerStore;
    private final ChallengeManager challengeManager;
    private final RobotManager robotManager;
    private final AscendHudManager hudManager;
    private final AscendMapStore mapStore;
    private final AchievementManager achievementManager;

    public ElevateCommand(AscendPlayerStore playerStore, ChallengeManager challengeManager,
                          RobotManager robotManager, AscendHudManager hudManager,
                          AscendMapStore mapStore, AchievementManager achievementManager) {
        super("elevate", "Elevate your multiplier");
        this.playerStore = playerStore;
        this.challengeManager = challengeManager;
        this.robotManager = robotManager;
        this.hudManager = hudManager;
        this.mapStore = mapStore;
        this.achievementManager = achievementManager;
        this.setPermissionGroup(GameMode.Adventure);
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

            if (playerStore == null) {
                player.sendMessage(AbstractAscendPageInteraction.LOADING_MESSAGE);
                return;
            }

            UUID playerId = playerRef.getUuid();

            // Block elevation during active challenge that blocks elevation
            if (challengeManager != null && challengeManager.isElevationBlocked(playerId)) {
                player.sendMessage(Message.raw("[Ascend] Elevation is blocked during this challenge.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            BigNumber accumulatedVolt = playerStore.getElevationAccumulatedVolt(playerId);
            int currentElevation = playerStore.getElevationLevel(playerId);

            ElevationPurchaseResult purchase = AscendConstants.calculateElevationPurchase(
                currentElevation, accumulatedVolt, BigNumber.ONE);

            if (purchase.levels <= 0) {
                BigNumber nextCost = AscendConstants.getElevationLevelUpCost(currentElevation, BigNumber.ONE);
                player.sendMessage(Message.raw("[Ascend] You need "
                    + FormatUtils.formatBigNumber(nextCost) + " accumulated volt to elevate.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            // Despawn all robots before resetting data to prevent completions with pre-reset multipliers
            if (robotManager != null) {
                robotManager.despawnRobotsForPlayer(playerId);
            }

            // Perform elevation
            int newElevation = currentElevation + purchase.levels;
            playerStore.atomicSetElevationAndResetVolt(playerId, newElevation);

            // Toast notification
            if (hudManager != null) {
                hudManager.showToast(playerId, ToastType.ECONOMY, "Elevation: "
                    + AscendConstants.formatElevationMultiplier(currentElevation) + " -> "
                    + AscendConstants.formatElevationMultiplier(newElevation));
            }

            // Reset progress (volt, map unlocks, runners)
            String firstMapId = null;
            if (mapStore != null) {
                List<AscendMap> maps = mapStore.listMapsSorted();
                if (!maps.isEmpty()) {
                    firstMapId = maps.get(0).getId();
                }
            }

            playerStore.resetProgressForElevation(playerId, firstMapId);

            if (achievementManager != null) {
                achievementManager.checkAndUnlockAchievements(playerId, player);
            }

            player.sendMessage(Message.raw("[Ascend] Elevated! "
                + AscendConstants.formatElevationMultiplier(currentElevation) + " -> "
                + AscendConstants.formatElevationMultiplier(newElevation)
                + " (+" + purchase.levels + " levels)")
                .color(SystemMessageUtils.SUCCESS));
        }, world);
    }
}
