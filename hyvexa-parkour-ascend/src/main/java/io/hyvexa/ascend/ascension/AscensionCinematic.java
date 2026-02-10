package io.hyvexa.ascend.ascension;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.camera.CameraShakeEffect;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent2D;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Plays the ascension cinematic (~10 seconds) with camera orbit, particles, and sounds.
 * Triggered automatically when a player crosses the ascension vexa threshold,
 * and available via /ctest ascension for testing.
 */
public class AscensionCinematic {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long WARNING_THROTTLE_MS = 10_000L;
    private static final Map<String, Long> LAST_WARNING_BY_PHASE = new ConcurrentHashMap<>();

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
        if (player == null || ph == null || playerRef == null || store == null || ref == null || world == null) {
            return;
        }

        AtomicBoolean finalized = new AtomicBoolean(false);
        long finalizerDelayMs = 1_000L;

        try {
            // Read player's current yaw so the orbit is relative to their facing direction
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            float playerYaw = (transform != null) ? transform.getRotation().getYaw() : 0f;
            float frontYaw = playerYaw + (float) Math.PI;

            // Freeze player movement for the entire cinematic
            freezeMovement(store, ref, ph, playerRef);

            long ms = 0;

            // Phase 1: Lock camera in natural 3rd person + effects
            final long phase1Delay = ms;
            schedulePhase(world, phase1Delay, "phase1-lock", playerRef, () -> {
                if (finalized.get() || !ref.isValid()) {
                    return;
                }
                ServerCameraSettings settings = base3p(3f);
                settings.applyLookType = ApplyLookType.Rotation;
                settings.lookMultiplier = new Vector2f(0f, 0f);
                settings.skipCharacterPhysics = true;
                ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, settings));
                playSound2D(ph, playerRef, "phase1-lock", "SFX_Portal_Neutral_Open", 1.5f, 0.8f);
                spawnParticleAtPlayer(store, ref, ph, "Magic_Sparks_GS", 1.5f);
            });
            ms += 500;

            // Phase 2: Orbit + zoom out + particles (6 seconds)
            int orbitSteps = 120;
            long orbitInterval = 50;
            for (int i = 0; i < orbitSteps; i++) {
                final int step = i;
                final long delay = ms + i * orbitInterval;
                schedulePhase(world, delay, "phase2-orbit", playerRef, () -> {
                    if (finalized.get() || !ref.isValid()) {
                        return;
                    }
                    float t = (float) step / orbitSteps;
                    float angle = playerYaw + (float) (t * 3.0 * Math.PI);
                    float eased = 1f - (1f - t) * (1f - t);
                    float dist = 3f + 11f * eased;
                    float pitchDeg = -10f - 20f * eased;

                    ServerCameraSettings settings = base3p(dist);
                    settings.positionLerpSpeed = 0.25f;
                    settings.rotationLerpSpeed = 0.25f;
                    settings.applyLookType = ApplyLookType.Rotation;
                    settings.lookMultiplier = new Vector2f(0f, 0f);
                    settings.skipCharacterPhysics = true;
                    settings.rotationType = RotationType.Custom;
                    settings.rotation = new Direction(angle, deg(pitchDeg), 0f);
                    ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, settings));

                    if (step == 0) {
                        spawnParticleAtPlayer(store, ref, ph, "Example_Vertical_Buff", 1.5f);
                        spawnParticleAtPlayer(store, ref, ph, "Rings_Rings", 1.5f);
                    }
                    if (step % 30 == 0 && step > 0) {
                        spawnParticleAtPlayer(store, ref, ph, "Azure_Spiral", 1.0f);
                    }
                    if (step == 60) {
                        spawnParticleAtPlayer(store, ref, ph, "Example_Vertical_Buff", 2.0f);
                        playSound2D(ph, playerRef, "phase2-orbit", "SFX_Memories_Unlock_Local", 1.5f, 1.0f);
                        spawnParticleAtPlayer(store, ref, ph, "Rings_Rings", 1.5f);
                    }
                    if (step == 90) {
                        spawnParticleAtPlayer(store, ref, ph, "Rings_Rings", 1.5f);
                    }
                });
            }
            ms += orbitSteps * orbitInterval;

            // Phase 3: Hold in front of player, particles intensify
            final long intensifyTime = ms;
            schedulePhase(world, intensifyTime, "phase3-intensify", playerRef, () -> {
                if (finalized.get() || !ref.isValid()) {
                    return;
                }
                ServerCameraSettings settings = base3p(14f);
                settings.positionLerpSpeed = 0.15f;
                settings.rotationLerpSpeed = 0.15f;
                settings.applyLookType = ApplyLookType.Rotation;
                settings.lookMultiplier = new Vector2f(0f, 0f);
                settings.skipCharacterPhysics = true;
                settings.rotationType = RotationType.Custom;
                settings.rotation = new Direction(frontYaw, deg(-20), 0f);
                ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, settings));
                spawnParticleAtPlayer(store, ref, ph, "Rings_Rings", 2.0f);
                spawnParticleAtPlayer(store, ref, ph, "Aura_Heal", 2.0f);
                playSound2D(ph, playerRef, "phase3-intensify", "SFX_Avatar_Powers_Enable", 2.0f, 1.0f);
            });
            ms += 1500;

            // Phase 4: Fast zoom in + shake + impact
            int zoomSteps = 10;
            long zoomInterval = 50;
            for (int i = 0; i <= zoomSteps; i++) {
                final int step = i;
                final long delay = ms + i * zoomInterval;
                schedulePhase(world, delay, "phase4-zoom", playerRef, () -> {
                    if (finalized.get() || !ref.isValid()) {
                        return;
                    }
                    float t = (float) step / zoomSteps;
                    float eased = t * t;
                    float dist = 14f - 10f * eased;
                    ServerCameraSettings settings = base3p(dist);
                    settings.positionLerpSpeed = 0.6f;
                    settings.rotationLerpSpeed = 0.6f;
                    settings.applyLookType = ApplyLookType.Rotation;
                    settings.lookMultiplier = new Vector2f(0f, 0f);
                    settings.skipCharacterPhysics = true;
                    settings.rotationType = RotationType.Custom;
                    settings.rotation = new Direction(frontYaw, deg(-10), 0f);
                    ph.writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, settings));
                });
            }
            final long impactTime = ms + zoomSteps * zoomInterval;
            schedulePhase(world, impactTime, "phase4-impact", playerRef, () -> {
                if (finalized.get() || !ref.isValid()) {
                    return;
                }
                ph.writeNoCache(new CameraShakeEffect(0, 0.6f, AccumulationMode.Set));
                spawnParticleAtPlayer(store, ref, ph, "Firework_GS", 2.0f);
                spawnParticleAtPlayer(store, ref, ph, "Teleport", 1.5f);
                playSound2D(ph, playerRef, "phase4-impact", "SFX_Divine_Respawn", 2.0f, 1.0f);
            });
            ms += zoomSteps * zoomInterval;

            // Phase 5: Hold, then always run reset finalizer
            finalizerDelayMs = ms + 1500;
        } catch (Exception e) {
            logPhaseWarning(playerRef, "pipeline-build", e);
        } finally {
            scheduleFinalizer(finalizerDelayMs, finalized, player, playerRef, store, ref, world);
        }
    }

    private static void freezeMovement(Store<EntityStore> store, Ref<EntityStore> ref,
                                       PacketHandler ph, PlayerRef playerRef) {
        try {
            MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
            if (movementManager == null) {
                return;
            }
            var settings = movementManager.getSettings();
            if (settings == null) {
                return;
            }
            settings.baseSpeed = 0f;
            settings.jumpForce = 0f;
            settings.acceleration = 0f;
            settings.horizontalFlySpeed = 0f;
            settings.verticalFlySpeed = 0f;
            movementManager.update(ph);
        } catch (Exception e) {
            logPhaseWarning(playerRef, "freeze-movement", e);
        }
    }

    private static void schedulePhase(World world, long delayMs, String phaseId,
                                      PlayerRef playerRef, Runnable action) {
        try {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                try {
                    world.execute(() -> {
                        try {
                            action.run();
                        } catch (Exception e) {
                            logPhaseWarning(playerRef, phaseId, e);
                        }
                    });
                } catch (Exception e) {
                    logPhaseWarning(playerRef, phaseId + "-dispatch", e);
                }
            }, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logPhaseWarning(playerRef, phaseId + "-schedule", e);
        }
    }

    private static void scheduleFinalizer(long delayMs, AtomicBoolean finalized, Player player,
                                          PlayerRef playerRef, Store<EntityStore> store,
                                          Ref<EntityStore> ref, World world) {
        String phaseId = "phase5-finalizer";
        try {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                runFinalizer(finalized, player, playerRef, store, ref, world, phaseId);
            }, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logPhaseWarning(playerRef, phaseId + "-schedule", e);
            runFinalizer(finalized, player, playerRef, store, ref, world, phaseId + "-fallback");
        }
    }

    private static void runFinalizer(AtomicBoolean finalized, Player player, PlayerRef playerRef,
                                     Store<EntityStore> store, Ref<EntityStore> ref,
                                     World world, String phaseId) {
        if (!finalized.compareAndSet(false, true)) {
            return;
        }
        try {
            world.execute(() -> {
                try {
                    restoreMovementAndCamera(player, playerRef, store, ref);
                } catch (Exception e) {
                    logPhaseWarning(playerRef, phaseId, e);
                }
            });
        } catch (Exception e) {
            logPhaseWarning(playerRef, phaseId + "-dispatch", e);
        }
    }

    private static void restoreMovementAndCamera(Player player, PlayerRef playerRef,
                                                 Store<EntityStore> store, Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager != null) {
            movementManager.resetDefaultsAndUpdate(ref, store);
        }
        CameraManager cameraManager = store.getComponent(ref, CameraManager.getComponentType());
        if (cameraManager != null) {
            cameraManager.resetCamera(playerRef);
        }
        if (player != null) {
            player.sendMessage(Message.raw("You are now ready to Ascend!").color("#FFD700"));
        }
    }

    private static void logPhaseWarning(PlayerRef playerRef, String phaseId, Exception error) {
        String playerId = "unknown";
        if (playerRef != null && playerRef.getUuid() != null) {
            playerId = playerRef.getUuid().toString();
        }
        String key = playerId + "|" + phaseId;
        long now = System.currentTimeMillis();
        Long last = LAST_WARNING_BY_PHASE.get(key);
        if (last != null && now - last < WARNING_THROTTLE_MS) {
            return;
        }
        LAST_WARNING_BY_PHASE.put(key, now);
        LOGGER.at(Level.WARNING).withCause(error)
                .log("Ascension cinematic warning player=" + playerId + " phase=" + phaseId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static float deg(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static ServerCameraSettings base3p(float distance) {
        ServerCameraSettings settings = new ServerCameraSettings();
        settings.positionLerpSpeed = 0.15f;
        settings.rotationLerpSpeed = 0.15f;
        settings.distance = distance;
        settings.isFirstPerson = false;
        settings.eyeOffset = true;
        settings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffsetRaycast;
        return settings;
    }

    private static void spawnParticleAtPlayer(Store<EntityStore> store, Ref<EntityStore> ref,
                                              PacketHandler ph, String particleId, float scale) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        var pos = transform.getPosition();
        ph.writeNoCache(new SpawnParticleSystem(
                particleId,
                new Position(pos.getX(), pos.getY() + 1.0, pos.getZ()),
                new Direction(0f, 0f, 0f),
                scale,
                new Color((byte) 255, (byte) 255, (byte) 255)
        ));
    }

    private static void playSound2D(PacketHandler ph, PlayerRef playerRef,
                                    String phaseId, String soundId, float volume, float pitch) {
        try {
            int index = SoundEvent.getAssetMap().getIndex(soundId);
            if (index >= 0) {
                ph.writeNoCache(new PlaySoundEvent2D(index, SoundCategory.SFX, volume, pitch));
            }
        } catch (Exception e) {
            logPhaseWarning(playerRef, phaseId + "-sound-" + soundId, e);
        }
    }
}
