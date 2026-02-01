# AGENTS.md

Instructions for AI agents working on this Hytale plugin codebase.

## Quick Start

For code patterns, API gotchas, and implementation details, see:
- `docs/CODE_PATTERNS.md` - Commands, UI, threading, ECS, NPC patterns
- `docs/HYTALE_API.md` - Hytale API gotchas and discovery
- `docs/ARCHITECTURE.md` - System overview and threading model
- `docs/DATABASE.md` - MySQL schema reference

## Workflow

- Update `CHANGELOG.md` with a short note describing each completed feature
- Write all documentation in English
- Follow existing code patterns before introducing new abstractions
- Build/test runs are handled by the project owner (do not run `./gradlew build`)

## Project Structure

### Modules

| Module | Purpose | Entry Point |
|--------|---------|-------------|
| `hyvexa-core` | Shared DB, utilities, mode state | N/A (library) |
| `hyvexa-parkour` | Main parkour gameplay | `io.hyvexa.HyvexaPlugin` |
| `hyvexa-parkour-ascend` | Ascend idle mode | `io.hyvexa.ascend.ParkourAscendPlugin` |
| `hyvexa-hub` | Hub routing + mode selection | `io.hyvexa.hub.HyvexaHubPlugin` |

### Source Layout
- **Parkour Java sources**: `hyvexa-parkour/src/main/java/io/hyvexa/`
- **Core Java sources**: `hyvexa-core/src/main/java/io/hyvexa/`
- **Commands**: `io.hyvexa.parkour.command/` - extend `AbstractAsyncCommand`
- **Data stores**: `io.hyvexa.parkour.data/` - MySQL persistence with in-memory caching
- **UI pages**: `io.hyvexa.parkour.ui/` - extend `InteractiveCustomUIPage`
- **Tracker**: `io.hyvexa.parkour.tracker/` - run tracking, HUD management

### Resources
- **Plugin manifest**: `hyvexa-*/src/main/resources/manifest.json`
- **UI definitions**: `hyvexa-parkour/src/main/resources/Common/UI/Custom/Pages/*.ui`
- **Server assets**: `hyvexa-parkour/src/main/resources/Server/...`

### Runtime Data
- **Working dir**: Server runs from `run/`, runtime data at `mods/Parkour/`
- **Database config**: `mods/Parkour/database.json` (MySQL credentials, gitignored)
- **World names**: Hub routing expects capitalized worlds: `Hub`, `Parkour`, `Ascend`

## Current Features

### Player Commands
| Command | Function |
|---------|----------|
| `/pk` | Category-first map selector |
| `/pk leaderboard` | Global + per-map best times |
| `/pk stats` | Player XP, level, rank |
| `/cp [set\|clear]` | Personal checkpoint (memory-only) |
| `/menu` | Open hub mode selector |

### Ascend Commands
| Command | Function |
|---------|----------|
| `/ascend` | Ascend map selector UI |
| `/ascend collect` | Collect pending coins |
| `/ascend stats` | Show coin balance |
| `/as admin` | Ascend admin UI (OP only) |

### Admin Commands (OP only)
| Command | Function |
|---------|----------|
| `/pk admin` | Admin panel UI |
| `/pk admin rank give/remove <player> <vip\|founder>` | Rank management |
| `/dbtest` | Validate MySQL connection |

### World-Based HUD System
- **Hub world** → Hub HUD
- **Parkour world** → Parkour HUD with timer
- **Ascend world** → Ascend HUD with coins

### Runtime Behavior
- Checkpoint/finish detection with radius-based triggers
- Fall respawn after configurable timeout
- Void cutoff respawn at configured Y level
- Player collision disabled via `HitboxCollision` removal
- VIP/Founder rank perks: chat tags, nameplates, speed multipliers

## Build and Run

```bash
./gradlew build              # Build all modules
./gradlew stagePlugins       # Copy jars to run/mods
./gradlew collectPlugins     # Copy jars to build/libs
```

Server JAR location:
```
%USERPROFILE%/AppData/Roaming/Hytale/install/<patchline>/package/game/latest/Server/HytaleServer.jar
```

## Adding New Features

1. Check existing code for similar patterns
2. Create classes in appropriate subpackage (`data/`, `ui/`, `command/`, etc.)
3. Register commands/interactions/systems in plugin `setup()`
4. Add UI files in `src/main/resources/Common/UI/Custom/Pages/` if needed
5. Update `CHANGELOG.md` with feature description

## Reference Files

| For... | Read... |
|--------|---------|
| Code patterns | `docs/CODE_PATTERNS.md` |
| API gotchas | `docs/HYTALE_API.md` |
| Architecture | `docs/ARCHITECTURE.md` |
| Database schema | `docs/DATABASE.md` |
| Ascend mode | `docs/ASCEND_MODULE_SUMMARY.md` |

## Code References

- `ParkourCommand.java` - command with subcommands
- `MapSelectPage.java` - UI page with pagination
- `MapStore.java` - MySQL persistence pattern
- `DatabaseManager.java` - connection pool management
