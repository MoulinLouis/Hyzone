package io.hyvexa.ascend.robot;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.ascend.data.AscendPlayerStore;

import java.util.Map;
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
            for (RobotState robot : robots.values()) {
                tickRobot(robot);
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Error in robot tick: " + e.getMessage());
        }
    }

    private void tickRobot(RobotState robot) {
        if (robot.isWaiting()) {
            return;
        }

        // TODO: Implement waypoint movement logic
        // 1. Get current position
        // 2. Calculate direction to next waypoint
        // 3. Set velocity
        // 4. Check if waypoint reached
        // 5. If at finish, add coins directly, reset to start
    }

    private String robotKey(UUID ownerId, String mapId) {
        return ownerId.toString() + ":" + mapId;
    }
}
