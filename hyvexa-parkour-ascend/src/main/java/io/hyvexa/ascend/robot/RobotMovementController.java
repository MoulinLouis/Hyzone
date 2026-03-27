package io.hyvexa.ascend.robot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.hyvexa.ascend.AscendConstants;
import io.hyvexa.ascend.data.AscendMap;
import io.hyvexa.ascend.data.AscendMapStore;
import io.hyvexa.common.ghost.GhostRecording;
import io.hyvexa.common.ghost.GhostSample;
import io.hyvexa.common.ghost.GhostStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

class RobotMovementController {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long HEADLESS_MOVEMENT_INTERVAL_MS = 50L;
    private static final long VISIBLE_MOVEMENT_INTERVAL_MS = AscendConstants.RUNNER_TICK_INTERVAL_MS;
    private static final long TELEPORT_WARNING_THROTTLE_MS = 10_000L;
    private static final long TELEPORT_WARNING_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(30L);
    private static final long TELEPORT_WARNING_CLEANUP_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5L);

    private final RobotManager manager;
    private final RobotRefreshSystem refreshSystem;
    private final RunnerSpeedCalculator speedCalculator;
    private final Map<String, Long> teleportWarningByRobot = new ConcurrentHashMap<>();
    private volatile long lastTeleportWarningCleanupMs = 0L;

    RobotMovementController(RobotManager manager, RobotRefreshSystem refreshSystem,
                            RunnerSpeedCalculator speedCalculator) {
        this.manager = manager;
        this.refreshSystem = refreshSystem;
        this.speedCalculator = speedCalculator;
    }

    void tickMovement(RobotState robot, long now, RobotRefreshSystem.ViewerContext currentViewerContext,
                      Map<World, List<PendingTeleport>> teleportsByWorld) {
        if (!robot.isEntityDesired()) {
            return;
        }
        Ref<EntityStore> entityRef = robot.getEntityRef();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        AscendMap map = refreshSystem.getCachedMap(robot, now);
        GhostRecording ghost = refreshSystem.getCachedGhost(robot, now);
        World world = refreshSystem.getCachedWorld(robot, now);
        if (map == null || ghost == null || world == null) {
            return;
        }

        long targetMovementIntervalMs = currentViewerContext.isHighFrequencyMap(robot.getMapId())
                ? VISIBLE_MOVEMENT_INTERVAL_MS
                : HEADLESS_MOVEMENT_INTERVAL_MS;
        if (robot.getMovementIntervalMs() != targetMovementIntervalMs) {
            robot.setMovementIntervalMs(targetMovementIntervalMs);
            robot.setNextMovementAtMs(now);
        }
        if (now < robot.getNextMovementAtMs()) {
            return;
        }
        robot.setNextMovementAtMs(now + targetMovementIntervalMs);

        long intervalMs = computeCompletionIntervalMs(map, ghost, robot.getSpeedLevel(), robot.getOwnerId());
        if (intervalMs <= 0L) {
            return;
        }

        long lastCompletionMs = robot.getLastCompletionMs();
        if (lastCompletionMs <= 0L) {
            return;
        }

        long elapsed = now - lastCompletionMs;
        double progress = Math.min(1.0d, (double) elapsed / (double) intervalMs);
        GhostSample sample = ghost.interpolateAt(progress);
        queueTeleport(teleportsByWorld, world, robot, entityRef, sample.x(), sample.y(), sample.z(), sample.yaw());
        robot.setPreviousPosition(sample.x(), sample.y(), sample.z());
    }

    void flushTeleportBatches(Map<World, List<PendingTeleport>> teleportsByWorld) {
        for (Map.Entry<World, List<PendingTeleport>> entry : teleportsByWorld.entrySet()) {
            World world = entry.getKey();
            List<PendingTeleport> teleports = entry.getValue();
            if (world == null || teleports == null || teleports.isEmpty()) {
                continue;
            }
            world.execute(() -> {
                for (PendingTeleport teleport : teleports) {
                    teleportNpcWithRecordedRotation(teleport.robot(), teleport.entityRef(), world,
                            teleport.x(), teleport.y(), teleport.z(), teleport.yaw());
                }
            });
        }
    }

    void queueTeleport(Map<World, List<PendingTeleport>> teleportsByWorld, World world, RobotState robot,
                       Ref<EntityStore> entityRef, double x, double y, double z, float yaw) {
        if (world == null || entityRef == null || !entityRef.isValid()) {
            return;
        }
        teleportsByWorld.computeIfAbsent(world, ignored -> new ArrayList<>())
                .add(new PendingTeleport(robot, entityRef, x, y, z, yaw));
    }

    void cleanupTeleportWarnings(long now) {
        if (now - lastTeleportWarningCleanupMs >= TELEPORT_WARNING_CLEANUP_INTERVAL_MS) {
            Map<String, RobotState> robots = manager.getRobots();
            teleportWarningByRobot.entrySet().removeIf(entry ->
                    now - entry.getValue() >= TELEPORT_WARNING_CACHE_TTL_MS || !robots.containsKey(entry.getKey()));
            lastTeleportWarningCleanupMs = now;
        }
    }

    void clearTeleportWarning(RobotState state) {
        if (state == null) {
            return;
        }
        teleportWarningByRobot.remove(manager.robotKey(state.getOwnerId(), state.getMapId()));
    }

    double getRunnerProgress(UUID ownerId, String mapId) {
        Map<String, RobotState> robots = manager.getRobots();
        RobotState robot = robots.get(manager.robotKey(ownerId, mapId));
        if (robot == null) {
            return -1;
        }
        AscendMapStore mapStore = manager.getMapStore();
        AscendMap map = mapStore.getMap(mapId);
        if (map == null) {
            return -1;
        }
        GhostStore ghostStore = manager.getGhostStore();
        GhostRecording ghost = ghostStore.getRecording(ownerId, map.getId());
        long intervalMs = computeCompletionIntervalMs(map, ghost, robot.getSpeedLevel(), ownerId);
        if (intervalMs <= 0L) {
            return -1;
        }
        long lastCompletion = robot.getLastCompletionMs();
        if (lastCompletion <= 0L) {
            return -1;
        }
        long elapsed = System.currentTimeMillis() - lastCompletion;
        if (elapsed < 0) {
            return 0;
        }
        return Math.min(1.0, (double) elapsed / intervalMs);
    }

    long computeCompletionIntervalMs(AscendMap map, GhostRecording ghost, int speedLevel, UUID ownerId) {
        if (map == null) {
            return -1L;
        }

        // Use player's PB time as base (from ghost recording)
        long base;
        if (ghost != null) {
            base = ghost.getCompletionTimeMs();
        } else {
            // Fallback: use map's base run time so runners still earn without a ghost
            base = map.getEffectiveBaseRunTimeMs();
        }
        if (base <= 0L) {
            return -1L;
        }

        double speedMultiplier = speedCalculator != null
                ? speedCalculator.calculateSpeedMultiplier(map, speedLevel, ownerId)
                : 1.0;

        long interval = (long) (base / speedMultiplier);
        return Math.max(1L, interval);
    }

    private void teleportNpcWithRecordedRotation(RobotState robot, Ref<EntityStore> entityRef, World world,
                                                 double x, double y, double z, float yaw) {
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store == null) {
                return;
            }

            Vector3d targetVec = new Vector3d(x, y, z);
            Vector3f rotation = new Vector3f(0, yaw, 0);
            store.addComponent(entityRef, Teleport.getComponentType(),
                    new Teleport(world, targetVec, rotation));
        } catch (Exception e) {
            logTeleportWarning(robot, world, x, y, z, yaw, e);
        }
    }

    private void logTeleportWarning(RobotState robot, World world, double x, double y, double z,
                                    float yaw, Exception error) {
        String key = manager.robotKey(robot.getOwnerId(), robot.getMapId());
        long now = System.currentTimeMillis();
        Long lastLogged = teleportWarningByRobot.get(key);
        if (lastLogged != null && now - lastLogged < TELEPORT_WARNING_THROTTLE_MS) {
            return;
        }
        teleportWarningByRobot.put(key, now);
        String worldName = world != null ? world.getName() : "unknown";
        LOGGER.atWarning().withCause(error).log(
                "Runner teleport failed owner=" + robot.getOwnerId()
                        + " map=" + robot.getMapId()
                        + " world=" + worldName
                        + " target=" + String.format("(%.2f, %.2f, %.2f)", x, y, z)
                        + " yaw=" + yaw
        );
    }

    record PendingTeleport(RobotState robot, Ref<EntityStore> entityRef,
                           double x, double y, double z, float yaw) {}
}
