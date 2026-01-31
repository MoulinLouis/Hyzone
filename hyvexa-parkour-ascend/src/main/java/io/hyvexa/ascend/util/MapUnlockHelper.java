package io.hyvexa.ascend.util;

import io.hyvexa.ascend.data.AscendMap;
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
        if (playerId == null || map == null || playerStore == null) {
            return new UnlockResult(false, null);
        }

        AscendPlayerProgress.MapProgress mapProgress = playerStore.getMapProgress(playerId, map.getId());
        boolean unlocked = map.getPrice() <= 0;

        if (mapProgress != null && mapProgress.isUnlocked()) {
            unlocked = true;
        }

        // Auto-unlock free maps
        if (map.getPrice() <= 0 && (mapProgress == null || !mapProgress.isUnlocked())) {
            playerStore.setMapUnlocked(playerId, map.getId(), true);
            mapProgress = playerStore.getMapProgress(playerId, map.getId());
            unlocked = true;
        }

        return new UnlockResult(unlocked, mapProgress);
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
        if (map.getPrice() <= 0) {
            return true;
        }
        return mapProgress != null && mapProgress.isUnlocked();
    }
}
