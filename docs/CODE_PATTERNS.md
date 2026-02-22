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

**CRITICAL:** If your command accepts arguments (e.g., `/mycommand arg1 arg2`), you **must** call `setAllowsExtraArguments(true)` in the constructor. Without it, Hytale rejects with "wrong number of required arguments" error:

```java
public MyCommand() {
    super("mycommand");
    setAllowsExtraArguments(true);  // Required for commands with arguments
}
// Parse args with CommandUtils.getArgs(ctx), not ctx.args()
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

### UI Pages with Background Tasks

When a page has scheduled tasks (auto-refresh, animations, etc.), you **must** implement proper cleanup to avoid crashes when the UI is replaced by external systems (e.g., NPCDialog).

**CRITICAL:** Hytale calls `onDismiss()` when any UI replaces your page - even external UIs. Use this for cleanup, **not** `close()` alone.

```java
public class MyAutoRefreshPage extends BaseAscendPage {
    private ScheduledFuture<?> refreshTask;

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder evt, Store<EntityStore> store) {
        commandBuilder.append("Pages/MyPage.ui");
        startAutoRefresh(ref, store);  // Start background task
    }

    @Override
    public void close() {
        stopAutoRefresh();  // Clean up when user closes
        super.close();
    }

    @Override
    protected void stopBackgroundTasks() {
        stopAutoRefresh();  // Clean up when replaced by external UI
    }

    private void startAutoRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        refreshTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            if (!isCurrentPage()) {  // Check if still active
                stopAutoRefresh();
                return;
            }
            // Update UI...
            try {
                sendUpdate(commandBuilder, null, false);
            } catch (Exception e) {
                // UI was replaced - stop refreshing
                stopAutoRefresh();
            }
        }, 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
    }
}
```

**Base page pattern with `onDismiss()` hook:**

```java
public abstract class BaseMyModulePage extends InteractiveCustomUIPage<ButtonEventData> {

    protected BaseMyModulePage(@Nonnull PlayerRef playerRef,
                               @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, ButtonEventData.CODEC);
    }

    /**
     * Called automatically by Hytale when page is dismissed or replaced.
     * Override stopBackgroundTasks() in subclasses to clean up scheduled tasks.
     * NOTE: Do NOT call close() here - causes StackOverflowError recursion!
     */
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        stopBackgroundTasks();  // Clean up before dismissal
        super.onDismiss(ref, store);
    }

    /**
     * Override this in subclasses to stop scheduled tasks.
     * Called from both close() and onDismiss() to ensure cleanup.
     */
    protected void stopBackgroundTasks() {
        // Default: no background tasks to stop
    }
}
```

**Key points:**
1. **Never call `close()` from `onDismiss()`** - causes infinite recursion (StackOverflowError)
2. Use `stopBackgroundTasks()` pattern for cleanup logic shared between `close()` and `onDismiss()`
3. Always wrap `sendUpdate()` in try-catch when called from scheduled tasks
4. Check `isCurrentPage()` before sending updates in long-running tasks
5. Cancel `ScheduledFuture` tasks in `stopBackgroundTasks()` to prevent memory leaks

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

**CRITICAL - No underscores in element IDs:**
```
❌ #Path_Label_VEXA    - Will cause UI parsing error
❌ #Node_VEXA_T1       - Will cause UI parsing error
✅ #PathLabelVexa      - Use camelCase instead
✅ #NodeVexaT1         - Use camelCase instead
```
Hytale UI does NOT support underscores in element IDs. Always use camelCase for IDs.

**CRITICAL - TextButton cannot have children:**

TextButton is an **atomic** element. It does NOT support child elements (Group, Label, etc.) or `LayoutMode`. Placing children inside a TextButton causes a silent UI parsing error.

```
❌ TextButton #MyTab {
     LayoutMode: Top;
     Background: #2d3f50;
     Group #Accent { ... }   // CRASHES - TextButton can't have children
     Label { Text: "Tab"; }  // CRASHES
   }

✅ Group #MyTabWrap {
     Background: #2d3f50;
     Group #Accent { ... }
     Label { Text: "Tab"; }
     TextButton #MyTab {                          // Transparent overlay for click
       Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
       Text: "";
       Style: TextButtonStyle(
         Default: (Background: #000000(0)),
         Hovered: (Background: #ffffff(0.05)),
         Pressed: (Background: #ffffff(0.1)),
         Sounds: $C.@ButtonSounds,
       );
     }
   }
```

Use the **Group wrapper + TextButton overlay** pattern for any clickable element that needs custom visual content.

### Dynamic Background Alpha Limitation

**`commandBuilder.set()` does NOT support alpha notation** like `#1a2530(0.6)` for Background values. Setting a dynamic Background with alpha causes an error texture (white + red cross). Use pre-blended opaque hex instead:

```java
// ❌ WRONG - causes error texture:
commandBuilder.set("#MyGroup", "Background", "#1a2530(0.6)");

// ✅ CORRECT - use opaque hex (pre-blend the color):
commandBuilder.set("#MyGroup", "Background", "#0f1720");
```

Alpha notation works in `.ui` files (static), just not in dynamic `commandBuilder.set()` calls.

### Disabled Button with Overlay Pattern

Hytale doesn't support dynamic style changes on TextButtons (e.g., changing `Style.Default.Background` at runtime). To create a "disabled" or "grayed out" button effect, use an overlay pattern:

**UI file (.ui):**
```
Group #MyButtonWrapper {
  Anchor: (Height: 56);
  FlexWeight: 1;

  TextButton #MyButton {
    Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
    Text: "Click Me";
    Style: TextButtonStyle(
      Default: (Background: #5a3d6b(0.85), LabelStyle: (...)),
      Hovered: (Background: #7a4d8f(0.92), LabelStyle: (...)),
      Pressed: (Background: #9460a8(0.95), LabelStyle: (...)),
      Sounds: $C.@ButtonSounds,
    );
  }

  TextButton #MyButtonOverlay {
    Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0);
    Text: "";
    Style: TextButtonStyle(
      Default: (Background: #1a1a2a(0.6)),
      Hovered: (Background: #1a1a2a(0.6)),
      Pressed: (Background: #1a1a2a(0.7)),
    );
    Visible: false;
  }
}
```

**Key points:**
1. Wrap the button in a Group container
2. Add a `TextButton` overlay (not `Group` - Groups don't support `Activating` events)
3. Overlay has empty text and same color for Default/Hovered (no visible hover effect)
4. Overlay starts hidden (`Visible: false`)

**Java - Event bindings:**
```java
// Bind both the real button and the overlay
eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MyButton",
    EventData.of(ButtonEventData.KEY_BUTTON, "MyAction"), false);
eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MyButtonOverlay",
    EventData.of(ButtonEventData.KEY_BUTTON, "MyActionDisabled"), false);
```

**Java - Toggle disabled state:**
```java
// Show overlay = button appears grayed out and blocks hover/clicks
commandBuilder.set("#MyButtonOverlay.Visible", isDisabled);
```

**Java - Handle disabled click:**
```java
if ("MyActionDisabled".equals(data.getButton())) {
    sendMessage(store, ref, "Action not available.");
    return;
}
```

**Why TextButton for overlay (not Group):**
- `Group` elements don't support `Activating` events
- Error: "Target element in CustomUI event binding has no compatible Activating event"
- `TextButton` with empty text acts as an invisible interactive overlay

### Off-Screen Labels as Data Carriers (Known Workaround)

Hytale UI has no dedicated data-binding element. To store and read values from Java
without displaying them, we position Labels off-screen with zero dimensions:

```
Label #RunPbText {
  Anchor: (Left: 0, Top: -200, Width: 0, Height: 0);
  Style: (FontSize: 1, TextColor: #000000);
  Text: "";
}
```

Java sets/reads the value via `commandBuilder.set("#RunPbText", "Text", value)`.
This is an intentional workaround, not dead code.

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

### CommandBuffer in Tick Systems

Inside `EntityTickingSystem.tick()`, the store is locked for writes. **Reads are safe** (`store.getComponent()`, `chunk.getComponent()`), but writes (`addComponent`, `removeComponent`) will throw `IllegalStateException: Store is currently processing`.

Use the `CommandBuffer` parameter for writes:

```java
@Override
public void tick(float delta, int entityId, ArchetypeChunk<EntityStore> chunk,
                 Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
    // READS: use chunk (preferred) or store — both safe
    PlayerRef playerRef = chunk.getComponent(entityId, PlayerRef.getComponentType());
    TransformComponent transform = chunk.getComponent(entityId, TransformComponent.getComponentType());

    // WRITES: use buffer, NOT store
    Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
    buffer.tryRemoveComponent(ref, KnockbackComponent.getComponentType());

    // Pass buffer to helpers that need to write
    runTracker.checkPlayer(ref, store, buffer, delta);
}
```

**When the tick system can't use CommandBuffer** (e.g., complex logic that needs store writes), defer to the world thread:

```java
// Inside tick(): defer store-modifying work outside system processing
Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
AsyncExecutionHelper.runBestEffort(world, () -> {
    if (ref != null && ref.isValid()) {
        // Now safe to use store directly
        runTracker.checkPlayer(ref, ref.getStore());
    }
}, "description", ...);
```

### Page-ID Tracking for Long-Running Refresh Tasks

**Recommended:** Extend `BaseAscendPage` for all Ascend module pages. It provides page-ID tracking that prevents stale UI updates when a page is replaced.

```java
// BaseAscendPage assigns each instance a unique pageId and tracks the current page per player.
// When a new page opens, the old page's isCurrentPage() returns false.

public class MyPage extends BaseAscendPage {
    private ScheduledFuture<?> refreshTask;

    @Override
    public void build(...) {
        commandBuilder.append("Pages/MyPage.ui");
        startAutoRefresh(ref, store);
    }

    private void startAutoRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        refreshTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            if (!isCurrentPage()) {   // Page was replaced — stop
                stopAutoRefresh();
                return;
            }
            // Use PageRefreshScheduler to coalesce refreshes on world thread
            PageRefreshScheduler.requestRefresh(world, refreshInFlight, refreshRequested,
                () -> refreshDisplay(ref, store), this::stopAutoRefresh, "MyPage");
        }, 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void stopBackgroundTasks() {
        stopAutoRefresh();
    }
}
```

**Key points:**
1. `isCurrentPage()` — checks if this page instance is still the active one for the player
2. `stopBackgroundTasks()` — called by `BaseAscendPage.onDismiss()` when page is dismissed/replaced
3. `PageRefreshScheduler` — coalesces multiple refresh requests on the world thread, prevents concurrent refreshes

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
