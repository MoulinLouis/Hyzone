package io.hyvexa.ascend.data;

import io.hyvexa.ascend.ascension.ChallengeManager;
import io.hyvexa.ascend.robot.RobotManager;
import io.hyvexa.ascend.util.MapUnlockHelper;
import io.hyvexa.common.math.BigNumber;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facade for runner/map operations: map progress, multipliers, unlocks, and robots.
 * Delegates to the shared player cache with dirty-marking for persistence.
 */
public class AscendRunnerFacade {

    private final Map<UUID, AscendPlayerProgress> players;
    private final AscendPlayerPersistence persistence;
    private final AscendPlayerStore store;
    private ChallengeManager challengeManager;
    private RobotManager robotManager;

    AscendRunnerFacade(Map<UUID, AscendPlayerProgress> players, AscendPlayerPersistence persistence, AscendPlayerStore store) {
        this.players = players;
        this.persistence = persistence;
        this.store = store;
    }

    void setRuntimeServices(ChallengeManager challengeManager, RobotManager robotManager) {
        this.challengeManager = challengeManager;
        this.robotManager = robotManager;
    }

    public GameplayState.MapProgress getMapProgress(UUID playerId, String mapId) {
        AscendPlayerProgress progress = players.get(playerId);
        if (progress == null) {
            return null;
        }
        return progress.gameplay().getMapProgress().get(mapId);
    }

    public GameplayState.MapProgress getOrCreateMapProgress(UUID playerId, String mapId) {
        AscendPlayerProgress progress = store.getOrCreatePlayer(playerId);
        return progress.gameplay().getOrCreateMapProgress(mapId);
    }

    /**
     * Returns authoritative best time for this player/map.
     * Uses in-memory value first, then DB fallback if memory is missing.
     */
    public Long getBestTimeMs(UUID playerId, String mapId) {
        if (playerId == null || mapId == null) {
            return null;
        }

        GameplayState.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress != null && mapProgress.getBestTimeMs() != null) {
            return mapProgress.getBestTimeMs();
        }

        Long dbBest = persistence.loadBestTimeFromDatabase(playerId, mapId);
        if (dbBest != null) {
            GameplayState.MapProgress target = getOrCreateMapProgress(playerId, mapId);
            Long current = target.getBestTimeMs();
            if (current == null || dbBest < current) {
                target.setBestTimeMs(dbBest);
            }
        }
        return dbBest;
    }

    public boolean setMapUnlocked(UUID playerId, String mapId, boolean unlocked) {
        GameplayState.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        if (mapProgress.isUnlocked() == unlocked) {
            return false;
        }
        mapProgress.setUnlocked(unlocked);
        store.markDirty(playerId);
        return true;
    }

    /**
     * Check if any maps should be auto-unlocked based on runner levels.
     * Returns the list of newly unlocked map IDs for notification.
     */
    public List<String> checkAndUnlockEligibleMaps(UUID playerId, AscendMapStore mapStore) {
        if (playerId == null || mapStore == null) {
            return List.of();
        }

        List<String> newlyUnlockedMapIds = new ArrayList<>();
        List<AscendMap> allMaps = new ArrayList<>(mapStore.listMapsSorted());

        // Start from index 1 (skip first map which is always unlocked)
        for (int i = 1; i < allMaps.size(); i++) {
            AscendMap map = allMaps.get(i);
            if (map == null) {
                continue;
            }

            // Check if already unlocked
            GameplayState.MapProgress mapProgress = getMapProgress(playerId, map.getId());
            if (mapProgress != null && mapProgress.isUnlocked()) {
                continue;
            }

            // Check if meets unlock requirement
            if (MapUnlockHelper.meetsUnlockRequirement(playerId, map, store, mapStore, challengeManager)) {
                setMapUnlocked(playerId, map.getId(), true);
                newlyUnlockedMapIds.add(map.getId());
            }
        }

        return newlyUnlockedMapIds;
    }

    public BigNumber getMapMultiplier(UUID playerId, String mapId) {
        GameplayState.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return BigNumber.ONE;
        }
        return mapProgress.getMultiplier().max(BigNumber.ONE);
    }

    public BigNumber addMapMultiplier(UUID playerId, String mapId, BigNumber amount) {
        GameplayState.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        BigNumber value = mapProgress.addMultiplier(amount);
        store.markDirty(playerId);
        return value;
    }

    public boolean hasRobot(UUID playerId, String mapId) {
        GameplayState.MapProgress mapProgress = getMapProgress(playerId, mapId);
        return mapProgress != null && mapProgress.hasRobot();
    }

    public void setHasRobot(UUID playerId, String mapId, boolean hasRobot) {
        GameplayState.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        mapProgress.setHasRobot(hasRobot);
        store.markDirty(playerId);
        notifyRobotManager(playerId);
    }

    public int getRobotSpeedLevel(UUID playerId, String mapId) {
        GameplayState.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 0;
        }
        return Math.max(0, mapProgress.getRobotSpeedLevel());
    }

    public int incrementRobotSpeedLevel(UUID playerId, String mapId) {
        GameplayState.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        int value = mapProgress.incrementRobotSpeedLevel();
        store.markDirty(playerId);
        notifyRobotManager(playerId);
        return value;
    }

    public int getRobotStars(UUID playerId, String mapId) {
        GameplayState.MapProgress mapProgress = getMapProgress(playerId, mapId);
        if (mapProgress == null) {
            return 0;
        }
        return Math.max(0, mapProgress.getRobotStars());
    }

    public int evolveRobot(UUID playerId, String mapId) {
        GameplayState.MapProgress mapProgress = getOrCreateMapProgress(playerId, mapId);
        mapProgress.setRobotSpeedLevel(0);
        int newStars = mapProgress.incrementRobotStars();
        store.markDirty(playerId);
        notifyRobotManager(playerId);
        return newStars;
    }

    private void notifyRobotManager(UUID playerId) {
        if (robotManager == null) {
            return;
        }
        robotManager.markPlayerDirty(playerId);
    }
}
