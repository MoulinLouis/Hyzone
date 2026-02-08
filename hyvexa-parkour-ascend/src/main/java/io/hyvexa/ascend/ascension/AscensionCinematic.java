package io.hyvexa.ascend.ascension;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.camera.CameraShakeEffect;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent2D;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Plays the ascension cinematic (~10 seconds) with camera orbit, particles, and sounds.
 * Triggered automatically when a player crosses the ascension coin threshold,
 * and available via /ctest ascension for testing.
 */
public class AscensionCinematic {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AscensionCinematic");
        t.setDaemon(true);
        return t;
    });

    /**
     * Plays the full ascension cinematic for a player.
     * Must be called from the world thread.
     *
     * Phase 1 (0-500ms): Lock camera in natural 3rd person, sound + particles
     * Phase 2 (500-6500ms): Orbit 1.5 rotations + zoom out (relative to player yaw)
     * Phase 3 (6500-8000ms): Camera holds in front, particles intensify, build-up sound
     * Phase 4 (8000-8500ms): Fast zoom in + shake + impact
     * Phase 5 (8500-10000ms): Brief hold, then reset
     */
    public static void play(Player player, PacketHandler ph, PlayerRef playerRef,
                            Store<EntityStore> store, Ref<EntityStore> ref, World world) {
        // Read player's current yaw so the orbit is relative to their facing direction
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        float playerYaw = (transform != null) ? transform.getRotation().getYaw() : 0f;
        float frontYaw = playerYaw + (float) Math.PI;

        long ms = 0;

        // Freeze player movement for the entire cinematic
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager != null) {
            var settings = movementManager.getSettings();
            if (settings != null) {
                settings.baseSpeed = 0f;
                settings.jumpForce = 0f;
                settings.acceleration = 0f;
                settings.horizontalFlySpeed = 0f;
                settings.verticalFlySpeed = 0f;
                movementManager.update(ph);
            }
        }

        // Phase 1: Lock camera in natural 3rd person + effects
        SCHEDULER.schedule(() -> {
            try {
                world.execute(() -> {
                    if (!ref.isValid()) return;
                    ServerCameraSettings s = base3p(3f);
                    s.applyLookType = ApplyLookType.Rotation;
                    s.lookMultiplier = new Vector2f(0f, 0f);
                    s.skipCharacterPhysics = true;
                    ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, s));
                    playSound2D(ph, "SFX_Portal_Neutral_Open", 1.5f, 0.8f);
                    spawnParticleAtPlayer(store, ref, ph, "Magic_Sparks_GS", 1.5f);
                });
            } catch (Exception ignored) {}
        }, ms, TimeUnit.MILLISECONDS);
        ms += 500;

        // Phase 2: Orbit + zoom out + particles (6 seconds)
        int orbitSteps = 120;
        long orbitInterval = 50;
        for (int i = 0; i < orbitSteps; i++) {
            final int step = i;
            final long delay = ms + i * orbitInterval;
            SCHEDULER.schedule(() -> {
                try {
                    world.execute(() -> {
                        if (!ref.isValid()) return;
                        float t = (float) step / orbitSteps;
                        float angle = playerYaw + (float) (t * 3.0 * Math.PI);
                        float eased = 1f - (1f - t) * (1f - t);
                        float dist = 3f + 11f * eased;
                        float pitchDeg = -10f - 20f * eased;

                        ServerCameraSettings s = base3p(dist);
                        s.positionLerpSpeed = 0.25f;
                        s.rotationLerpSpeed = 0.25f;
                        s.applyLookType = ApplyLookType.Rotation;
                        s.lookMultiplier = new Vector2f(0f, 0f);
                        s.skipCharacterPhysics = true;
                        s.rotationType = RotationType.Custom;
                        s.rotation = new Direction(angle, deg(pitchDeg), 0f);
                        ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, s));

                        if (step == 0) {
                            spawnParticleAtPlayer(store, ref, ph, "Example_Vertical_Buff", 1.5f);
                            spawnParticleAtPlayer(store, ref, ph, "Rings_Rings", 1.5f);
                        }
                        if (step % 30 == 0 && step > 0) {
                            spawnParticleAtPlayer(store, ref, ph, "Azure_Spiral", 1.0f);
                        }
                        if (step == 60) {
                            spawnParticleAtPlayer(store, ref, ph, "Example_Vertical_Buff", 2.0f);
                            playSound2D(ph, "SFX_Memories_Unlock_Local", 1.5f, 1.0f);
                            spawnParticleAtPlayer(store, ref, ph, "Rings_Rings", 1.5f);
                        }
                        if (step == 90) {
                            spawnParticleAtPlayer(store, ref, ph, "Rings_Rings", 1.5f);
                        }
                    });
                } catch (Exception ignored) {}
            }, delay, TimeUnit.MILLISECONDS);
        }
        ms += orbitSteps * orbitInterval;

        // Phase 3: Hold in front of player, particles intensify
        final long intensifyTime = ms;
        SCHEDULER.schedule(() -> {
            try {
                world.execute(() -> {
                    if (!ref.isValid()) return;
                    ServerCameraSettings s = base3p(14f);
                    s.positionLerpSpeed = 0.15f;
                    s.rotationLerpSpeed = 0.15f;
                    s.applyLookType = ApplyLookType.Rotation;
                    s.lookMultiplier = new Vector2f(0f, 0f);
                    s.skipCharacterPhysics = true;
                    s.rotationType = RotationType.Custom;
                    s.rotation = new Direction(frontYaw, deg(-20), 0f);
                    ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, s));
                    spawnParticleAtPlayer(store, ref, ph, "Rings_Rings", 2.0f);
                    spawnParticleAtPlayer(store, ref, ph, "Aura_Heal", 2.0f);
                    playSound2D(ph, "SFX_Avatar_Powers_Enable", 2.0f, 1.0f);
                });
            } catch (Exception ignored) {}
        }, intensifyTime, TimeUnit.MILLISECONDS);
        ms += 1500;

        // Phase 4: Fast zoom in + shake + impact
        int zoomSteps = 10;
        long zoomInterval = 50;
        for (int i = 0; i <= zoomSteps; i++) {
            final int step = i;
            final long delay = ms + i * zoomInterval;
            SCHEDULER.schedule(() -> {
                try {
                    world.execute(() -> {
                        if (!ref.isValid()) return;
                        float t = (float) step / zoomSteps;
                        float eased = t * t;
                        float dist = 14f - 10f * eased;
                        ServerCameraSettings s = base3p(dist);
                        s.positionLerpSpeed = 0.6f;
                        s.rotationLerpSpeed = 0.6f;
                        s.applyLookType = ApplyLookType.Rotation;
                        s.lookMultiplier = new Vector2f(0f, 0f);
                        s.skipCharacterPhysics = true;
                        s.rotationType = RotationType.Custom;
                        s.rotation = new Direction(frontYaw, deg(-10), 0f);
                        ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, s));
                    });
                } catch (Exception ignored) {}
            }, delay, TimeUnit.MILLISECONDS);
        }
        final long impactTime = ms + zoomSteps * zoomInterval;
        SCHEDULER.schedule(() -> {
            try {
                world.execute(() -> {
                    if (!ref.isValid()) return;
                    ph.writeNoCache(new CameraShakeEffect(0, 0.6f, AccumulationMode.Set));
                    spawnParticleAtPlayer(store, ref, ph, "Firework_GS", 2.0f);
                    spawnParticleAtPlayer(store, ref, ph, "Teleport", 1.5f);
                    playSound2D(ph, "SFX_Divine_Respawn", 2.0f, 1.0f);
                });
            } catch (Exception ignored) {}
        }, impactTime, TimeUnit.MILLISECONDS);
        ms += zoomSteps * zoomInterval;

        // Phase 5: Hold, then reset
        final long resetTime = ms + 1500;
        SCHEDULER.schedule(() -> {
            try {
                world.execute(() -> {
                    if (!ref.isValid()) return;
                    MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
                    if (mm != null) {
                        mm.resetDefaultsAndUpdate(ref, store);
                    }
                    CameraManager camMgr = store.getComponent(ref, CameraManager.getComponentType());
                    if (camMgr != null) {
                        camMgr.resetCamera(playerRef);
                    }
                    player.sendMessage(Message.raw("You are now ready to Ascend!").color("#FFD700"));
                });
            } catch (Exception ignored) {}
        }, resetTime, TimeUnit.MILLISECONDS);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static float deg(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static ServerCameraSettings base3p(float distance) {
        ServerCameraSettings s = new ServerCameraSettings();
        s.positionLerpSpeed = 0.15f;
        s.rotationLerpSpeed = 0.15f;
        s.distance = distance;
        s.isFirstPerson = false;
        s.eyeOffset = true;
        s.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffsetRaycast;
        return s;
    }

    private static void spawnParticleAtPlayer(Store<EntityStore> store, Ref<EntityStore> ref,
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

    private static void playSound2D(PacketHandler ph, String soundId, float volume, float pitch) {
        try {
            int index = SoundEvent.getAssetMap().getIndex(soundId);
            if (index >= 0) {
                ph.writeNoCache(new PlaySoundEvent2D(index, SoundCategory.SFX, volume, pitch));
            }
        } catch (Exception ignored) {}
    }
}
