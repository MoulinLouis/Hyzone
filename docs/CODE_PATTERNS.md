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
// Parse args with CommandUtils.tokenize(ctx), not ctx.args()
```

## Dependency Wiring

Use constructor injection for managers, stores, and page dependencies.

```java
public final class MyPage extends BaseAscendPage {
    private final MyStore myStore;
    private final MyManager myManager;

    public MyPage(PlayerRef playerRef, MyStore myStore, MyManager myManager) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.myStore = myStore;
        this.myManager = myManager;
    }
}
```

Composition root rule:
1. `Plugin.setup()` creates stores/managers/services and wires them together.
2. Commands, interactions, and other framework-owned entry points may read dependencies from the plugin singleton once to bootstrap.
3. Business logic classes (stores, managers, services, pages) must never call `getInstance()` or access static singletons. Dependencies are passed via constructor. Only plugin `setup()` methods and interaction bridge `configure()` calls may use static accessors.
4. If a page needs to open sibling pages, inject a small page factory/navigator rather than re-reading the plugin singleton inside the page.
5. Cross-module stores use `SharedInstance<T>` (in `io.hyvexa.core`) — the owning plugin calls `createAndRegister()` in `setup()`, other plugins call `get()` in their `setup()`. The owning plugin must call `destroy()` in `shutdown()` for clean hot-reload.
6. **Shared classloader constraint:** Non-core modules use `compileOnly project(':hyvexa-core')` so hyvexa-core classes are loaded once via Hytale's `PluginBridgeClassLoader`. This means all plugins share the same static fields (single `DatabaseManager`, single connection pool). **Never change `compileOnly` back to `implementation`** in non-core modules — it would re-bundle core classes and break shared state. Only `hyvexa-parkour` (the core plugin JAR) uses `implementation`.

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

**NOTE:** `LayoutMode: Center` does NOT exist as a standalone value. Use `CenterMiddle` (both axes) or the FlexWeight pattern below for horizontal centering.

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
1. `LayoutMode: Center` - Does not exist; use `CenterMiddle` or FlexWeight pattern above
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

All stores follow the memory-first with MySQL persistence pattern. Player-bound stores use one of two base classes:
- `BasePlayerStore<V>` for module-owned typed player data (`PurgePlayerStore`, `DuelStatsStore`, `RunOrFallStatsStore`)
- `CachedCurrencyStore` for global core currencies (`VexaStore`, `FeatherStore`)

### Creating a New Store (End-to-End)

Use `PurgePlayerStore` as the smallest direct `BasePlayerStore<V>` example. Use `VexaStore` when you need the core singleton currency-store variant.

#### 1. Extend `BasePlayerStore<V>`

Every `BasePlayerStore<V>` subclass must implement these 5 template methods:

```java
public class PurgePlayerStore extends BasePlayerStore<PurgePlayerStats> {

    public PurgePlayerStore(ConnectionProvider db) {
        super(db);
    }

    @Override
    protected String loadSql() {
        return "SELECT best_wave, total_kills, total_sessions FROM purge_player_stats WHERE uuid = ?";
    }

    @Override
    protected String upsertSql() {
        return "INSERT INTO purge_player_stats (uuid, best_wave, total_kills, total_sessions) VALUES (?, ?, ?, ?) "
             + "ON DUPLICATE KEY UPDATE best_wave = ?, total_kills = ?, total_sessions = ?";
    }

    @Override
    protected PurgePlayerStats parseRow(ResultSet rs, UUID playerId) throws SQLException {
        return new PurgePlayerStats(rs.getInt("best_wave"), rs.getInt("total_kills"), rs.getInt("total_sessions"));
    }

    @Override
    protected void bindUpsertParams(PreparedStatement stmt, UUID id, PurgePlayerStats s) throws SQLException {
        stmt.setString(1, id.toString());
        stmt.setInt(2, s.getBestWave());
        stmt.setInt(3, s.getTotalKills());
        stmt.setInt(4, s.getTotalSessions());
        stmt.setInt(5, s.getBestWave());
        stmt.setInt(6, s.getTotalKills());
        stmt.setInt(7, s.getTotalSessions());
    }

    @Override
    protected PurgePlayerStats defaultValue() {
        return new PurgePlayerStats(0, 0, 0);
    }
}
```

What each override is responsible for:
1. `loadSql()` returns a single-row `SELECT` with one UUID placeholder. `BasePlayerStore.getOrLoad()` binds that UUID for you.
2. `upsertSql()` returns the full insert/update statement used by `save()`.
3. `parseRow()` maps one `ResultSet` row into your value object.
4. `bindUpsertParams()` binds every parameter required by `upsertSql()`.
5. `defaultValue()` is returned when no row exists yet, the DB is unavailable, or the caller passes `null`.

`BasePlayerStore` already provides the cache lifecycle:
- `getOrLoad(playerId)` uses `cache.computeIfAbsent(...)` and falls back to SQL only on a miss
- `save(playerId, value)` updates cache first, then writes through to MySQL
- `evict(playerId)` removes only the in-memory entry

If the store needs extra behavior such as startup bulk-loads or read models for leaderboards, add them on top of the base contract the way `DuelStatsStore.syncLoad()` and `RunOrFallStatsStore.syncLoad()` do.

#### 2. Implement SQL Around the Real Table

Keep the SQL and the Java value object in lockstep:
- `loadSql()` and `parseRow()` must read the same columns
- `upsertSql()` and `bindUpsertParams()` must write the same columns in the same order
- `defaultValue()` must represent a valid "player has no row yet" state

Examples already in the repo:
- `DuelStatsStore` reads and writes `duel_player_stats`
- `PurgePlayerStore` reads and writes `purge_player_stats`
- `RunOrFallStatsStore` reads and writes `runorfall_player_stats`

#### 3. Add Table Creation in the Right Startup Owner

For new module-owned stores, prefer centralizing schema creation in the module's `*DatabaseSetup.java`.

`PurgeDatabaseSetup.ensureTables()` is the clearest example:

```java
stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purge_player_stats ("
        + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
        + "best_wave INT NOT NULL DEFAULT 0, "
        + "total_kills INT NOT NULL DEFAULT 0, "
        + "total_sessions INT NOT NULL DEFAULT 0"
        + ") ENGINE=InnoDB");
```

For core singleton stores, the store owns its own table setup during `initialize()`. `VexaStore` inherits that from `CachedCurrencyStore.initialize()`, which creates `player_vexa` before registering the store with `CurrencyBridge`.

Older module stores such as `DuelStatsStore` and `RunOrFallStatsStore` still call `ensureTable()` inside the store itself. That works, but do not add `ensureTable()` to new `BasePlayerStore` subclasses — new work must use the centralized `*DatabaseSetup` path for module schemas.

#### 4. Wire Startup in `Plugin.setup()`

There are 2 valid startup paths in the current codebase:

1. Shared-instance store (backed by `SharedInstance<T>`) created by the owning plugin via `createAndRegister()`:

```java
initSafe("VexaStore", () -> VexaStore.createAndRegister(DatabaseManager.get()));
this.vexaStore = VexaStore.get();
```

Inside the store class, the pattern is:

```java
private static final SharedInstance<VexaStore> SHARED = new SharedInstance<>("VexaStore");

public static VexaStore createAndRegister(ConnectionProvider db) {
    var store = new VexaStore(db);
    store.initialize();
    return SHARED.register(store);
}
public static VexaStore get()  { return SHARED.get(); }
public static void destroy()   { SHARED.destroy(); }
```

2. Module `BasePlayerStore` after DB setup and table creation:

```java
RunOrFallDatabaseSetup.ensureTables();
statsStore = new RunOrFallStatsStore(DatabaseManager.get());
```

Rule of thumb:
- If the store uses the shared-instance pattern (`createAndRegister()` + `get()`), the owning plugin (HyvexaPlugin/core) calls `createAndRegister()` in `setup()` and non-core plugins just call `get()`. Non-core plugins must **never** call `initialize()` on shared stores — they're already initialized by core.
- If the store is a plain `BasePlayerStore<V>` with no startup hook, instantiate it only after `DatabaseManager` and the relevant `*DatabaseSetup.ensureTables()` have run.
- Only the core plugin (`HyvexaPlugin`) should call `DatabaseManager.get().shutdown()`. Non-core plugins must not shut down shared resources.

#### 5. Evict on `PlayerDisconnectEvent`

Every player-cached store needs explicit disconnect cleanup.

**Shared stores** (VexaStore, FeatherStore, CosmeticStore, DiscordLinkStore, CosmeticManager, MultiHudBridge, PurgeSkinStore) are automatically evicted by `SharedStoreCleanup.evictPlayer()`, which is registered as a global handler in `HyvexaPlugin.setup()`. Non-core plugins do **not** need to evict shared stores — only their own local stores.

Example from `HyvexaPurgePlugin` (local stores only):

```java
PlayerCleanupHelper.cleanup(playerId, LOGGER,
        id -> playerStore.evict(id),
        id -> scrapStore.evictPlayer(id),
        id -> weaponUpgradeStore.evictPlayer(id),
        id -> weaponXpStore.evictPlayer(id),
        id -> classStore.evictPlayer(id),
        id -> missionStore.evictPlayer(id)
);
```

If you add a new **shared** store, add it to `SharedStoreCleanup.evictPlayer()`. If you add a new **local** store, add it to the owning plugin's disconnect handler. Forgetting eviction means reconnects can read stale in-memory state instead of the latest DB row.

#### 6. Add the Schema to Docs

When you add a new table:
1. Add the `CREATE TABLE` block to the module-specific schema file (`docs/<Module>/DATABASE.md`)
2. Name the owning store or setup class directly below it
3. Update the index in `docs/DATABASE.md` if it is a new table
4. Keep the docs in sync with the actual SQL in `*DatabaseSetup.java` or `initialize()`

Existing examples:
- `player_vexa` -> `VexaStore`
- `duel_player_stats` -> `DuelStatsStore`
- `purge_player_stats` -> `PurgePlayerStore`
- `runorfall_player_stats` -> `RunOrFallStatsStore`

#### 7. Understand the Full Lifecycle

For `BasePlayerStore<V>`, the real lifecycle is:

1. Player connects. The plugin has already initialized the DB and created the store instance in `setup()`.
2. Nothing is loaded just because the player connected. The first call to `getOrLoad(playerId)` is the load boundary.
3. On a cache miss, `BasePlayerStore.getOrLoad()` runs `cache.computeIfAbsent(playerId, this::loadFromDatabase)`, executes `loadSql()`, and falls back to `defaultValue()` when no row exists.
4. On a cache hit, the value comes straight from memory and no SQL runs.
5. Your gameplay code mutates the value and calls `save(playerId, value)`. `BasePlayerStore.save()` updates the cache and then executes `upsertSql()`.
6. On disconnect, the plugin must call `evict(playerId)` to remove the cached copy.
7. On the next session, the first `getOrLoad()` reads the fresh DB state again.

Concrete example from `PurgeSessionManager.persistResults()`:

```java
PurgePlayerStats stats = playerStore.getOrLoad(playerId);
stats.updateBestWave(session.getCurrentWave());
stats.incrementKills(kills);
stats.incrementSessions();
playerStore.save(playerId, stats);
```

Core singleton currency stores follow the same cache -> persist -> evict shape, but the public API is `getBalance()/setBalance()/addBalance()/evictPlayer()` instead of `getOrLoad()/save()/evict()`.

#### Testing

Stores depend on `ConnectionProvider` / MySQL and cannot be unit-tested in isolation. Pure-logic value objects (e.g. `PurgePlayerStats`) are testable — put assertions on mutation methods there. For the store itself, verify correctness through integration testing or manual gameplay.

#### When NOT to Use BasePlayerStore

- Data needs TTL expiration or periodic refresh → use `CachedCurrencyStore` pattern (see `VexaStore`, `FeatherStore`)
- Data is complex/nested with debounced saves or domain facades → use a custom store (see `AscendPlayerStore`)
- Data is not per-player → use a plain manager with manual cache

## Cross-Module Communication

**Rule:** Gameplay modules never import from each other. All cross-cutting communication goes through `hyvexa-core` bridges. Three distinct sub-patterns exist: **Static Registry** (producers register, consumers lookup by key), **Service Locator** (for codec-instantiated classes), and **Reflection Adapter** (optional third-party plugins).

### Bridge Inventory

| Bridge | Location | Pattern | Purpose |
|--------|----------|---------|---------|
| `CurrencyBridge` | `core/economy/` | Static registry | Currency operations by name ("vexa", "feathers") |
| `GameModeBridge` | `core/bridge/` | Static registry | Cross-mode interaction dispatch by key |
| `WardrobeBridge` | `core/wardrobe/` | Static facade | Cosmetic purchase operations (vexa deduction + permission grant) |
| `ParkourInteractionBridge` | `parkour/interaction/` | Service locator | Parkour services for codec-instantiated interactions |
| `AscendInteractionBridge` | `ascend/interaction/` | Service locator | Ascend services for interactions |
| `RunOrFallInteractionBridge` | `runorfall/interaction/` | Service locator | RunOrFall services for interactions |
| `PurgeInteractionBridge` | `purge/interaction/` | Service locator | Purge services for interactions |
| `MultiHudBridge` | `common/util/` | Reflection adapter | Optional MultipleHUD plugin integration |
| `HylogramsBridge` | `common/util/` | Reflection adapter | Optional Hylogram plugin integration |

### CurrencyBridge — Currency access from any module

Currency stores register themselves at startup; any module queries/deducts by name.

```java
// Registration (in store's initialize/registerBridge):
CurrencyBridge.register("vexa", this);       // VexaStore
CurrencyBridge.register("feathers", this);   // FeatherStore

// Usage from any module (e.g. wardrobe purchasing):
long balance = CurrencyBridge.getBalance("vexa", playerId);
boolean ok = CurrencyBridge.deduct("vexa", playerId, 500);
```

Registered currencies: `"vexa"` (VexaStore), `"feathers"` (FeatherStore). To add a new currency, implement `CurrencyStore` and call `CurrencyBridge.register()` during setup.

### GameModeBridge — Cross-module interactions

Modules register named interaction handlers; other modules invoke them by key without importing the target module.

```java
// Registration (in HyvexaPlugin.setup() — parkour module):
GameModeBridge.register(GameModeBridge.PARKOUR_RESTART_CHECKPOINT,
        (ref, firstRun, time, type, ctx) ->
                new RestartCheckpointInteraction().handle(ref, firstRun, time, type, ctx));

// Invocation (from another module that needs to trigger a parkour restart):
GameModeBridge.invoke(GameModeBridge.PARKOUR_RESTART_CHECKPOINT,
        ref, firstRun, time, type, interactionContext);
```

Keys are string constants defined in `GameModeBridge` (e.g. `PARKOUR_RESTART_CHECKPOINT`). Add new keys there when adding cross-module interactions.

### ModeGate + ModeMessages — World-based access control

Commands and interactions that only apply in a specific world use `ModeGate` to reject players in the wrong world, with `ModeMessages` providing the denial text.

```java
// Guard a command to Ascend world only:
if (ModeGate.denyIfNot(ctx, world, WorldConstants.WORLD_ASCEND, ModeMessages.MESSAGE_ENTER_ASCEND)) {
    return;
}
// Check world in interaction logic:
if (ModeGate.isPurgeWorld(world)) { /* Purge-specific path */ }
```

`ModeGate` lives in `hyvexa-core` (`io.hyvexa.common.util`), so any module can use it without cross-module imports.

### Anti-patterns

```java
// ❌ WRONG: Direct cross-module access — creates hard dependency
ParkourAscendPlugin.getInstance().getRobotManager().despawnAll();

// ❌ WRONG: Importing from a sibling module
import io.hyvexa.ascend.data.AscendPlayerStore;

// ✅ CORRECT: Use a core bridge or move shared logic to hyvexa-core
CurrencyBridge.getBalance("vexa", playerId);
GameModeBridge.invoke(GameModeBridge.PARKOUR_RESTART_CHECKPOINT, ...);
```

If two modules need to share behavior, extract it into `hyvexa-core` as a bridge, utility, or shared store — never have one gameplay module import another.

### Reflection Adapter — Optional third-party plugins

`MultiHudBridge` and `HylogramsBridge` use reflection to detect an optional external plugin at runtime. If the plugin is loaded, calls go through its API; if not, operations fall back gracefully (no-op or direct alternative). Both live in `common/util/` and use the same structure: a `volatile boolean` availability flag, cached `Method` references, and try-catch around every reflective call.

Do not create new reflection adapters without discussion — only two exist and they're stable. If you need a new optional plugin integration, follow the same `checked`/`available`/cached-method pattern.

### Adding a New Bridge — Decision Tree

- Need to call another module's service by key? → **Static Registry** (like `GameModeBridge`)
- Need services in a codec-instantiated (no-arg constructor) class? → **Service Locator** (like `ParkourInteractionBridge`) — see [InteractionBridge](#interactionbridge-service-locator-for-codecs)
- Need an optional external plugin? → **Reflection Adapter** (rare — ask before creating)

## Threading

```java
// Entity/world ops require world thread
CompletableFuture.runAsync(() -> {
    store.addComponent(ref, Teleport.getComponentType(), teleport);
}, world);
```

## Facade Access Pattern

Large coordinator stores use domain facades to organize operations by concern. The coordinator owns cache lifecycle and cross-domain operations; facades own domain-specific logic.

```java
// AscendPlayerStore is the coordinator — access domain facades via accessor methods:
AscendPlayerStore playerStore = ...;

playerStore.volt().getVolt(playerId);              // currency operations
playerStore.progression().getElevation(playerId);  // prestige/progression
playerStore.runners().getRunnerLevel(playerId, mapId); // map progress
playerStore.gameplay().getAchievements(playerId);  // achievements, skills, tutorials
playerStore.settings().isAutomationEnabled(playerId);  // player settings
```

**When to use this pattern:**
- A store class has grown past ~500 lines with clearly separable concerns
- Different callers only need a subset of the store's API
- The coordinator still owns: player cache lifecycle (`getOrLoad`, `removePlayer`, `syncLoad`), cross-domain resets (prestige), and leaderboard queries

**When NOT to use:**
- Small stores where a single class is clear enough
- When operations frequently span multiple domains (keep them in the coordinator)

## Concurrency Patterns

### Pattern 1: Read-Heavy Configuration Store
**Use:** Config data loaded once, read frequently, rarely written (map definitions, game settings).
**Primitive:** `ReadWriteLock` + plain `HashMap`/`LinkedHashMap`
**Examples:** `MapStore`, `SettingsStore`, `MineHierarchyStore.mines`

```java
private final ReadWriteLock lock = new ReentrantReadWriteLock();
private final Map<String, Config> configs = new LinkedHashMap<>();

public Config get(String id) {
    lock.readLock().lock();
    try { return configs.get(id); } finally { lock.readLock().unlock(); }
}

public void reload(List<Config> newConfigs) {
    lock.writeLock().lock();
    try { configs.clear(); newConfigs.forEach(c -> configs.put(c.id(), c)); }
    finally { lock.writeLock().unlock(); }
}
```

### Pattern 2: Player Data Cache
**Use:** Per-player mutable state, concurrent reads/writes from different players, no cross-player atomicity needed.
**Primitive:** `ConcurrentHashMap<UUID, PlayerData>` (NO external lock)
**Examples:** `AscendPlayerStore`, HUD managers

```java
private final ConcurrentHashMap<UUID, PlayerData> players = new ConcurrentHashMap<>();

public PlayerData get(UUID id) { return players.get(id); }
public void evict(UUID id) { players.remove(id); }
```

### Pattern 3: Transactional Per-Player Operation
**Use:** Operations that need atomicity within a single player (purchase, unlock).
**Primitive:** Per-player lock via `ConcurrentHashMap<UUID, Object>`
**Examples:** `CosmeticStore`, `PurgeSkinStore`

```java
private final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

public PurchaseResult purchase(UUID playerId, ItemDef def) {
    Object lock = playerLocks.computeIfAbsent(playerId, k -> new Object());
    synchronized (lock) {
        // check-deduct-grant is atomic per player
    }
}

public void evict(UUID playerId) {
    playerLocks.remove(playerId); // prevent memory leak
}
```

### Pattern 4: Independent Counters / Flags
**Use:** Simple counters, boolean flags, one-time initialization.
**Primitive:** `AtomicInteger`/`AtomicLong`/`AtomicBoolean` or `volatile`

```java
private final AtomicLong counter = new AtomicLong(0);
counter.incrementAndGet();

private volatile boolean initialized = false;

// Use synchronized only for compound init (check + set + side effects)
synchronized (INIT_LOCK) {
    if (initialized) return;
    // ... initialize ...
    initialized = true;
}
```

### Pattern 5: Snapshot References
**Use:** Data rebuilt periodically, readers see complete snapshot.
**Primitive:** `volatile` reference to immutable or independently thread-safe collection
**Examples:** `MineHierarchyStore.sortedCache`, `MineHierarchyStore.layerById`

```java
private volatile List<Mine> sortedCache;

public List<Mine> getSorted() { return sortedCache; }  // readers get atomic snapshot

public void rebuild() {
    List<Mine> newList = /* build sorted list */;
    sortedCache = Collections.unmodifiableList(newList);  // atomic publish
}
```

### Anti-Patterns to Avoid
- **`Collections.synchronizedMap/List`** — Use `ConcurrentHashMap` or `CopyOnWriteArrayList`
- **`ReadWriteLock` + `ConcurrentHashMap` on same data** — Pick one; layer them only when they guard different concerns (e.g., bulk-load lock + per-key map)
- **`synchronized` on entire store method** — Use per-player locks or atomics
- **Multiple `volatile` fields that must be consistent together** — Use `AtomicReference<Record>` or a lock
- **`computeIfAbsent` vs check-then-put** — Always prefer `computeIfAbsent`

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

## Event Handler Registration

Register platform events with `eventRegistry.registerGlobal(EventClass.class, handler)`. Two patterns exist:

### Centralized Router (Parkour)

A dedicated class receives all dependencies and registers handlers in one method:

```java
// ParkourEventRouter — receives stores/managers via constructor
void registerAll(EventRegistry eventRegistry) {
    eventRegistry.registerGlobal(PlayerConnectEvent.class, this::handlePlayerConnect);
    eventRegistry.registerGlobal(PlayerReadyEvent.class, this::handlePlayerReady);
    eventRegistry.registerGlobal(AddPlayerToWorldEvent.class, this::handleAddPlayerToWorld);
    eventRegistry.registerGlobal(PlayerDisconnectEvent.class, this::handlePlayerDisconnect);
    eventRegistry.registerGlobal(PlayerChatEvent.class, this::handlePlayerChat);
}

// Wired in Plugin.setup():
this.eventRouter = new ParkourEventRouter(mapStore, progressStore, runTracker, ...);
this.eventRouter.registerAll(this.getEventRegistry());
```

### Inline Handlers (Ascend)

Register lambdas directly in `setup()` when the plugin class holds the dependencies:

```java
// ParkourAscendPlugin.setup()
this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
    try {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) { return; }
        // ... handler logic with direct access to plugin fields
    } catch (Exception e) {
        LOGGER.atWarning().log("Exception in PlayerReadyEvent (ascend): " + e.getMessage());
    }
});
```

### Key Rules

- **Wrap each handler in try-catch** — one handler crash must not affect others
- **Validate refs early** — `if (ref == null || !ref.isValid()) return;` before accessing ECS components
- Common events: `PlayerConnectEvent`, `PlayerReadyEvent`, `PlayerDisconnectEvent`, `AddPlayerToWorldEvent`, `PlayerChatEvent`
- Use the centralized router when the module has many handlers with shared dependencies; use inline when handler count is small

## Plugin Initialization Sequence

Every plugin's `setup()` follows a strict order. Skipping or reordering steps causes NPEs or stale state.

### Canonical Order

```
1.  DatabaseManager.createAndRegister() / .get()
2.  *DatabaseSetup.ensureTables()           — DDL
3.  Core shared-instance stores             — VexaStore.createAndRegister(db) / .get()
4.  Module stores                           — new XxxStore(db) + syncLoad()
5.  Managers                                — created with store dependencies
6.  Service wiring                          — connect managers to each other
7.  Bridge configuration                    — XxxBridge.configure(new Services(...))
8.  Interaction codec registration          — registerInteractionCodecs()
9.  Command registration                    — getCommandRegistry().registerCommand(...)
10. ECS system registration                 — EntityStore.REGISTRY.registerSystem(...)
11. Event handler registration              — registerGlobal(...) or router
12. Scheduled tasks                         — scheduleTick(...)
13. Post-init player loop                   — Universe.get().getPlayers() for already-connected players
14. Shutdown hook                           — Runtime.getRuntime().addShutdownHook(...)
```

### initSafe Wrapper

Wrap non-critical initialization steps so one failure doesn't crash the entire plugin:

```java
// HyvexaPlugin — logs and continues on failure
private void initSafe(String name, Runnable init) {
    try { init.run(); }
    catch (Exception e) { LOGGER.atWarning().withCause(e).log("Failed to initialize " + name); }
}

// Usage:
initSafe("VexaStore", () -> VexaStore.createAndRegister(DatabaseManager.get()));
initSafe("AnalyticsStore", () -> {
    analyticsStore.initialize();
    analyticsStore.purgeOldEvents(90);
});
```

### Fail-Fast for Critical Stores

Core stores that the plugin cannot function without should abort setup on failure:

```java
// ParkourAscendPlugin — returns early, plugin is non-functional
try {
    mapStore = new AscendMapStore(DatabaseManager.get());
    mapStore.syncLoad();
    playerStore = new AscendPlayerStore(DatabaseManager.get());
    playerStore.syncLoad();
} catch (Exception e) {
    LOGGER.atSevere().withCause(e).log("Failed to initialize core stores — plugin will not function");
    return;
}
```

### Post-Init Player Loop

Players may already be connected when the plugin starts (hot reload). Process them after all systems are ready:

```java
// HyvexaPlugin.setup() — handle players connected before plugin init
for (Player player : Universe.get().getPlayers()) {
    // cache, load settings, attach HUD, mark ready
}
```

## InteractionBridge (Service Locator for Codecs)

Hytale instantiates interaction handlers via no-arg constructors (codec system), so they can't receive constructor dependencies. The InteractionBridge pattern provides a static service locator scoped to what interactions need.

### Pattern

```java
// AscendInteractionBridge — static holder with volatile Services record
public final class AscendInteractionBridge {

    private static volatile Services services;

    private AscendInteractionBridge() {}

    public static void configure(Services services) {
        AscendInteractionBridge.services = services;
    }

    public static Services get() {
        return services;
    }

    public static void clear() {
        services = null;
    }

    public record Services(
        AscendMapStore mapStore,
        AscendPlayerStore playerStore,
        AscendRunTracker runTracker,
        AscendHudManager hudManager,
        // ... only what interactions actually need
    ) {}
}
```

### Lifecycle

1. **Configure** — called once in `Plugin.setup()` after all dependencies are initialized:
   ```java
   AscendInteractionBridge.configure(new AscendInteractionBridge.Services(
           mapStore, playerStore, runTracker, hudManager, ...));
   ```
2. **Use** — interactions call `XxxBridge.get().someStore()` in their handler methods:
   ```java
   public class MyInteraction extends ServerInteraction {
       @Override
       public void handle(...) {
           AscendInteractionBridge.Services s = AscendInteractionBridge.get();
           s.mapStore().getMap(mapId);
       }
   }
   ```
3. **Clear** — on shutdown to break reference cycles:
   ```java
   AscendInteractionBridge.clear();
   ```

### Key Rules

- **Narrower than a plugin singleton** — only expose what interactions need, not the entire plugin
- **volatile field** — ensures thread-safe publication of the Services record
- **Record type** — immutable, no setters, all dependencies visible at construction
- Parkour uses `ParkourInteractionBridge` with the same pattern

## Player Disconnect Cleanup

Disconnect cleanup is split into two layers:

### Shared Store Cleanup (centralized)

`SharedStoreCleanup.evictPlayer()` runs once per disconnect via a global handler in `HyvexaPlugin.setup()`, registered before any module-specific handlers. It evicts all shared stores (VexaStore, FeatherStore, CosmeticStore, DiscordLinkStore, CosmeticManager, MultiHudBridge, PurgeSkinStore). Non-core plugins do not evict shared stores — they only clean up their own local state.

### Local Cleanup (per-module)

Each plugin's disconnect handler cleans up local state only. Every step gets its own try-catch so one failure doesn't skip the rest.

**Isolated try-catch** (Parkour — `ParkourEventRouter`):

```java
private void handlePlayerDisconnect(PlayerDisconnectEvent event) {
    UUID playerId = event.getPlayerRef().getUuid();
    // Shared stores already evicted by SharedStoreCleanup

    try { if (duelTracker != null) { duelTracker.handleDisconnect(playerId); } }
    catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: duelTracker"); }

    try { cleanupManager.handleDisconnect(event.getPlayerRef()); }
    catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: cleanupManager"); }

    try { if (medalStore != null) { medalStore.evictPlayer(playerId); } }
    catch (Exception e) { LOGGER.atWarning().withCause(e).log("Disconnect cleanup: MedalStore"); }
    // ... one try-catch per local operation
}
```

**runSafe helper** (Ascend):

```java
private static void runSafe(Runnable action, String logMessage) {
    try { action.run(); }
    catch (Exception e) { LOGGER.atWarning().withCause(e).log(logMessage); }
}

// Usage in disconnect handler (local state only):
runSafe(() -> { if (eventHandler != null) eventHandler.cleanupPlayer(playerId); },
        "Disconnect cleanup: eventHandler");
runSafe(() -> { if (playerStore != null) playerStore.removePlayer(playerId); },
        "Disconnect cleanup: playerStore");
runSafe(() -> { if (minePlayerStore != null) minePlayerStore.evict(playerId); },
        "Disconnect cleanup: minePlayerStore");
```

**PlayerCleanupHelper** (Purge, Wardrobe):

```java
PlayerCleanupHelper.cleanup(playerId, LOGGER,
        id -> playerStore.evict(id),
        id -> scrapStore.evictPlayer(id),
        id -> weaponUpgradeStore.evictPlayer(id)
);
```

### Cleanup Order

1. **SharedStoreCleanup** (automatic, runs first via registration order)
2. **Gameplay systems** — trackers, managers, active sessions
3. **Local stores** — evict module-owned cached player data
4. **Caches** — clear cooldowns, UI state
5. **Analytics last** — fire disconnect event

### Key Rules

- **Shared stores: add to `SharedStoreCleanup`** — never evict them in non-core plugins
- **Local stores: add to the owning plugin's disconnect handler**
- **Null-check optional components** — `if (manager != null)` before calling cleanup
- **Log at WARNING with player UUID context** — essential for debugging partial cleanup failures
- **Never let one failure cascade** — the whole point is isolation
- See also: [Data Stores § Evict on PlayerDisconnectEvent](#5-evict-on-playerdisconnectevent) for store-specific cleanup

## Deferred Player State Initialization (PlayerReadyEvent)

`PlayerReadyEvent` — not `PlayerConnectEvent` — is the canonical hook for loading persistent player data and initializing UI.

### Why PlayerReadyEvent

`PlayerConnectEvent` fires before the player's world and ECS components are fully available. `PlayerReadyEvent` fires once the player is in-world and safe to operate on.

### Pattern (Parkour)

```java
// ParkourEventRouter.handlePlayerReady()
private void handlePlayerReady(PlayerReadyEvent event) {
    Ref<EntityStore> ref = event.getPlayerRef();
    if (ref == null || !ref.isValid()) return;
    Store<EntityStore> store = ref.getStore();
    UUID playerId = playerRef.getUuid();

    // 1. Load persisted settings from DB
    PlayerSettings settings = playerSettingsPersistence.loadOrDefault(playerId);

    // 2. Apply state BEFORE HUD attach to avoid flicker
    hudManager.loadHudHiddenFromStore(playerId);

    // 3. Attach HUD
    hudManager.attach(playerRef, player);

    // 4. Restore in-progress state async on world thread
    CompletableFuture.runAsync(() -> {
        runTracker.restoreSavedRun(playerId, ref, store);
    }, world);

    // 5. Deferred operations (Discord checks, vote rewards) after HUD
    HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
        discordLinkStore.checkDiscordReady(playerId, ref);
        voteManager.checkVoteReady(playerId, ref);
    }, 2, TimeUnit.SECONDS);
}
```

### Pattern (Ascend)

```java
// ParkourAscendPlugin — inline handler with world-thread async block
this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
    try {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) return;
        World world = ref.getStore().getExternalData().getWorld();
        if (!isAscendWorld(world)) return;  // World gate

        UUID playerId = playerRef.getUuid();
        playersInAscendWorld.add(playerId);

        CompletableFuture.runAsync(() -> {
            hudManager.loadHudHiddenFromStore(playerId);
            hudManager.attach(playerRef, player);
            // ... restore active sessions, apply speed, etc.
        }, world).orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> {
            LOGGER.atWarning().withCause(ex).log("Exception in PlayerReadyEvent async task");
            return null;
        });
    } catch (Exception e) {
        LOGGER.atWarning().log("Exception in PlayerReadyEvent (ascend): " + e.getMessage());
    }
});
```

### Key Rules

- **Load settings before HUD attach** — prevents a frame of default state showing then flickering to saved state
- **World-gate early** — check `isXxxWorld(world)` before doing module-specific work
- **Async with timeout** — use `orTimeout()` on world-thread futures to prevent indefinite hangs
- **Deferred checks after HUD** — Discord link status, vote rewards, and similar checks can wait a few seconds
