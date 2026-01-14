# AGENTS.md

This file tells Codex how to work in this repo when asked to create or modify a Hytale plugin. Ignore README.md because it is sample text.

## Project workflow
- Any code agent working on this project should update `CHANGELOG.md` with a short note describing each completed feature.

## Project map
- Java sources live under `src/main/java`. Current plugin package is `io.parkour.plugins.parkour`.
- Plugin entrypoint is `ExamplePlugin` (extends `JavaPlugin`) in `src/main/java/io/parkour/plugins/parkour/ExamplePlugin.java`.
- Commands are `CommandBase` subclasses in the same package and are registered in `ExamplePlugin.setup()`.
- Plugin resources live in `src/main/resources`. The plugin manifest is `src/main/resources/manifest.json`.
- Optional asset/data files live under `src/main/resources/Server/...`.

## Manifest and versioning
- `manifest.json` fields `Main`, `Group`, `Name`, and `Description` should match the plugin you are building.
- `Version` and `IncludesAssetPack` are overwritten by the Gradle task `updatePluginManifest` using values from `gradle.properties` (`version` and `includes_pack`). Edit `gradle.properties` instead of hardcoding those values.

## Build and run
- Hytale server JAR is expected at `%USERPROFILE%/AppData/Roaming/Hytale/install/<patchline>/package/game/latest/Server/HytaleServer.jar` (`patchline` comes from `gradle.properties`).
- Build with `./gradlew build` (Windows: `gradlew.bat build`).
- The Gradle IDEA run config `HytaleServer` (defined in `build.gradle`) launches the server with your plugin from the `run/` directory.

## Expected workflow for new plugins
1. Create or rename the plugin entry class in `src/main/java` (extend `JavaPlugin`).
2. Update `manifest.json` `Main` to the fully-qualified class name of the new entrypoint.
3. Register commands and listeners inside `setup()`.
4. Add command classes as `CommandBase` subclasses and register them.
5. If adding resources or recipes, place them under `src/main/resources/Server/...` and keep paths consistent.
