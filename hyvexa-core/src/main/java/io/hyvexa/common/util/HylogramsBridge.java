package io.hyvexa.common.util;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class HylogramsBridge {
    private static final String HYLOGRAMS_GROUP = "ehko";
    private static final String HYLOGRAMS_NAME = "Hylograms";
    private static final String HYLOGRAMS_API_CLASS = "dev.ehko.hylograms.api.HologramsAPI";
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private HylogramsBridge() {
    }

    public static boolean isAvailable() {
        return resolveHylogramsClassLoader() != null;
    }

    public static List<String> listHologramNames() {
        Class<?> apiClass = resolveApiClass();
        try {
            Method listMethod = apiClass.getMethod("list");
            Object result = listMethod.invoke(null);
            if (!(result instanceof List<?> rawList)) {
                throw new IllegalStateException("Hylograms API returned an unexpected result.");
            }
            List<String> names = new ArrayList<>(rawList.size());
            for (Object entry : rawList) {
                if (entry != null) {
                    names.add(entry.toString());
                }
            }
            return names;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Hylograms API method 'list' not found (version mismatch?)", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Hylograms API access denied for 'list'", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Hylograms API call 'list' failed", e.getCause() != null ? e.getCause() : e);
        }
    }

    public static HologramBuilder create(String name, Store<EntityStore> store) {
        Object builder = invokeStatic("create", new Class<?>[]{String.class, Store.class}, name, store);
        return builder != null ? new HologramBuilder(builder) : null;
    }

    public static Hologram get(String name) {
        Object hologram = invokeStatic("get", new Class<?>[]{String.class}, name);
        return hologram != null ? new Hologram(hologram) : null;
    }

    public static boolean exists(String name) {
        Object result = invokeStatic("exists", new Class<?>[]{String.class}, name);
        return result instanceof Boolean value && value;
    }

    public static boolean delete(String name, Store<EntityStore> store) {
        Object result = invokeStatic("delete", new Class<?>[]{String.class, Store.class}, name, store);
        return result instanceof Boolean value && value;
    }

    public static boolean updateHologramLines(String name, List<String> lines, Store<EntityStore> store) {
        if (name == null || name.isBlank()) {
            return false;
        }
        List<String> safeLines = new ArrayList<>();
        if (lines != null) {
            for (String line : lines) {
                safeLines.add(line != null ? line : "");
            }
        }
        Hologram hologram = get(name);
        if (hologram == null) {
            return false;
        }

        int currentCount = hologram.getLineCount();
        int targetCount = safeLines.size();
        int setCount = Math.min(currentCount, targetCount);
        for (int i = 0; i < setCount; i++) {
            hologram.setLine(i + 1, safeLines.get(i));
        }
        if (targetCount > currentCount) {
            for (int i = currentCount; i < targetCount; i++) {
                hologram.addLine(safeLines.get(i));
            }
        } else if (currentCount > targetCount) {
            for (int i = currentCount; i > targetCount; i--) {
                hologram.removeLine(i);
            }
        }
        if (store != null) {
            hologram.despawn(store);
            hologram.spawn(store);
            hologram.save(store);
        }
        return true;
    }

    public static final class Hologram {
        private final Object handle;

        private Hologram(Object handle) {
            this.handle = handle;
        }

        public String getName() {
            return (String) invoke("getName", new Class<?>[]{});
        }

        public String getWorldName() {
            return (String) invoke("getWorldName", new Class<?>[]{});
        }

        public Vector3d getPosition() {
            return (Vector3d) invoke("getPosition", new Class<?>[]{});
        }

        public Vector3f getRotation() {
            return (Vector3f) invoke("getRotation", new Class<?>[]{});
        }

        public String getColor() {
            return (String) invoke("getColor", new Class<?>[]{});
        }

        public String[] getLines() {
            return (String[]) invoke("getLines", new Class<?>[]{});
        }

        public int getLineCount() {
            Object value = invoke("getLineCount", new Class<?>[]{});
            return value instanceof Number number ? number.intValue() : 0;
        }

        public String getLine(int index) {
            return (String) invoke("getLine", new Class<?>[]{int.class}, index);
        }

        public Hologram setLine(int index, String text) {
            invoke("setLine", new Class<?>[]{int.class, String.class}, index, text);
            return this;
        }

        public Hologram addLine(String text) {
            invoke("addLine", new Class<?>[]{String.class}, text);
            return this;
        }

        public Hologram addItem(String itemId, float scale) {
            invoke("addItem", new Class<?>[]{String.class, float.class}, itemId, scale);
            return this;
        }

        public Hologram removeLine(int index) {
            invoke("removeLine", new Class<?>[]{int.class}, index);
            return this;
        }

        public Hologram alignLine(int index, double x, double y, double z) {
            invoke("alignLine", new Class<?>[]{int.class, double.class, double.class, double.class}, index, x, y, z);
            return this;
        }

        public Vector3d getLineOffset(int index) {
            return (Vector3d) invoke("getLineOffset", new Class<?>[]{int.class}, index);
        }

        public Hologram moveTo(Vector3d position) {
            invoke("moveTo", new Class<?>[]{Vector3d.class}, position);
            return this;
        }

        public Hologram moveTo(double x, double y, double z) {
            invoke("moveTo", new Class<?>[]{double.class, double.class, double.class}, x, y, z);
            return this;
        }

        public Hologram rotate(Vector3f rotation) {
            invoke("rotate", new Class<?>[]{Vector3f.class}, rotation);
            return this;
        }

        public Hologram loadAnimation(int index, String animationName) {
            invoke("loadAnimation", new Class<?>[]{int.class, String.class}, index, animationName);
            return this;
        }

        public Hologram removeAnimation(int index) {
            invoke("removeAnimation", new Class<?>[]{int.class}, index);
            return this;
        }

        public boolean hasAnimation(int index) {
            Object value = invoke("hasAnimation", new Class<?>[]{int.class}, index);
            return value instanceof Boolean flag && flag;
        }

        public String getAnimationName(int index) {
            return (String) invoke("getAnimationName", new Class<?>[]{int.class}, index);
        }

        public Hologram playAnimation(Store<EntityStore> store, int index) {
            invoke("playAnimation", new Class<?>[]{Store.class, int.class}, store, index);
            return this;
        }

        public Hologram stopAnimation(int index) {
            invoke("stopAnimation", new Class<?>[]{int.class}, index);
            return this;
        }

        public Hologram spawn(Store<EntityStore> store) {
            invoke("spawn", new Class<?>[]{Store.class}, store);
            return this;
        }

        public Hologram despawn(Store<EntityStore> store) {
            invoke("despawn", new Class<?>[]{Store.class}, store);
            return this;
        }

        public Hologram respawn(Store<EntityStore> store) {
            invoke("respawn", new Class<?>[]{Store.class}, store);
            return this;
        }

        public Hologram save(Store<EntityStore> store) {
            invoke("save", new Class<?>[]{Store.class}, store);
            return this;
        }

        public Hologram setInteractable(int index, String action) {
            invoke("setInteractable", new Class<?>[]{int.class, String.class}, index, action);
            return this;
        }

        public Hologram setInteractable(int index) {
            invoke("setInteractable", new Class<?>[]{int.class}, index);
            return this;
        }

        public Hologram setNotInteractable(int index) {
            invoke("setNotInteractable", new Class<?>[]{int.class}, index);
            return this;
        }

        public boolean isInteractable(int index) {
            Object value = invoke("isInteractable", new Class<?>[]{int.class}, index);
            return value instanceof Boolean flag && flag;
        }

        public String getInteractAction(int index) {
            return (String) invoke("getInteractAction", new Class<?>[]{int.class}, index);
        }

        private Object invoke(String methodName, Class<?>[] paramTypes, Object... args) {
            try {
                Method method = getCachedMethod(handle.getClass(), methodName, paramTypes);
                return method.invoke(handle, args);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Hylograms API access denied for hologram method: " + methodName, e);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException("Hylograms hologram method failed: " + methodName, e.getCause() != null ? e.getCause() : e);
            }
        }
    }

    public static final class HologramBuilder {
        private final Object handle;

        private HologramBuilder(Object handle) {
            this.handle = handle;
        }

        public HologramBuilder at(Vector3d position) {
            invoke("at", new Class<?>[]{Vector3d.class}, position);
            return this;
        }

        public HologramBuilder at(double x, double y, double z) {
            invoke("at", new Class<?>[]{double.class, double.class, double.class}, x, y, z);
            return this;
        }

        public HologramBuilder inWorld(String worldName) {
            invoke("inWorld", new Class<?>[]{String.class}, worldName);
            return this;
        }

        public HologramBuilder color(String color) {
            invoke("color", new Class<?>[]{String.class}, color);
            return this;
        }

        public HologramBuilder addLine(String text) {
            invoke("addLine", new Class<?>[]{String.class}, text);
            return this;
        }

        public HologramBuilder addItem(String itemId) {
            invoke("addItem", new Class<?>[]{String.class}, itemId);
            return this;
        }

        public HologramBuilder addItem(String itemId, float scale) {
            invoke("addItem", new Class<?>[]{String.class, float.class}, itemId, scale);
            return this;
        }

        public HologramBuilder addItem(String itemId, float scale, float yaw, float pitch, float roll) {
            invoke("addItem", new Class<?>[]{String.class, float.class, float.class, float.class, float.class},
                    itemId, scale, yaw, pitch, roll);
            return this;
        }

        public Hologram spawn() {
            Object hologram = invoke("spawn", new Class<?>[]{});
            return hologram != null ? new Hologram(hologram) : null;
        }

        private Object invoke(String methodName, Class<?>[] paramTypes, Object... args) {
            try {
                Method method = getCachedMethod(handle.getClass(), methodName, paramTypes);
                return method.invoke(handle, args);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Hylograms API access denied for builder method: " + methodName, e);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException("Hylograms builder method failed: " + methodName, e.getCause() != null ? e.getCause() : e);
            }
        }
    }

    private static Object invokeStatic(String methodName, Class<?>[] paramTypes, Object... args) {
        Class<?> apiClass = resolveApiClass();
        try {
            Method method = getCachedMethod(apiClass, methodName, paramTypes);
            return method.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Hylograms API access denied for method: " + methodName, e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Hylograms API call failed for method: " + methodName, e.getCause() != null ? e.getCause() : e);
        }
    }

    private static Class<?> resolveApiClass() {
        ClassLoader classLoader = resolveHylogramsClassLoader();
        if (classLoader == null) {
            throw new IllegalStateException("Hylograms plugin is not loaded.");
        }
        try {
            return Class.forName(HYLOGRAMS_API_CLASS, true, classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Hylograms API is not available at runtime.", e);
        }
    }

    private static Method getCachedMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        String cacheKey = clazz.getName() + "#" + methodName + "#" + Arrays.toString(paramTypes);
        return METHOD_CACHE.computeIfAbsent(cacheKey, k -> {
            try {
                return clazz.getMethod(methodName, paramTypes);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Method not found: " + methodName, e);
            }
        });
    }

    private static ClassLoader resolveHylogramsClassLoader() {
        PluginManager manager = PluginManager.get();
        if (manager == null) {
            return null;
        }
        PluginIdentifier identifier = new PluginIdentifier(HYLOGRAMS_GROUP, HYLOGRAMS_NAME);
        PluginBase plugin = manager.getPlugin(identifier);
        if (plugin == null) {
            return null;
        }
        return plugin.getClass().getClassLoader();
    }
}
