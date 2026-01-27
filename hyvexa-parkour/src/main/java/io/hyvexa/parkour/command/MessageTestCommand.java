package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.common.util.SystemMessageUtils;
import io.hyvexa.duel.DuelConstants;
import io.hyvexa.parkour.util.ParkourModeGate;

import javax.annotation.Nonnull;

public class MessageTestCommand extends AbstractPlayerCommand {

    public MessageTestCommand() {
        super("messagetest", "Preview system messages.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (ParkourModeGate.denyIfNotParkour(ctx, world)) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (!PermissionUtils.isOp(player)) {
            ctx.sendMessage(SystemMessageUtils.serverError("You must be OP to use /messagetest."));
            return;
        }

        String playerName = playerRef.getUsername() != null ? playerRef.getUsername() : "Player";

        ctx.sendMessage(SystemMessageUtils.serverInfo("System message preview starting."));

        previewServerMessages(ctx, playerName);
        previewDuelMessages(ctx);
        previewParkourMessages(ctx, playerName);

        ctx.sendMessage(SystemMessageUtils.serverInfo("System message preview complete."));
    }

    private void previewServerMessages(CommandContext ctx, String playerName) {
        ctx.sendMessage(Message.join(
                Message.raw("[").color(SystemMessageUtils.SECONDARY),
                Message.raw("+").color(SystemMessageUtils.SUCCESS),
                Message.raw("] ").color(SystemMessageUtils.SECONDARY),
                Message.raw(playerName).color(SystemMessageUtils.PRIMARY_TEXT)
        ));
        ctx.sendMessage(Message.join(
                Message.raw("[").color(SystemMessageUtils.SECONDARY),
                Message.raw("-").color(SystemMessageUtils.ERROR),
                Message.raw("] ").color(SystemMessageUtils.SECONDARY),
                Message.raw(playerName).color(SystemMessageUtils.PRIMARY_TEXT)
        ));
        ctx.sendMessage(SystemMessageUtils.adminAnnouncement("Maintenance starts in 10 minutes."));
    }

    private void previewDuelMessages(CommandContext ctx) {
        ctx.sendMessage(SystemMessageUtils.duelSuccess(
                String.format(DuelConstants.MSG_QUEUE_JOINED, "Easy/Medium", 3)
        ));
        ctx.sendMessage(SystemMessageUtils.duelWarn(String.format(DuelConstants.MSG_QUEUE_ALREADY, 3)));
        ctx.sendMessage(SystemMessageUtils.duelWarn(DuelConstants.MSG_IN_MATCH));
        ctx.sendMessage(SystemMessageUtils.duelWarn(DuelConstants.MSG_IN_PARKOUR));
        ctx.sendMessage(SystemMessageUtils.duelWarn(
                String.format(DuelConstants.MSG_DUEL_UNLOCK_REQUIRED, 5, 2, 3, 5)
        ));
        ctx.sendMessage(SystemMessageUtils.duelSuccess(
                String.format(DuelConstants.MSG_MATCH_FOUND, "Rival", "Sky Ruins")
        ));
        ctx.sendMessage(SystemMessageUtils.duelInfo("Checkpoint reached."));
        ctx.sendMessage(SystemMessageUtils.duelWarn("You did not reach all checkpoints."));
        ctx.sendMessage(SystemMessageUtils.duelSuccess(String.format(DuelConstants.MSG_WIN,
                FormatUtils.formatDuration(12_345L), "Rival")));
        ctx.sendMessage(SystemMessageUtils.duelWarn(String.format(DuelConstants.MSG_LOSE,
                FormatUtils.formatDuration(12_345L), "Rival")));
        ctx.sendMessage(SystemMessageUtils.duelWarn(DuelConstants.MSG_FORFEITED));
        ctx.sendMessage(SystemMessageUtils.duelSuccess(String.format(DuelConstants.MSG_WIN_FORFEIT, "Rival")));
        ctx.sendMessage(SystemMessageUtils.duelSuccess(String.format(DuelConstants.MSG_WIN_DISCONNECT, "Rival")));
        ctx.sendMessage(SystemMessageUtils.duelWarn("Opponent disconnected. Returning you to the queue."));
    }

    private void previewParkourMessages(CommandContext ctx, String playerName) {
        ctx.sendMessage(SystemMessageUtils.withParkourPrefix(
                Message.raw("Run started: ").color(SystemMessageUtils.SECONDARY),
                Message.raw("Tutorial").color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(".").color(SystemMessageUtils.SECONDARY)
        ));
        ctx.sendMessage(SystemMessageUtils.parkourInfo("Checkpoint reached."));
        ctx.sendMessage(SystemMessageUtils.parkourWarn("You did not reach all checkpoints."));
        ctx.sendMessage(SystemMessageUtils.parkourInfo("Finish line reached."));
        ctx.sendMessage(SystemMessageUtils.parkourSuccess(
                "Map completed in " + FormatUtils.formatDuration(54_321L) + "."
        ));
        ctx.sendMessage(SystemMessageUtils.parkourSuccess("You earned 250 XP."));
        ctx.sendMessage(SystemMessageUtils.parkourSuccess("Rank up! You are now Master."));

        Message broadcast = SystemMessageUtils.withParkourPrefix(
                Message.raw("[").color(SystemMessageUtils.SECONDARY),
                FormatUtils.getRankMessage("Gold"),
                Message.raw("] ").color(SystemMessageUtils.SECONDARY),
                Message.raw(playerName).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(" finished ").color(SystemMessageUtils.SECONDARY),
                Message.raw("Ancient Spires").color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(" (").color(SystemMessageUtils.SECONDARY),
                Message.raw("Hard").color("#ff9f1c"),
                Message.raw(") in ").color(SystemMessageUtils.SECONDARY),
                Message.raw(FormatUtils.formatDuration(48_765L)).color(SystemMessageUtils.SUCCESS),
                Message.raw(" - ").color(SystemMessageUtils.SECONDARY),
                Message.raw("#1").color("#ffd166"),
                Message.raw(" WR!").color("#ffd166"),
                Message.raw(".").color(SystemMessageUtils.SECONDARY)
        );
        ctx.sendMessage(broadcast);
        ctx.sendMessage(SystemMessageUtils.withParkourPrefix(
                Message.raw("WORLD RECORD! SAY GG!").color(SystemMessageUtils.SUCCESS).bold(true)
        ));

        ctx.sendMessage(SystemMessageUtils.withParkourPrefix(
                Message.raw(playerName).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(" is now ").color(SystemMessageUtils.SECONDARY),
                FormatUtils.getRankMessage("VexaGod"),
                Message.raw(" (but for how long?)").color(SystemMessageUtils.SECONDARY)
        ));

        Message supporterRank = Message.raw("VIP").color("#b76cff");
        ctx.sendMessage(SystemMessageUtils.withServerPrefix(
                Message.raw("[").color(SystemMessageUtils.SECONDARY),
                supporterRank,
                Message.raw("] ").color(SystemMessageUtils.SECONDARY),
                Message.raw(playerName).color(SystemMessageUtils.PRIMARY_TEXT),
                Message.raw(" supports our server and helps us a lot!").color(SystemMessageUtils.SECONDARY)
        ));
    }
}
