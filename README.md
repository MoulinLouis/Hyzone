# Hyvexa Parkour Plugin (Hytale)

A Hytale server plugin providing a complete parkour experience with map selection, leaderboards, progression system, and admin tools.

## Features

- **Map Selection** - Category-first UI via `/pk` with Easy/Medium/Hard/Insane difficulties
- **Leaderboards** - Global and per-map best times via `/pk leaderboard`
- **Progression** - XP system with ranks (Bronze to Grandmaster) based on map completions
- **Admin Tools** - Full map/player management, settings, playtime tracking, population history via `/pk admin` (OP only)
- **VIP/Founder Ranks** - Purchasable ranks with chat tags, nameplates, and speed multipliers (x1/x2/x4)
- **Player Settings** - Music controls (multiple OSTs), HUD visibility toggle, speed boost, SFX toggles
- **Run HUD** - Live timer display during active runs with server info and support links
- **Auto-respawn** - Fall detection teleports players back to checkpoint/start (configurable per-map)
- **Void Protection** - Teleport back to checkpoint/start when falling below configured Y level
- **Collision-free** - Player-player collision disabled; item drops/block breaking blocked for non-OPs
- **God Mode** - Player damage and knockback disabled globally
- **Personal Checkpoints** - `/cp` command for setting personal checkpoints (memory-only, separate from map checkpoints)
- **Community Links** - `/discord` and `/store` commands for server links

## Commands

| Command | Description |
|---------|-------------|
| `/pk` | Open map selection menu |
| `/pk leaderboard` | Open leaderboard menu |
| `/pk stats` | View your progression (XP, rank, completions) |
| `/pk items` | Give yourself the menu items |
| `/cp [set\|clear]` | Save/teleport to personal checkpoint |
| `/discord` | Display Discord server link |
| `/store` | Display store link |
| `/pk admin` | Admin panel for map/player/settings management (OP only) |
| `/pk admin rank give <player> <vip\|founder>` | Grant VIP or Founder rank (OP only) |
| `/pk admin rank remove <player> <vip\|founder>` | Remove VIP or Founder rank (OP only) |
| `/pk admin rank broadcast <player> <vip\|founder>` | Broadcast rank announcement (OP only) |
| `/pkadminitem` | Give admin remote control item (OP only) |
| `/dbtest` | Test MySQL connection (OP only) |
| `/dbmigrate` | Migrate JSON data into MySQL (OP only) |
| `/dbclear` | Clear all parkour tables (OP only) |

## Quick Start

### Build
```bash
./gradlew build
# Windows: gradlew.bat build
```
Build produces a shaded plugin JAR that bundles runtime dependencies.

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
    common/                    # Shared utilities
    parkour/
      command/                 # Command handlers
      data/                    # MySQL persistence (MapStore, ProgressStore, etc.)
      tracker/                 # Run tracking and HUD
      ui/                      # Custom UI pages
      interaction/             # Right-click item handlers
      system/                  # Event filtering systems
      visibility/              # Player visibility management

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
- All runtime data is stored in MySQL and loaded into memory on startup
- Working directory is `run/`, so runtime config lives in `mods/Parkour/`
- DB config: `mods/Parkour/database.json` (gitignored)
- JSON files are only used by `/dbmigrate` and must all exist:
  `Settings.json`, `GlobalMessages.json`, `PlayerCounts.json`, `Progress.json`, `Maps.json`

## Documentation

- [docs/codex/AGENTS.md](docs/codex/AGENTS.md) - AI agent instructions for development
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) - Technical design and edge cases
- [docs/DATABASE.md](docs/DATABASE.md) - MySQL schema reference
- [CHANGELOG.md](CHANGELOG.md) - Version history

## License

Internal project. Add a license before distributing.
