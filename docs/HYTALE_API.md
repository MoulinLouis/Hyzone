# Hytale API Reference

## HytaleServer.jar Location

```
/mnt/c/Users/User/AppData/Roaming/Hytale/install/release/package/game/latest/Server/HytaleServer.jar
```

Use `jar tf` to list classes and `javap -c -p` to decompile for API exploration.

## API Gotchas

### Vector3d Has NO Accessor Methods

You cannot read x, y, z values back from a Vector3d:

```java
// WRONG - will not compile:
Vector3d pos = new Vector3d(1, 2, 3);
double x = pos.x();  // ERROR: no x() method
double y = pos.y();  // ERROR: no y() method

// CORRECT - use double[] arrays when you need to read/compare coordinates:
double[] pos = new double[]{1, 2, 3};
double x = pos[0];
double y = pos[1];
double z = pos[2];

// Convert to Vector3d only when calling Hytale APIs:
Vector3d vec = new Vector3d(pos[0], pos[1], pos[2]);
store.addComponent(ref, Teleport.getComponentType(), new Teleport(world, vec, rotation));
```

### Position Tracking Pattern

```java
// Store positions as double[] for computation
private volatile double[] previousPosition;

// When calculating (e.g., direction/rotation):
double dx = targetPos[0] - previousPos[0];
double dz = targetPos[2] - previousPos[2];
float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

// Convert to Vector3d only for Hytale API calls
Vector3d targetVec = new Vector3d(targetPos[0], targetPos[1], targetPos[2]);
```

### Singleton Components

Some components use singleton patterns - check for `INSTANCE` or `get()`:

```java
// Correct
store.addComponent(entityRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
store.addComponent(entityRef, Frozen.getComponentType(), Frozen.get());

// Wrong - don't use constructor if singleton exists
store.addComponent(entityRef, Invulnerable.getComponentType(), new Invulnerable());
```

### Entity Removal

Use the proper API, not reflection:

```java
store.removeEntity(entityRef, RemoveReason.REMOVE);
```

### World Thread Requirement

Entity/world operations must run on the world thread:

```java
CompletableFuture.runAsync(() -> {
    store.addComponent(ref, Teleport.getComponentType(), teleport);
}, world);

// Or
world.execute(() -> {
    // entity operations
});
```

### Common Gotchas Summary

1. **Entity refs expire** - Always check `ref.isValid()` before use
2. **World thread required** - Use `CompletableFuture.runAsync(..., world)` for entity/world ops
3. **UI paths** - Code uses `"Pages/MyPage.ui"`, files go in `Common/UI/Custom/Pages/`
4. **Inventory access** - Need `Player` component, not just `PlayerRef`
5. **Event registration** - Use `getEventRegistry().registerGlobal(...)` in `setup()`
6. **UI page lifecycle** - Override `onDismiss()` for cleanup, never call `close()` from it (see below)

### UI Page Lifecycle

Understanding the CustomUIPage lifecycle is critical for pages with background tasks (scheduled refreshes, animations, etc.).

**Lifecycle callbacks:**

```java
public abstract class CustomUIPage {
    // Called once when page is first opened
    public abstract void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                               UIEventBuilder evt, Store<EntityStore> store);

    // Called when user manually closes the page OR when replaced by another page
    protected void close();

    // Called when page is dismissed/replaced (including by external UIs like NPCDialog)
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store);
}
```

**Call sequences:**

| Scenario | Methods Called (in order) |
|----------|--------------------------|
| User closes page manually | `onDismiss()` → `close()` |
| Your plugin opens new page | `onDismiss()` (old page) → `close()` (old page) → `build()` (new page) |
| External UI opens (NPCDialog) | `onDismiss()` only (your page) |
| External UI closes | (your page is NOT restored) |

**CRITICAL - Recursion trap:**

```java
// ❌ WRONG - causes StackOverflowError:
@Override
public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
    this.close();  // close() calls PageManager.setPage()
                   // which calls onDismiss() again → infinite loop!
}

// ✅ CORRECT - separate cleanup method:
@Override
public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
    stopBackgroundTasks();  // Clean up without calling close()
    super.onDismiss(ref, store);
}

@Override
public void close() {
    stopBackgroundTasks();  // Same cleanup for manual close
    super.close();
}

protected void stopBackgroundTasks() {
    if (refreshTask != null) {
        refreshTask.cancel(false);
        refreshTask = null;
    }
}
```

**Why both callbacks exist:**
- `close()` - For manual cleanup when you explicitly close the page
- `onDismiss()` - For automatic cleanup when Hytale closes/replaces your page (including external UIs)

**Best practice:** Implement cleanup logic in a separate method (`stopBackgroundTasks()`) called by both `close()` and `onDismiss()`.

**See `CODE_PATTERNS.md`** for complete example with base page pattern.

## API Discovery

### Key Packages to Explore

| Package | Purpose |
|---------|---------|
| `com.hypixel.hytale.server.core.entity` | Entity components, refs, stores |
| `com.hypixel.hytale.server.core.entity.component` | Built-in components (Player, Transform, Teleport) |
| `com.hypixel.hytale.server.core.world` | World operations, block access |
| `com.hypixel.hytale.server.core.command` | Command framework (AbstractAsyncCommand) |
| `com.hypixel.hytale.server.core.ui` | UI pages, HUD management |
| `com.hypixel.hytale.server.core.codec` | Serialization codecs |
| `com.hypixel.hytale.server.core.event` | Event system |

### Decompilation Tools

- **IntelliJ IDEA** - Built-in decompiler (just open the JAR)
- **jadx** - GUI decompiler with search
- **cfr** - Command-line decompiler

### Discovered APIs in This Project

| API Class | Purpose |
|-----------|---------|
| `AbstractAsyncCommand` | Async command execution |
| `InteractiveCustomUIPage` | Interactive UI framework |
| `Teleport` component | Player teleportation |
| `TransformComponent` | Position/rotation access |
| `HudManager` | Custom HUD attachment |
| `HitboxCollision` | Player collision control |
| `MovementSettings` | Speed multiplier adjustments |
| `Nameplate` | Player nameplate customization |

### Hylograms API

Hylograms classes are isolated in their own plugin classloader. Use `HylogramsBridge`:

```java
HylogramsBridge.Hologram holo = HylogramsBridge.create("example", store)
        .inWorld("Parkour")
        .at(0, 64, 0)
        .addLine("Header")
        .spawn();

HylogramsBridge.updateHologramLines("example", List.of("Title", "1. Player"), store);
```
