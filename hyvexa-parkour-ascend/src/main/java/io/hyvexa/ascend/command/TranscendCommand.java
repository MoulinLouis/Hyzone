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
import io.hyvexa.ascend.ParkourAscendPlugin;
import io.hyvexa.ascend.transcendence.TranscendenceManager;
import io.hyvexa.ascend.util.AscendModeGate;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.common.util.SystemMessageUtils;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /transcend - Test command to perform transcendence directly via chat.
 */
public class TranscendCommand extends AbstractAsyncCommand {

    public TranscendCommand() {
        super("transcend", "Transcend (4th prestige reset)");
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

        if (!PermissionUtils.isOp(player)) {
            ctx.sendMessage(Message.raw("[Transcendence] This feature is currently locked.")
                .color(SystemMessageUtils.SECONDARY));
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
            if (plugin == null || plugin.getPlayerStore() == null || plugin.getTranscendenceManager() == null) {
                player.sendMessage(Message.raw("[Ascend] Ascend systems are still loading.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            UUID playerId = playerRef.getUuid();
            TranscendenceManager transcendenceManager = plugin.getTranscendenceManager();

            if (!transcendenceManager.isEligible(playerId)) {
                player.sendMessage(Message.raw("[Transcendence] Not eligible. Need 1e100 volt with BREAK_ASCENSION active and all challenges completed.")
                    .color(SystemMessageUtils.SECONDARY));
                return;
            }

            // Despawn all robots before resetting data to prevent completions with pre-reset multipliers
            if (plugin.getRobotManager() != null) {
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

            if (plugin.getAchievementManager() != null) {
                plugin.getAchievementManager().checkAndUnlockAchievements(playerId, player);
            }
        }, world);
    }
}
