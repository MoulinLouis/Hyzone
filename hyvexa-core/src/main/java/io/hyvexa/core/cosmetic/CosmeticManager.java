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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
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

    /** Tracks active preview timers so we can cancel on disconnect. */
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> previewTimers = new ConcurrentHashMap<>();

    private CosmeticManager() {}

    public static CosmeticManager getInstance() {
        return INSTANCE;
    }

    /**
     * Apply an equipped cosmetic. Must be called from world thread.
     */
    public void applyCosmetic(Ref<EntityStore> ref, Store<EntityStore> store, String cosmeticId) {
        if (ref == null || !ref.isValid()) return;
        CosmeticDefinition def = CosmeticDefinition.fromId(cosmeticId);

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        if (def == null) {
            clearCosmeticChannels(ref, store, playerRef.getUuid());
            return;
        }

        clearCosmeticChannels(ref, store, playerRef.getUuid());
        applyDefinition(ref, store, playerRef, def, false);
    }

    /**
     * Remove all cosmetic visuals from a player. Must be called from world thread.
     */
    public void removeCosmetic(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;
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
        clearCosmeticChannels(ref, store, playerId);
        applyDefinition(ref, store, playerRef, def, true);

        World world = store.getExternalData().getWorld();
        if (world == null) return;

        ScheduledFuture<?> timer = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            previewTimers.remove(playerId);
            if (!ref.isValid()) return;

            AsyncExecutionHelper.runBestEffort(world, () -> {
                if (!ref.isValid()) return;
                String equipped = CosmeticStore.getInstance().getEquippedCosmeticId(playerId);
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
     * Re-apply the player's equipped cosmetic on login. Must be called from world thread.
     */
    public void reapplyOnLogin(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        String equipped = CosmeticStore.getInstance().getEquippedCosmeticId(playerRef.getUuid());
        if (equipped != null) {
            if (CosmeticDefinition.fromId(equipped) == null) {
                CosmeticStore.getInstance().unequipCosmetic(playerRef.getUuid());
                removeCosmetic(ref, store);
            } else {
                applyCosmetic(ref, store, equipped);
            }
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
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef != null && player != null) {
            sendEffectSyncToSelf(playerRef.getPacketHandler(), player, ctrl);
        }
    }

    /**
     * Clean up preview timers/trails on disconnect.
     */
    public void cleanupOnDisconnect(UUID playerId) {
        if (playerId == null) return;
        cancelPreviewTimer(playerId);
        TrailManager.getInstance().stopTrail(playerId);
        ModelParticleTrailManager.getInstance().stopTrail(playerId);
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
                ModelParticleTrailManager.getInstance().startTrail(playerRef.getUuid(), ref, store, world,
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
        TrailManager.getInstance().startTrail(playerRef.getUuid(), ref, store, world,
                finalParticleId, scale, intervalMs);
    }

    private String resolveParticleId(String id) {
        var map = ParticleSystem.getAssetMap().getAssetMap();
        if (map.containsKey(id)) return id;

        String idLower = id.toLowerCase();
        for (String key : map.keySet()) {
            String keyLower = key.toLowerCase();
            if (keyLower.equals(idLower) || keyLower.endsWith("/" + idLower) || keyLower.endsWith(idLower)) {
                return key;
            }
        }
        return null;
    }

    private void clearCosmeticChannels(Ref<EntityStore> ref, Store<EntityStore> store, UUID playerId) {
        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl != null) {
            ctrl.clearEffects(ref, store);

            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            Player player = store.getComponent(ref, Player.getComponentType());
            if (playerRef != null && player != null) {
                sendEffectSyncToSelf(playerRef.getPacketHandler(), player, ctrl);
            }
        }

        if (playerId != null) {
            TrailManager.getInstance().stopTrail(playerId);
            ModelParticleTrailManager.getInstance().stopTrail(playerId);
        }
    }

    private void cancelPreviewTimer(UUID playerId) {
        ScheduledFuture<?> timer = previewTimers.remove(playerId);
        if (timer != null) {
            timer.cancel(false);
        }
    }

    private void applyInfiniteEffect(Ref<EntityStore> ref, Store<EntityStore> store, String effectName) {
        EntityEffect effect = resolveEffect(effectName);
        if (effect == null) {
            LOGGER.atWarning().log("Could not resolve effect for cosmetic: " + effectName);
            return;
        }

        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) return;

        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
        if (effectIndex < 0) return;

        ctrl.addInfiniteEffect(ref, effectIndex, effect, store);
        syncEffectsToSelf(ref, store, ctrl);
    }

    private void applyTimedEffect(Ref<EntityStore> ref, Store<EntityStore> store, String effectName,
                                  float durationSeconds) {
        EntityEffect effect = resolveEffect(effectName);
        if (effect == null) {
            LOGGER.atWarning().log("Could not resolve effect for cosmetic preview: " + effectName);
            return;
        }

        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) return;

        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
        if (effectIndex < 0) return;

        ctrl.addEffect(ref, effectIndex, effect, durationSeconds, OverlapBehavior.OVERWRITE, store);
        syncEffectsToSelf(ref, store, ctrl);
    }

    private void syncEffectsToSelf(Ref<EntityStore> ref, Store<EntityStore> store, EffectControllerComponent ctrl) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef != null && player != null) {
            sendEffectSyncToSelf(playerRef.getPacketHandler(), player, ctrl);
        }
    }

    private EntityEffect resolveEffect(String name) {
        var assetMap = EntityEffect.getAssetMap();
        var map = assetMap.getAssetMap();

        EntityEffect effect = map.get(name);
        if (effect != null) return effect;

        for (Map.Entry<String, EntityEffect> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }

        String nameLower = name.toLowerCase();
        for (Map.Entry<String, EntityEffect> entry : map.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.endsWith("/" + nameLower) || key.endsWith(nameLower)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private void sendEffectSyncToSelf(PacketHandler ph, Player player, EffectControllerComponent ctrl) {
        if (ph == null) return;
        try {
            EntityEffectUpdate[] updates = ctrl.createInitUpdates();
            if (updates == null) {
                updates = new EntityEffectUpdate[0];
            }

            EntityEffectsUpdate cu = new EntityEffectsUpdate(updates);
            EntityUpdate eu = new EntityUpdate(player.getNetworkId(), null, new EntityEffectsUpdate[]{cu});
            ph.writeNoCache(new EntityUpdates(null, new EntityUpdate[]{eu}));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to sync effect to self");
        }
    }
}
