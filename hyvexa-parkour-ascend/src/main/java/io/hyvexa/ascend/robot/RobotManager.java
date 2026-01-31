package io.hyvexa.ascend.robot;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerProgress;
import io.hyvexa.ascend.data.AscendPlayerStore;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class RobotManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AscendMapStore mapStore;
    private final AscendPlayerStore playerStore;
    private final Map<String, RobotState> robots = new ConcurrentHashMap<>();
    private ScheduledFuture<?> tickTask;
    private long lastRefreshMs;

    public RobotManager(AscendMapStore mapStore, AscendPlayerStore playerStore) {
        this.mapStore = mapStore;
        this.playerStore = playerStore;
    }

    public void start() {
        tickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
            this::tick,
            AscendConstants.ROBOT_TICK_INTERVAL_MS,
            AscendConstants.ROBOT_TICK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        LOGGER.atInfo().log("RobotManager started");
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
        despawnAllRobots();
        LOGGER.atInfo().log("RobotManager stopped");
    }

    public void spawnRobot(UUID ownerId, String mapId) {
        String key = robotKey(ownerId, mapId);
        if (robots.containsKey(key)) {
            LOGGER.atWarning().log("Robot already exists for " + key);
            return;
        }

        RobotState state = new RobotState(ownerId, mapId);
        robots.put(key, state);

        // TODO: Actually spawn the entity in the world
        LOGGER.atInfo().log("Robot spawned for " + ownerId + " on map " + mapId);
    }

    public void despawnRobot(UUID ownerId, String mapId) {
        String key = robotKey(ownerId, mapId);
        RobotState state = robots.remove(key);
        if (state != null && state.getEntityRef() != null && state.getEntityRef().isValid()) {
            // TODO: Remove entity from world
        }
    }

    public void despawnAllRobots() {
        for (RobotState state : robots.values()) {
            if (state.getEntityRef() != null && state.getEntityRef().isValid()) {
                // TODO: Remove entity from world
            }
        }
        robots.clear();
    }

    public RobotState getRobot(UUID ownerId, String mapId) {
        return robots.get(robotKey(ownerId, mapId));
    }

    private void tick() {
        try {
            long now = System.currentTimeMillis();
            refreshRobots(now);
            for (RobotState robot : robots.values()) {
                tickRobot(robot, now);
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Error in robot tick: " + e.getMessage());
        }
    }

    private void refreshRobots(long now) {
        if (playerStore == null || mapStore == null) {
            return;
        }
        if (now - lastRefreshMs < AscendConstants.ROBOT_REFRESH_INTERVAL_MS) {
            return;
        }
        lastRefreshMs = now;
        Map<UUID, AscendPlayerProgress> players = playerStore.getPlayersSnapshot();
        Set<String> activeKeys = new HashSet<>();
        for (Map.Entry<UUID, AscendPlayerProgress> entry : players.entrySet()) {
            UUID playerId = entry.getKey();
            AscendPlayerProgress progress = entry.getValue();
            if (playerId == null || progress == null) {
                continue;
            }
            for (Map.Entry<String, AscendPlayerProgress.MapProgress> mapEntry : progress.getMapProgress().entrySet()) {
                String mapId = mapEntry.getKey();
                AscendPlayerProgress.MapProgress mapProgress = mapEntry.getValue();
                if (mapId == null || mapProgress == null) {
                    continue;
                }
                int robotCount = Math.max(0, mapProgress.getRobotCount());
                if (robotCount <= 0) {
                    continue;
                }
                AscendMap map = mapStore.getMap(mapId);
                if (map == null) {
                    continue;
                }
                String key = robotKey(playerId, mapId);
                RobotState state = robots.computeIfAbsent(key, k -> new RobotState(playerId, mapId));
                state.setRobotCount(robotCount);
                if (state.getLastCompletionMs() <= 0L) {
                    state.setLastCompletionMs(now);
                }
                activeKeys.add(key);
            }
        }
        if (activeKeys.isEmpty()) {
            robots.clear();
            return;
        }
        for (String key : robots.keySet()) {
            if (!activeKeys.contains(key)) {
                robots.remove(key);
            }
        }
    }

    private void tickRobot(RobotState robot, long now) {
        if (robot.isWaiting()) {
            return;
        }
        AscendMap map = mapStore.getMap(robot.getMapId());
        if (map == null) {
            return;
        }
        int robotCount = robot.getRobotCount();
        if (robotCount <= 0) {
            return;
        }
        long intervalMs = computeCompletionIntervalMs(map, robotCount);
        if (intervalMs <= 0L) {
            return;
        }
        long lastCompletionMs = robot.getLastCompletionMs();
        if (lastCompletionMs <= 0L) {
            robot.setLastCompletionMs(now);
            return;
        }
        long elapsed = now - lastCompletionMs;
        if (elapsed < intervalMs) {
            return;
        }
        long completions = elapsed / intervalMs;
        if (completions <= 0L) {
            return;
        }
        long reward = Math.max(0L, map.getBaseReward());
        if (reward > 0L) {
            long total;
            try {
                total = Math.multiplyExact(reward, completions);
            } catch (ArithmeticException e) {
                total = Long.MAX_VALUE;
            }
            playerStore.addCoins(robot.getOwnerId(), total);
        }
        playerStore.addRobotMultiplierBonus(robot.getOwnerId(), robot.getMapId(), completions * 0.01);
        robot.setLastCompletionMs(lastCompletionMs + (intervalMs * completions));
        robot.addRunsCompleted(completions);
    }

    private long computeCompletionIntervalMs(AscendMap map, int robotCount) {
        if (map == null || robotCount <= 0) {
            return -1L;
        }
        long base = Math.max(0L, map.getBaseRunTimeMs());
        long reduction = Math.max(0L, map.getRobotTimeReductionMs());
        if (base <= 0L) {
            return -1L;
        }
        long interval = base - (reduction * Math.max(0, robotCount - 1));
        return Math.max(1L, interval);
    }

    private String robotKey(UUID ownerId, String mapId) {
        return ownerId.toString() + ":" + mapId;
    }
}
