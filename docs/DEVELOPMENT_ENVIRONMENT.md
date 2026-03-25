# Development Environment

Canonical reference for the Hyvexa development setup. All other docs use `mods/...` logical paths — this page has the concrete details.

## Layout

| What | Where | Filesystem |
|------|-------|------------|
| Source code + repo | `~/dev/Hytale/hyvexa_plugin/` (WSL2) | ext4 |
| Server runtime (`run/`) | `~/dev/Hytale/hyvexa_plugin/run/` (WSL2) | ext4 |
| Assets.zip | `~/dev/Hytale/assets/Assets.zip` (WSL2) | ext4 |
| HytaleServer.jar | `libs/HytaleServer.jar` (in repo) | ext4 |
| Hytale install (Windows) | `C:\Users\<user>\AppData\Roaming\Hytale\` | NTFS |

Everything runs on native Linux ext4 for performance. The Windows Hytale install is only the source for `sync-hytale.sh`.

## Build & Deploy

All commands run from the repo root in WSL2:

```bash
./gradlew build            # Compile + shade all modules
./gradlew stagePlugins     # Copy JARs to run/mods/ (default)
./gradlew collectPlugins   # Copy JARs to build/libs (CI-friendly)
./gradlew test             # Run unit tests
```

### stagePlugins target directory

By default, `stagePlugins` copies plugin JARs to `run/mods/` (ext4). This can be overridden via `~/.gradle/gradle.properties`:

```properties
stageModsDir=/some/other/path
```

This is a per-machine setting and is **not committed** to the repository.

## Syncing from Windows

After a Hytale update, run `./sync-hytale.sh` from the repo root to copy the latest `Assets.zip` and `HytaleServer.jar` from the Windows Hytale install to WSL2 ext4.

Third-party mods and their configs live in `run/mods/` and must be copied manually if updated.

## Runtime Paths

The server's working directory is `run/`. All runtime paths are relative to it:

| Logical path | Purpose |
|--------------|---------|
| `mods/Parkour/database.json` | MySQL credentials (gitignored) |
| `mods/Parkour/ascend.properties` | Ascend runtime flags |
| `mods/Parkour/tebex.json` | Tebex secret key (gitignored) |
| `mods/Hyguns/` | HyGuns modpack (weapons, ammo, assets) |

```
run/
  auth.enc
  config.json
  permissions.json
  mods/
    Parkour/
      database.json
      ascend.properties
      tebex.json
    Hyguns/
      ...
    HyvexaParkour-1.1.0.jar   (built by stagePlugins)
    hylograms-1.0.7.jar        (third-party)
    ...
```

The `run/` directory is gitignored. It contains credentials, runtime data, and third-party binaries.

## IntelliJ Setup

The `hyvexa-launch` module provides a classpath anchor for running the Hytale server from IntelliJ:

- Use the `HytaleServerWSL` run configuration (manual, points to ext4 paths)
- Or the generated `HytaleServerLaunch` config (from `hyvexa-launch/build.gradle`)
- Both use `run/` as working directory and `libs/HytaleServer.jar` on the classpath
- The pre-run task `stagePlugins` builds and copies all plugin JARs to `run/mods/`
