package io.hyvexa.ascend.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSetEntityLight;
import com.hypixel.hytale.protocol.packets.camera.CameraShakeEffect;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent2D;
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent3D;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import io.hyvexa.common.util.CommandUtils;
import io.hyvexa.common.util.PermissionUtils;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Test command for experimenting with cinematic capabilities one by one.
 * Temporary - will be removed once the ascension cinematic is finalized.
 */
public class CinematicTestCommand extends AbstractAsyncCommand {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CinematicTest");
        t.setDaemon(true);
        return t;
    });

    public CinematicTestCommand() {
        super("ctest", "Cinematic test commands");
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

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            PacketHandler ph = playerRef.getPacketHandler();
            if (ph == null) {
                player.sendMessage(Message.raw("[CTest] No packet handler."));
                return;
            }

            String[] args = CommandUtils.getArgs(ctx);
            if (args.length == 0) {
                showHelp(player);
                return;
            }

            switch (args[0].toLowerCase()) {
                case "cam3p" -> testThirdPerson(player, ph, args);
                case "camreset" -> testCameraReset(player, playerRef, store, ref);
                case "camorbit" -> testOrbit(player, ph, playerRef, store, ref, world, args);
                case "camlock" -> testLocked(player, ph, args);
                case "campos" -> testCameraPosition(player, ph, args);
                case "shake" -> testShake(player, ph, args);
                case "zoomout" -> testZoomOut(player, ph, playerRef, store, ref, world, args);
                case "sequence" -> testFullSequence(player, ph, playerRef, store, ref, world);
                case "ascension" -> testAscensionCinematic(player, ph, playerRef, store, ref, world);
                case "particle" -> testParticle(player, ph, store, ref, args);
                case "listparticles" -> listParticles(player, args);
                case "sound" -> testSound(player, ph, store, ref, args);
                case "listsounds" -> listSounds(player, args);
                case "light" -> testLight(player, ph, args);
                case "help" -> showHelp(player);
                default -> player.sendMessage(Message.raw("[CTest] Unknown: " + args[0] + ". Use /ctest help"));
            }
        }, world);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static float deg(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private ServerCameraSettings base3p(float distance) {
        ServerCameraSettings s = new ServerCameraSettings();
        s.positionLerpSpeed = 0.15f;
        s.rotationLerpSpeed = 0.15f;
        s.distance = distance;
        s.isFirstPerson = false;
        s.eyeOffset = true;
        s.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffsetRaycast;
        return s;
    }

    private void resetCamera(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref) {
        CameraManager camMgr = store.getComponent(ref, CameraManager.getComponentType());
        if (camMgr != null) {
            camMgr.resetCamera(playerRef);
        }
    }

    // ── Test: Third person ───────────────────────────────────────────────

    private void testThirdPerson(Player player, PacketHandler ph, String[] args) {
        float distance = 8f;
        if (args.length > 1) {
            try { distance = Float.parseFloat(args[1]); } catch (NumberFormatException ignored) {}
        }

        ServerCameraSettings s = base3p(distance);
        ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, s));
        player.sendMessage(Message.raw("[CTest] 3rd person, distance=" + distance));
    }

    // ── Test: Camera reset ───────────────────────────────────────────────

    private void testCameraReset(Player player, PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref) {
        // Restore movement in case cinematic was interrupted
        MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
        if (mm != null) {
            mm.resetDefaultsAndUpdate(ref, store);
        }
        resetCamera(playerRef, store, ref);
        player.sendMessage(Message.raw("[CTest] Camera reset to default."));
    }

    // ── Test: Orbit ──────────────────────────────────────────────────────

    private void testOrbit(Player player, PacketHandler ph, PlayerRef playerRef,
                           Store<EntityStore> store, Ref<EntityStore> ref, World world, String[] args) {
        float distance = 10f;
        float durationSec = 3f;
        if (args.length > 1) {
            try { distance = Float.parseFloat(args[1]); } catch (NumberFormatException ignored) {}
        }
        if (args.length > 2) {
            try { durationSec = Float.parseFloat(args[2]); } catch (NumberFormatException ignored) {}
        }

        float orbitDist = distance;
        long intervalMs = 50;
        int totalSteps = (int) (durationSec * 1000 / intervalMs);

        player.sendMessage(Message.raw("[CTest] Orbit (dist=" + orbitDist + ", " + durationSec + "s)..."));

        for (int i = 0; i < totalSteps; i++) {
            final int step = i;
            scheduler.schedule(() -> {
                try {
                    world.execute(() -> {
                        if (!ref.isValid()) return;
                        float angle = (float) (step * 2.0 * Math.PI / totalSteps);

                        ServerCameraSettings s = base3p(orbitDist);
                        s.positionLerpSpeed = 0.3f;
                        s.rotationLerpSpeed = 0.3f;
                        s.rotationType = RotationType.Custom;
                        s.rotation = new Direction(angle, deg(-15), 0f);
                        ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, s));
                    });
                } catch (Exception ignored) {}
            }, i * intervalMs, TimeUnit.MILLISECONDS);
        }

        scheduler.schedule(() -> {
            try {
                world.execute(() -> {
                    if (!ref.isValid()) return;
                    resetCamera(playerRef, store, ref);
                    player.sendMessage(Message.raw("[CTest] Orbit done."));
                });
            } catch (Exception ignored) {}
        }, totalSteps * intervalMs + 200, TimeUnit.MILLISECONDS);
    }

    // ── Test: Locked camera (mouse look disabled) ────────────────────────

    private void testLocked(Player player, PacketHandler ph, String[] args) {
        float distance = 6f;
        if (args.length > 1) {
            try { distance = Float.parseFloat(args[1]); } catch (NumberFormatException ignored) {}
        }

        ServerCameraSettings s = base3p(distance);
        // Lock: disable mouse look via zero multiplier, freeze player movement
        s.applyLookType = ApplyLookType.Rotation;
        s.lookMultiplier = new Vector2f(0f, 0f);
        s.skipCharacterPhysics = true;
        s.rotationType = RotationType.Custom;
        s.rotation = new Direction(0f, deg(-10), 0f);

        ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, s));
        player.sendMessage(Message.raw("[CTest] Camera LOCKED (look+move disabled, dist=" + distance + "). /ctest camreset to restore."));
    }

    // ── Test: Camera at custom angle (degrees) ───────────────────────────

    private void testCameraPosition(Player player, PacketHandler ph, String[] args) {
        float distance = 12f;
        float pitchDeg = -30f;
        float yawDeg = 0f;

        if (args.length > 1) {
            try { distance = Float.parseFloat(args[1]); } catch (NumberFormatException ignored) {}
        }
        if (args.length > 2) {
            try { pitchDeg = Float.parseFloat(args[2]); } catch (NumberFormatException ignored) {}
        }
        if (args.length > 3) {
            try { yawDeg = Float.parseFloat(args[3]); } catch (NumberFormatException ignored) {}
        }

        ServerCameraSettings s = base3p(distance);
        s.rotationType = RotationType.Custom;
        s.rotation = new Direction(deg(yawDeg), deg(pitchDeg), 0f);

        ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, s));
        player.sendMessage(Message.raw("[CTest] Camera: dist=" + distance + ", pitch=" + pitchDeg + "°, yaw=" + yawDeg + "°. /ctest camreset to restore."));
    }

    // ── Test: Camera shake ───────────────────────────────────────────────

    private void testShake(Player player, PacketHandler ph, String[] args) {
        float intensity = 0.5f;
        if (args.length > 1) {
            try { intensity = Float.parseFloat(args[1]); } catch (NumberFormatException ignored) {}
        }

        ph.writeNoCache(new CameraShakeEffect(0, intensity, AccumulationMode.Set));
        player.sendMessage(Message.raw("[CTest] Shake (intensity=" + intensity + ")."));
    }

    // ── Test: Smooth zoom out ────────────────────────────────────────────

    private void testZoomOut(Player player, PacketHandler ph, PlayerRef playerRef,
                             Store<EntityStore> store, Ref<EntityStore> ref, World world, String[] args) {
        float startDist = 3f;
        float endDist = 20f;
        if (args.length > 1) {
            try { endDist = Float.parseFloat(args[1]); } catch (NumberFormatException ignored) {}
        }

        long intervalMs = 50;
        int totalSteps = 40; // 2 seconds

        player.sendMessage(Message.raw("[CTest] Zoom out " + startDist + " -> " + endDist + "..."));

        for (int i = 0; i <= totalSteps; i++) {
            final int step = i;
            final float finalEnd = endDist;
            scheduler.schedule(() -> {
                try {
                    world.execute(() -> {
                        if (!ref.isValid()) return;
                        float t = (float) step / totalSteps;
                        // Ease-out quad: decelerate
                        float eased = 1f - (1f - t) * (1f - t);
                        float dist = startDist + (finalEnd - startDist) * eased;

                        ServerCameraSettings s = base3p(dist);
                        s.positionLerpSpeed = 0.5f;
                        s.rotationLerpSpeed = 0.5f;
                        // Slight upward tilt as we zoom out
                        float pitchDeg = -5f - 25f * eased;
                        s.rotationType = RotationType.Custom;
                        s.rotation = new Direction(0f, deg(pitchDeg), 0f);
                        ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, s));
                    });
                } catch (Exception ignored) {}
            }, step * intervalMs, TimeUnit.MILLISECONDS);
        }

        scheduler.schedule(() -> {
            try {
                world.execute(() -> {
                    if (!ref.isValid()) return;
                    resetCamera(playerRef, store, ref);
                    player.sendMessage(Message.raw("[CTest] Zoom out done."));
                });
            } catch (Exception ignored) {}
        }, (totalSteps + 1) * intervalMs + 500, TimeUnit.MILLISECONDS);
    }

    // ── Test: Full mini-sequence ─────────────────────────────────────────

    private void testFullSequence(Player player, PacketHandler ph, PlayerRef playerRef,
                                  Store<EntityStore> store, Ref<EntityStore> ref, World world) {
        player.sendMessage(Message.raw("[CTest] Starting test sequence...").color("#FFD700"));

        long ms = 0;

        // Phase 1: Lock camera, switch to 3rd person close (0-500ms)
        scheduleCamera(ph, world, ref, ms, () -> {
            ServerCameraSettings s = base3p(4f);
            s.applyLookType = ApplyLookType.Rotation;
            s.lookMultiplier = new Vector2f(0f, 0f);
            s.skipCharacterPhysics = true;
            s.rotationType = RotationType.Custom;
            s.rotation = new Direction(0f, deg(-10), 0f);
            return s;
        });
        ms += 500;

        // Phase 2: Slow orbit + zoom out (500ms - 3500ms)
        int orbitSteps = 60;
        long orbitInterval = 50;
        for (int i = 0; i < orbitSteps; i++) {
            final int step = i;
            final long delay = ms + i * orbitInterval;
            scheduler.schedule(() -> {
                try {
                    world.execute(() -> {
                        if (!ref.isValid()) return;
                        float t = (float) step / orbitSteps;
                        float angle = (float) (t * 2.0 * Math.PI);
                        float dist = 4f + 8f * t; // 4 -> 12

                        ServerCameraSettings s = base3p(dist);
                        s.positionLerpSpeed = 0.3f;
                        s.rotationLerpSpeed = 0.3f;
                        s.applyLookType = ApplyLookType.Rotation;
                        s.lookMultiplier = new Vector2f(0f, 0f);
                        s.skipCharacterPhysics = true;
                        s.rotationType = RotationType.Custom;
                        s.rotation = new Direction(angle, deg(-15f - 15f * t), 0f);
                        ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, s));
                    });
                } catch (Exception ignored) {}
            }, delay, TimeUnit.MILLISECONDS);
        }
        ms += orbitSteps * orbitInterval;

        // Phase 3: Camera shake (3500ms)
        final long shakeTime = ms;
        scheduler.schedule(() -> {
            try {
                world.execute(() -> {
                    if (!ref.isValid()) return;
                    ph.writeNoCache(new CameraShakeEffect(0, 0.3f, AccumulationMode.Set));
                });
            } catch (Exception ignored) {}
        }, shakeTime, TimeUnit.MILLISECONDS);
        ms += 1000;

        // Phase 4: Reset (4500ms)
        final long resetTime = ms;
        scheduler.schedule(() -> {
            try {
                world.execute(() -> {
                    if (!ref.isValid()) return;
                    resetCamera(playerRef, store, ref);
                    player.sendMessage(Message.raw("[CTest] Sequence done!").color("#FFD700"));
                });
            } catch (Exception ignored) {}
        }, resetTime, TimeUnit.MILLISECONDS);
    }

    // ── Test: Ascension Cinematic ─────────────────────────────────────────

    private void testAscensionCinematic(Player player, PacketHandler ph, PlayerRef playerRef,
                                         Store<EntityStore> store, Ref<EntityStore> ref, World world) {
        io.hyvexa.ascend.ascension.AscensionCinematic.play(player, ph, playerRef, store, ref, world);
    }

    // ── Cinematic helpers ────────────────────────────────────────────────

    private void spawnParticleAtPlayer(Store<EntityStore> store, Ref<EntityStore> ref,
                                        PacketHandler ph, String particleId, float scale) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        var pos = transform.getPosition();
        ph.writeNoCache(new SpawnParticleSystem(
            particleId,
            new Position(pos.getX(), pos.getY() + 1.0, pos.getZ()),
            new Direction(0f, 0f, 0f),
            scale,
            new Color((byte) 255, (byte) 255, (byte) 255)
        ));
    }

    private void playSound2D(PacketHandler ph, String soundId, float volume, float pitch) {
        try {
            int index = SoundEvent.getAssetMap().getIndex(soundId);
            if (index >= 0) {
                ph.writeNoCache(new PlaySoundEvent2D(index, SoundCategory.SFX, volume, pitch));
            }
        } catch (Exception ignored) {}
    }

    // ── Test: Particle ──────────────────────────────────────────────────

    private void testParticle(Player player, PacketHandler ph, Store<EntityStore> store, Ref<EntityStore> ref, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw("[CTest] Usage: /ctest particle <id> [scale]"));
            player.sendMessage(Message.raw("[CTest] Use /ctest listparticles to see available IDs"));
            return;
        }

        String particleId = args[1];
        float scale = 1.0f;
        if (args.length > 2) {
            try { scale = Float.parseFloat(args[2]); } catch (NumberFormatException ignored) {}
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            player.sendMessage(Message.raw("[CTest] No transform component."));
            return;
        }

        var pos = transform.getPosition();
        // Spawn particle slightly above player
        ph.writeNoCache(new SpawnParticleSystem(
            particleId,
            new Position(pos.getX(), pos.getY() + 1.5, pos.getZ()),
            new Direction(0f, 0f, 0f),
            scale,
            new Color((byte) 255, (byte) 255, (byte) 255)
        ));
        player.sendMessage(Message.raw("[CTest] Particle '" + particleId + "' spawned (scale=" + scale + ")."));
    }

    // ── List: Particles ──────────────────────────────────────────────────

    private void listParticles(Player player, String[] args) {
        try {
            var assetMap = ParticleSystem.getAssetMap();
            var map = assetMap.getAssetMap();

            String filter = args.length > 1 ? args[1].toLowerCase() : "";
            int count = 0;
            int max = 20;

            player.sendMessage(Message.raw("[CTest] Particle systems" + (filter.isEmpty() ? "" : " matching '" + filter + "'") + ":").color("#FFD700"));
            for (var key : map.keySet()) {
                if (!filter.isEmpty() && !key.toLowerCase().contains(filter)) continue;
                player.sendMessage(Message.raw("  " + key));
                count++;
                if (count >= max) {
                    player.sendMessage(Message.raw("  ... and more. Use /ctest listparticles <filter>"));
                    break;
                }
            }
            if (count == 0) {
                player.sendMessage(Message.raw("  (none found)"));
            }
        } catch (Exception e) {
            player.sendMessage(Message.raw("[CTest] Error listing particles: " + e.getMessage()));
        }
    }

    // ── Test: Sound ──────────────────────────────────────────────────────

    private void testSound(Player player, PacketHandler ph, Store<EntityStore> store, Ref<EntityStore> ref, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Message.raw("[CTest] Usage: /ctest sound <id_or_index> [volume] [pitch]"));
            player.sendMessage(Message.raw("[CTest] Use /ctest listsounds to see available IDs"));
            return;
        }

        float volume = 1.0f;
        float pitch = 1.0f;
        if (args.length > 2) {
            try { volume = Float.parseFloat(args[2]); } catch (NumberFormatException ignored) {}
        }
        if (args.length > 3) {
            try { pitch = Float.parseFloat(args[3]); } catch (NumberFormatException ignored) {}
        }

        // Try as index first, then as string ID
        int soundIndex;
        try {
            soundIndex = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            // Look up string ID in asset map
            try {
                var assetMap = SoundEvent.getAssetMap();
                soundIndex = assetMap.getIndex(args[1]);
                if (soundIndex < 0) {
                    player.sendMessage(Message.raw("[CTest] Sound ID '" + args[1] + "' not found."));
                    return;
                }
            } catch (Exception ex) {
                player.sendMessage(Message.raw("[CTest] Error looking up sound: " + ex.getMessage()));
                return;
            }
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        var pos = transform.getPosition();
        ph.writeNoCache(new PlaySoundEvent3D(
            soundIndex,
            SoundCategory.SFX,
            new Position(pos.getX(), pos.getY() + 1.0, pos.getZ()),
            volume,
            pitch
        ));
        player.sendMessage(Message.raw("[CTest] Sound index=" + soundIndex + " (vol=" + volume + ", pitch=" + pitch + ")."));
    }

    // ── List: Sounds ─────────────────────────────────────────────────────

    private void listSounds(Player player, String[] args) {
        try {
            var assetMap = SoundEvent.getAssetMap();
            int nextIndex = assetMap.getNextIndex();

            String filter = args.length > 1 ? args[1].toLowerCase() : "";
            int count = 0;
            int max = 20;

            player.sendMessage(Message.raw("[CTest] Sound events (total ~" + nextIndex + ")" + (filter.isEmpty() ? "" : " matching '" + filter + "'") + ":").color("#FFD700"));

            for (int i = 0; i < nextIndex && count < max; i++) {
                var se = assetMap.getAsset(i);
                if (se == null) continue;
                String id = se.getId();
                if (!filter.isEmpty() && !id.toLowerCase().contains(filter)) continue;
                player.sendMessage(Message.raw("  [" + i + "] " + id));
                count++;
            }
            if (count == 0) {
                player.sendMessage(Message.raw("  (none found)"));
            } else if (count >= max) {
                player.sendMessage(Message.raw("  ... and more. Use /ctest listsounds <filter>"));
            }
        } catch (Exception e) {
            player.sendMessage(Message.raw("[CTest] Error listing sounds: " + e.getMessage()));
        }
    }

    // ── Test: Entity light ───────────────────────────────────────────────

    private void testLight(Player player, PacketHandler ph, String[] args) {
        int r = 255, g = 200, b = 100;
        int radius = 15;

        if (args.length > 1) {
            try { r = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }
        if (args.length > 2) {
            try { g = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
        }
        if (args.length > 3) {
            try { b = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
        }
        if (args.length > 4) {
            try { radius = Integer.parseInt(args[4]); } catch (NumberFormatException ignored) {}
        }

        int networkId = player.getNetworkId();
        ph.writeNoCache(new BuilderToolSetEntityLight(
            networkId,
            new ColorLight((byte) radius, (byte) r, (byte) g, (byte) b)
        ));
        player.sendMessage(Message.raw("[CTest] Light on entity #" + networkId + " (r=" + r + " g=" + g + " b=" + b + " radius=" + radius + "). Use /ctest light 0 0 0 0 to remove."));
    }

    private void scheduleCamera(PacketHandler ph, World world, Ref<EntityStore> ref,
                                long delayMs, java.util.function.Supplier<ServerCameraSettings> settingsFactory) {
        scheduler.schedule(() -> {
            try {
                world.execute(() -> {
                    if (!ref.isValid()) return;
                    ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, settingsFactory.get()));
                });
            } catch (Exception ignored) {}
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    // ── Help ─────────────────────────────────────────────────────────────

    private void showHelp(Player player) {
        player.sendMessage(Message.raw("[CTest] === Camera ===").color("#FFD700"));
        player.sendMessage(Message.raw("  /ctest cam3p [dist]              - 3rd person"));
        player.sendMessage(Message.raw("  /ctest camreset                  - Reset to normal"));
        player.sendMessage(Message.raw("  /ctest camorbit [dist] [sec]     - Orbit"));
        player.sendMessage(Message.raw("  /ctest camlock [dist]            - Lock look+move"));
        player.sendMessage(Message.raw("  /ctest campos [dist] [pitch] [yaw] - Custom angle (deg)"));
        player.sendMessage(Message.raw("  /ctest shake [intensity]         - Camera shake"));
        player.sendMessage(Message.raw("  /ctest zoomout [endDist]         - Smooth zoom out"));
        player.sendMessage(Message.raw("  /ctest sequence                  - Full test sequence"));
        player.sendMessage(Message.raw("  /ctest ascension                 - Ascension cinematic"));
        player.sendMessage(Message.raw("[CTest] === Effects ===").color("#FFD700"));
        player.sendMessage(Message.raw("  /ctest particle <id> [scale]     - Spawn particle"));
        player.sendMessage(Message.raw("  /ctest listparticles [filter]    - List particle IDs"));
        player.sendMessage(Message.raw("  /ctest sound <id|index> [vol] [pitch] - Play sound"));
        player.sendMessage(Message.raw("  /ctest listsounds [filter]       - List sound IDs"));
        player.sendMessage(Message.raw("  /ctest light [r] [g] [b] [radius] - Entity glow"));
    }
}
