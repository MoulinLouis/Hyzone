package io.hyvexa.purge.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import io.hyvexa.purge.data.PurgeSession;
import io.hyvexa.purge.data.PurgeVariantConfig;
import io.hyvexa.purge.manager.PurgeVariantConfigManager;

import java.util.List;
import java.util.Map;

/**
 * All aggro boost logic for purge zombies: speed multiplier, instruction tree traversal,
 * sensor range boosting, view cone widening, and delay elimination.
 */
public final class ZombieAggroBooster {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Detection range for purge zombies — covers entire arena. */
    private static final double PURGE_AGGRO_RANGE = 80.0;
    /** Zero delay for all instruction tree timeouts (eliminates Alerted->Search pause). */
    private static final double[] PURGE_NO_DELAY = {0.0, 0.0};
    /** Omni-directional view cone so zombies detect players in all directions. */
    private static final float PURGE_VIEW_CONE = 360.0f;

    private ZombieAggroBooster() {
    }

    /**
     * Modifies the zombie's per-entity instruction tree to boost detection range,
     * eliminate transition delays, and widen view cones. This makes purge zombies
     * immediately and persistently pursue players across the entire arena.
     * <p>
     * The instruction tree is cloned per-entity, so these modifications only affect
     * the specific zombie — other NPCs server-wide are unaffected.
     */
    public static void boostZombieAggro(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        try {
            NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
            if (npcEntity == null || npcEntity.getRole() == null) return;
            Object rootInstruction = npcEntity.getRole().getRootInstruction();
            if (rootInstruction == null) return;
            sun.misc.Unsafe unsafe = UnsafeReflectionHelper.getUnsafe();
            if (unsafe == null) return;
            traverseAndBoost(unsafe, rootInstruction);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to boost zombie aggro");
        }
    }

    private static int traverseAndBoost(sun.misc.Unsafe unsafe, Object instruction) {
        return traverseAndBoost(unsafe, instruction, 0);
    }

    private static int traverseAndBoost(sun.misc.Unsafe unsafe, Object instruction, int depth) {
        if (instruction == null) return 0;
        int modified = 0;

        Object sensor = UnsafeReflectionHelper.readFieldCached(instruction, "sensor");
        if (sensor != null) {
            modified += boostSensorTree(unsafe, sensor, 0);
        }

        Object bodyMotion = UnsafeReflectionHelper.readFieldCached(instruction, "bodyMotion");
        if (bodyMotion != null) {
            if (boostBodyMotionRange(unsafe, bodyMotion)) modified++;
        }

        Object actions = UnsafeReflectionHelper.readFieldCached(instruction, "actions");
        if (actions != null) {
            modified += eliminateActionDelays(unsafe, actions);
        }

        // Recurse into child instructions
        Object children = UnsafeReflectionHelper.readFieldCached(instruction, "instructionList");
        if (children == null) children = UnsafeReflectionHelper.readFieldCached(instruction, "instructions");
        if (children == null) children = UnsafeReflectionHelper.readFieldCached(instruction, "children");
        if (children instanceof Object[] arr) {
            for (Object child : arr) {
                modified += traverseAndBoost(unsafe, child, depth + 1);
            }
        }
        return modified;
    }

    /**
     * Recursively boosts a sensor and all nested inner sensors (handles Not/And/Or wrappers).
     * Without this recursion, wrapped sensors like Not(Mob Range 5) keep their original small
     * range, causing the zombie to immediately transition out of Angry state.
     */
    private static int boostSensorTree(sun.misc.Unsafe unsafe, Object sensor, int depth) {
        if (sensor == null || depth > 5) return 0;
        int modified = 0;

        // Boost range on this sensor (works if it's a SensorEntityBase subclass)
        if (boostSensorRange(unsafe, sensor)) modified++;

        // Boost view cone on this sensor's entity filters
        boostSensorViewCone(unsafe, sensor);

        // Recurse into wrapped inner sensors (Not, And, Or wrappers)
        for (String name : List.of("sensor", "baseSensor", "inner", "wrapped",
                                    "sensorA", "sensorB", "first", "second")) {
            Object inner = UnsafeReflectionHelper.readFieldCached(sensor, name);
            if (inner != null && inner != sensor) {
                modified += boostSensorTree(unsafe, inner, depth + 1);
            }
        }

        // Handle composite sensors with a list of inner sensors
        Object sensors = UnsafeReflectionHelper.readFieldCached(sensor, "sensors");
        if (sensors instanceof Object[] arr) {
            for (Object s : arr) {
                if (s != null && s != sensor) modified += boostSensorTree(unsafe, s, depth + 1);
            }
        } else if (sensors instanceof Iterable<?> it) {
            for (Object s : it) {
                if (s != null && s != sensor) modified += boostSensorTree(unsafe, s, depth + 1);
            }
        }

        return modified;
    }

    private static boolean boostSensorRange(sun.misc.Unsafe unsafe, Object sensor) {
        long offset = UnsafeReflectionHelper.resolveAggroFieldOffset(unsafe, sensor, "range",
                UnsafeReflectionHelper.SENSOR_RANGE_OFFSETS);
        if (offset < 0) return false;
        double original = unsafe.getDouble(sensor, offset);
        // Don't boost small-range sensors (< 5 blocks) — they're melee/proximity checks
        // that may be used in NOT wrappers. Boosting them inverts critical logic.
        if (original < 5.0) return false;
        unsafe.putDouble(sensor, offset, PURGE_AGGRO_RANGE);
        return true;
    }

    private static void boostSensorViewCone(sun.misc.Unsafe unsafe, Object sensor) {
        // Entity filters are stored inside SensorWithEntityFilters subclasses
        Object filters = UnsafeReflectionHelper.readFieldCached(sensor, "entityFilters");
        if (filters == null) filters = UnsafeReflectionHelper.readFieldCached(sensor, "filters");
        if (filters instanceof Object[] arr) {
            for (Object filter : arr) {
                if (filter == null) continue;
                long offset = UnsafeReflectionHelper.resolveAggroFieldOffset(unsafe, filter, "viewCone",
                        UnsafeReflectionHelper.VIEW_CONE_OFFSETS);
                if (offset >= 0) {
                    unsafe.putFloat(filter, offset, PURGE_VIEW_CONE);
                }
            }
        } else if (filters instanceof Iterable<?> it) {
            for (Object filter : it) {
                if (filter == null) continue;
                long offset = UnsafeReflectionHelper.resolveAggroFieldOffset(unsafe, filter, "viewCone",
                        UnsafeReflectionHelper.VIEW_CONE_OFFSETS);
                if (offset >= 0) {
                    unsafe.putFloat(filter, offset, PURGE_VIEW_CONE);
                }
            }
        }
    }

    /**
     * Boosts distance fields on BodyMotionFind objects so the pathfinder can handle
     * long-range pursuit. Three critical fields:
     * - abortDistance: prevents the pathfinder from giving up at range
     * - switchToSteeringDistance: forces direct beeline pursuit instead of A* pathfinding,
     *   bypassing the A* node budget limits that prevent long-range path computation
     */
    private static boolean boostBodyMotionRange(sun.misc.Unsafe unsafe, Object bodyMotion) {
        boolean modified = false;
        double range = PURGE_AGGRO_RANGE;
        double rangeSq = range * range;

        long offset = UnsafeReflectionHelper.resolveAggroFieldOffset(unsafe, bodyMotion, "abortDistance",
                UnsafeReflectionHelper.ABORT_DIST_OFFSETS);
        if (offset >= 0) {
            unsafe.putDouble(bodyMotion, offset, range);
            modified = true;
        }
        offset = UnsafeReflectionHelper.resolveAggroFieldOffset(unsafe, bodyMotion, "abortDistanceSquared",
                UnsafeReflectionHelper.ABORT_DIST_SQ_OFFSETS);
        if (offset >= 0) {
            unsafe.putDouble(bodyMotion, offset, rangeSq);
        }

        offset = UnsafeReflectionHelper.resolveAggroFieldOffset(unsafe, bodyMotion, "switchToSteeringDistance",
                UnsafeReflectionHelper.STEER_DIST_OFFSETS);
        if (offset >= 0) {
            unsafe.putDouble(bodyMotion, offset, range);
            modified = true;
        }
        offset = UnsafeReflectionHelper.resolveAggroFieldOffset(unsafe, bodyMotion, "switchToSteeringDistanceSquared",
                UnsafeReflectionHelper.STEER_DIST_SQ_OFFSETS);
        if (offset >= 0) {
            unsafe.putDouble(bodyMotion, offset, rangeSq);
        }

        // If no fields found on this object, check for wrapped inner motion (e.g. BodyMotionTimer.motion)
        if (!modified) {
            for (String innerName : List.of("motion", "bodyMotion", "inner", "wrapped", "find")) {
                Object inner = UnsafeReflectionHelper.readFieldCached(bodyMotion, innerName);
                if (inner != null && inner != bodyMotion) {
                    if (boostBodyMotionRange(unsafe, inner)) {
                        modified = true;
                        break;
                    }
                }
            }
        }
        return modified;
    }

    private static int eliminateActionDelays(sun.misc.Unsafe unsafe, Object actions) {
        int modified = 0;
        // ActionList is a container — try iterating it or accessing its internal list
        Iterable<?> actionIter = null;
        if (actions instanceof Iterable<?> it) {
            actionIter = it;
        } else {
            Object list = UnsafeReflectionHelper.readFieldCached(actions, "actions");
            if (list == null) list = UnsafeReflectionHelper.readFieldCached(actions, "actionList");
            if (list == null) list = UnsafeReflectionHelper.readFieldCached(actions, "list");
            if (list instanceof Iterable<?> it) {
                actionIter = it;
            } else if (list instanceof Object[] arr) {
                for (Object action : arr) {
                    if (action != null && zeroDelayRange(unsafe, action)) modified++;
                }
                return modified;
            }
        }
        if (actionIter != null) {
            for (Object action : actionIter) {
                if (action != null && zeroDelayRange(unsafe, action)) modified++;
            }
        }
        return modified;
    }

    private static boolean zeroDelayRange(sun.misc.Unsafe unsafe, Object action) {
        long offset = UnsafeReflectionHelper.resolveAggroFieldOffset(unsafe, action, "delayRange",
                UnsafeReflectionHelper.DELAY_RANGE_OFFSETS);
        if (offset < 0) return false;
        unsafe.putObject(action, offset, PURGE_NO_DELAY);
        return true;
    }

    /**
     * Safety-net re-aggro: forces zombies that dropped out of combat state back into
     * Angry with a fresh target. Handles edge cases like LOS obstruction where the
     * state machine might downgrade despite boosted instruction tree parameters.
     */
    public static void refreshZombieAggro(PurgeSession session, Store<EntityStore> store) {
        // Just refresh LockedTarget — don't force setState (paralyzes the NPC).
        // The boosted sensors + natural AI handle state transitions.
        Ref<EntityStore> targetRef = session.getRandomAlivePlayerRef();
        if (targetRef == null) return;
        for (Ref<EntityStore> zombieRef : session.getAliveZombies()) {
            if (zombieRef == null || !zombieRef.isValid()) continue;
            try {
                NPCEntity npc = store.getComponent(zombieRef, NPCEntity.getComponentType());
                if (npc == null || npc.getRole() == null) continue;
                npc.getRole().setMarkedTarget("LockedTarget", targetRef);
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to refresh zombie target: " + e.getMessage());
            }
        }
    }

    public static void applySpeedMultiplier(Store<EntityStore> store, Ref<EntityStore> entityRef,
                                             PurgeVariantConfig variant) {
        if (variant.speedMultiplier() == 1.0) {
            return;
        }
        try {
            NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
            if (npcEntity == null || npcEntity.getRole() == null) {
                return;
            }
            // Modify maxHorizontalSpeed (final field) on all motion controllers.
            // horizontalSpeedMultiplier is reset to 1.0 every tick by the engine, so we scale the base speed instead.
            Map<String, ?> controllers = UnsafeReflectionHelper.getMotionControllers(npcEntity.getRole());
            if (controllers == null || controllers.isEmpty()) {
                return;
            }
            sun.misc.Unsafe unsafe = UnsafeReflectionHelper.getUnsafe();
            if (unsafe == null) {
                LOGGER.atWarning().log("Unsafe not available, cannot apply speed multiplier");
                return;
            }
            int applied = 0;
            for (Map.Entry<String, ?> entry : controllers.entrySet()) {
                long offset = UnsafeReflectionHelper.resolveMaxSpeedFieldOffset(unsafe, entry.getValue());
                if (offset >= 0) {
                    double baseSpeed = unsafe.getDouble(entry.getValue(), offset);
                    unsafe.putDouble(entry.getValue(), offset, baseSpeed * variant.speedMultiplier());
                    applied++;
                }
            }
            LOGGER.atInfo().log("Applied speed x" + variant.speedMultiplier() + " to " + applied + "/" + controllers.size()
                    + " motion controllers for variant " + variant.key());
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to apply speed multiplier for " + variant.key() + ": " + e.getMessage());
        }
    }
}
