package io.hyvexa.ascend.mine.egg;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.AccumulationMode;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.camera.CameraShakeEffect;
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent2D;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import io.hyvexa.ascend.mine.data.MinerRarity;
import io.hyvexa.common.visibility.EntityVisibilityManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Plays a 3D NPC roulette animation when opening an egg.
 * Kweebec models cycle rapidly in front of the player, slow down, and land on the result.
 * Follows the AscensionCinematic pattern (schedulePhase, finalizer, AtomicBoolean guard).
 */
public class EggRouletteAnimation {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long WARNING_THROTTLE_MS = 10_000L;
    private static final Map<String, Long> LAST_WARNING_BY_PHASE = new ConcurrentHashMap<>();

    // One animation per player at a time
    private static final Map<UUID, AtomicBoolean> ACTIVE_ROULETTES = new ConcurrentHashMap<>();

    private static final MinerRarity[] ALL_RARITIES = MinerRarity.values();
    private static final double SPAWN_DISTANCE = 3.0;

    public static void play(Player player, PacketHandler ph, PlayerRef playerRef,
                            Store<EntityStore> store, Ref<EntityStore> ref, World world,
                            MinerRarity result, Runnable onComplete) {
        if (player == null || ph == null || playerRef == null || store == null
                || ref == null || world == null || result == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // Concurrency guard: one roulette per player
        AtomicBoolean existing = ACTIVE_ROULETTES.putIfAbsent(playerId, new AtomicBoolean(false));
        if (existing != null) {
            player.sendMessage(Message.raw("Roulette already in progress!"));
            if (onComplete != null) onComplete.run();
            return;
        }

        NPCPlugin npcPlugin;
        try {
            npcPlugin = NPCPlugin.get();
        } catch (Exception e) {
            ACTIVE_ROULETTES.remove(playerId);
            player.sendMessage(Message.raw("Hatched a " + result.getDisplayName() + " miner!"));
            if (onComplete != null) onComplete.run();
            return;
        }

        AtomicBoolean finalized = new AtomicBoolean(false);
        // Track the current NPC ref for cleanup — array wrapper for lambda mutation
        Ref<EntityStore>[] currentNpcRef = new Ref[]{null};
        long finalizerDelayMs = 1_000L;

        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            float playerYaw = (transform != null) ? transform.getRotation().getYaw() : 0f;

            // Position: 3 blocks in front of player (negate offset — engine yaw points backward)
            double playerX = (transform != null) ? transform.getPosition().getX() : 0;
            double playerY = (transform != null) ? transform.getPosition().getY() : 0;
            double playerZ = (transform != null) ? transform.getPosition().getZ() : 0;
            double spawnX = playerX - Math.sin(playerYaw) * SPAWN_DISTANCE;
            double spawnZ = playerZ - Math.cos(playerYaw) * SPAWN_DISTANCE;
            float npcYaw = playerYaw + (float) Math.PI; // Rotate 180deg to face the player

            Vector3d spawnPos = new Vector3d(spawnX, playerY, spawnZ);
            Vector3f spawnRot = new Vector3f(0f, npcYaw, 0f);

            // Build the roulette sequence: cycles through rarities, ending on result
            MinerRarity[] sequence = buildSequence(result);

            long ms = 0;

            // Phase 1: Spawn first NPC
            final long phase1Delay = ms;
            final int firstIdx = 0;
            schedulePhase(world, phase1Delay, "phase1-first", playerRef, () -> {
                if (finalized.get() || !ref.isValid()) return;
                currentNpcRef[0] = spawnRouletteNpc(npcPlugin, store, world, playerId,
                        sequence[firstIdx], spawnPos, spawnRot, playerRef);
                playSound2D(ph, playerRef, "phase1-first", "SFX_Portal_Neutral_Open", 1.5f, 0.8f);
            });
            ms += 100;

            // Phase 2: Fast cycling (25 steps at 100ms each)
            int fastSteps = 25;
            long fastInterval = 100;
            for (int i = 1; i <= fastSteps; i++) {
                final int seqIdx = i % sequence.length;
                final long delay = ms + (i - 1) * fastInterval;
                schedulePhase(world, delay, "phase2-fast", playerRef, () -> {
                    if (finalized.get() || !ref.isValid()) return;
                    // Despawn old, spawn new in same world.execute
                    despawnNpc(currentNpcRef[0], store);
                    currentNpcRef[0] = spawnRouletteNpc(npcPlugin, store, world, playerId,
                            sequence[seqIdx], spawnPos, spawnRot, playerRef);
                    playSound2D(ph, playerRef, "phase2-fast", "SFX_Click_Forward", 0.8f, 1.2f);
                });
            }
            ms += fastSteps * fastInterval;

            // Phase 3: Slowdown (6 steps at increasing intervals)
            int[] slowIntervals = {200, 250, 300, 350, 400, 500};
            int slowOffset = fastSteps + 1;
            long slowMs = ms;
            for (int i = 0; i < slowIntervals.length; i++) {
                final int seqIdx = (slowOffset + i) % sequence.length;
                final long delay = slowMs;
                final boolean isLast = (i == slowIntervals.length - 1);
                schedulePhase(world, delay, "phase3-slow", playerRef, () -> {
                    if (finalized.get() || !ref.isValid()) return;
                    despawnNpc(currentNpcRef[0], store);
                    // Last step in slowdown uses the actual result
                    MinerRarity rarity = isLast ? result : sequence[seqIdx];
                    currentNpcRef[0] = spawnRouletteNpc(npcPlugin, store, world, playerId,
                            rarity, spawnPos, spawnRot, playerRef);
                    playSound2D(ph, playerRef, "phase3-slow", "SFX_Click_Forward", 0.8f, 0.9f);
                });
                slowMs += slowIntervals[i];
            }
            ms = slowMs;

            // Phase 4: Reveal
            final long revealDelay = ms;
            schedulePhase(world, revealDelay, "phase4-reveal", playerRef, () -> {
                if (finalized.get() || !ref.isValid()) return;
                // Final NPC is already the result from last slowdown step
                playSound2D(ph, playerRef, "phase4-reveal", "SFX_Memories_Unlock_Local", 1.5f, 1.0f);
                spawnParticleAt(ph, spawnPos, "Firework_GS", 1.5f);

                if (result == MinerRarity.LEGENDARY) {
                    playSound2D(ph, playerRef, "phase4-legendary", "SFX_Divine_Respawn", 2.0f, 1.0f);
                    playSound2D(ph, playerRef, "phase4-legendary2", "SFX_Avatar_Powers_Enable", 2.0f, 1.0f);
                    spawnParticleAt(ph, spawnPos, "Teleport", 1.5f);
                    spawnParticleAt(ph, spawnPos, "Magic_Sparks_GS", 2.0f);
                    ph.writeNoCache(new CameraShakeEffect(0, 0.6f, AccumulationMode.Set));
                } else if (result == MinerRarity.EPIC) {
                    spawnParticleAt(ph, spawnPos, "Magic_Sparks_GS", 1.5f);
                    ph.writeNoCache(new CameraShakeEffect(0, 0.3f, AccumulationMode.Set));
                } else if (result == MinerRarity.RARE) {
                    spawnParticleAt(ph, spawnPos, "Magic_Sparks_GS", 1.0f);
                }

                player.sendMessage(Message.raw("Hatched a " + result.getDisplayName() + " miner!"));
            });
            ms += 2500;

            // Phase 5: Finalizer
            finalizerDelayMs = ms;
        } catch (Exception e) {
            logPhaseWarning(playerRef, "pipeline-build", e);
        } finally {
            scheduleFinalizer(finalizerDelayMs, finalized, player, playerRef, store, ref, world,
                    playerId, currentNpcRef, onComplete);
        }
    }

    /**
     * Cancel an active roulette for a disconnecting player.
     * The finalizer's AtomicBoolean guard prevents double-cleanup.
     */
    public static void cancelIfActive(UUID playerId) {
        if (playerId == null) return;
        ACTIVE_ROULETTES.remove(playerId);
    }

    // ── Sequence Generation ─────────────────────────────────────────────

    /**
     * Build a cycling sequence of rarities that ends on the result.
     * The second-to-last entry is guaranteed to NOT be the result (for visual contrast).
     */
    private static MinerRarity[] buildSequence(MinerRarity result) {
        // Total entries: enough for fast + slow phases (32 entries, cycling all rarities)
        int totalSteps = 32;
        MinerRarity[] seq = new MinerRarity[totalSteps];

        // Fill with cycling rarities
        for (int i = 0; i < totalSteps; i++) {
            seq[i] = ALL_RARITIES[i % ALL_RARITIES.length];
        }

        // Ensure last entry is the result
        seq[totalSteps - 1] = result;

        // Ensure second-to-last is NOT the result (pick the next different rarity)
        if (seq[totalSteps - 2] == result) {
            int nextIdx = (result.ordinal() + 1) % ALL_RARITIES.length;
            seq[totalSteps - 2] = ALL_RARITIES[nextIdx];
        }

        return seq;
    }

    // ── NPC Spawn/Despawn ───────────────────────────────────────────────

    /**
     * Spawn a roulette NPC and hide it from all other players.
     * Must be called from within world.execute() (via schedulePhase).
     */
    private static Ref<EntityStore> spawnRouletteNpc(NPCPlugin npcPlugin, Store<EntityStore> store,
                                                      World world, UUID ownerId,
                                                      MinerRarity rarity, Vector3d position,
                                                      Vector3f rotation, PlayerRef playerRef) {
        try {
            Object result = npcPlugin.spawnNPC(store, rarity.getEntityType(), "Roulette", position, rotation);
            if (result == null) return null;

            Ref<EntityStore> entityRef = extractEntityRef(result);
            if (entityRef == null) return null;

            // Make invulnerable and frozen
            try {
                store.addComponent(entityRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
            } catch (Exception ignored) {}
            try {
                store.addComponent(entityRef, Frozen.getComponentType(), Frozen.get());
            } catch (Exception ignored) {}

            // Hide from all other players
            try {
                UUIDComponent uuidComp = store.getComponent(entityRef, UUIDComponent.getComponentType());
                if (uuidComp != null) {
                    UUID entityUuid = uuidComp.getUuid();
                    if (entityUuid != null) {
                        EntityVisibilityManager visibilityManager = EntityVisibilityManager.get();
                        for (PlayerRef other : Universe.get().getPlayers()) {
                            if (other == null) continue;
                            UUID otherId = other.getUuid();
                            if (otherId == null || otherId.equals(ownerId)) continue;
                            visibilityManager.hideEntity(otherId, entityUuid);
                        }
                    }
                }
            } catch (Exception e) {
                logPhaseWarning(playerRef, "hide-npc", e);
            }

            return entityRef;
        } catch (Exception e) {
            logPhaseWarning(playerRef, "spawn-npc", e);
            return null;
        }
    }

    private static void despawnNpc(Ref<EntityStore> npcRef, Store<EntityStore> store) {
        if (npcRef == null || !npcRef.isValid()) return;
        try {
            store.removeEntity(npcRef, RemoveReason.REMOVE);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static Ref<EntityStore> extractEntityRef(Object pairResult) {
        if (pairResult == null) return null;
        try {
            for (String methodName : List.of("getFirst", "getLeft", "getKey", "first", "left")) {
                try {
                    java.lang.reflect.Method method = pairResult.getClass().getMethod(methodName);
                    Object value = method.invoke(pairResult);
                    if (value instanceof Ref<?> ref) {
                        return (Ref<EntityStore>) ref;
                    }
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to extract entity ref from roulette NPC: " + e.getMessage());
        }
        return null;
    }

    // ── Scheduling (from AscensionCinematic) ────────────────────────────

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
                                          Ref<EntityStore> ref, World world, UUID playerId,
                                          Ref<EntityStore>[] currentNpcRef, Runnable onComplete) {
        String phaseId = "finalizer";
        try {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                runFinalizer(finalized, player, playerRef, store, ref, world, playerId,
                        currentNpcRef, phaseId, onComplete);
            }, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logPhaseWarning(playerRef, phaseId + "-schedule", e);
            runFinalizer(finalized, player, playerRef, store, ref, world, playerId,
                    currentNpcRef, phaseId + "-fallback", onComplete);
        }
    }

    private static void runFinalizer(AtomicBoolean finalized, Player player, PlayerRef playerRef,
                                     Store<EntityStore> store, Ref<EntityStore> ref,
                                     World world, UUID playerId,
                                     Ref<EntityStore>[] currentNpcRef,
                                     String phaseId, Runnable onComplete) {
        if (!finalized.compareAndSet(false, true)) return;
        try {
            world.execute(() -> {
                try {
                    // Despawn roulette NPC
                    despawnNpc(currentNpcRef[0], store);
                    currentNpcRef[0] = null;

                    // Remove concurrency guard
                    ACTIVE_ROULETTES.remove(playerId);

                    if (onComplete != null) {
                        onComplete.run();
                    }
                } catch (Exception e) {
                    logPhaseWarning(playerRef, phaseId, e);
                    ACTIVE_ROULETTES.remove(playerId);
                }
            });
        } catch (Exception e) {
            logPhaseWarning(playerRef, phaseId + "-dispatch", e);
            ACTIVE_ROULETTES.remove(playerId);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void spawnParticleAt(PacketHandler ph, Vector3d pos,
                                        String particleId, float scale) {
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

    private static void logPhaseWarning(PlayerRef playerRef, String phaseId, Exception error) {
        if (LAST_WARNING_BY_PHASE.size() > 500) {
            LAST_WARNING_BY_PHASE.clear();
        }
        String playerId = "unknown";
        if (playerRef != null && playerRef.getUuid() != null) {
            playerId = playerRef.getUuid().toString();
        }
        String key = playerId + "|" + phaseId;
        long now = System.currentTimeMillis();
        Long last = LAST_WARNING_BY_PHASE.get(key);
        if (last != null && now - last < WARNING_THROTTLE_MS) return;
        LAST_WARNING_BY_PHASE.put(key, now);
        LOGGER.atWarning().withCause(error)
                .log("Egg roulette warning player=" + playerId + " phase=" + phaseId);
    }
}
