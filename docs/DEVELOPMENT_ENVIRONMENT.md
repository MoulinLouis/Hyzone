# Development Environment

Canonical reference for the Hyvexa development setup. All other docs use `mods/...` logical paths — this page has the concrete details.

## Hybrid Layout

| What | Where | Filesystem |
|------|-------|------------|
| Source code + repo | `~/dev/Hytale/hyvexa_plugin/` (WSL2) | ext4 |
| Hytale install + server runtime | `C:\Users\<user>\hyvexa_server\` (Windows) | NTFS |

The repo lives on native Linux ext4 for fast builds. The Hytale server runs on Windows because it is a Windows binary.

## Build & Deploy

All commands run from the repo root in WSL2:

```bash
./gradlew build            # Compile + shade all modules
./gradlew stagePlugins     # Copy JARs to stageModsDir (see below)
./gradlew collectPlugins   # Copy JARs to build/libs (CI-friendly, no Windows dependency)
./gradlew test             # Run unit tests
```

### stagePlugins target directory

`stagePlugins` copies plugin JARs to the directory set in `~/.gradle/gradle.properties`:

```properties
stageModsDir=/mnt/c/Users/<user>/hyvexa_server/mods
```

This is a per-machine setting and is **not committed** to the repository. Each developer configures it to point at their local Hytale server `mods/` directory.

`collectPlugins` has no external dependency — it always copies to `build/libs`.

## Runtime Paths

The Hytale server's working directory is where the server binary runs from. All runtime paths are relative to that directory:

| Logical path | Purpose |
|--------------|---------|
| `mods/Parkour/database.json` | MySQL credentials (gitignored) |
| `mods/Parkour/ascend.properties` | Ascend runtime flags |
| `mods/Parkour/tebex.json` | Tebex secret key (gitignored) |
| `mods/Hyguns/` | HyGuns modpack (weapons, ammo, assets) |

**Concrete example (Windows):**

```
C:\Users\<user>\hyvexa_server\
  mods\
    Parkour\
      database.json
      ascend.properties
      tebex.json
    Hyguns\
      manifest.json
      ...
  HytaleServer.jar
```

All other documentation uses `mods/...` as a logical path relative to the server working directory.

## IntelliJ Setup

The `hyvexa-launch` module provides a classpath anchor for running the Hytale server from IntelliJ:

- Use the `HytaleServerLaunch` run configuration (generated from `hyvexa-launch`)
- Or set an existing `HytaleServer` config to module `hyvexa-launch.main`
- The Hytale server JAR is resolved from `%USERPROFILE%/AppData/Roaming/Hytale/install/<patchline>/package/game/latest/Server/HytaleServer.jar` where `<patchline>` is set in `gradle.properties`
