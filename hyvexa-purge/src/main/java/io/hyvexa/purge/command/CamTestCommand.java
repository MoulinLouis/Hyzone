package io.hyvexa.purge.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.PermissionUtils;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Test command for top-down camera.
 * Tracking task every 50ms overrides camera to fixed overhead position.
 * No lookMultiplier — mouse controls head rotation naturally.
 * Vertical head aim is allowed (cannot clamp without Teleport which disrupts camera).
 * Projectile direction should be handled at the combat level for horizontal-only fire.
 *
 * Usage:
 *   /cam on [distance] [pitch]  - Enable top-down camera (default: 15, -90)
 *   /cam set <param> <value>    - Adjust: distance, pitch, yaw
 *   /cam off                    - Reset camera
 *   /cam info                   - Show current settings
 */
public class CamTestCommand extends AbstractAsyncCommand {

    private float distance = 15f;
    private float pitchDeg = -90f;
    private float yawDeg = 0f;

    private volatile ScheduledFuture<?> trackingTask;
    private volatile UUID trackedPlayerId;

    public CamTestCommand() {
        super("cam", "Test top-down camera for Purge");
        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        if (!(sender instanceof Player player)) {
            ctx.sendMessage(Message.raw("Players only."));
            return CompletableFuture.completedFuture(null);
        }
        if (!PermissionUtils.isOp(player)) {
            ctx.sendMessage(Message.raw("OP only."));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return CompletableFuture.completedFuture(null);
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            String[] args = CommandUtils.getArgs(ctx);
            if (args.length == 0) {
                sendUsage(player);
                return;
            }

            switch (args[0].toLowerCase()) {
                case "on" -> {
                    if (args.length >= 2) {
                        try { distance = Float.parseFloat(args[1]); } catch (NumberFormatException ignored) {}
                    }
                    if (args.length >= 3) {
                        try { pitchDeg = Float.parseFloat(args[2]); } catch (NumberFormatException ignored) {}
                    }
                    startTracking(playerRef.getUuid(), world);
                    player.sendMessage(Message.raw("[cam] ON - distance=" + distance
                            + " pitch=" + pitchDeg + " yaw=" + yawDeg));
                }
                case "set" -> {
                    if (args.length < 3) {
                        player.sendMessage(Message.raw("Usage: /cam set <param> <value>"));
                        player.sendMessage(Message.raw("Params: distance, pitch, yaw"));
                        return;
                    }
                    String param = args[1].toLowerCase();
                    float value;
                    try {
                        value = Float.parseFloat(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(Message.raw("Invalid number: " + args[2]));
                        return;
                    }
                    switch (param) {
                        case "distance" -> distance = value;
                        case "pitch" -> pitchDeg = value;
                        case "yaw" -> yawDeg = value;
                        default -> {
                            player.sendMessage(Message.raw("Unknown param: " + param));
                            return;
                        }
                    }
                    player.sendMessage(Message.raw("[cam] Set " + param + "=" + value));
                }
                case "off" -> {
                    stopTracking();
                    CameraManager camMgr = store.getComponent(ref, CameraManager.getComponentType());
                    if (camMgr != null) {
                        camMgr.resetCamera(playerRef);
                    }
                    player.sendMessage(Message.raw("[cam] Camera reset."));
                }
                case "info" -> {
                    player.sendMessage(Message.raw("[cam] distance=" + distance
                            + " pitch=" + pitchDeg + " yaw=" + yawDeg));
                    player.sendMessage(Message.raw("[cam] tracking="
                            + (trackingTask != null && !trackingTask.isCancelled())));
                }
                default -> sendUsage(player);
            }
        }, world);
    }

    private void startTracking(UUID playerId, World world) {
        stopTracking();
        trackedPlayerId = playerId;
        trackingTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                world.execute(() -> tickCamera(world));
            } catch (Exception ignored) {
                stopTracking();
            }
        }, 0L, 50L, TimeUnit.MILLISECONDS);
    }

    private void stopTracking() {
        ScheduledFuture<?> task = trackingTask;
        if (task != null) {
            task.cancel(false);
            trackingTask = null;
        }
        trackedPlayerId = null;
    }

    private void tickCamera(World world) {
        UUID playerId = trackedPlayerId;
        if (playerId == null) return;

        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(playerId);
        if (ref == null || !ref.isValid()) {
            stopTracking();
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            stopTracking();
            return;
        }
        PacketHandler ph = playerRef.getPacketHandler();
        if (ph == null) {
            stopTracking();
            return;
        }

        ServerCameraSettings s = new ServerCameraSettings();
        s.distance = distance;
        s.isFirstPerson = false;
        s.eyeOffset = true;
        s.positionLerpSpeed = 1.0f;
        s.rotationLerpSpeed = 1.0f;
        s.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffsetRaycast;
        s.rotationType = RotationType.Custom;
        s.rotation = new Direction(
                (float) Math.toRadians(yawDeg),
                (float) Math.toRadians(pitchDeg),
                0f
        );

        ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, s));
    }

    private void sendUsage(Player player) {
        player.sendMessage(Message.raw("Usage: /cam <on|set|off|info>"));
        player.sendMessage(Message.raw("  on [distance] [pitch] - Top-down camera (default: 15, -90)"));
        player.sendMessage(Message.raw("  set <param> <value>   - Adjust: distance, pitch, yaw"));
        player.sendMessage(Message.raw("  off                   - Reset camera"));
        player.sendMessage(Message.raw("  info                  - Show current settings"));
    }
}
