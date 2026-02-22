package io.hyvexa.parkour.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.camera.SetFlyCameraMode;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.PermissionUtils;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class SpectatorCommand extends AbstractAsyncCommand {

    public SpectatorCommand() {
        super("spec", "Spectator mode commands");
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
            ctx.sendMessage(Message.raw("You must be OP to use this command."));
            return CompletableFuture.completedFuture(null);
        }

        String[] args = CommandUtils.getArgs(ctx);
        if (args.length < 1) {
            sendUsage(ctx);
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
            if (playerRef == null) return;

            PacketHandler packetHandler = playerRef.getPacketHandler();
            if (packetHandler == null) return;

            switch (args[0].toLowerCase()) {
                case "on" -> {
                    packetHandler.writeNoCache(new SetFlyCameraMode(true));
                    player.sendMessage(Message.raw("Fly camera enabled."));
                }
                case "off" -> {
                    packetHandler.writeNoCache(new SetFlyCameraMode(false));
                    player.sendMessage(Message.raw("Fly camera disabled."));
                }
                case "watch" -> handleWatch(player, playerRef, packetHandler, store, ref, args);
                case "stop" -> handleStop(player, playerRef, store, ref);
                default -> sendUsage(ctx);
            }
        }, world);
    }

    private void handleWatch(Player player, PlayerRef playerRef, PacketHandler packetHandler,
                             Store<EntityStore> store, Ref<EntityStore> ref, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw("Usage: /spec watch <player>"));
            return;
        }

        String targetName = args[1];
        PlayerRef targetRef = findPlayer(targetName);
        if (targetRef == null) {
            player.sendMessage(Message.raw("Player not found: " + targetName));
            return;
        }
        if (targetRef.getUuid().equals(playerRef.getUuid())) {
            player.sendMessage(Message.raw("You cannot spectate yourself."));
            return;
        }

        Ref<EntityStore> targetEntityRef = targetRef.getReference();
        if (targetEntityRef == null || !targetEntityRef.isValid()) {
            player.sendMessage(Message.raw("Target player entity not available."));
            return;
        }

        Store<EntityStore> targetStore = targetEntityRef.getStore();
        Player targetPlayer = targetStore.getComponent(targetEntityRef, Player.getComponentType());
        if (targetPlayer == null) {
            player.sendMessage(Message.raw("Target player entity not available."));
            return;
        }

        int targetNetworkId = targetPlayer.getNetworkId();

        ServerCameraSettings s = new ServerCameraSettings();
        s.attachedToType = AttachedToType.EntityId;
        s.attachedToEntityId = targetNetworkId;
        s.distance = 0f;
        s.isFirstPerson = false;
        s.eyeOffset = true;
        s.positionLerpSpeed = 0.15f;
        s.rotationLerpSpeed = 0.15f;
        s.skipCharacterPhysics = true;
        s.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffsetRaycast;

        packetHandler.writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, s));
        player.sendMessage(Message.raw("Now spectating " + targetRef.getUsername() + ". Use /spec stop to exit."));
    }

    private void handleStop(Player player, PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref) {
        CameraManager camMgr = store.getComponent(ref, CameraManager.getComponentType());
        if (camMgr != null) {
            camMgr.resetCamera(playerRef);
        }
        player.sendMessage(Message.raw("Spectating stopped."));
    }

    private PlayerRef findPlayer(String name) {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef != null && name.equalsIgnoreCase(playerRef.getUsername())) {
                return playerRef;
            }
        }
        return null;
    }

    private void sendUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("Usage: /spec <on|off|watch|stop>"));
        ctx.sendMessage(Message.raw("  on/off - Toggle fly camera"));
        ctx.sendMessage(Message.raw("  watch <player> - Spectate a player"));
        ctx.sendMessage(Message.raw("  stop - Stop spectating"));
    }
}
