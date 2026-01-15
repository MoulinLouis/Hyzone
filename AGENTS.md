# AGENTS.md

This file tells Codex how to work in this repo when asked to create or modify a Hytale plugin.

## Project workflow
- Any code agent working on this project should update `CHANGELOG.md` with a short note describing each completed feature.

## Project map
- Java sources live under `src/main/java`. Current plugin package is `io.hyvexa` with parkour code under `io.hyvexa.parkour`.
- Plugin entrypoint is `HyvexaPlugin` (extends `JavaPlugin`) in `src/main/java/io/hyvexa/HyvexaPlugin.java`.
- Commands are `CommandBase` subclasses in `io.hyvexa.parkour.command` and are registered in `HyvexaPlugin.setup()`.
- Plugin resources live in `src/main/resources`. The plugin manifest is `src/main/resources/manifest.json`.
- Optional asset/data files live under `src/main/resources/Server/...`.
- Audio assets live under `src/main/resources/Common/Sounds` and `src/main/resources/Common/Music`, with sound events in `src/main/resources/Server/Audio/SoundEvents` and ambience overrides in `src/main/resources/Server/Audio/AmbienceFX`.
- Parkour data files live under `Parkour/` at runtime and JSON stubs may be placed in repo root for testing (e.g., `Maps.json`, `Progress.json`).
- `Progress.json` stores last-known player names under `name` for admin display; online names resolve via `PlayerRef` when available.

## Current features (Parkour plugin)
- `/pk` opens a category-first map selector; `/pk leaderboard` opens a leaderboard menu (global + per-map best times).
- `/pk stats` shows player XP/level, achievements, and titles.
- `/pkadmin` manages maps (create, set start/finish, add checkpoints, update category/name, delete; categories saved per map). `/parkourmap` command removed.
- `/pkadmin` includes a Player Progress page that lists players found in `Progress.json` and can reset a selected player.
- Map runtime: checkpoint/finish detection, fall respawn, completion + best-time persistence, teleport to spawn on finish.
- Right-click items: “Select a level” opens `/pk`, “Leaderboards” opens leaderboard menu (given via `/pkitem`).
- Player-player collision is disabled by removing `HitboxCollision` on connect.
- Item dropping is blocked for non-OP players; OPs can drop items normally.
- Inventory is cleared on map start/leave/finish and swaps between run items (Reset/Leave) and menu items (Select/Leaderboard) for all players.
- Run HUD: lightweight timer shown during active runs; UI lives in `src/main/resources/Common/UI/Custom/Pages/Parkour_RunHud.ui`.

## Manifest and versioning
- `manifest.json` fields `Main`, `Group`, `Name`, and `Description` should match the plugin you are building.
- `Version` and `IncludesAssetPack` are overwritten by the Gradle task `updatePluginManifest` using values from `gradle.properties` (`version` and `includes_pack`). Edit `gradle.properties` instead of hardcoding those values.

## Build and run
- Hytale server JAR is expected at `%USERPROFILE%/AppData/Roaming/Hytale/install/<patchline>/package/game/latest/Server/HytaleServer.jar` (`patchline` comes from `gradle.properties`).
- Local example path from this machine: `C:\Users\User\AppData\Roaming\Hytale\install\release\package\game\latest\Server\HytaleServer.jar`.
- Build with `./gradlew build` (Windows: `gradlew.bat build`).
- The Gradle IDEA run config `HytaleServer` (defined in `build.gradle`) launches the server with your plugin from the `run/` directory.
- The jar output name is `HyvexaPlugin-<version>.jar` using `version` from `gradle.properties`.

## HytaleServer.jar exploration tips
- Prefer API patterns already used in this repo (`CustomUI`, `InteractiveCustomUIPage`, `UICommandBuilder`, `PlayerRef`, `Teleport`).
- Only inspect decompiled `HytaleServer.jar` sources when blocked; search by class/method names from stack traces.
- For UI properties, cross-check existing `.ui` files under `src/main/resources/Common/UI/Custom/Pages`.
- Follow existing async/world-thread patterns (commands use `CompletableFuture.runAsync(..., world)`).

## Key Hytale API patterns (reference existing code for examples)

### Entity-Component System (ECS)
Hytale uses an ECS pattern. Key classes:
- `PlayerRef` - Reference to a player, use `getReference()` to get the entity ref
- `Ref<EntityStore>` - Entity reference, check `isValid()` before use
- `Store<EntityStore>` - Access components via `store.getComponent(ref, ComponentType.getComponentType())`
- `Player` - Player component with inventory, HUD, etc.
- `TransformComponent` - Position/rotation data

### Threading model
- Commands run on calling thread; use `CompletableFuture.runAsync(() -> { ... }, world)` for world operations
- Get world via `store.getExternalData().getWorld()`
- Scheduled tasks use `HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(...)`

### Commands
- Extend `CommandBase`, implement `execute()` method
- Register in `HyvexaPlugin.setup()` via `getCommandRegistry().registerCommand(new YourCommand())`
- See `ParkourCommand.java` for a full example with subcommands

### UI Pages
- Extend `InteractiveCustomUIPage`, call `super()` with UI file path
- UI files live in `src/main/resources/Common/UI/Custom/Pages/*.ui`
- Open pages via `CustomUI.open(playerRef, new YourPage(...))`
- Use `UICommandBuilder` to send commands from UI to Java
- See `MapSelectPage.java` for a full example

### Interactions (right-click items)
- Create interaction class implementing `Interaction`, with a `CODEC`
- Register in `setup()`: `getCodecRegistry(Interaction.CODEC).register("Name", YourInteraction.class, YourInteraction.CODEC)`
- Create JSON in `src/main/resources/Server/Item/Interactions/` referencing the interaction
- See `MenuInteraction.java` and `Parkour_Menu_Interaction.json`

### Data storage
- Use JSON files in `Parkour/` directory at runtime
- See `MapStore.java`, `ProgressStore.java`, and `SettingsStore.java` for load/save patterns
- `Settings.json` stores global parkour config (spawn position + dead-zone Y)
- Use `syncLoad()` on startup, `syncSave()` after changes

## Common gotchas
- Always check `ref.isValid()` before using entity refs - players can disconnect
- World operations must run on world thread via `CompletableFuture.runAsync(..., world)`
- UI page paths use forward slashes: `"Common/UI/Custom/Pages/MyPage.ui"`
- JSON data files go in `Parkour/` at runtime, not in resources
- Inventory operations need the `Player` component, not just `PlayerRef`
- Event handlers registered in `setup()` via `getEventRegistry().registerGlobal(...)`
- Use `@Nonnull` annotations where appropriate (follow existing patterns)

## Expected workflow for new plugins
1. Create or rename the plugin entry class in `src/main/java` (extend `JavaPlugin`).
2. Update `manifest.json` `Main` to the fully-qualified class name of the new entrypoint.
3. Register commands and listeners inside `setup()`.
4. Add command classes as `CommandBase` subclasses and register them.
5. If adding resources or recipes, place them under `src/main/resources/Server/...` and keep paths consistent.

## Expected workflow for new features
1. Check existing code for similar patterns before implementing.
2. Create new classes in appropriate subpackages (`data/`, `interaction/`, `system/`, `util/`).
3. Register any new commands, interactions, or systems in `HyvexaPlugin.setup()`.
4. Add UI files if needed in `src/main/resources/Common/UI/Custom/Pages/`.
5. Update `CHANGELOG.md` with a short description of the feature.
6. Test with `./gradlew build` and run the server.
