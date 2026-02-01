package io.hyvexa.ascend.util;

import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;

import java.util.UUID;

/**
 * Helper for map unlock logic to avoid duplication.
 */
public final class MapUnlockHelper {

    private MapUnlockHelper() {
    }

    /**
     * Result of checking/ensuring map unlock status.
     */
    public static final class UnlockResult {
        public final boolean unlocked;
        public final AscendPlayerProgress.MapProgress mapProgress;

        public UnlockResult(boolean unlocked, AscendPlayerProgress.MapProgress mapProgress) {
            this.unlocked = unlocked;
            this.mapProgress = mapProgress;
        }
    }

    /**
     * Check if a map is unlocked for a player, auto-unlocking free maps.
     *
     * @param playerId The player UUID
     * @param map The map to check
     * @param playerStore The player store
     * @return UnlockResult with status and updated progress
     */
    public static UnlockResult checkAndEnsureUnlock(UUID playerId, AscendMap map, AscendPlayerStore playerStore) {
        return checkAndEnsureUnlock(playerId, map, playerStore, null);
    }

    /**
     * Check if a map is unlocked for a player, auto-unlocking free maps.
     * If mapStore is provided, also auto-unlocks the first map (lowest displayOrder).
     *
     * @param playerId The player UUID
     * @param map The map to check
     * @param playerStore The player store
     * @param mapStore The map store (optional, for first-map detection)
     * @return UnlockResult with status and updated progress
     */
    public static UnlockResult checkAndEnsureUnlock(UUID playerId, AscendMap map,
                                                     AscendPlayerStore playerStore,
                                                     AscendMapStore mapStore) {
        if (playerId == null || map == null || playerStore == null) {
            return new UnlockResult(false, null);
        }

        AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerId, map.getId());

        // Check if already unlocked
        if (mapProgress != null && mapProgress.isUnlocked()) {
            return new UnlockResult(true, mapProgress);
        }

        // Check if this map should be free (price = 0 or first map)
        boolean isFree = map.getEffectivePrice() <= 0;
        if (!isFree && mapStore != null) {
            isFree = isFirstMap(map, mapStore);
        }

        // Auto-unlock free maps
        if (isFree) {
            playerStore.setMapUnlocked(playerId, map.getId(), true);
            mapProgress = playerStore.getMapProgress(playerId, map.getId());
            return new UnlockResult(true, mapProgress);
        }

        return new UnlockResult(false, mapProgress);
    }

    /**
     * Check if a map is the first map (lowest displayOrder).
     */
    public static boolean isFirstMap(AscendMap map, AscendMapStore mapStore) {
        if (map == null || mapStore == null) {
            return false;
        }
        for (AscendMap other : mapStore.listMaps()) {
            if (other.getDisplayOrder() < map.getDisplayOrder()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a map is unlocked (without auto-unlock).
     *
     * @param mapProgress The map progress (may be null)
     * @param map The map
     * @return true if unlocked
     */
    public static boolean isUnlocked(AscendPlayerProgress.MapProgress mapProgress, AscendMap map) {
        if (map == null) {
            return false;
        }
        if (map.getEffectivePrice() <= 0) {
            return true;
        }
        return mapProgress != null && mapProgress.isUnlocked();
    }
}
