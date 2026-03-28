package io.hyvexa.ascend.mine.command;

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
import io.hyvexa.ascend.mine.data.MinePlayerProgress;
import io.hyvexa.ascend.mine.data.BlockConfigStore;
import io.hyvexa.ascend.mine.data.MinerConfigStore;
import io.hyvexa.ascend.mine.data.TierConfigStore;
import io.hyvexa.ascend.mine.data.MinePlayerStore;
import io.hyvexa.ascend.mine.MineGateChecker;
import io.hyvexa.ascend.mine.achievement.MineAchievementTracker;
import io.hyvexa.ascend.mine.data.MineHierarchyStore;
import io.hyvexa.ascend.mine.robot.MineRobotManager;
import io.hyvexa.ascend.mine.ui.MineAchievementsPage;
import io.hyvexa.ascend.mine.ui.MineSellPage;
import io.hyvexa.ascend.mine.ui.MinePage;
import io.hyvexa.ascend.mine.ui.MineBagPage;
import io.hyvexa.common.WorldConstants;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.core.state.ModeMessages;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class MineCommand extends AbstractAsyncCommand {
    private final MineGateChecker mineGateChecker;
    private final MinePlayerStore minePlayerStore;
    private final BlockConfigStore blockConfigStore;
    private final MinerConfigStore minerConfigStore;
    private final TierConfigStore tierConfigStore;
    private final MineAchievementTracker mineAchievementTracker;
    private final MineRobotManager mineRobotManager;
    private final MineHierarchyStore mineHierarchyStore;

    public MineCommand(MineGateChecker mineGateChecker, MinePlayerStore minePlayerStore,
                       BlockConfigStore blockConfigStore, MinerConfigStore minerConfigStore,
                       TierConfigStore tierConfigStore,
                       MineAchievementTracker mineAchievementTracker,
                       MineRobotManager mineRobotManager,
                       MineHierarchyStore mineHierarchyStore) {
        super("mine", "Mine commands");
        this.mineGateChecker = mineGateChecker;
        this.minePlayerStore = minePlayerStore;
        this.blockConfigStore = blockConfigStore;
        this.minerConfigStore = minerConfigStore;
        this.tierConfigStore = tierConfigStore;
        this.mineAchievementTracker = mineAchievementTracker;
        this.mineRobotManager = mineRobotManager;
        this.mineHierarchyStore = mineHierarchyStore;
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
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

            if (mineGateChecker == null) {
                player.sendMessage(Message.raw("[Mine] Mining system not available.").color(SystemMessageUtils.SECONDARY));
                return;
            }
            if (mineGateChecker.denyMineAccess(playerRef.getUuid(), player)) {
                return;
            }

            if (minePlayerStore == null) {
                player.sendMessage(Message.raw("[Mine] Mining system not available.").color(SystemMessageUtils.SECONDARY));
                return;
            }

            String[] args = CommandUtils.tokenize(ctx);
            MinePlayerProgress progress = minePlayerStore.getOrCreatePlayer(playerRef.getUuid());

            if (args.length == 0) {
                MineBagPage page = new MineBagPage(playerRef, progress,
                    blockConfigStore, minePlayerStore, mineAchievementTracker);
                player.getPageManager().openCustomPage(ref, store, page);
                return;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "sell" -> {
                    MineSellPage page = new MineSellPage(playerRef, progress,
                        blockConfigStore, minePlayerStore, mineAchievementTracker);
                    player.getPageManager().openCustomPage(ref, store, page);
                }
                case "upgrades" -> {
                    MinePage page = new MinePage(playerRef, progress,
                        minerConfigStore, tierConfigStore, minePlayerStore,
                        mineRobotManager, mineGateChecker, mineAchievementTracker,
                        mineHierarchyStore);
                    player.getPageManager().openCustomPage(ref, store, page);
                }
                case "achievements" -> {
                    MineAchievementsPage page = new MineAchievementsPage(playerRef, mineAchievementTracker);
                    player.getPageManager().openCustomPage(ref, store, page);
                }
                case "addcrystals" -> {
                    if (!PermissionUtils.isOp(player)) {
                        player.sendMessage(Message.raw("[Mine] You do not have permission to use this command.").color(SystemMessageUtils.SECONDARY));
                        return;
                    }
                    if (args.length < 2) {
                        player.sendMessage(Message.raw("[Mine] Usage: /mine addcrystals <amount>").color(SystemMessageUtils.SECONDARY));
                        return;
                    }
                    try {
                        double amount = Double.parseDouble(args[1]);
                        if (!Double.isFinite(amount)) {
                            player.sendMessage(Message.raw("[Mine] Invalid number: " + args[1]).color(SystemMessageUtils.SECONDARY));
                            return;
                        }
                        if (amount < 0) {
                            player.sendMessage(Message.raw("[Mine] Crystal amount must be 0 or higher.").color(SystemMessageUtils.SECONDARY));
                            return;
                        }
                        progress.addCrystals(amount);
                        minePlayerStore.markDirty(playerRef.getUuid());
                        player.sendMessage(Message.raw("[Mine] Added " + amount + " crystals. Total: " + FormatUtils.formatDouble(progress.getCrystals())).color(SystemMessageUtils.SECONDARY));
                    } catch (NumberFormatException e) {
                        player.sendMessage(Message.raw("[Mine] Invalid number: " + args[1]).color(SystemMessageUtils.SECONDARY));
                    }
                }
                default -> player.sendMessage(Message.raw("Unknown subcommand. Use: /mine, /mine sell, /mine upgrades, /mine achievements"));
            }
        }, world);
    }
}
