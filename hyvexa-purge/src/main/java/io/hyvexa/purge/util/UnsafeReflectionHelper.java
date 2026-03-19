package io.hyvexa.purge.util;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static utility class for all sun.misc.Unsafe operations and field resolution caching
 * used by the purge zombie system.
 */
public final class UnsafeReflectionHelper {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile sun.misc.Unsafe UNSAFE_INSTANCE;
    private static volatile java.lang.reflect.Field MOTION_CONTROLLERS_FIELD;

    // Generic field read cache for instruction tree traversal (className#fieldName -> Field)
    private static final ConcurrentHashMap<String, java.lang.reflect.Field> FIELD_READ_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> FIELD_READ_MISSING = ConcurrentHashMap.newKeySet();

    // Aggro boost: per-class Unsafe offsets for final-field writes on instruction tree nodes
    static final ConcurrentHashMap<Class<?>, Long> SENSOR_RANGE_OFFSETS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Class<?>, Long> DELAY_RANGE_OFFSETS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Class<?>, Long> VIEW_CONE_OFFSETS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Class<?>, Long> ABORT_DIST_OFFSETS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Class<?>, Long> ABORT_DIST_SQ_OFFSETS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Class<?>, Long> STEER_DIST_OFFSETS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Class<?>, Long> STEER_DIST_SQ_OFFSETS = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Class<?>, Long> MAX_SPEED_OFFSET_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, java.lang.reflect.Field> DROP_LIST_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Set<Class<?>> DROP_LIST_FIELD_MISSING = ConcurrentHashMap.newKeySet();

    private UnsafeReflectionHelper() {
    }

    public static sun.misc.Unsafe getUnsafe() {
        if (UNSAFE_INSTANCE != null) return UNSAFE_INSTANCE;
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE_INSTANCE = (sun.misc.Unsafe) f.get(null);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to get Unsafe: " + e.getMessage());
        }
        return UNSAFE_INSTANCE;
    }

    public static Object readFieldCached(Object target, String fieldName) {
        if (target == null) return null;
        String key = target.getClass().getName() + "#" + fieldName;
        if (FIELD_READ_MISSING.contains(key)) return null;
        java.lang.reflect.Field field = FIELD_READ_CACHE.get(key);
        if (field == null) {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    FIELD_READ_CACHE.put(key, field);
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (field == null) {
                FIELD_READ_MISSING.add(key);
                return null;
            }
        }
        try {
            return field.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    public static long resolveAggroFieldOffset(sun.misc.Unsafe unsafe, Object target, String fieldName,
                                                ConcurrentHashMap<Class<?>, Long> cache) {
        Class<?> targetClass = target.getClass();
        Long cached = cache.get(targetClass);
        if (cached != null) return cached;
        Class<?> clazz = targetClass;
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                long offset = unsafe.objectFieldOffset(field);
                cache.put(targetClass, offset);
                return offset;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        cache.put(targetClass, -1L);
        LOGGER.atFine().log(fieldName + " field not found in " + targetClass.getName() + " hierarchy");
        return -1;
    }

    public static long resolveMaxSpeedFieldOffset(sun.misc.Unsafe unsafe, Object controller) {
        Class<?> controllerClass = controller.getClass();
        Long cached = MAX_SPEED_OFFSET_CACHE.get(controllerClass);
        if (cached != null) {
            return cached;
        }
        // maxHorizontalSpeed is declared on MotionControllerBase, search up hierarchy
        Class<?> clazz = controllerClass;
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField("maxHorizontalSpeed");
                long offset = unsafe.objectFieldOffset(field);
                MAX_SPEED_OFFSET_CACHE.put(controllerClass, offset);
                return offset;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        MAX_SPEED_OFFSET_CACHE.put(controllerClass, -1L);
        LOGGER.atWarning().log("maxHorizontalSpeed field not found in " + controllerClass.getName() + " hierarchy");
        return -1;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, ?> getMotionControllers(Object role) {
        try {
            if (MOTION_CONTROLLERS_FIELD == null) {
                // motionControllers is declared on Role parent class, not concrete subclass
                Class<?> clazz = role.getClass();
                while (clazz != null) {
                    try {
                        MOTION_CONTROLLERS_FIELD = clazz.getDeclaredField("motionControllers");
                        MOTION_CONTROLLERS_FIELD.setAccessible(true);
                        break;
                    } catch (NoSuchFieldException e) {
                        clazz = clazz.getSuperclass();
                    }
                }
                if (MOTION_CONTROLLERS_FIELD == null) {
                    LOGGER.atWarning().log("motionControllers field not found in " + role.getClass().getName() + " hierarchy");
                    return null;
                }
            }
            return (Map<String, ?>) MOTION_CONTROLLERS_FIELD.get(role);
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to access motionControllers map: " + e.getMessage());
            return null;
        }
    }

    public static java.lang.reflect.Field resolveDropListField(Object role) {
        Class<?> roleClass = role.getClass();
        java.lang.reflect.Field cached = DROP_LIST_FIELD_CACHE.get(roleClass);
        if (cached != null) {
            return cached;
        }
        if (DROP_LIST_FIELD_MISSING.contains(roleClass)) {
            return null;
        }
        try {
            java.lang.reflect.Field field = roleClass.getDeclaredField("dropListId");
            field.setAccessible(true);
            DROP_LIST_FIELD_CACHE.put(roleClass, field);
            return field;
        } catch (Exception e) {
            DROP_LIST_FIELD_MISSING.add(roleClass);
            LOGGER.atFine().log("Drop list field not available for " + roleClass.getName() + ": " + e.getMessage());
            return null;
        }
    }
}
