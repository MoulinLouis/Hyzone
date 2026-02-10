package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.parkour.util.ParkourModeGate;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CheckpointCommand extends AbstractPlayerCommand {

    private static final Message MESSAGE_CHECKPOINT_SET = Message.raw("Checkpoint saved.");
    private static final Message MESSAGE_CHECKPOINT_CLEARED = Message.raw("Checkpoint cleared.");
    private static final Message MESSAGE_CHECKPOINT_MISSING = Message.raw("No checkpoint set.");
    private static final Message MESSAGE_TELEPORTED = Message.raw("Teleported to checkpoint.");
    private static final Message MESSAGE_USAGE = Message.raw("Usage: /cp [set|clear]");
    private static final Map<UUID, Checkpoint> CHECKPOINTS = new ConcurrentHashMap<>();

    private final OptionalArg<String> actionArg;

    public CheckpointCommand() {
        super("cp", "Save or return to your checkpoint.");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
        this.actionArg = this.withOptionalArg("action", "set|clear", ArgTypes.STRING);
    }

    public static void clearCheckpoint(UUID playerId) {
        if (playerId == null) {
            return;
        }
        CHECKPOINTS.remove(playerId);
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef player, @Nonnull World world) {
        if (ParkourModeGate.denyIfNotParkour(ctx, world)) {
            return;
        }
        String action = null;
        if (this.actionArg.provided(ctx)) {
            action = this.actionArg.get(ctx);
        } else {
            action = readPositionalAction(ctx);
        }
        if (action != null) {
            action = action.trim();
            if (!action.isEmpty()) {
                action = action.toLowerCase(Locale.ROOT);
            }
        }

        if (action == null || action.isEmpty()) {
            teleportToCheckpoint(ctx, store, ref, player, world);
            return;
        }

        switch (action) {
            case "set":
                saveCheckpoint(ctx, player);
                break;
            case "clear":
                clearCheckpoint(ctx, player);
                break;
            default:
                ctx.sendMessage(MESSAGE_USAGE);
                break;
        }
    }

    private void saveCheckpoint(@Nonnull CommandContext ctx, @Nonnull PlayerRef player) {
        Transform transform = player.getTransform().clone();
        Vector3f headRotation = player.getHeadRotation();
        Vector3f headRotationCopy = headRotation == null ? null : headRotation.clone();
        CHECKPOINTS.put(player.getUuid(), new Checkpoint(transform, headRotationCopy));
        ctx.sendMessage(MESSAGE_CHECKPOINT_SET);
    }

    private void teleportToCheckpoint(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                                      @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        Checkpoint checkpoint = CHECKPOINTS.get(player.getUuid());
        if (checkpoint == null) {
            ctx.sendMessage(MESSAGE_CHECKPOINT_MISSING);
            return;
        }

        Transform transform = checkpoint.transform.clone();
        Teleport teleport = Teleport.createForPlayer(world, transform);
        if (checkpoint.headRotation != null) {
            teleport.setHeadRotation(checkpoint.headRotation.clone());
        }
        store.addComponent(ref, Teleport.getComponentType(), teleport);
        ctx.sendMessage(MESSAGE_TELEPORTED);
    }

    private void clearCheckpoint(@Nonnull CommandContext ctx, @Nonnull PlayerRef player) {
        if (CHECKPOINTS.remove(player.getUuid()) == null) {
            ctx.sendMessage(MESSAGE_CHECKPOINT_MISSING);
            return;
        }
        ctx.sendMessage(MESSAGE_CHECKPOINT_CLEARED);
    }

    private static String readPositionalAction(@Nonnull CommandContext ctx) {
        String input = ctx.getInputString();
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length == 0) {
            return null;
        }
        String first = tokens[0];
        if (first.startsWith("/")) {
            first = first.substring(1);
        }
        String commandName = ctx.getCalledCommand().getName();
        int actionIndex = first.equalsIgnoreCase(commandName) ? 1 : 0;
        if (actionIndex >= tokens.length) {
            return null;
        }
        return tokens[actionIndex];
    }

    private static final class Checkpoint {
        private final Transform transform;
        private final Vector3f headRotation;

        private Checkpoint(Transform transform, Vector3f headRotation) {
            this.transform = transform;
            this.headRotation = headRotation;
        }
    }
}
