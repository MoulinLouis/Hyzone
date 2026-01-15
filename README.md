# Parkour Plugin (Hytale)

A Hytale server plugin that provides a full parkour course experience with
course selection, leaderboards, admin course management, and a live run timer HUD.

## Features
- Course selection UI via `/pk` (category-first).
- Leaderboards via `/pk leaderboard` (global + per-course best times).
- Admin management via `/pkadmin` (create, set start/finish, checkpoints, rename, delete, category).
- Run flow: checkpoints, finish detection, dead-zone respawn below Y=300, best-time persistence.
- Run items: **Reset current course** and **Leave course**.
- Menu items: **Select a level** and **Leaderboards**.
- Player-player collision disabled on connect; item drops blocked for non-OP players.
- Lightweight HUD timer shown while running a course.

## Commands
- `/pk` - Open the course selector.
- `/pk leaderboard` - Open leaderboard menu.
- `/pkadmin` - Manage courses.
- `/pkitem` - Give menu items.

## Project layout
- Java sources: `src/main/java`.
- Plugin package: `io.parkour.plugins.parkour`.
- Entrypoint: `src/main/java/io/parkour/plugins/parkour/ExamplePlugin.java`.
- Plugin manifest: `src/main/resources/manifest.json`.
- UI assets: `src/main/resources/Common/UI/Custom/Pages`.
- Server assets/interactions: `src/main/resources/Server/...`.

## Build & run
- Hytale server jar is expected at:
  `%USERPROFILE%/AppData/Roaming/Hytale/install/<patchline>/package/game/latest/Server/HytaleServer.jar`
- Build: `./gradlew build` (Windows: `gradlew.bat build`).
- Run from IDEA: use the `HytaleServer` run config (creates `run/` directory).

## Manifest/versioning
- `manifest.json` fields `Main`, `Group`, `Name`, `Description` should match this plugin.
- `Version` and `IncludesAssetPack` are written by Gradle from `gradle.properties`.

## Notes
- Parkour data is stored at runtime under `Parkour/`.
- JSON stubs may be placed in the repo root for testing (e.g., `Courses.json`, `Progress.json`).

## License
- Internal project; add a license if you plan to distribute.
