package io.hyvexa.ascend.util;

import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.ParkourAscendPlugin;
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
     * Check if a map is unlocked for a player, auto-unlocking maps that meet unlock requirements.
     * If mapStore is provided, checks if the map meets progression unlock requirements.
     *
     * @param playerId The player UUID
     * @param map The map to check
     * @param playerStore The player store
     * @param mapStore The map store (optional, for unlock requirement checking)
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

        // Check if this map meets the unlock requirement (runner level on previous map)
        boolean meetsRequirement = mapStore != null && meetsUnlockRequirement(playerId, map, playerStore, mapStore);

        // Auto-unlock if requirement met
        if (meetsRequirement) {
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
     * Get the previous map (displayOrder - 1) for unlock requirement checking.
     * Returns null if this is the first map.
     */
    public static AscendMap getPreviousMap(AscendMap currentMap, AscendMapStore mapStore) {
        if (currentMap == null || mapStore == null) {
            return null;
        }
        int targetOrder = currentMap.getDisplayOrder() - 1;
        if (targetOrder < 0) {
            return null;
        }
        for (AscendMap map : mapStore.listMaps()) {
            if (map.getDisplayOrder() == targetOrder) {
                return map;
            }
        }
        return null;
    }

    /**
     * Get the next map (displayOrder + 1) for auto-unlock after level up.
     * Returns null if this is the last map.
     */
    public static AscendMap getNextMap(AscendMap currentMap, AscendMapStore mapStore) {
        if (currentMap == null || mapStore == null) {
            return null;
        }
        int targetOrder = currentMap.getDisplayOrder() + 1;
        for (AscendMap map : mapStore.listMaps()) {
            if (map.getDisplayOrder() == targetOrder) {
                return map;
            }
        }
        return null;
    }

    /**
     * Check if a map meets the unlock requirement based on previous map's runner level.
     * Map 1 (displayOrder 0) always returns true.
     * Other maps check if previous map's runner is level 3+.
     */
    public static boolean meetsUnlockRequirement(UUID playerId, AscendMap map,
                                                   AscendPlayerStore playerStore,
                                                   AscendMapStore mapStore) {
        if (playerId == null || map == null || playerStore == null || mapStore == null) {
            return false;
        }

        // First map is always unlockable
        if (map.getDisplayOrder() == 0) {
            return true;
        }

        // Map 6 (displayOrder 5): requires transcendence milestone 1
        if (map.getDisplayOrder() == 5) {
            if (playerStore.getTranscendenceCount(playerId) < 1) {
                return false;
            }
        }

        // Challenge 3: block maps with displayOrder 3 or 4
        ParkourAscendPlugin plugin = ParkourAscendPlugin.getInstance();
        if (plugin != null && plugin.getChallengeManager() != null
                && plugin.getChallengeManager().isMapBlocked(playerId, map.getDisplayOrder())) {
            return false;
        }

        // Get previous map
        AscendMap previousMap = getPreviousMap(map, mapStore);
        if (previousMap == null) {
            return false;
        }

        // Check if previous map's runner is at required level
        int runnerLevel = playerStore.getRobotSpeedLevel(playerId, previousMap.getId());
        return runnerLevel >= AscendConstants.MAP_UNLOCK_REQUIRED_RUNNER_LEVEL;
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
