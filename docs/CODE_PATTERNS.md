# Code Patterns

Common patterns used throughout the Hyvexa codebase.

## Commands

```java
public class MyCommand extends AbstractAsyncCommand {
    @Override
    public CompletableFuture<Void> executeAsync(CommandContext context) {
        // Implementation
        return CompletableFuture.completedFuture(null);
    }
}
// Register: getCommandRegistry().registerCommand(new MyCommand());
```

## UI Pages

```java
public class MyPage extends InteractiveCustomUIPage<MyPage.Data> {
    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commandBuilder,
                      UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("Pages/MyPage.ui");  // Use Pages/ prefix
        // ...
    }
}
// Open: CustomUI.open(playerRef, new MyPage(playerRef));
```

## UI Files (.ui)

### Path Convention (IMPORTANT)

Hytale resolves UI paths with an implicit `Common/UI/Custom/` prefix:
- **Code path:** `Pages/MyPage.ui`
- **File location:** `resources/Common/UI/Custom/Pages/MyPage.ui`

**Rule:** Files go in `Common/UI/Custom/Pages/`, code uses `Pages/` prefix.

```
Code:   commandBuilder.append("Pages/Ascend_Menu.ui");
                              ↓
Hytale resolves to:           Common/UI/Custom/Pages/Ascend_Menu.ui
                              ↓
File:   src/main/resources/Common/UI/Custom/Pages/Ascend_Menu.ui
```

### File Organization

```
hyvexa-*/src/main/resources/
└── Common/UI/Custom/Pages/     ← All UI files go here (single location)
    ├── ModuleName_PageName.ui
    ├── ModuleName_EntryName.ui
    └── ...
```

**Naming convention:** `ModuleName_PageName.ui` (e.g., `Ascend_MapSelect.ui`, `Parkour_RunHud.ui`)

### DO NOT

- ❌ Create files in `resources/Pages/` directly
- ❌ Create files in `resources/Custom/Pages/`
- ❌ Duplicate files in multiple locations
- ❌ Use full path `Common/UI/Custom/Pages/X.ui` in code

### Basic UI Structure
```
$C = "../Common.ui";

$C.@PageOverlay {
  $C.@Container {
    Anchor: (Width: 400, Height: 300);

    #Title {
      Group {
        TextButton #CloseButton {
          Anchor: (Left: 12, Top: 4, Width: 64, Height: 24);
          Padding: (Left: 8, Top: 2);
          Text: "Close";
          Style: TextButtonStyle(
            Default: (LabelStyle: (FontSize: 14, TextColor: #93844c, RenderBold: true)),
            Sounds: $C.@ButtonSounds,
          );
          Background: #000000(0.13);
        }
        $C.@Title {
          @Text = "Page Title";
        }
      }
    }

    #Content {
      LayoutMode: Top;
      Padding: (Left: 18, Right: 18, Top: 12, Bottom: 12);

      Label {
        Style: (FontSize: 14, TextColor: #cfd7dc);
        Text: "Description text";
      }

      $C.@TextButton #MyButton {
        Anchor: (Top: 12, Height: 36);
        Text: "Click Me";
      }
    }
  }
}
```

**Valid LayoutMode values:**
- `LayoutMode: Top` - Vertical stacking (most common)
- `LayoutMode: Left` - Horizontal stacking
- `LayoutMode: TopScrolling` - Vertical with scroll

**INVALID:** `LayoutMode: Center` does NOT exist!

**Centering content horizontally:**
```
Group {
  LayoutMode: Left;

  Group { FlexWeight: 1; }
  Label #CenteredLabel {
    Text: "I am centered";
  }
  Group { FlexWeight: 1; }
}
```

**Common mistakes to avoid:**
1. `LayoutMode: Center` - Does not exist, use FlexWeight pattern above
2. Multiline text with `\n` - Use separate Labels instead
3. Complex inline styles - Use `$C.@TextButton` template instead
4. Putting UI files in wrong location (must be in `Common/UI/Custom/Pages/`)
5. Using full path in code (use `Pages/X.ui`, not `Common/UI/Custom/Pages/X.ui`)

**Keep UIs simple:** Start minimal, add complexity only when needed. Simple UIs are easier to debug.

## Data Stores

```java
// Memory-first with MySQL persistence
private final ConcurrentHashMap<UUID, Data> cache = new ConcurrentHashMap<>();
public void syncLoad() { /* Load from MySQL on startup */ }
public void save(Data d) { cache.put(d.id, d); /* + MySQL write */ }
```

## Threading

```java
// Entity/world ops require world thread
CompletableFuture.runAsync(() -> {
    store.addComponent(ref, Teleport.getComponentType(), teleport);
}, world);
```

## Thread Safety

```java
// Use AtomicLong/AtomicInteger for counters accessed from multiple threads
private final AtomicLong counter = new AtomicLong(0);
counter.incrementAndGet();

// Use volatile for simple flags
private volatile boolean initialized = false;

// Use synchronized for initialization
synchronized (INIT_LOCK) {
    if (initialized) return;
    // ... initialize ...
    initialized = true;
}

// Use computeIfAbsent instead of check-then-put
map.computeIfAbsent(key, k -> new Value());
```

## ECS Access

```java
Ref<EntityStore> ref = playerRef.getReference();
if (ref == null || !ref.isValid()) return;
Store<EntityStore> store = ref.getStore();
Player player = store.getComponent(ref, Player.getComponentType());
```

## NPC/Entity Spawning

```java
// Spawn NPC using NPCPlugin (returns Pair, extract first element for entity ref)
NPCPlugin npcPlugin = NPCPlugin.get();
Object result = npcPlugin.spawnNPC(store, "Skeleton", "DisplayName", position, rotation);
// Use reflection to extract Ref from Pair (getFirst, getLeft, etc.)

// Entity removal - use proper API, not reflection guessing
store.removeEntity(entityRef, RemoveReason.REMOVE);

// Singleton components - use INSTANCE or get(), not constructor
store.addComponent(entityRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
store.addComponent(entityRef, Frozen.getComponentType(), Frozen.get());  // Disables AI

// Teleport entity
store.addComponent(entityRef, Teleport.getComponentType(), new Teleport(world, position, rotation));

// Calculate yaw rotation for entity to face movement direction
double dx = targetX - previousX;
double dz = targetZ - previousZ;
float yaw = (float) (Math.toDegrees(Math.atan2(dx, dz)) + 180.0);  // +180 to face forward
Vector3f rotation = new Vector3f(0, yaw, 0);
```

## NPC Management

```java
// Track online players - only spawn NPCs for connected players
private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();

// Prevent duplicate spawns with spawning flag
private volatile boolean spawning;
if (state.isSpawning()) return; // Skip if spawn in progress
state.setSpawning(true);
try {
    // spawn logic
} finally {
    state.setSpawning(false);
}
```

**Important:** See `HYTALE_API.md` (in docs/) for Vector3d limitations when working with positions.
