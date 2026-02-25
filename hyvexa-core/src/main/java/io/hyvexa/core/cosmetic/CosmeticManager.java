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
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.common.util.AsyncExecutionHelper;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles applying / removing cosmetic EntityEffects on players.
 * Must be called from the world thread for entity operations.
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
     * Apply a cosmetic effect to a player. Must be called from world thread.
     */
    public void applyCosmetic(Ref<EntityStore> ref, Store<EntityStore> store, String cosmeticId) {
        if (ref == null || !ref.isValid()) return;
        CosmeticDefinition def = CosmeticDefinition.fromId(cosmeticId);
        if (def == null) return;

        EntityEffect effect = resolveEffect(def.getEffectId());
        if (effect == null) {
            LOGGER.atWarning().log("Could not resolve effect for cosmetic: " + cosmeticId);
            return;
        }

        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) return;

        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
        if (effectIndex < 0) return;

        ctrl.addInfiniteEffect(ref, effectIndex, effect, store);

        // Self-sync: entity tracker only sends to OTHER players
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef != null && player != null) {
            sendEffectSyncToSelf(playerRef.getPacketHandler(), player, ctrl);
        }
    }

    /**
     * Remove all cosmetic effects from a player. Must be called from world thread.
     */
    public void removeCosmetic(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return;

        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) return;

        ctrl.clearEffects(ref, store);

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef != null && player != null) {
            sendEffectSyncToSelf(playerRef.getPacketHandler(), player, ctrl);
        }
    }

    /**
     * Preview a cosmetic for PREVIEW_DURATION_SECONDS, then re-apply equipped cosmetic (if any).
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

        EffectControllerComponent ctrl = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ctrl == null) return;

        EntityEffect effect = resolveEffect(def.getEffectId());
        if (effect == null) return;

        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
        if (effectIndex < 0) return;

        ctrl.clearEffects(ref, store);
        ctrl.addEffect(ref, effectIndex, effect, PREVIEW_DURATION_SECONDS, OverlapBehavior.OVERWRITE, store);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            sendEffectSyncToSelf(playerRef.getPacketHandler(), player, ctrl);
        }

        // Schedule re-apply of equipped cosmetic after preview ends
        World world = store.getExternalData().getWorld();
        ScheduledFuture<?> timer = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            previewTimers.remove(playerId);
            if (ref.isValid()) {
                AsyncExecutionHelper.runBestEffort(world, () -> {
                    if (!ref.isValid()) return;
                    String equipped = CosmeticStore.getInstance().getEquippedCosmeticId(playerId);
                    if (equipped != null) {
                        applyCosmetic(ref, store, equipped);
                    }
                }, "cosmetic.preview.restore", "restore cosmetic after preview", "player=" + playerId);
            }
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
            applyCosmetic(ref, store, equipped);
        }
    }

    /**
     * Apply a timed celebration effect on top of any equipped cosmetic. Must be called from world thread.
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
     * Clean up preview timers on disconnect.
     */
    public void cleanupOnDisconnect(UUID playerId) {
        if (playerId == null) return;
        cancelPreviewTimer(playerId);
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private void cancelPreviewTimer(UUID playerId) {
        ScheduledFuture<?> timer = previewTimers.remove(playerId);
        if (timer != null) {
            timer.cancel(false);
        }
    }

    private EntityEffect resolveEffect(String name) {
        var assetMap = EntityEffect.getAssetMap();
        var map = assetMap.getAssetMap();

        // Try exact match first
        EntityEffect effect = map.get(name);
        if (effect != null) return effect;

        // Try case-insensitive match
        for (var entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }

        // Try partial match (key ends with the name)
        for (var entry : map.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String nameLower = name.toLowerCase();
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

            EntityUpdate eu = new EntityUpdate(
                    player.getNetworkId(),
                    null,
                    new EntityEffectsUpdate[]{cu}
            );

            ph.writeNoCache(new EntityUpdates(null, new EntityUpdate[]{eu}));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to sync effect to self");
        }
    }
}
