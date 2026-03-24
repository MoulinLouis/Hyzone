package io.hyvexa.core.cosmetic;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.EntityEffectUpdate;
import com.hypixel.hytale.protocol.EntityEffectsUpdate;
import com.hypixel.hytale.protocol.EntityUpdate;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.AsyncExecutionHelper;
import io.hyvexa.core.trail.ModelParticleTrailManager;
import io.hyvexa.core.trail.TrailManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles applying/removing cosmetic visuals on players.
 * Must be called from world thread for entity operations.
 */
public class CosmeticManager {

    private static final CosmeticManager INSTANCE = new CosmeticManager();
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float PREVIEW_DURATION_SECONDS = 5f;
    private static final long APPLY_DELAY_MS = 100;
    private static final long LOGIN_APPLY_DELAY_MS = 1000;

    private TrailManager trailManager;
    private ModelParticleTrailManager modelParticleTrailManager;
    private volatile CosmeticStore cosmeticStore;

    /** Tracks active preview timers so we can cancel on disconnect. */
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> previewTimers = new ConcurrentHashMap<>();

    /**
     * Tracks the latest cosmetic ID that should be applied per player.
     * Used to handle rapid equip switches: only the last requested cosmetic gets applied
     * when the deferred next-tick task runs.
     */
    private final ConcurrentHashMap<UUID, String> pendingCosmeticId = new ConcurrentHashMap<>();

    private CosmeticManager() {}

    public static CosmeticManager getInstance() {
        return INSTANCE;
    }

    public void setTrailManager(TrailManager trailManager) {
        this.trailManager = trailManager;
    }

    public void setModelParticleTrailManager(ModelParticleTrailManager modelParticleTrailManager) {
        this.modelParticleTrailManager = modelParticleTrailManager;
    }

    public void setCosmeticStore(CosmeticStore cosmeticStore) {
        this.cosmeticStore = cosmeticStore;
    }

    /**
     * Apply an equipped cosmetic. Must be called from world thread.
     * The effect is cleared immediately but the new effect is applied on the next tick,
     * because clearEffects + addInfiniteEffect in the same tick causes the engine to
     * lose the new effect.
     */
    public void applyCosmetic(Ref<EntityStore> ref, Store<EntityStore> store, String cosmeticId) {
        if (ref == null || !ref.isValid()) return;
        CosmeticDefinition def = CosmeticDefinition.fromId(cosmeticId);

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        UUID playerId = playerRef.getUuid();

        if (def == null) {
            pendingCosmeticId.remove(playerId);
            clearCosmeticChannels(ref, store, playerId);
            return;
        }

        clearCosmeticChannels(ref, store, playerId);

        // Record the desired cosmetic and defer the apply after a short delay.
        // clearEffects + addInfiniteEffect in the same tick causes the engine to lose the effect.
        // world.execute() can still run in the same tick, so we use a real time delay.
        // If the player rapidly switches cosmetics, only the latest one is applied.
        pendingCosmeticId.put(playerId, cosmeticId);
        World world = store.getExternalData().getWorld();
        if (world == null) return;

        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (!ref.isValid()) return;
            if (!cosmeticId.equals(pendingCosmeticId.get(playerId))) return;
            pendingCosmeticId.remove(playerId);

            AsyncExecutionHelper.runBestEffort(world, () -> {
                if (!ref.isValid()) return;
                PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (pRef == null) return;

                CosmeticDefinition latestDef = CosmeticDefinition.fromId(cosmeticId);
                if (latestDef == null) return;

                applyDefinition(ref, store, pRef, latestDef, false);
            }, "cosmetic.apply.deferred", "deferred cosmetic apply", "player=" + playerId);
        }, APPLY_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Remove all cosmetic visuals from a player. Must be called from world thread.
     */
    public void removeCosmetic(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;
        if (playerId != null) pendingCosmeticId.remove(playerId);
        clearCosmeticChannels(ref, store, playerId);
    }

    /**
     * Preview a cosmetic for PREVIEW_DURATION_SECONDS, then restore equipped cosmetic (if any).
     * Must be called from world thread.
     */
    public void previewCosmetic(Ref<EntityStore> ref, Store<EntityStore> store, String cosmeticId) {
        if (ref == null || !ref.isValid()) return;
        CosmeticDefinition def = CosmeticDefinition.fromId(cosmeticId);
        if (def == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        UUID playerId = playerRef.getUuid();

        cancelPreviewTimer(playerId);
        pendingCosmeticId.remove(playerId);
        clearCosmeticChannels(ref, store, playerId);

        // Delay needed: clearEffects + addEffect in same tick loses the effect
        World world = store.getExternalData().getWorld();
        if (world == null) return;

        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (!ref.isValid()) return;
            AsyncExecutionHelper.runBestEffort(world, () -> {
                if (!ref.isValid()) return;
                PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (pRef == null) return;
                applyDefinition(ref, store, pRef, def, true);
            }, "cosmetic.preview.apply", "deferred preview apply", "player=" + playerId);
        }, APPLY_DELAY_MS, TimeUnit.MILLISECONDS);

        ScheduledFuture<?> timer = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            previewTimers.remove(playerId);
            if (!ref.isValid()) return;

            AsyncExecutionHelper.runBestEffort(world, () -> {
                if (!ref.isValid()) return;
                CosmeticStore store2 = cosmeticStore;
                String equipped = store2 != null ? store2.getEquippedCosmeticId(playerId) : null;
                if (equipped != null) {
                    applyCosmetic(ref, store, equipped);
                } else {
                    removeCosmetic(ref, store);
                }
            }, "cosmetic.preview.restore", "restore cosmetic after preview", "player=" + playerId);
        }, (long) (PREVIEW_DURATION_SECONDS * 1000), TimeUnit.MILLISECONDS);

        previewTimers.put(playerId, timer);
    }

    /**
     * Re-apply the player's equipped cosmetic on login.
     * Delayed to let the client finish receiving the initial entity state from the entity tracker,
     * otherwise our sync packet gets overwritten.
     */
    public void reapplyOnLogin(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        CosmeticStore store2 = cosmeticStore;
        if (store2 == null) return;
        String equipped = store2.getEquippedCosmeticId(playerRef.getUuid());
        if (equipped != null) {
            if (CosmeticDefinition.fromId(equipped) == null) {
                store2.unequipCosmetic(playerRef.getUuid());
                removeCosmetic(ref, store);
                return;
            }

            World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
            if (world == null) return;

            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                if (!ref.isValid()) return;
                AsyncExecutionHelper.runBestEffort(world, () -> {
                    if (!ref.isValid()) return;
                    applyCosmetic(ref, store, equipped);
                }, "cosmetic.login.reapply", "reapply cosmetic on login", "player=" + playerRef.getUuid());
            }, LOGIN_APPLY_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Apply a timed celebration effect on top of any equipped cosmetic.
     * Must be called from world thread.
     */
    public void applyCelebrationEffect(Ref<EntityStore> ref, Store<EntityStore> store,
                                       String effectName, float durationSeconds) {
        if (ref == null || !ref.isValid()) return;

        EntityEffect effect = resolveEffect(effectName);
        if (effect == null) {
            LOGGER.atWarning().log("Could not resolve celebration effect: " + effectName);
            return;
        }

        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) return;

        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
        if (effectIndex < 0) return;

        ctrl.addEffect(ref, effectIndex, effect, durationSeconds, OverlapBehavior.OVERWRITE, store);

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        NetworkId nid = store.getComponent(ref, NetworkId.getComponentType());
        if (playerRef != null && nid != null) {
            sendEffectSyncToSelf(playerRef.getPacketHandler(), nid.getId(), ctrl);
        }
    }

    /**
     * Clean up preview timers/trails on disconnect.
     */
    public void cleanupOnDisconnect(UUID playerId) {
        if (playerId == null) return;
        cancelPreviewTimer(playerId);
        pendingCosmeticId.remove(playerId);
        trailManager.stopTrail(playerId);
        modelParticleTrailManager.stopTrail(playerId);
    }

    /**
     * Cancel all preview timers and shut down trail managers. Call on plugin shutdown.
     */
    public void shutdown() {
        for (ScheduledFuture<?> timer : previewTimers.values()) {
            if (timer != null) timer.cancel(false);
        }
        previewTimers.clear();
        pendingCosmeticId.clear();
        trailManager.shutdown();
        modelParticleTrailManager.shutdown();
    }

    private void applyDefinition(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
                                 CosmeticDefinition def, boolean preview) {
        switch (def.getType()) {
            case ENTITY_EFFECT -> {
                String effectId = def.getEffectId();
                if (effectId == null) return;
                if (preview) {
                    applyTimedEffect(ref, store, effectId, PREVIEW_DURATION_SECONDS);
                } else {
                    applyInfiniteEffect(ref, store, effectId);
                }
            }
            case WORLD_PARTICLE_TRAIL ->
                    startWorldTrail(playerRef, ref, store, def.getParticleId(), def.getScale(), def.getIntervalMs());
            case MODEL_PARTICLE_TRAIL -> {
                World world = store.getExternalData().getWorld();
                if (world == null) return;
                modelParticleTrailManager.startTrail(playerRef.getUuid(), ref, store, world,
                        def.getParticleId(), def.getScale(), def.getIntervalMs(),
                        def.getXOffset(), def.getYOffset(), def.getZOffset());
            }
            default -> {}
        }
    }

    private void startWorldTrail(PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store,
                                 String particleId, float scale, long intervalMs) {
        if (particleId == null || particleId.isBlank()) return;
        String resolvedParticleId = resolveParticleId(particleId);
        String finalParticleId = resolvedParticleId != null ? resolvedParticleId : particleId;
        World world = store.getExternalData().getWorld();
        if (world == null) return;
        trailManager.startTrail(playerRef.getUuid(), ref, store, world,
                finalParticleId, scale, intervalMs);
    }

    private String resolveParticleId(String id) {
        var map = ParticleSystem.getAssetMap().getAssetMap();
        return resolveKeyByName(map, id);
    }

    private void clearCosmeticChannels(Ref<EntityStore> ref, Store<EntityStore> store, UUID playerId) {
        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl != null) {
            ctrl.clearEffects(ref, store);

            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            NetworkId nid = store.getComponent(ref, NetworkId.getComponentType());
            if (playerRef != null && nid != null) {
                sendEffectSyncToSelf(playerRef.getPacketHandler(), nid.getId(), ctrl);
            }
        }

        if (playerId != null) {
            trailManager.stopTrail(playerId);
            modelParticleTrailManager.stopTrail(playerId);
        }
    }

    private void cancelPreviewTimer(UUID playerId) {
        ScheduledFuture<?> timer = previewTimers.remove(playerId);
        if (timer != null) {
            timer.cancel(false);
        }
    }

    private void applyInfiniteEffect(Ref<EntityStore> ref, Store<EntityStore> store, String effectName) {
        ResolvedEffect resolved = resolveEffectSetup(ref, store, effectName);
        if (resolved == null) return;

        resolved.ctrl.addInfiniteEffect(ref, resolved.effectIndex, resolved.effect, store);
        syncEffectsToSelf(ref, store, resolved.ctrl);
    }

    private void applyTimedEffect(Ref<EntityStore> ref, Store<EntityStore> store, String effectName,
                                  float durationSeconds) {
        ResolvedEffect resolved = resolveEffectSetup(ref, store, effectName);
        if (resolved == null) return;

        resolved.ctrl.addEffect(ref, resolved.effectIndex, resolved.effect, durationSeconds, OverlapBehavior.OVERWRITE, store);
        syncEffectsToSelf(ref, store, resolved.ctrl);
    }

    private ResolvedEffect resolveEffectSetup(Ref<EntityStore> ref, Store<EntityStore> store, String effectName) {
        EntityEffect effect = resolveEffect(effectName);
        if (effect == null) {
            LOGGER.atWarning().log("Could not resolve effect for cosmetic: " + effectName);
            return null;
        }

        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) return null;

        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
        if (effectIndex < 0) return null;

        return new ResolvedEffect(effect, ctrl, effectIndex);
    }

    private record ResolvedEffect(EntityEffect effect, EffectControllerComponent ctrl, int effectIndex) {}

    private void syncEffectsToSelf(Ref<EntityStore> ref, Store<EntityStore> store, EffectControllerComponent ctrl) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        NetworkId nid = store.getComponent(ref, NetworkId.getComponentType());
        if (playerRef != null && nid != null) {
            sendEffectSyncToSelf(playerRef.getPacketHandler(), nid.getId(), ctrl);
        }
    }

    private EntityEffect resolveEffect(String name) {
        var map = EntityEffect.getAssetMap().getAssetMap();
        String key = resolveKeyByName(map, name);
        return key != null ? map.get(key) : null;
    }

    /**
     * Fuzzy key lookup: exact match, then case-insensitive, then suffix match.
     */
    private static <T> String resolveKeyByName(Map<String, T> map, String name) {
        if (map.containsKey(name)) return name;

        String nameLower = name.toLowerCase();
        for (String key : map.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }

        for (String key : map.keySet()) {
            String keyLower = key.toLowerCase();
            if (keyLower.endsWith("/" + nameLower) || keyLower.endsWith(nameLower)) {
                return key;
            }
        }

        return null;
    }

    private void sendEffectSyncToSelf(PacketHandler ph, int networkId, EffectControllerComponent ctrl) {
        if (ph == null) return;
        try {
            EntityEffectUpdate[] updates = ctrl.createInitUpdates();
            if (updates == null) {
                updates = new EntityEffectUpdate[0];
            }

            EntityEffectsUpdate cu = new EntityEffectsUpdate(updates);
            EntityUpdate eu = new EntityUpdate(networkId, null, new EntityEffectsUpdate[]{cu});
            ph.writeNoCache(new EntityUpdates(null, new EntityUpdate[]{eu}));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to sync effect to self");
        }
    }
}
