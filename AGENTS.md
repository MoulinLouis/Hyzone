# AGENTS.md

Instructions for AI agents working on this Hytale plugin codebase.

## Workflow

- Update `CHANGELOG.md` with a short note describing each completed feature
- Follow existing code patterns before introducing new abstractions
- Build/test runs are handled by the project owner (do not run `./gradlew build`)

## Project Structure

### Source Layout
- **Java sources**: `src/main/java/io/hyvexa/`
- **Plugin entrypoint**: `HyvexaPlugin.java` (extends `JavaPlugin`)
- **Parkour code**: `io.hyvexa.parkour.*` subpackages
- **Commands**: `io.hyvexa.parkour.command/` - extend `CommandBase`
- **Data stores**: `io.hyvexa.parkour.data/` - JSON persistence
- **UI pages**: `io.hyvexa.parkour.ui/` - extend `InteractiveCustomUIPage`
- **Interactions**: `io.hyvexa.parkour.interaction/` - right-click handlers

### Resources
- **Plugin manifest**: `src/main/resources/manifest.json`
- **UI definitions**: `src/main/resources/Common/UI/Custom/Pages/*.ui`
- **Server assets**: `src/main/resources/Server/...`
- **Audio**: `src/main/resources/Common/Sounds/`, `Common/Music/`

### Runtime Data
- **Location**: `Parkour/` directory (created at runtime)
- **Files**: `Maps.json`, `Progress.json`, `Settings.json`, `PlayerCounts.json`
- `Progress.json` stores last-known player names for admin display

## Current Features

### Player Commands
| Command | Function |
|---------|----------|
| `/pk` | Category-first map selector |
| `/pk leaderboard` | Global + per-map best times |
| `/pk stats` | Player XP, level, rank |
| `/pk items` | Give menu items |

### Admin Commands (OP only)
| Command | Function |
|---------|----------|
| `/pk admin` | Map management UI (create, edit, delete) |
| `/pk admin` | Player progress management (view, reset) |
| `/pk admin` | Settings (fall respawn, category order) |

### Runtime Behavior
- Checkpoint/finish detection with radius-based triggers
- Fall respawn after configurable timeout (returns to checkpoint or start)
- Completion persistence with best-time tracking
- Inventory swaps between run items (Reset/Checkpoint/Leave) and menu items
- Player collision disabled via `HitboxCollision` removal
- Item drops blocked for non-OP players

## Build and Run

### Build
```bash
./gradlew build          # Linux/Mac
gradlew.bat build        # Windows
```

### Server Location
Hytale server JAR expected at:
```
%USERPROFILE%/AppData/Roaming/Hytale/install/<patchline>/package/game/latest/Server/HytaleServer.jar
```
The `patchline` value comes from `gradle.properties`.

### Run
Use the `HytaleServer` IntelliJ run config (defined in `build.gradle`). Launches from `run/` directory.

### Output
JAR name: `HyvexaPlugin-<version>.jar` (version from `gradle.properties`)

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
public class MyCommand extends CommandBase {
    @Override
    public void execute(CommandSource source, String[] args) {
        // Implementation
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

### Data Storage
```java
public class MyStore extends BlockingDiskFile {
    public MyStore() {
        super(Path.of("Parkour/MyData.json"));
    }
    // Use syncLoad() on startup, syncSave() after changes
}
```

## Common Gotchas

1. **Entity refs expire** - Always check `ref.isValid()` before use
2. **World thread required** - Use `CompletableFuture.runAsync(..., world)` for entity/world operations
3. **UI paths** - Use forward slashes: `"Common/UI/Custom/Pages/MyPage.ui"`
4. **Runtime data** - JSON files go in `Parkour/` at runtime, not in resources
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
| `com.hypixel.hytale.server.core.util.io` | Persistence (BlockingDiskFile) |
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
| `BlockingDiskFile` | `*Store` classes | JSON file persistence |
| `AbstractAsyncCommand` | All commands | Async command execution |
| `InteractiveCustomUIPage` | All UI pages | Interactive UI framework |
| `Teleport` component | `RunTracker` | Player teleportation |
| `TransformComponent` | `RunTracker` | Position/rotation access |
| `HudManager` | `HyvexaPlugin` | Custom HUD attachment |
| `Interaction` codec | `*Interaction` classes | Right-click item handlers |
| `HitboxCollision` | `HyvexaPlugin` | Player collision control |

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
4. Add UI files in `src/main/resources/Common/UI/Custom/Pages/` if needed
5. Update `CHANGELOG.md` with feature description
6. Test with `./gradlew build` and server run

## Adding New Plugins

1. Create plugin entry class extending `JavaPlugin`
2. Update `manifest.json` `Main` to the fully-qualified class name
3. Register commands and listeners in `setup()`
4. Place resources under `src/main/resources/Server/...`

## References

- See `ParkourCommand.java` for command with subcommands
- See `MapSelectPage.java` for complete UI page example
- See `MenuInteraction.java` for interaction pattern
- See `MapStore.java` for data persistence pattern
- See `docs/ARCHITECTURE.md` for threading model and edge cases
