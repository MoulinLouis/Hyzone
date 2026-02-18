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

/**
 * Reflection bridge for Hylograms 1.1.1+ developer API.
 * All operations go through HologramsAPI.get() -> HylogramsDeveloperApi instance.
 */
public final class HylogramsBridge {
    private static final String HYLOGRAMS_GROUP = "ehko";
    private static final String HYLOGRAMS_NAME = "Hylograms";
    private static final String HYLOGRAMS_API_CLASS = "dev.ehko.hylograms.api.HologramsAPI";
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static volatile Object cachedApiInstance;

    private HylogramsBridge() {
    }

    public static boolean isAvailable() {
        return resolveHylogramsClassLoader() != null;
    }

    public static List<String> listHologramNames() {
        Object result = invokeOnApi("list", new Class<?>[]{});
        if (!(result instanceof List<?> rawList)) {
            throw new IllegalStateException("Hylograms API returned an unexpected result.");
        }
        List<String> names = new ArrayList<>(rawList.size());
        for (Object entry : rawList) {
            if (entry != null) {
                try {
                    Object id = entry.getClass().getField("id").get(entry);
                    if (id != null) {
                        names.add(id.toString());
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    names.add(entry.toString());
                }
            }
        }
        return names;
    }

    public static HologramBuilder create(String name, Store<EntityStore> store) {
        return name != null && !name.isBlank() ? new HologramBuilder(name) : null;
    }

    public static Hologram get(String name) {
        Object definition = invokeOnApi("get", new Class<?>[]{String.class}, name);
        return definition != null ? new Hologram(definition) : null;
    }

    public static boolean exists(String name) {
        Object result = invokeOnApi("exists", new Class<?>[]{String.class}, name);
        return result instanceof Boolean value && value;
    }

    public static boolean delete(String name, Store<EntityStore> store) {
        Object result = invokeOnApi("delete", new Class<?>[]{String.class}, name);
        if (result != null) {
            try {
                Method successMethod = result.getClass().getMethod("success");
                Object val = successMethod.invoke(result);
                return val instanceof Boolean b && b;
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        return false;
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
        invokeOnApi("setLines", new Class<?>[]{String.class, List.class}, name, safeLines);
        invokeOnApi("respawn", new Class<?>[]{String.class}, name);
        return true;
    }

    public static final class Hologram {
        private final Object handle;
        private final String name;

        private Hologram(Object handle) {
            this.handle = handle;
            this.name = readStringField("id");
        }

        public String getName() {
            return name;
        }

        public String getWorldName() {
            return readStringField("worldName");
        }

        public Vector3d getPosition() {
            try {
                Method method = getCachedMethod(handle.getClass(), "toPosition", new Class<?>[]{});
                return (Vector3d) method.invoke(handle);
            } catch (IllegalAccessException | InvocationTargetException e) {
                return null;
            }
        }

        public Vector3f getRotation() {
            try {
                Method method = getCachedMethod(handle.getClass(), "toRotation", new Class<?>[]{});
                return (Vector3f) method.invoke(handle);
            } catch (IllegalAccessException | InvocationTargetException e) {
                return null;
            }
        }

        public String getColor() {
            return null;
        }

        public String[] getLines() {
            try {
                Object lines = handle.getClass().getField("lines").get(handle);
                if (lines instanceof List<?> list) {
                    String[] arr = new String[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        arr[i] = list.get(i) != null ? list.get(i).toString() : "";
                    }
                    return arr;
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
            return new String[0];
        }

        public int getLineCount() {
            try {
                Object lines = handle.getClass().getField("lines").get(handle);
                return lines instanceof List<?> list ? list.size() : 0;
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                return 0;
            }
        }

        public String getLine(int index) {
            try {
                Object lines = handle.getClass().getField("lines").get(handle);
                if (lines instanceof List<?> list) {
                    int i = index - 1;
                    if (i >= 0 && i < list.size()) {
                        Object line = list.get(i);
                        return line != null ? line.toString() : "";
                    }
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
            return "";
        }

        public Hologram setLine(int index, String text) {
            HylogramsBridge.invokeOnApi("setLine",
                    new Class<?>[]{String.class, int.class, String.class}, name, index, text);
            return this;
        }

        public Hologram addLine(String text) {
            HylogramsBridge.invokeOnApi("addLine",
                    new Class<?>[]{String.class, String.class}, name, text);
            return this;
        }

        public Hologram addItem(String itemId, float scale) {
            return this;
        }

        public Hologram removeLine(int index) {
            HylogramsBridge.invokeOnApi("removeLine",
                    new Class<?>[]{String.class, int.class}, name, index);
            return this;
        }

        public Hologram alignLine(int index, double x, double y, double z) {
            HylogramsBridge.invokeOnApi("alignLine",
                    new Class<?>[]{String.class, int.class, Double.class, Double.class, Double.class},
                    name, index, x, y, z);
            return this;
        }

        public Vector3d getLineOffset(int index) {
            return null;
        }

        public Hologram moveTo(Vector3d position) {
            Vector3f rotation = getRotation();
            HylogramsBridge.invokeOnApi("move",
                    new Class<?>[]{String.class, String.class, Vector3d.class, Vector3f.class},
                    name, getWorldName(), position, rotation != null ? rotation : new Vector3f(0, 0, 0));
            return this;
        }

        public Hologram moveTo(double x, double y, double z) {
            return moveTo(new Vector3d(x, y, z));
        }

        public Hologram rotate(Vector3f rotation) {
            Vector3d position = getPosition();
            HylogramsBridge.invokeOnApi("move",
                    new Class<?>[]{String.class, String.class, Vector3d.class, Vector3f.class},
                    name, getWorldName(), position != null ? position : new Vector3d(0, 0, 0), rotation);
            return this;
        }

        public Hologram loadAnimation(int index, String animationName) {
            HylogramsBridge.invokeOnApi("assignLineAnimation",
                    new Class<?>[]{String.class, int.class, String.class}, name, index, animationName);
            return this;
        }

        public Hologram removeAnimation(int index) {
            HylogramsBridge.invokeOnApi("clearLineAnimation",
                    new Class<?>[]{String.class, int.class}, name, index);
            return this;
        }

        public boolean hasAnimation(int index) {
            return false;
        }

        public String getAnimationName(int index) {
            return null;
        }

        public Hologram playAnimation(Store<EntityStore> store, int index) {
            HylogramsBridge.invokeOnApi("playLineAnimation",
                    new Class<?>[]{String.class, int.class}, name, index);
            return this;
        }

        public Hologram stopAnimation(int index) {
            HylogramsBridge.invokeOnApi("stopLineAnimation",
                    new Class<?>[]{String.class, int.class}, name, index);
            return this;
        }

        public Hologram spawn(Store<EntityStore> store) {
            HylogramsBridge.invokeOnApi("respawn", new Class<?>[]{String.class}, name);
            return this;
        }

        public Hologram despawn(Store<EntityStore> store) {
            return this;
        }

        public Hologram respawn(Store<EntityStore> store) {
            HylogramsBridge.invokeOnApi("respawn", new Class<?>[]{String.class}, name);
            return this;
        }

        public Hologram save(Store<EntityStore> store) {
            HylogramsBridge.invokeOnApi("saveNow", new Class<?>[]{});
            return this;
        }

        public Hologram setInteractable(int index, String action) {
            HylogramsBridge.invokeOnApi("setLineInteractable",
                    new Class<?>[]{String.class, int.class, String.class}, name, index, action);
            return this;
        }

        public Hologram setInteractable(int index) {
            HylogramsBridge.invokeOnApi("setLineInteractable",
                    new Class<?>[]{String.class, int.class, String.class}, name, index, "");
            return this;
        }

        public Hologram setNotInteractable(int index) {
            HylogramsBridge.invokeOnApi("clearLineInteractable",
                    new Class<?>[]{String.class, int.class}, name, index);
            return this;
        }

        public boolean isInteractable(int index) {
            return false;
        }

        public String getInteractAction(int index) {
            return null;
        }

        private String readStringField(String fieldName) {
            try {
                Object value = handle.getClass().getField(fieldName).get(handle);
                return value != null ? value.toString() : null;
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return null;
            }
        }
    }

    public static final class HologramBuilder {
        private final String name;
        private String worldName;
        private Vector3d position;
        private final List<String> lines = new ArrayList<>();

        private HologramBuilder(String name) {
            this.name = name;
        }

        public HologramBuilder at(Vector3d position) {
            this.position = position;
            return this;
        }

        public HologramBuilder at(double x, double y, double z) {
            this.position = new Vector3d(x, y, z);
            return this;
        }

        public HologramBuilder inWorld(String worldName) {
            this.worldName = worldName;
            return this;
        }

        public HologramBuilder color(String color) {
            return this;
        }

        public HologramBuilder addLine(String text) {
            lines.add(text != null ? text : "");
            return this;
        }

        public HologramBuilder addItem(String itemId) {
            return this;
        }

        public HologramBuilder addItem(String itemId, float scale) {
            return this;
        }

        public HologramBuilder addItem(String itemId, float scale, float yaw, float pitch, float roll) {
            return this;
        }

        public Hologram spawn() {
            Vector3f rotation = new Vector3f(0, 0, 0);
            invokeOnApi("create",
                    new Class<?>[]{String.class, String.class, Vector3d.class, Vector3f.class, List.class},
                    name, worldName, position, rotation, lines);
            Object definition = invokeOnApi("get", new Class<?>[]{String.class}, name);
            return definition != null ? new Hologram(definition) : null;
        }
    }

    private static Object invokeOnApi(String methodName, Class<?>[] paramTypes, Object... args) {
        Object api = resolveApiInstance();
        try {
            Method method = getCachedMethod(api.getClass(), methodName, paramTypes);
            return method.invoke(api, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Hylograms API access denied for method: " + methodName, e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Hylograms API call failed for method: " + methodName,
                    e.getCause() != null ? e.getCause() : e);
        }
    }

    private static Object resolveApiInstance() {
        Object instance = cachedApiInstance;
        if (instance != null) {
            return instance;
        }
        Class<?> apiClass = resolveApiClass();
        try {
            Method getMethod = getCachedMethod(apiClass, "get", new Class<?>[]{});
            instance = getMethod.invoke(null);
            if (instance == null) {
                throw new IllegalStateException("Hylograms API instance is null.");
            }
            cachedApiInstance = instance;
            return instance;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Hylograms API access denied for 'get'", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Hylograms API call 'get' failed",
                    e.getCause() != null ? e.getCause() : e);
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
