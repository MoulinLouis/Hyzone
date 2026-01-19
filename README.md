# Hyvexa Parkour Plugin (Hytale)

A Hytale server plugin providing a complete parkour experience with map selection, leaderboards, progression system, and admin tools.

## Features

- **Map Selection** - Category-first UI via `/pk` with Easy/Medium/Hard/Insane difficulties
- **Leaderboards** - Global and per-map best times via `/pk leaderboard`
- **Progression** - XP system with ranks (Bronze to Grandmaster) based on map completions
- **Admin Tools** - Full map management via `/pk admin` (OP only)
- **Run HUD** - Live timer display during active runs
- **Auto-respawn** - Fall detection teleports players back to checkpoint/start
- **Collision-free** - Player-player collision disabled; item drops blocked for non-OPs

## Commands

| Command | Description |
|---------|-------------|
| `/pk` | Open map selection menu |
| `/pk leaderboard` | Open leaderboard menu |
| `/pk stats` | View your progression (XP, rank, completions) |
| `/pk admin` | Admin panel for map/settings management (OP only) |
| `/pk items` | Give yourself the menu items |

## Quick Start

### Build
```bash
./gradlew build
# Windows: gradlew.bat build
```

### Run
Use the `HytaleServer` IntelliJ run config, which launches from the `run/` directory.

### Server JAR Location
The Hytale server JAR is expected at:
```
%USERPROFILE%/AppData/Roaming/Hytale/install/<patchline>/package/game/latest/Server/HytaleServer.jar
```
Where `<patchline>` is configured in `gradle.properties`.

## Project Layout

```
src/main/java/
  io/hyvexa/
    HyvexaPlugin.java          # Plugin entrypoint
    parkour/
      command/                 # Command handlers
      data/                    # JSON persistence (MapStore, ProgressStore, etc.)
      tracker/                 # Run tracking and HUD
      ui/                      # Custom UI pages
      interaction/             # Right-click item handlers

src/main/resources/
  manifest.json                # Plugin manifest
  Common/UI/Custom/Pages/      # UI definition files
  Server/                      # Server-side assets and interactions
```

## Configuration

### Manifest
- `manifest.json` fields `Main`, `Group`, `Name`, `Description` should match your plugin
- `Version` and `IncludesAssetPack` are auto-populated by Gradle from `gradle.properties`

### Runtime Data
- All parkour data is stored in `Parkour/` at runtime
- Files: `Maps.json`, `Progress.json`, `Settings.json`, `PlayerCounts.json`

## Documentation

- [AGENTS.md](AGENTS.md) - AI agent instructions for development
- [ARCHITECTURE.md](ARCHITECTURE.md) - Technical design and edge cases
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines
- [CHANGELOG.md](CHANGELOG.md) - Version history

## License

Internal project. Add a license before distributing.
