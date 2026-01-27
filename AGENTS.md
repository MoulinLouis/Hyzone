# AGENTS.md

Instructions for AI agents working on this Hytale plugin codebase.

## Workflow

- Update `CHANGELOG.md` with a short note describing each completed feature
- Follow existing code patterns before introducing new abstractions
- Build/test runs are handled by the project owner (do not run `./gradlew build`)

## Project Structure

### Source Layout
- **Parkour Java sources**: `hyvexa-parkour/src/main/java/io/hyvexa/`
- **Core Java sources**: `hyvexa-core/src/main/java/io/hyvexa/`
- **Plugin entrypoint**: `hyvexa-parkour/src/main/java/io/hyvexa/HyvexaPlugin.java` (extends `JavaPlugin`)
- **Parkour code**: `io.hyvexa.parkour.*` subpackages (in `hyvexa-parkour`)
- **Common utilities**: `io.hyvexa.common.*` (mostly in `hyvexa-core`; `InventoryUtils` stays in `hyvexa-parkour` because it references parkour/duel types)
- **Commands**: `io.hyvexa.parkour.command/` - extend `AbstractAsyncCommand` or `AbstractPlayerCommand`
- **Data stores**: `io.hyvexa.parkour.data/` - MySQL persistence with in-memory caching
- **UI pages**: `io.hyvexa.parkour.ui/` - extend `InteractiveCustomUIPage` or `BaseParkourPage`
- **Interactions**: `io.hyvexa.parkour.interaction/` - right-click handlers
- **Systems**: `io.hyvexa.parkour.system/` - event filtering systems (NoDropSystem, NoBreakSystem, etc.)
- **Tracker**: `io.hyvexa.parkour.tracker/` - run tracking, HUD management
- **Visibility**: `io.hyvexa.parkour.visibility/` - player visibility management

### Manager Layer
- **HudManager**: Player HUD lifecycle (timer, checkpoints, leaderboard)
- **AnnouncementManager**: HUD announcements + scheduled chat broadcasts
- **PlayerPerksManager**: VIP speed, nameplates, rank caching
- **PlaytimeManager**: Session tracking, player counts
- **PlayerCleanupManager**: Disconnect cleanup, stale state sweeps

Managers are instantiated in `HyvexaPlugin.setup()` and accessed via plugin helpers.

### Resources
- **Plugin manifest**: `hyvexa-parkour/src/main/resources/manifest.json`
- **UI definitions**: `hyvexa-parkour/src/main/resources/Common/UI/Custom/Pages/*.ui`
- **Server assets**: `hyvexa-parkour/src/main/resources/Server/...`
- **Audio**: `hyvexa-parkour/src/main/resources/Common/Sounds/`, `Common/Music/`

### Docs
- **ARCHITECTURE.md**: System overview, threading model, and runtime flow
- **DATABASE.md**: MySQL schema reference and runtime data notes

### Runtime Data
- **Working dir**: Server runs from `run/`, so runtime data lives at `mods/Parkour/`
- **Database config**: `mods/Parkour/database.json` (MySQL credentials, gitignored)
- **Source of truth**: Parkour data is stored in MySQL and loaded into memory on startup
- **/dbmigrate JSON inputs** (required, no fallback): `Settings.json`, `GlobalMessages.json`,
  `PlayerCounts.json`, `Progress.json`, `Maps.json` in `mods/Parkour/`
- **World names**: Hub routing expects capitalized worlds: `Hub`, `Parkour`, `Ascend`

## Current Features

### Player Commands
| Command | Function |
|---------|----------|
| `/pk` | Category-first map selector |
| `/pk leaderboard` | Global + per-map best times |
| `/pk stats` | Player XP, level, rank |
| `/pk items` | Give menu items |
| `/cp [set\|clear]` | Save/teleport to personal checkpoint (memory-only) |
| `/discord` | Display Discord server link |
| `/store` | Display store link |
| `/menu` | Open hub mode selector |

### Ascend (Current)
- `/ascend` opens Ascend map selector UI (manual runs).
- `/ascend collect` collects pending Ascend coins.
- `/ascend stats` shows current Ascend coin balance.
- Hub menu Ascend button is OP-only (non-OP players see "coming soon" message).
- Ascend world clears inventory and gives the hub Server Selector in the last hotbar slot.
- Ascend world shows a minimal HUD label: "HYVEXA ASCEND".

### Ascend Admin (OP only)
- `/as admin` opens Ascend map admin UI.
- `/as admin map <create|setstart|setfinish|addwaypoint|clearwaypoints|setreward|list>` for map setup.

### Admin Commands (OP only)
| Command | Function |
|---------|----------|
| `/pk admin` | Open admin panel UI (Maps, Players, Settings, Playtime, Population) |
| `/pk admin rank give <player> <vip\|founder>` | Grant VIP or Founder rank |
| `/pk admin rank remove <player> <vip\|founder>` | Remove VIP or Founder rank |
| `/pk admin rank broadcast <player> <vip\|founder>` | Broadcast rank announcement to server |
| `/pkadminitem` | Give admin remote control item (opens player settings) |

### Maintenance Commands (OP only)
| Command | Function |
|---------|----------|
| `/dbtest` | Validate MySQL connection and core tables |
| `/dbclear` | Clear all parkour tables (restart to reset in-memory caches) |
| `/pkmusic` | Debug command to list loaded ambience music assets |

### Admin UI Features
The `/pk admin` panel provides access to:
- **Maps**: Create, edit, delete maps; set categories, difficulties, XP rewards, start/checkpoint/finish/leave positions and triggers; toggle free-fall mode, mithril daggers
- **Players**: Search and view all players; detailed per-player stats with map progress; teleport, kill, grant/revoke flight, reset inventory, clear progress per map
- **Settings**: Fall respawn timeout, void cutoff Y, idle fall for OPs, weapon damage toggle, teleport debug, rank thresholds, category order, global announcement
- **Playtime**: View total playtime per player with search and pagination
- **Population**: View online player count history with 10-minute sampling intervals
- **Global Messages**: Configure server-wide announcement messages
- **Player Counts**: (Legacy feature for map-specific counts)

### World-Based HUD System
Each module's HUD is determined by which world the player is in:
- **Hub world** → Hub HUD with "HYVEXA HUB" label
- **Parkour world** → Parkour HUD with timer, checkpoints, leaderboard
- **Ascend world** → Ascend HUD with "HYVEXA ASCEND" label

No mode persistence is needed - players always spawn in Hub world on reconnect. Each plugin checks the player's current world and attaches the appropriate HUD.

### Runtime Behavior
- Checkpoint/finish detection with radius-based triggers
- Checkpoint split times stored on new personal best runs (player_checkpoint_times)
- Fall respawn after configurable timeout (returns to checkpoint or start, optional per-map)
- Void cutoff respawn at configured Y level (always active)
- Completion persistence with best-time tracking
- Inventory swaps between run items (Reset/Checkpoint/Leave) and menu items
- Player collision disabled via `HitboxCollision` removal
- Item drops blocked for non-OP players
- Block breaking blocked for non-OP players
- Player damage disabled (global god mode)
- Player knockback disabled
- Weapon damage disabled (configurable in admin settings)
- Run timer starts on first movement after map start
- VIP/Founder rank perks: chat tags, nameplates, speed multipliers (x1/x2/x4)
- Player settings: music controls, HUD visibility, speed boost, SFX toggles
- Welcome UI shown on first join
- Playtime tracking with periodic snapshots
- Population history tracking (10-minute intervals)

### Hotbar Interactions
Players receive right-click items that open UIs or perform actions:
- **Menu Item** (Candy Cane) - Opens `/pk` map selector
- **Leaderboard Item** (Trophy) - Opens `/pk leaderboard`
- **Stats Item** (Candy Cane Stick) - Opens `/pk stats`
- **Player Settings Item** (Remote Control) - Opens player settings (music, HUD, speed)
- **Reset Item** (during run) - Restart map from beginning
- **Restart-to-Checkpoint Item** (during run, after first checkpoint) - Restart from last checkpoint
- **Leave Item** (during run) - Exit map and return to spawn/leave position

## Build and Run

### Build
```bash
./gradlew build          # Linux/Mac
gradlew.bat build        # Windows
```
Build outputs a shaded plugin JAR that bundles runtime dependencies.
Use `./gradlew stagePlugins` to copy all plugin jars into `run/mods` for local testing.
Use `./gradlew collectPlugins` to copy all plugin jars into `build/libs` for production packaging.

### Server Location
Hytale server JAR expected at:
```
%USERPROFILE%/AppData/Roaming/Hytale/install/<patchline>/package/game/latest/Server/HytaleServer.jar
```
The `patchline` value comes from `gradle.properties`.

### Run
Use the `HytaleServer` IntelliJ run config (defined in `hyvexa-parkour/build.gradle`). Launches from `run/` directory.

### Output
JAR name: `HyvexaParkour-<version>.jar` (version from `gradle.properties`)
Additional modules output `HyvexaHub-<version>.jar` and `HyvexaParkourAscend-<version>.jar`.

## Hytale API Patterns

### Entity-Component System (ECS)
```java
PlayerRef playerRef = ...;
Ref<EntityStore> ref = playerRef.getReference();
if (ref == null || !ref.isValid()) return;  // Always check validity

Store<EntityStore> store = ref.getStore();
Player player = store.getComponent(ref, Player.getComponentType());
TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
```

### Threading Model
```java
// Commands run on calling thread - use CompletableFuture for world operations
World world = store.getExternalData().getWorld();
CompletableFuture.runAsync(() -> {
    // World operations here
}, world);

// Scheduled tasks
HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::tick, 200, 200, TimeUnit.MILLISECONDS);
```

### Commands
```java
public class MyCommand extends AbstractAsyncCommand {
    @Override
    public CompletableFuture<Void> executeAsync(CommandContext context) {
        // Implementation
        return CompletableFuture.completedFuture(null);
    }
}
// Register in HyvexaPlugin.setup():
getCommandRegistry().registerCommand(new MyCommand());
```

### UI Pages
```java
public class MyPage extends InteractiveCustomUIPage {
    public MyPage(PlayerRef playerRef) {
        super("Common/UI/Custom/Pages/MyPage.ui");  // Forward slashes required
    }
}
// Open via:
CustomUI.open(playerRef, new MyPage(playerRef));
```

### Interactions (Right-Click Items)
```java
public class MyInteraction implements Interaction {
    public static final Codec<MyInteraction> CODEC = ...;
}
// Register in setup():
getCodecRegistry(Interaction.CODEC).register("My_Interaction", MyInteraction.class, MyInteraction.CODEC);
// Create JSON in src/main/resources/Server/Item/Interactions/
```

### Systems (Event Filters)
Systems modify player behavior by filtering events. Current systems:
```java
// NoDropSystem - blocks item drops for non-OP players
// NoBreakSystem - blocks block breaking for non-OP players
// NoPlayerDamageSystem - blocks all player damage (god mode)
// NoPlayerKnockbackSystem - blocks player knockback
// NoWeaponDamageSystem - blocks weapon damage (configurable)
// PlayerVisibilityFilterSystem - manages player visibility filters
```
Register systems in `setup()` or defer to avoid blocking on module initialization.

### Data Storage (MySQL)
```java
public class MyStore {
    private final ConcurrentHashMap<UUID, MyData> data = new ConcurrentHashMap<>();

    public void syncLoad() {
        // Load from MySQL into memory on startup
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM my_table")) {
            // Populate in-memory map
        }
    }

    public void saveToDatabase(MyData item) {
        // Write to MySQL on changes
        String sql = "INSERT INTO my_table (...) VALUES (?) ON DUPLICATE KEY UPDATE ...";
        // Execute
    }
}
```

**Pattern**: Memory-first with MySQL persistence
- Load all data into `ConcurrentHashMap` on startup via `syncLoad()`
- All reads come from memory (fast)
- Writes update memory + persist to MySQL (debounced for high-frequency updates)

## Common Gotchas

1. **Entity refs expire** - Always check `ref.isValid()` before use
2. **World thread required** - Use `CompletableFuture.runAsync(..., world)` for entity/world operations
3. **UI paths** - Use forward slashes: `"Common/UI/Custom/Pages/MyPage.ui"`
4. **Runtime data** - DB credentials live in `mods/Parkour/database.json`; parkour data is in MySQL
5. **Inventory access** - Need `Player` component, not just `PlayerRef`
6. **Event registration** - Use `getEventRegistry().registerGlobal(...)` in `setup()`
7. **Null annotations** - Use `@Nonnull` following existing patterns

## Hytale API Discovery

When implementing features that require Hytale APIs not yet used in this project, explore the server JAR directly.

### Server JAR Location
```
%USERPROFILE%/AppData/Roaming/Hytale/install/<patchline>/package/game/latest/Server/HytaleServer.jar
```

To find the `<patchline>` value:
1. Check `gradle.properties` for the configured patchline
2. Or navigate to `%USERPROFILE%/AppData/Roaming/Hytale/install/` and use the most recent version folder

### Key Packages to Explore
| Package | Purpose |
|---------|---------|
| `com.hypixel.hytale.server.core.entity` | Entity components, refs, stores |
| `com.hypixel.hytale.server.core.entity.component` | Built-in components (Player, Transform, Teleport) |
| `com.hypixel.hytale.server.core.world` | World operations, block access |
| `com.hypixel.hytale.server.core.command` | Command framework (AbstractAsyncCommand) |
| `com.hypixel.hytale.server.core.ui` | UI pages, HUD management |
| `com.hypixel.hytale.server.core.util.io` | File utilities |
| `com.hypixel.hytale.server.core.codec` | Serialization codecs |
| `com.hypixel.hytale.server.core.event` | Event system |

### Decompilation Tools
Use any of these to explore the JAR:
- **IntelliJ IDEA** - Built-in decompiler (just open the JAR)
- **jadx** - GUI decompiler with search
- **cfr** - Command-line decompiler

### Discovered APIs in This Project
| API Class | Used In | Purpose |
|-----------|---------|---------|
| `HikariCP` | `DatabaseManager` | MySQL connection pooling |
| `AbstractAsyncCommand` | All commands | Async command execution |
| `InteractiveCustomUIPage` | All UI pages | Interactive UI framework |
| `Teleport` component | `RunTracker` | Player teleportation |
| `TransformComponent` | `RunTracker` | Position/rotation access |
| `HudManager` | `HyvexaPlugin` | Custom HUD attachment |
| `Interaction` codec | `*Interaction` classes | Right-click item handlers |
| `HitboxCollision` | `HyvexaPlugin` | Player collision control |
| `MovementSettings` | `HyvexaPlugin` | Speed multiplier adjustments |
| `Nameplate` | `HyvexaPlugin` | Player nameplate customization |
| `SlotFilter` | `HyvexaPlugin` | Inventory action filtering |

### APIs Needing Exploration
- Particle effects and visual feedback
- Additional sound events
- Entity spawning / NPC creation
- Scoreboard / tab list customization
- Block placement and modification
- Custom item creation

## Adding New Features

1. Check existing code for similar patterns
2. Create classes in appropriate subpackage (`data/`, `ui/`, `command/`, etc.)
3. Register commands/interactions/systems in `HyvexaPlugin.setup()`
4. Add UI files in `hyvexa-parkour/src/main/resources/Common/UI/Custom/Pages/` if needed
5. Update `CHANGELOG.md` with feature description
6. Test with `./gradlew build` and server run

## Adding New Plugins

1. Create plugin entry class extending `JavaPlugin`
2. Update `manifest.json` `Main` to the fully-qualified class name
3. Register commands and listeners in `setup()`
4. Place resources under the target module, e.g. `hyvexa-parkour/src/main/resources/Server/...`

## References

- See `ParkourCommand.java` for command with subcommands (including rank management)
- See `MapSelectPage.java` for complete UI page example with pagination
- See `PlayerSettingsPage.java` for UI page with speed/music controls
- See `AdminPlayersPage.java` for search and pagination patterns
- See `MenuInteraction.java` for interaction pattern
- See `MapStore.java` for MySQL persistence pattern
- See `ProgressStore.java` for VIP/Founder rank management
- See `DatabaseManager.java` for connection pool management
- See `NoPlayerDamageSystem.java` for event filtering system pattern
- See `PlayerVisibilityManager.java` for player visibility management
- See `docs/ARCHITECTURE.md` for threading model and edge cases
- See `docs/DATABASE.md` for database schema reference
- See `docs/MYSQL_MIGRATION.md` for database schema and migration details
