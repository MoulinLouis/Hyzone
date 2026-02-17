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
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;
import io.hyvexa.ascend.hud.AscendHudManager;
import io.hyvexa.ascend.hud.ToastType;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.util.AscendModeGate;
import io.hyvexa.common.math.BigNumber;
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

    public ElevateCommand() {
        super("elevate", "Elevate your multiplier");
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
            if (plugin == null || plugin.getPlayerStore() == null) {
                player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            AscendPlayerStore playerStore = plugin.getPlayerStore();
            UUID playerId = playerRef.getUuid();
            BigNumber accumulatedVexa = playerStore.getElevationAccumulatedVexa(playerId);
            int currentElevation = playerStore.getElevationLevel(playerId);

            // Get cost multiplier from skill tree (Elevation Boost = -30%)
            BigNumber costMultiplier = (plugin.getSummitManager() != null)
                ? plugin.getSummitManager().getElevationCostMultiplier(playerId)
                : BigNumber.ONE;
            ElevationPurchaseResult purchase = AscendConstants.calculateElevationPurchase(
                currentElevation, accumulatedVexa, costMultiplier);

            if (purchase.levels <= 0) {
                BigNumber nextCost = AscendConstants.getElevationLevelUpCost(currentElevation, costMultiplier);
                player.sendMessage(Message.raw("[Ascend] You need "
                    + FormatUtils.formatBigNumber(nextCost) + " accumulated vexa to elevate.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            // Despawn all robots before resetting data to prevent completions with pre-reset multipliers
            RobotManager robotManager = plugin.getRobotManager();
            if (robotManager != null) {
                robotManager.despawnRobotsForPlayer(playerId);
            }

            // Perform elevation
            int newElevation = currentElevation + purchase.levels;
            playerStore.atomicSetElevationAndResetVexa(playerId, newElevation);

            // Toast notification
            AscendHudManager hm = plugin.getHudManager();
            if (hm != null) {
                hm.showToast(playerId, ToastType.ECONOMY, "Elevation: "
                    + AscendConstants.formatElevationMultiplier(currentElevation) + " -> "
                    + AscendConstants.formatElevationMultiplier(newElevation));
            }

            // Reset progress (vexa, map unlocks, runners)
            AscendMapStore mapStore = plugin.getMapStore();

            String firstMapId = null;
            if (mapStore != null) {
                List<AscendMap> maps = mapStore.listMapsSorted();
                if (!maps.isEmpty()) {
                    firstMapId = maps.get(0).getId();
                }
            }

            playerStore.resetProgressForElevation(playerId, firstMapId);

            if (plugin.getAchievementManager() != null) {
                plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
            }

            player.sendMessage(Message.raw("[Ascend] Elevated! "
                + AscendConstants.formatElevationMultiplier(currentElevation) + " -> "
                + AscendConstants.formatElevationMultiplier(newElevation)
                + " (+" + purchase.levels + " levels)")
                .color(SystemMessageUtils.SUCCESS));
        }, world);
    }
}
