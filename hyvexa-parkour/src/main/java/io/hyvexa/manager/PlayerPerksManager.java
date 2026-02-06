package io.hyvexa.manager;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.Message;
import io.hyvexa.common.util.PermissionUtils;
import io.hyvexa.parkour.ParkourConstants;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Manages VIP speed boosts, rank caching, and nameplate rendering. */
public class PlayerPerksManager {

    private static final float VIP_SPEED_MIN_MULTIPLIER = 1.0f;
    private static final float VIP_SPEED_MAX_MULTIPLIER = 4.0f;

    private final ConcurrentHashMap<UUID, String> cachedRankNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> cachedNameplateTexts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Float> vipSpeedMultiplier = new ConcurrentHashMap<>();

    private final ProgressStore progressStore;
    private final MapStore mapStore;

    public PlayerPerksManager(ProgressStore progressStore, MapStore mapStore) {
        this.progressStore = progressStore;
        this.mapStore = mapStore;
    }

    public void invalidateRankCache(UUID playerId) {
        if (playerId != null) {
            cachedRankNames.remove(playerId);
        }
    }

    public void invalidateAllRankCaches() {
        cachedRankNames.clear();
    }

    public String getCachedRankName(UUID playerId) {
        if (playerId == null || progressStore == null || mapStore == null) {
            return "Unranked";
        }
        return cachedRankNames.computeIfAbsent(playerId, id -> progressStore.getRankName(id, mapStore));
    }

    public String getSpecialRankLabel(UUID playerId) {
        if (playerId == null || progressStore == null) {
            return null;
        }
        if (progressStore.isFounder(playerId)) {
            return "FOUNDER";
        }
        if (progressStore.isVip(playerId)) {
            return "VIP";
        }
        return null;
    }

    public String getSpecialRankColor(UUID playerId) {
        if (playerId == null || progressStore == null) {
            return null;
        }
        if (progressStore.isFounder(playerId)) {
            return "#ff8a3d";
        }
        if (progressStore.isVip(playerId)) {
            return "#b76cff";
        }
        return null;
    }

    public float getVipSpeedMultiplier(UUID playerId) {
        if (playerId == null) {
            return VIP_SPEED_MIN_MULTIPLIER;
        }
        return vipSpeedMultiplier.getOrDefault(playerId, VIP_SPEED_MIN_MULTIPLIER);
    }

    public void applyVipSpeedMultiplier(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
                                        float multiplier, boolean notify) {
        if (ref == null || store == null || playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (progressStore == null || (!progressStore.isVip(playerId) && !progressStore.isFounder(playerId))) {
            if (notify) {
                player.sendMessage(Message.raw("Speed boost is VIP/Founder only."));
            }
            return;
        }
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) {
            if (notify) {
                player.sendMessage(Message.raw("Movement settings unavailable."));
            }
            return;
        }
        movementManager.refreshDefaultSettings(ref, store);
        movementManager.applyDefaultSettings();
        MovementSettings settings = movementManager.getSettings();
        if (settings == null) {
            if (notify) {
                player.sendMessage(Message.raw("Movement settings unavailable."));
            }
            return;
        }
        float clampedMultiplier = Math.max(VIP_SPEED_MIN_MULTIPLIER,
                Math.min(VIP_SPEED_MAX_MULTIPLIER, multiplier));
        if (clampedMultiplier > VIP_SPEED_MIN_MULTIPLIER) {
            settings.maxSpeedMultiplier *= clampedMultiplier;
            settings.forwardRunSpeedMultiplier *= clampedMultiplier;
            settings.backwardRunSpeedMultiplier *= clampedMultiplier;
            settings.strafeRunSpeedMultiplier *= clampedMultiplier;
            settings.forwardSprintSpeedMultiplier *= clampedMultiplier;
            vipSpeedMultiplier.put(playerId, clampedMultiplier);
        } else {
            vipSpeedMultiplier.remove(playerId);
        }
        var packetHandler = playerRef.getPacketHandler();
        if (packetHandler != null) {
            movementManager.update(packetHandler);
        }
        if (notify) {
            String label = clampedMultiplier > VIP_SPEED_MIN_MULTIPLIER
                    ? "Speed set to x" + stripTrailingZeros(clampedMultiplier) + "."
                    : "Speed reset to normal.";
            player.sendMessage(Message.raw(label));
        }
    }

    public void disableVipSpeedBoost(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null || getVipSpeedMultiplier(playerId) <= VIP_SPEED_MIN_MULTIPLIER) {
            return;
        }
        applyVipSpeedMultiplier(ref, store, playerRef, VIP_SPEED_MIN_MULTIPLIER, false);
    }

    public boolean shouldDisableVipSpeedForStartTrigger(Store<EntityStore> store, Ref<EntityStore> ref,
                                                        PlayerRef playerRef) {
        if (store == null || ref == null || playerRef == null || mapStore == null) {
            return false;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null || getVipSpeedMultiplier(playerId) <= VIP_SPEED_MIN_MULTIPLIER) {
            return false;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return false;
        }
        Vector3d position = transform.getPosition();
        if (position == null) {
            return false;
        }
        double touchRadiusSq = ParkourConstants.TOUCH_RADIUS * ParkourConstants.TOUCH_RADIUS;
        return mapStore.findMapByStartTrigger(position.getX(), position.getY(), position.getZ(), touchRadiusSq) != null;
    }

    public void updatePlayerNameplate(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef,
                                      String rankName) {
        if (ref == null || store == null || playerRef == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return;
        }
        String name = playerRef.getUsername();
        if (name == null || name.isBlank()) {
            name = "Player";
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null && PermissionUtils.isOp(player)) {
            String text = "[ADMIN] " + name;
            String cached = cachedNameplateTexts.get(playerId);
            if (text.equals(cached)) {
                return;
            }
            cachedNameplateTexts.put(playerId, text);
            Nameplate nameplate = store.ensureAndGetComponent(ref, Nameplate.getComponentType());
            nameplate.setText(text);
            return;
        }
        String safeRank = (rankName == null || rankName.isBlank()) ? "Unranked" : rankName;
        String badgeLabel = getSpecialRankLabel(playerId);
        String badgeSuffix = badgeLabel != null ? "(" + badgeLabel + ")" : "";
        String text = "[" + safeRank + "]" + badgeSuffix + " " + name;
        String cached = cachedNameplateTexts.get(playerId);
        if (text.equals(cached)) {
            return;
        }
        cachedNameplateTexts.put(playerId, text);
        Nameplate nameplate = store.ensureAndGetComponent(ref, Nameplate.getComponentType());
        nameplate.setText(text);
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        cachedRankNames.remove(playerId);
        cachedNameplateTexts.remove(playerId);
        vipSpeedMultiplier.remove(playerId);
    }

    public void sweepStalePlayers(Set<UUID> onlinePlayers) {
        cachedRankNames.keySet().removeIf(id -> !onlinePlayers.contains(id));
        cachedNameplateTexts.keySet().removeIf(id -> !onlinePlayers.contains(id));
        vipSpeedMultiplier.keySet().removeIf(id -> !onlinePlayers.contains(id));
    }

    private String stripTrailingZeros(float value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
