# TextUtils 3D Text System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Integrate TextUtils-1.2.1 mod via reflection bridge + builder API for colored 3D text displays, starting with parkour leaderboards.

**Architecture:** Three-layer system in hyvexa-core: `TextUtilsBridge` (low-level reflection to TextUtils mod), `Text3D` (ergonomic builder API). Consumer `TextLeaderboardManager` in hyvexa-parkour renders colored leaderboards. Coexists with existing Hylograms system.

**Tech Stack:** Java 21, Hytale server API, TextUtils-1.2.1 mod (reflection), existing HylogramsBridge as reference pattern.

---

### Task 1: Create TextUtilsBridge reflection bridge

**Files:**
- Create: `hyvexa-core/src/main/java/io/hyvexa/common/util/TextUtilsBridge.java`

**Reference:** `hyvexa-core/src/main/java/io/hyvexa/common/util/HylogramsBridge.java` — follow the same reflection pattern (PluginManager lookup, classloader resolution, Method cache).

**Step 1: Create TextUtilsBridge.java**

```java
package io.hyvexa.common.util;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class TextUtilsBridge {
    private static final String TEXTUTILS_GROUP = "TextUtils";
    private static final String TEXTUTILS_NAME = "TextUtils";
    private static final String TEXT_MANAGER_CLASS = "io.github.flo_12344.textutils.utils.TextManager";
    private static final String HOLOGRAM_REGISTRY_CLASS = "io.github.flo_12344.textutils.registry.TextUtilsHologramRegistry";
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private TextUtilsBridge() {
    }

    // --- Availability ---

    public static boolean isAvailable() {
        return resolveTextUtilsClassLoader() != null;
    }

    // --- Spawn / Remove ---

    public static void spawn(Vector3d position, Vector3f rotation, World world,
                             String id, String text, String font, float size) {
        invokeTextManager("SpawnText3dEntity",
                new Class<?>[]{Vector3d.class, Vector3f.class, World.class,
                        String.class, String.class, String.class, float.class},
                position, rotation, world, id, text, font, size);
    }

    public static void remove(String id, World world, Store<EntityStore> store) {
        invokeTextManager("RemoveText3dEntity",
                new Class<?>[]{String.class, World.class, Store.class},
                id, world, store);
    }

    // --- Edit ---

    public static void editContent(String id, World world, Store<EntityStore> store, String text) {
        invokeTextManager("EditText3dContent",
                new Class<?>[]{String.class, World.class, Store.class, String.class},
                id, world, store, text);
    }

    public static void editLine(String id, World world, Store<EntityStore> store, String text, int line) {
        invokeTextManager("EditText3dLine",
                new Class<?>[]{String.class, World.class, Store.class, String.class, int.class},
                id, world, store, text, line);
    }

    // --- Transform ---

    public static void resize(String id, World world, Store<EntityStore> store, float size) {
        invokeTextManager("ResizeText3dEntity",
                new Class<?>[]{String.class, World.class, Store.class, float.class},
                id, world, store, size);
    }

    public static void move(String id, World world, Store<EntityStore> store, Vector3d position) {
        invokeTextManager("MoveText3dEntity",
                new Class<?>[]{String.class, World.class, Store.class, Vector3d.class},
                id, world, store, position);
    }

    public static void rotate(String id, World world, Store<EntityStore> store, Vector3f rotation) {
        invokeTextManager("RotateText3dEntity",
                new Class<?>[]{String.class, World.class, Store.class, Vector3f.class},
                id, world, store, rotation);
    }

    // --- Visibility ---

    public static void setVisibility(String id, World world, Store<EntityStore> store, boolean visible) {
        invokeTextManager("SetText3dVisibility",
                new Class<?>[]{String.class, World.class, Store.class, Boolean.class},
                id, world, store, Boolean.valueOf(visible));
    }

    // --- Font ---

    public static void changeFont(String id, World world, Store<EntityStore> store, String fontName) {
        invokeTextManager("ChangeText3dFont",
                new Class<?>[]{String.class, World.class, Store.class, String.class},
                id, world, store, fontName);
    }

    // --- Registry ---

    public static boolean exists(String id) {
        Object result = invokeRegistry("contains", new Class<?>[]{String.class}, id);
        return result instanceof Boolean value && value;
    }

    public static List<String> listNames() {
        Object result = invokeRegistry("getKeys", new Class<?>[]{});
        if (result instanceof List<?> rawList) {
            List<String> names = new ArrayList<>(rawList.size());
            for (Object entry : rawList) {
                if (entry != null) {
                    names.add(entry.toString());
                }
            }
            return names;
        }
        return List.of();
    }

    // --- Internals ---

    private static Object invokeTextManager(String methodName, Class<?>[] paramTypes, Object... args) {
        Class<?> clazz = resolveClass(TEXT_MANAGER_CLASS);
        return invokeStatic(clazz, methodName, paramTypes, args);
    }

    private static Object invokeRegistry(String methodName, Class<?>[] paramTypes, Object... args) {
        Class<?> clazz = resolveClass(HOLOGRAM_REGISTRY_CLASS);
        try {
            Method getMethod = getCachedMethod(clazz, "get", new Class<?>[]{});
            Object instance = getMethod.invoke(null);
            if (instance == null) {
                throw new IllegalStateException("TextUtilsHologramRegistry instance is null.");
            }
            Method method = getCachedMethod(instance.getClass(), methodName, paramTypes);
            return method.invoke(instance, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("TextUtils registry access denied: " + methodName, e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("TextUtils registry call failed: " + methodName,
                    e.getCause() != null ? e.getCause() : e);
        }
    }

    private static Object invokeStatic(Class<?> clazz, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = getCachedMethod(clazz, methodName, paramTypes);
            return method.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("TextUtils API access denied: " + methodName, e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("TextUtils API call failed: " + methodName,
                    e.getCause() != null ? e.getCause() : e);
        }
    }

    private static Class<?> resolveClass(String className) {
        ClassLoader classLoader = resolveTextUtilsClassLoader();
        if (classLoader == null) {
            throw new IllegalStateException("TextUtils plugin is not loaded.");
        }
        try {
            return Class.forName(className, true, classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("TextUtils class not available: " + className, e);
        }
    }

    private static Method getCachedMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        String cacheKey = clazz.getName() + "#" + methodName + "#" + Arrays.toString(paramTypes);
        return METHOD_CACHE.computeIfAbsent(cacheKey, k -> {
            try {
                return clazz.getMethod(methodName, paramTypes);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("TextUtils method not found: " + methodName, e);
            }
        });
    }

    private static ClassLoader resolveTextUtilsClassLoader() {
        PluginManager manager = PluginManager.get();
        if (manager == null) {
            return null;
        }
        PluginIdentifier identifier = new PluginIdentifier(TEXTUTILS_GROUP, TEXTUTILS_NAME);
        PluginBase plugin = manager.getPlugin(identifier);
        if (plugin == null) {
            return null;
        }
        return plugin.getClass().getClassLoader();
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-core/src/main/java/io/hyvexa/common/util/TextUtilsBridge.java
git commit -m "feat: add TextUtilsBridge reflection bridge for TextUtils mod"
```

---

### Task 2: Create Text3D builder API

**Files:**
- Create: `hyvexa-core/src/main/java/io/hyvexa/common/util/Text3D.java`

**Step 1: Create Text3D.java**

```java
package io.hyvexa.common.util;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class Text3D {

    private String id;
    private Vector3d position;
    private Vector3f rotation;
    private String font = "default";
    private float size = 1.0f;
    private String text;
    private String lineText;
    private int lineIndex = -1;

    private Text3D(String id) {
        this.id = id;
    }

    // --- Factory methods ---

    public static Text3D create(String id) {
        return new Text3D(id);
    }

    public static Text3D get(String id) {
        return new Text3D(id);
    }

    // --- Static helpers ---

    public static boolean isAvailable() {
        return TextUtilsBridge.isAvailable();
    }

    public static boolean exists(String id) {
        return TextUtilsBridge.exists(id);
    }

    public static String color(String colorName, String content) {
        return "{" + colorName + "}" + content + "{/" + colorName + "}";
    }

    // --- Builder setters ---

    public Text3D at(Vector3d position) {
        this.position = position;
        return this;
    }

    public Text3D rotation(Vector3f rotation) {
        this.rotation = rotation;
        return this;
    }

    public Text3D font(String font) {
        this.font = font;
        return this;
    }

    public Text3D size(float size) {
        this.size = size;
        return this;
    }

    public Text3D text(String text) {
        this.text = text;
        return this;
    }

    public Text3D editLine(int lineIndex, String lineText) {
        this.lineIndex = lineIndex;
        this.lineText = lineText;
        return this;
    }

    // --- Terminal operations ---

    public void spawn(World world) {
        if (id == null || world == null) {
            return;
        }
        Vector3d pos = position != null ? position : new Vector3d();
        Vector3f rot = rotation != null ? rotation : new Vector3f();
        String content = text != null ? text : "";
        TextUtilsBridge.spawn(pos, rot, world, id, content, font, size);
    }

    public void update(World world) {
        if (id == null || world == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (lineIndex >= 0 && lineText != null) {
            TextUtilsBridge.editLine(id, world, store, lineText, lineIndex);
        } else if (text != null) {
            TextUtilsBridge.editContent(id, world, store, text);
        }
    }

    public void remove(World world) {
        if (id == null || world == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        TextUtilsBridge.remove(id, world, store);
    }

    public void resize(float newSize, World world) {
        if (id == null || world == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        TextUtilsBridge.resize(id, world, store, newSize);
    }

    public void move(Vector3d newPosition, World world) {
        if (id == null || world == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        TextUtilsBridge.move(id, world, store, newPosition);
    }

    public void rotate(Vector3f newRotation, World world) {
        if (id == null || world == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        TextUtilsBridge.rotate(id, world, store, newRotation);
    }

    public void hide(World world) {
        if (id == null || world == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        TextUtilsBridge.setVisibility(id, world, store, false);
    }

    public void show(World world) {
        if (id == null || world == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        TextUtilsBridge.setVisibility(id, world, store, true);
    }

    public void changeFont(String fontName, World world) {
        if (id == null || world == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        TextUtilsBridge.changeFont(id, world, store, fontName);
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-core/src/main/java/io/hyvexa/common/util/Text3D.java
git commit -m "feat: add Text3D builder API for 3D text creation"
```

---

### Task 3: Create TextLeaderboardManager

**Files:**
- Create: `hyvexa-parkour/src/main/java/io/hyvexa/manager/TextLeaderboardManager.java`
- Reference: `hyvexa-parkour/src/main/java/io/hyvexa/manager/LeaderboardHologramManager.java` for data access patterns (ProgressStore, MapStore, ParkourUtils.resolveName)

**Step 1: Create TextLeaderboardManager.java**

This manager renders colored leaderboards using Text3D. Color scheme:
- Rank 1: yellow (gold), Rank 2: grey (silver), Rank 3: brown (bronze), Rank 4+: white
- Player names: white
- Values: green

```java
package io.hyvexa.manager;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import io.hyvexa.common.util.FormatUtils;
import io.hyvexa.common.util.ModeGate;
import io.hyvexa.common.util.Text3D;
import io.hyvexa.parkour.ParkourTimingConstants;
import io.hyvexa.parkour.data.MapStore;
import io.hyvexa.parkour.data.ProgressStore;
import io.hyvexa.parkour.util.ParkourUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TextLeaderboardManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ProgressStore progressStore;
    private final MapStore mapStore;
    private String parkourWorldName;

    // Configurable spawn positions + rotations (set via admin or hardcode)
    private Vector3d leaderboardPosition;
    private Vector3f leaderboardRotation;
    private float leaderboardSize = 1.0f;
    private String leaderboardFont = "default";

    public TextLeaderboardManager(ProgressStore progressStore, MapStore mapStore, String parkourWorldName) {
        this.progressStore = progressStore;
        this.mapStore = mapStore;
        this.parkourWorldName = parkourWorldName != null ? parkourWorldName : "Parkour";
    }

    public void setLeaderboardTransform(Vector3d position, Vector3f rotation) {
        this.leaderboardPosition = position;
        this.leaderboardRotation = rotation;
    }

    public void setLeaderboardSize(float size) {
        this.leaderboardSize = size;
    }

    public void setLeaderboardFont(String font) {
        this.leaderboardFont = font;
    }

    // --- Global completion leaderboard ---

    public void refreshLeaderboard() {
        if (!Text3D.isAvailable()) {
            return;
        }
        World world = resolveParkourWorld();
        if (world == null) {
            return;
        }
        world.execute(() -> updateLeaderboard(world));
    }

    private void updateLeaderboard(World world) {
        String content = buildLeaderboardContent();
        String id = "text_leaderboard";
        try {
            if (Text3D.exists(id)) {
                Text3D.get(id).text(content).update(world);
            } else if (leaderboardPosition != null) {
                Text3D.create(id)
                        .at(leaderboardPosition)
                        .rotation(leaderboardRotation != null ? leaderboardRotation : new Vector3f())
                        .font(leaderboardFont)
                        .size(leaderboardSize)
                        .text(content)
                        .spawn(world);
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to update text leaderboard");
        }
    }

    private String buildLeaderboardContent() {
        StringBuilder sb = new StringBuilder();
        if (progressStore == null) {
            return sb.toString();
        }
        Map<UUID, Integer> counts = progressStore.getMapCompletionCounts();
        if (counts.isEmpty()) {
            return sb.toString();
        }
        List<CompletionRow> rows = buildRows(counts);
        int entries = Math.min(rows.size(), ParkourTimingConstants.LEADERBOARD_HOLOGRAM_ENTRIES);
        for (int i = 0; i < entries; i++) {
            if (i > 0) {
                sb.append("\\n");
            }
            CompletionRow row = rows.get(i);
            String rankColor = getRankColor(i + 1);
            String position = "#" + (i + 1);
            sb.append(Text3D.color(rankColor, position));
            sb.append(" - ");
            sb.append(Text3D.color("white", clamp(row.name, ParkourTimingConstants.LEADERBOARD_NAME_MAX)));
            sb.append(" - ");
            sb.append(Text3D.color("green", String.valueOf(row.count)));
        }
        return sb.toString();
    }

    // --- Per-map time leaderboard ---

    public void refreshMapLeaderboard(String mapId) {
        if (!Text3D.isAvailable() || mapId == null || mapId.isBlank()) {
            return;
        }
        World world = resolveParkourWorld();
        if (world == null) {
            return;
        }
        world.execute(() -> updateMapLeaderboard(mapId, world));
    }

    private void updateMapLeaderboard(String mapId, World world) {
        String content = buildMapLeaderboardContent(mapId);
        String id = "text_map_" + mapId;
        try {
            if (Text3D.exists(id)) {
                Text3D.get(id).text(content).update(world);
            }
            // Map leaderboards are only updated, not auto-created (need position from admin)
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to update text map leaderboard for " + mapId);
        }
    }

    private String buildMapLeaderboardContent(String mapId) {
        StringBuilder sb = new StringBuilder();
        String mapName = mapId;
        if (mapStore != null) {
            io.hyvexa.parkour.data.Map map = mapStore.getMap(mapId);
            if (map != null && map.getName() != null && !map.getName().isBlank()) {
                mapName = map.getName().trim();
            }
        }
        sb.append(Text3D.color("yellow", mapName));
        if (progressStore == null) {
            return sb.toString();
        }
        List<Map.Entry<UUID, Long>> entries = progressStore.getLeaderboardEntries(mapId);
        int limit = Math.min(entries.size(), ParkourTimingConstants.MAP_HOLOGRAM_TOP_LIMIT);
        for (int i = 0; i < limit; i++) {
            sb.append("\\n");
            Map.Entry<UUID, Long> entry = entries.get(i);
            String name = ParkourUtils.resolveName(entry.getKey(), progressStore);
            String safeName = clamp(name, ParkourTimingConstants.MAP_HOLOGRAM_NAME_MAX);
            String time = entry.getValue() != null ? FormatUtils.formatDuration(entry.getValue()) : "--";
            String rankColor = getRankColor(i + 1);
            sb.append(Text3D.color(rankColor, "#" + (i + 1)));
            sb.append(" - ");
            sb.append(Text3D.color("white", safeName));
            sb.append(" - ");
            sb.append(Text3D.color("green", time));
        }
        if (entries.isEmpty()) {
            sb.append("\\n");
            sb.append(Text3D.color("grey", "No completions yet."));
        }
        return sb.toString();
    }

    // --- Helpers ---

    private static String getRankColor(int rank) {
        return switch (rank) {
            case 1 -> "yellow";
            case 2 -> "grey";
            case 3 -> "brown";
            default -> "white";
        };
    }

    private static String clamp(String value, int width) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= width) {
            return trimmed;
        }
        if (width <= 3) {
            return trimmed.substring(0, width);
        }
        return trimmed.substring(0, width - 3) + "...";
    }

    private List<CompletionRow> buildRows(Map<UUID, Integer> counts) {
        List<CompletionRow> rows = new ArrayList<>(counts.size());
        for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            UUID playerId = entry.getKey();
            if (playerId == null) {
                continue;
            }
            int count = entry.getValue() != null ? entry.getValue() : 0;
            String name = ParkourUtils.resolveName(playerId, progressStore);
            rows.add(new CompletionRow(playerId, name, count));
        }
        rows.sort(CompletionRow.COMPARATOR);
        return rows;
    }

    private World resolveParkourWorld() {
        World world = Universe.get().getWorld(parkourWorldName);
        if (world != null) {
            return world;
        }
        for (World candidate : Universe.get().getWorlds().values()) {
            if (candidate != null && parkourWorldName.equalsIgnoreCase(candidate.getName())) {
                return candidate;
            }
        }
        return null;
    }

    private static final class CompletionRow {
        private static final Comparator<CompletionRow> COMPARATOR = Comparator
                .comparingInt((CompletionRow row) -> row.count).reversed()
                .thenComparing(row -> row.sortName)
                .thenComparing(row -> row.playerId.toString());

        private final UUID playerId;
        private final String name;
        private final String sortName;
        private final int count;

        private CompletionRow(UUID playerId, String name, int count) {
            this.playerId = playerId;
            this.name = name != null ? name : "";
            this.sortName = this.name.toLowerCase(Locale.ROOT);
            this.count = count;
        }
    }
}
```

**Step 2: Commit**

```bash
git add hyvexa-parkour/src/main/java/io/hyvexa/manager/TextLeaderboardManager.java
git commit -m "feat: add TextLeaderboardManager for colored 3D leaderboards"
```

---

### Task 4: Wire TextLeaderboardManager into HyvexaPlugin

**Files:**
- Modify: `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java`

**Step 1: Add field, instantiate, and hook into refresh calls**

Add alongside existing `leaderboardHologramManager`:
1. Import `TextLeaderboardManager`
2. Add field `private TextLeaderboardManager textLeaderboardManager;`
3. Instantiate in setup (near line 238 where `leaderboardHologramManager` is created):
   ```java
   this.textLeaderboardManager = new TextLeaderboardManager(progressStore, mapStore, PARKOUR_WORLD_NAME);
   ```
4. In `refreshLeaderboardHologram(Store)` method (line ~482), add call:
   ```java
   if (textLeaderboardManager != null) {
       textLeaderboardManager.refreshLeaderboard();
   }
   ```
5. In `refreshMapLeaderboardHologram(String, Store)` method (line ~488), add call:
   ```java
   if (textLeaderboardManager != null) {
       textLeaderboardManager.refreshMapLeaderboard(mapId);
   }
   ```
6. In private `refreshLeaderboardHologram()` (line ~549), add call:
   ```java
   if (textLeaderboardManager != null) {
       textLeaderboardManager.refreshLeaderboard();
   }
   ```

**Step 2: Commit**

```bash
git add hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java
git commit -m "feat: wire TextLeaderboardManager into HyvexaPlugin refresh hooks"
```

---

### Task 5: Update manifests with TextUtils optional dependency

**Files:**
- Modify: `hyvexa-parkour/src/main/resources/manifest.json`
- Modify: `hyvexa-parkour-ascend/src/main/resources/manifest.json`

**Step 1: Add TextUtils to OptionalDependencies in both manifests**

In `hyvexa-parkour/src/main/resources/manifest.json`, add to `OptionalDependencies`:
```json
"OptionalDependencies": {
    "TextUtils:TextUtils": "*"
},
```

In `hyvexa-parkour-ascend/src/main/resources/manifest.json`, add the same.

**Step 2: Commit**

```bash
git add hyvexa-parkour/src/main/resources/manifest.json hyvexa-parkour-ascend/src/main/resources/manifest.json
git commit -m "feat: add TextUtils as optional dependency in manifests"
```

---

### Task 6: Update CHANGELOG.md

**Files:**
- Modify: `CHANGELOG.md`

**Step 1: Add entry under latest section**

```
- Add TextUtils 3D text system with colored leaderboard support (Text3D builder API + TextUtilsBridge)
```

**Step 2: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: add TextUtils integration to changelog"
```
